package com.omobio.account.service;

import com.omobio.account.domain.Account;
import com.omobio.account.domain.Connection;
import com.omobio.account.repository.AccountRepository;
import com.omobio.account.repository.ConnectionRepository;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.ForbiddenException;
import com.omobio.platform.common.web.NotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private ConnectionRepository connectionRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private SetOperations<String, String> setOps;

    private EntitlementService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new EntitlementService(accountRepository, connectionRepository, redisTemplate);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- authorizeConnectionAction ---

    @Test
    @DisplayName("Redis cache hit: connection is in the linked list → no DB lookup")
    void authorize_redisHit() {
        when(setOps.isMember("omobio:linked:acc-1", "conn-2")).thenReturn(true);

        service.authorizeConnectionAction("acc-1", "conn-2", "PAY_BILL");

        // No DB calls expected
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(connectionRepository);
    }

    @Test
    @DisplayName("Redis miss + DB target belongs to actor → allowed")
    void authorize_dbLookupSuccess() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        Account account = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .build();
        Connection target = Connection.builder()
                .connectionId("conn-2")
                .accountId("acc-1") // belongs to acc-1
                .tenantId("dialog-lk")
                .build();

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(connectionRepository.findById("conn-2")).thenReturn(Optional.of(target));

        service.authorizeConnectionAction("acc-1", "conn-2", "PAY_BILL");

        // No exception thrown
    }

    @Test
    @DisplayName("Redis miss + DB target NOT in actor's connections → ForbiddenException")
    void authorize_forbidden() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        Account account = Account.builder()
                .accountId("acc-1")
                .tenantId("dialog-lk")
                .build();
        Connection target = Connection.builder()
                .connectionId("conn-99")
                .accountId("acc-other") // does NOT belong to acc-1
                .tenantId("dialog-lk")
                .build();

        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(account));
        when(connectionRepository.findById("conn-99")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.authorizeConnectionAction("acc-1", "conn-99", "PAY_BILL"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Unknown target connection → NotFoundException")
    void authorize_targetNotFound() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(
                Account.builder().accountId("acc-1").tenantId("dialog-lk").build()));
        when(connectionRepository.findById("conn-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorizeConnectionAction("acc-1", "conn-99", "PAY_BILL"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Connection");
    }

    @Test
    @DisplayName("Unknown actor account → NotFoundException")
    void authorize_actorNotFound() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(accountRepository.findById("acc-99")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorizeConnectionAction("acc-99", "conn-2", "PAY_BILL"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Account");
    }
}
