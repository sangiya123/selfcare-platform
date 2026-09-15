package com.selfcare.account.service;

import com.selfcare.account.domain.Account;
import com.selfcare.account.domain.Connection;
import com.selfcare.account.repository.AccountRepository;
import com.selfcare.account.repository.ConnectionRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ConflictException;
import com.selfcare.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountService.
 *
 * Tests account creation, lookup, linking, and primary switching
 * using TenantContext to simulate multi-tenant isolation.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private EntitlementService entitlementService;
    @Mock private RedisTemplate<String, String> redisTemplate;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, connectionRepository,
                entitlementService, redisTemplate);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // createAccount
    // ======================================================================

    @Test
    @DisplayName("createAccount succeeds for new primary identity")
    void createAccount_success() {
        when(accountRepository.existsByTenantIdAndPrimaryIdentity("dialog-lk", "94771123456"))
                .thenReturn(false);
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(inv -> {
                    Account a = inv.getArgument(0);
                    return a;
                });

        Account result = service.createAccount("94771123456", "John Doe", "john@example.com");

        assertThat(result.getPrimaryIdentity()).isEqualTo("94771123456");
        assertThat(result.getDisplayName()).isEqualTo("John Doe");
        assertThat(result.getEmail()).isEqualTo("john@example.com");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getTenantId()).isEqualTo("dialog-lk");
        assertThat(result.getAccountId()).isNotBlank();
    }

    @Test
    @DisplayName("createAccount throws ConflictException when identity exists")
    void createAccount_conflict() {
        when(accountRepository.existsByTenantIdAndPrimaryIdentity("dialog-lk", "94771123456"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createAccount("94771123456", "John", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("94771123456");

        verify(accountRepository, never()).save(any());
    }

    // ======================================================================
    // getAccount
    // ======================================================================

    @Test
    @DisplayName("getById returns account when found")
    void getById_found() {
        Account acc = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .primaryIdentity("94771123456")
                .build();
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(acc));

        Account result = service.getById("acc-1");

        assertThat(result.getAccountId()).isEqualTo("acc-1");
    }

    @Test
    @DisplayName("getById throws NotFoundException when not found")
    void getById_notFound() {
        when(accountRepository.findById("acc-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("acc-99"))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // linkConnection
    // ======================================================================

    @Test
    @DisplayName("linkConnection adds connection to account")
    void linkConnection_success() {
        Account acc = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .primaryIdentity("94771123456")
                .build();
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(acc));
        when(connectionRepository.save(any(Connection.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Connection result = service.linkConnection("acc-1",
                Connection.builder().number("94772222333").lob("MOBILE").relationship("LINKED").build());

        assertThat(result.getAccountId()).isEqualTo("acc-1");
        assertThat(result.getNumber()).isEqualTo("94772222333");
        verify(connectionRepository).save(any(Connection.class));
    }

    @Test
    @DisplayName("linkConnection throws NotFoundException when account missing")
    void linkConnection_accountNotFound() {
        when(accountRepository.findById("acc-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkConnection("acc-99",
                Connection.builder().build()))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // switchPrimaryConnection
    // ======================================================================

    @Test
    @DisplayName("switchPrimaryConnection updates both account and connection")
    void switchPrimaryConnection_success() {
        Account acc = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .primaryIdentity("94771123456")
                .build();
        Connection newPrimary = Connection.builder()
                .connectionId("conn-2")
                .accountId("acc-1")
                .isPrimary(false)
                .number("94772222333")
                .build();

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(acc));
        when(connectionRepository.findById("conn-2")).thenReturn(Optional.of(newPrimary));
        when(connectionRepository.save(any(Connection.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.switchPrimaryConnection("acc-1", "conn-2");

        // New primary should be marked as primary
        assertThat(newPrimary.getIsPrimary()).isTrue();
        // Old primary flag cleared via bulk repository update
        verify(connectionRepository).resetPrimaryFlag(eq("acc-1"), eq(false), any(Instant.class));
    }

    @Test
    @DisplayName("switchPrimaryConnection throws NotFoundException when target belongs to another account")
    void switchPrimaryConnection_crossAccount() {
        Account acc = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .build();
        Connection conn = Connection.builder()
                .connectionId("conn-other")
                .accountId("acc-other")
                .build();

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(acc));
        when(connectionRepository.findById("conn-other")).thenReturn(Optional.of(conn));

        assertThatThrownBy(() -> service.switchPrimaryConnection("acc-1", "conn-other"))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // unlinkConnection
    // ======================================================================

    @Test
    @DisplayName("unlinkConnection removes connection and invalidates cache")
    void unlinkConnection_success() {
        Account acc = Account.builder().accountId("acc-1").tenantId("dialog-lk").build();
        Connection conn = Connection.builder()
                .connectionId("conn-2")
                .accountId("acc-1")
                .build();

        when(connectionRepository.findById("conn-2")).thenReturn(Optional.of(conn));

        service.unlinkConnection("acc-1", "conn-2");

        verify(connectionRepository).delete(conn);
        verify(entitlementService).invalidateLinkedListCache("acc-1");
    }

    @Test
    @DisplayName("unlinkConnection throws NotFoundException when connection belongs to another account")
    void unlinkConnection_notFound() {
        Connection conn = Connection.builder()
                .connectionId("conn-other")
                .accountId("acc-other")
                .build();

        when(connectionRepository.findById("conn-other")).thenReturn(Optional.of(conn));

        assertThatThrownBy(() -> service.unlinkConnection("acc-1", "conn-other"))
                .isInstanceOf(NotFoundException.class);
    }

    // ======================================================================
    // getLinkedConnections
    // ======================================================================

    @Test
    @DisplayName("getLinkedConnections returns linked connections")
    void getLinkedConnections_returnsConnections() {
        Connection c1 = Connection.builder().connectionId("conn-1").number("94771123456").build();
        Connection c2 = Connection.builder().connectionId("conn-2").number("94772222333").build();
        when(connectionRepository.findByAccountIdAndRelationship("acc-1", "LINKED"))
                .thenReturn(List.of(c1, c2));

        EntitlementService entitlement = new EntitlementService(
                accountRepository, connectionRepository, redisTemplate);
        List<Connection> result = entitlement.getLinkedConnections("acc-1");

        assertThat(result).hasSize(2);
        verify(connectionRepository).findByAccountIdAndRelationship("acc-1", "LINKED");
    }
}
