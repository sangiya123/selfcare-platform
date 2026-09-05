package com.omobio.account.service;

import com.omobio.account.domain.Account;
import com.omobio.account.domain.Connection;
import com.omobio.account.repository.AccountRepository;
import com.omobio.account.repository.ConnectionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ConflictException;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Account service — CRUD for Account entity.
 *
 * Manages:
 * - Account creation (on first login via MSISDN)
 * - Account lookup (by id, by primary identity)
 * - Profile version update (from Kafka events)
 * - Last login tracking
 * - Connection linking/unlinking (delegates to EntitlementService)
 * - Primary connection switching (updates both Account and Connection)
 *
 * @see EntitlementService for cross-connection authorization
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ConnectionRepository connectionRepository;
    private final EntitlementService entitlementService;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String LINKED_LIST_KEY_PREFIX = "omobio:linked:";
    private static final long MAX_LINK_LIMIT = 10;

    /**
     * Create a new account.
     *
     * @param primaryIdentity the primary identity (MSISDN for telco)
     * @param displayName     optional display name
     * @param email           optional email
     * @return the created account
     * @throws ConflictException if an account with this identity already exists
     */
    @Transactional
    public Account createAccount(String primaryIdentity, String displayName, String email) {
        String tenantId = TenantContext.get().getTenantId();

        if (accountRepository.existsByTenantIdAndPrimaryIdentity(tenantId, primaryIdentity)) {
            throw new ConflictException("Account already exists for primary identity: " + primaryIdentity);
        }

        Account account = Account.builder()
                .accountId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .primaryIdentity(primaryIdentity)
                .status("ACTIVE")
                .displayName(displayName)
                .email(email)
                .profileVersion(0L)
                .build();

        account = accountRepository.save(account);
        log.info("Account created: accountId={}, tenant={}, primary={}",
                account.getAccountId(), tenantId, primaryIdentity);
        return account;
    }

    /**
     * Get an account by ID.
     *
     * @param accountId the account ID
     * @return the account
     * @throws NotFoundException if not found
     */
    public Account getById(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account", accountId));
    }

    /**
     * Get an account by primary identity.
     *
     * @param primaryIdentity the primary identity (MSISDN)
     * @return the account
     * @throws NotFoundException if not found
     */
    public Account getByPrimaryIdentity(String primaryIdentity) {
        String tenantId = TenantContext.get().getTenantId();
        return accountRepository.findByTenantIdAndPrimaryIdentity(tenantId, primaryIdentity)
                .orElseThrow(() -> new NotFoundException("Account", "primaryIdentity=" + primaryIdentity));
    }

    /**
     * Get or create an account by primary identity.
     * Used during login to ensure an account exists for the authenticated MSISDN.
     *
     * @param primaryIdentity the primary identity
     * @return the existing or newly created account
     */
    @Transactional
    public Account getOrCreate(String primaryIdentity) {
        String tenantId = TenantContext.get().getTenantId();
        return accountRepository.findByTenantIdAndPrimaryIdentity(tenantId, primaryIdentity)
                .orElseGet(() -> {
                    log.info("Auto-creating account for primaryIdentity={}, tenant={}", primaryIdentity, tenantId);
                    return createAccount(primaryIdentity, null, null);
                });
    }

    /**
     * Update last login timestamp for an account.
     *
     * @param accountId the account ID
     */
    @Transactional
    public void recordLogin(String accountId) {
        Instant now = Instant.now();
        int updated = accountRepository.updateLastLoginAt(accountId, now);
        if (updated == 0) {
            throw new NotFoundException("Account", accountId);
        }
        log.debug("Login recorded: accountId={}", accountId);
    }

    /**
     * Update the profile version for an account.
     * Called when a Kafka profile.updated event is received.
     *
     * @param accountId      the account ID
     * @param profileVersion the new profile version
     */
    @Transactional
    public void updateProfileVersion(String accountId, Long profileVersion) {
        int updated = accountRepository.updateProfileVersion(accountId, profileVersion);
        if (updated == 0) {
            log.warn("Profile version update failed — account not found: {}", accountId);
        } else {
            log.info("Profile version updated: accountId={}, version={}", accountId, profileVersion);
        }
    }

    /**
     * Link a connection to an account.
     *
     * @param accountId   the account ID
     * @param connection  the connection to link
     * @return the saved connection
     * @throws ConflictException if the connection number is already linked
     * @throws IllegalStateException if the max link limit is reached
     */
    @Transactional
    public Connection linkConnection(String accountId, Connection connection) {
        Account account = getById(accountId);
        String tenantId = TenantContext.get().getTenantId();

        // Check for duplicate number
        if (connectionRepository.existsByTenantIdAndNumber(tenantId, connection.getNumber())) {
            throw new ConflictException("Connection number already linked: " + connection.getNumber());
        }

        // Check max link limit
        long linkedCount = connectionRepository.countByAccountIdAndRelationship(accountId, "LINKED");
        if (linkedCount >= MAX_LINK_LIMIT) {
            throw new IllegalStateException(
                    "Maximum link limit (" + MAX_LINK_LIMIT + ") reached for account: " + accountId);
        }

        connection.setAccountId(accountId);
        connection.setTenantId(tenantId);
        connection.setRelationship("LINKED");
        connection.setIsPrimary(false);
        connection.setLinkedAt(Instant.now());

        Connection saved = connectionRepository.save(connection);

        // Invalidate linked list cache
        entitlementService.invalidateLinkedListCache(accountId);

        log.info("Connection linked: accountId={}, connectionId={}, number={}, lob={}",
                accountId, saved.getConnectionId(), saved.getNumber(), saved.getLob());
        return saved;
    }

    /**
     * Unlink a connection from an account.
     *
     * @param accountId    the account ID
     * @param connectionId the connection ID to unlink
     * @throws NotFoundException if connection not found
     * @throws IllegalStateException if trying to unlink the primary connection
     */
    @Transactional
    public void unlinkConnection(String accountId, String connectionId) {
        Connection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("Connection", connectionId));

        if (!connection.getAccountId().equals(accountId)) {
            throw new NotFoundException("Connection", connectionId);
        }

        if (Boolean.TRUE.equals(connection.getIsPrimary())) {
            throw new IllegalStateException("Cannot unlink the primary connection. Switch primary first.");
        }

        connectionRepository.delete(connection);

        // Invalidate linked list cache
        entitlementService.invalidateLinkedListCache(accountId);

        log.info("Connection unlinked: accountId={}, connectionId={}", accountId, connectionId);
    }

    /**
     * Switch the primary connection for an account.
     *
     * @param accountId         the account ID
     * @param newPrimaryId      the connection ID to set as primary
     * @return the new primary connection
     * @throws NotFoundException if either account or connection not found
     * @throws IllegalArgumentException if the target connection is not a linked connection
     */
    @Transactional
    public Connection switchPrimaryConnection(String accountId, String newPrimaryId) {
        Account account = getById(accountId);
        Connection newPrimary = connectionRepository.findById(newPrimaryId)
                .orElseThrow(() -> new NotFoundException("Connection", newPrimaryId));

        if (!newPrimary.getAccountId().equals(accountId)) {
            throw new NotFoundException("Connection", newPrimaryId);
        }

        Instant now = Instant.now();

        // Reset all connections to LINKED
        connectionRepository.resetPrimaryFlag(accountId, false, now);

        // Set the new primary
        newPrimary.setIsPrimary(true);
        newPrimary.setRelationship("PRIMARY");
        newPrimary.setUpdatedAt(now);
        newPrimary = connectionRepository.save(newPrimary);

        // Update account's primary identity
        account.setPrimaryIdentity(newPrimary.getNumber());
        account.setUpdatedAt(now);
        accountRepository.save(account);

        // Invalidate linked list cache
        entitlementService.invalidateLinkedListCache(accountId);

        log.info("Primary switched: accountId={}, oldPrimary={}, newPrimary={}, newIdentity={}",
                accountId, account.getPrimaryIdentity(), newPrimaryId, newPrimary.getNumber());
        return newPrimary;
    }

    /**
     * List all accounts for the current tenant (admin use).
     *
     * @return list of accounts
     */
    public List<Account> listAccounts() {
        String tenantId = TenantContext.get().getTenantId();
        return accountRepository.findByTenantId(tenantId);
    }

    /**
     * Update account status.
     *
     * @param accountId the account ID
     * @param status    the new status (ACTIVE, SUSPENDED, CLOSED)
     */
    @Transactional
    public void updateStatus(String accountId, String status) {
        Account account = getById(accountId);
        account.setStatus(status);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);
        log.info("Account status updated: accountId={}, status={}", accountId, status);
    }
}
