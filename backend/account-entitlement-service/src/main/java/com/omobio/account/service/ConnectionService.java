package com.omobio.account.service;

import com.omobio.account.domain.Connection;
import com.omobio.account.repository.ConnectionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Connection service — read operations for connections.
 *
 * Provides read-only or read-heavy operations on connections:
 * - List all connections for an account
 * - Get by ID
 * - Get by number
 * - Filter by LOB
 * - Filter by status
 *
 * Write operations (link, unlink, switch primary) are in AccountService
 * and EntitlementService.
 *
 * @see AccountService for write operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final ConnectionRepository connectionRepository;

    /**
     * List all connections for an account.
     *
     * @param accountId the account ID
     * @return list of all connections (primary + linked)
     */
    public List<Connection> listConnections(String accountId) {
        return connectionRepository.findByAccountId(accountId);
    }

    /**
     * List connections for an account by relationship.
     *
     * @param accountId    the account ID
     * @param relationship the relationship (PRIMARY, LINKED)
     * @return list of matching connections
     */
    public List<Connection> listByRelationship(String accountId, String relationship) {
        return connectionRepository.findByAccountIdAndRelationship(accountId, relationship);
    }

    /**
     * List connections for an account filtered by status.
     *
     * @param accountId the account ID
     * @param status    the status (ACTIVE, SUSPENDED, DISCONNECTED)
     * @return list of matching connections
     */
    public List<Connection> listByStatus(String accountId, String status) {
        return connectionRepository.findByAccountIdAndStatus(accountId, status);
    }

    /**
     * List connections for an account filtered by LOB.
     *
     * @param accountId the account ID
     * @param lob       the line of business (MOBILE, BB, DTV, FIBRE)
     * @return list of matching connections
     */
    public List<Connection> listByLob(String accountId, String lob) {
        return connectionRepository.findByAccountIdAndLob(accountId, lob);
    }

    /**
     * Get a connection by its ID.
     *
     * @param connectionId the connection ID
     * @return the connection
     * @throws NotFoundException if not found
     */
    public Connection getById(String connectionId) {
        return connectionRepository.findById(connectionId)
                .orElseThrow(() -> new NotFoundException("Connection", connectionId));
    }

    /**
     * Get a connection by its number/MSISDN.
     *
     * @param number the connection number
     * @return the connection
     * @throws NotFoundException if not found
     */
    public Connection getByNumber(String number) {
        String tenantId = TenantContext.get().getTenantId();
        return connectionRepository.findByTenantIdAndNumber(tenantId, number)
                .orElseThrow(() -> new NotFoundException("Connection", "number=" + number));
    }

    /**
     * Get a connection by number, returning empty if not found.
     *
     * @param number the connection number
     * @return the connection, or empty
     */
    public Optional<Connection> findByNumber(String number) {
        String tenantId = TenantContext.get().getTenantId();
        return connectionRepository.findByTenantIdAndNumber(tenantId, number);
    }

    /**
     * Count the number of linked connections for an account.
     *
     * @param accountId the account ID
     * @return count of LINKED connections
     */
    public long countLinked(String accountId) {
        return connectionRepository.countByAccountIdAndRelationship(accountId, "LINKED");
    }

    /**
     * Check if a connection number exists for the current tenant.
     *
     * @param number the connection number
     * @return true if it exists
     */
    public boolean numberExists(String number) {
        String tenantId = TenantContext.get().getTenantId();
        return connectionRepository.existsByTenantIdAndNumber(tenantId, number);
    }
}
