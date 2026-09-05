package com.omobio.account.service;

import com.omobio.account.domain.Account;
import com.omobio.account.domain.Connection;
import com.omobio.account.repository.AccountRepository;
import com.omobio.account.repository.ConnectionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ForbiddenException;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Entitlement service — enforces cross-connection authorization.
 *
 * ADR-006: Dialog entitlement = primary identity's linked connection list
 *   - A requested connection is authorized when it is in the currently
 *     logged-in primary mobile number's linked connection list
 *   - NOT a same-NIC ownership check
 *   - Linked list comes from operator's profile system, fed via Kafka
 *
 * Performs actor + action + target + context checks for all operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final AccountRepository accountRepository;
    private final ConnectionRepository connectionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String LINKED_LIST_KEY_PREFIX = "omobio:linked:";
    private static final Duration LINKED_LIST_TTL = Duration.ofHours(6);

    /**
     * Authorize that the target connection is in the account's linked list.
     *
     * @param accountId The currently authenticated account
     * @param targetConnectionId The connection being acted upon
     * @param action The action being attempted (PAY_BILL, RECHARGE, etc.)
     * @throws ForbiddenException if target is not in the account's linked list
     */
    public void authorizeConnectionAction(String accountId, String targetConnectionId, String action) {
        // Check Redis cache first
        String key = LINKED_LIST_KEY_PREFIX + accountId;
        Boolean isMember = redisTemplate.opsForSet().isMember(key, targetConnectionId);

        if (Boolean.TRUE.equals(isMember)) {
            log.debug("Entitlement check (cache hit): account={}, target={}, action={}",
                    accountId, targetConnectionId, action);
            return;
        }

        // Fallback to MySQL
        log.debug("Entitlement check (DB lookup): account={}, target={}, action={}",
                accountId, targetConnectionId, action);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account", accountId));

        Optional<Connection> targetConn = connectionRepository.findById(targetConnectionId);
        if (targetConn.isEmpty()) {
            throw new NotFoundException("Connection", targetConnectionId);
        }

        Connection conn = targetConn.get();

        // Verify target belongs to this account
        if (!conn.getAccountId().equals(account.getAccountId())) {
            log.warn("FORBIDDEN: connection {} does not belong to account {} (action={})",
                    targetConnectionId, accountId, action);
            throw new ForbiddenException(action, targetConnectionId);
        }

        // Verify connection is active
        if (!"ACTIVE".equals(conn.getStatus())) {
            log.warn("FORBIDDEN: connection {} is not active (status={}, action={})",
                    targetConnectionId, conn.getStatus(), action);
            throw new ForbiddenException(action, targetConnectionId);
        }

        // Cache for next time
        redisTemplate.opsForSet().add(key, targetConnectionId);
        redisTemplate.expire(key, LINKED_LIST_TTL);
    }

    /**
     * Get all linked connections for an account.
     */
    public List<Connection> getLinkedConnections(String accountId) {
        return connectionRepository.findByAccountIdAndRelationship(accountId, "LINKED");
    }

    /**
     * Get the primary connection for an account.
     */
    public Connection getPrimaryConnection(String accountId) {
        return connectionRepository.findByAccountIdAndIsPrimary(accountId, Boolean.TRUE)
                .orElseThrow(() -> new NotFoundException("PrimaryConnection", "account=" + accountId));
    }

    /**
     * Invalidate the Redis cache for an account's linked connections.
     * Called when a Kafka profile/connection-changed event is received.
     */
    public void invalidateLinkedListCache(String accountId) {
        String key = LINKED_LIST_KEY_PREFIX + accountId;
        redisTemplate.delete(key);
        log.info("Invalidated linked-list cache for account: {}", accountId);
    }

    /**
     * Add a connection to the account.
     */
    @Transactional
    public Connection addConnection(String accountId, Connection connection) {
        connection.setAccountId(accountId);
        connection.setTenantId(TenantContext.get().getTenantId());

        Connection saved = connectionRepository.save(connection);
        invalidateLinkedListCache(accountId);
        log.info("Added connection: account={}, connectionId={}, number={}",
                accountId, saved.getConnectionId(), saved.getNumber());
        return saved;
    }

    /**
     * Remove a connection from the account.
     */
    @Transactional
    public void removeConnection(String accountId, String connectionId) {
        Connection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("Connection", connectionId));

        if (!conn.getAccountId().equals(accountId)) {
            throw new ForbiddenException("REMOVE_CONNECTION", connectionId);
        }

        connectionRepository.delete(conn);
        invalidateLinkedListCache(accountId);
        log.info("Removed connection: account={}, connectionId={}", accountId, connectionId);
    }

    /**
     * Switch the active connection for the current session.
     * Returns the connection if authorized, throws ForbiddenException otherwise.
     */
    public Connection switchActiveConnection(String accountId, String connectionId) {
        authorizeConnectionAction(accountId, connectionId, "SWITCH_ACTIVE");
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("Connection", connectionId));
    }
}