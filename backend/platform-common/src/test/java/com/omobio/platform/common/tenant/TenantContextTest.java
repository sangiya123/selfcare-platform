package com.omobio.platform.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getTenantId returns UNKNOWN when no context is set")
    void getTenantId_unknownByDefault() {
        assertThat(TenantContext.get().getTenantId()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("set/get round-trip preserves tenantId, userId, and correlationId")
    void setGetRoundTrip() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setUserId("user-123");
        ctx.setCorrelationId("corr-abc");
        ctx.setSessionId("sess-xyz");
        ctx.setEnvironment("stg");
        TenantContext.set(ctx);

        TenantContext actual = TenantContext.get();
        assertThat(actual.getTenantId()).isEqualTo("dialog-lk");
        assertThat(actual.getUserId()).isEqualTo("user-123");
        assertThat(actual.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(actual.getSessionId()).isEqualTo("sess-xyz");
        assertThat(actual.getEnvironment()).isEqualTo("stg");
    }

    @Test
    @DisplayName("clear removes the context for the current thread")
    void clear() {
        TenantContext ctx = new TenantContext();
        ctx.setTenantId("aia-lk");
        TenantContext.set(ctx);
        assertThat(TenantContext.get().getTenantId()).isEqualTo("aia-lk");

        TenantContext.clear();

        assertThat(TenantContext.get().getTenantId()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Contexts are thread-local — different threads don't see each other's data")
    void threadLocal() throws Exception {
        TenantContext mainCtx = new TenantContext();
        mainCtx.setTenantId("main-thread");
        TenantContext.set(mainCtx);

        String[] otherThreadTenant = new String[1];
        Thread other = new Thread(() -> {
            // Different thread — should not see main-thread's context.
            otherThreadTenant[0] = TenantContext.get().getTenantId();
        });
        other.start();
        other.join();

        assertThat(otherThreadTenant[0]).isEqualTo("UNKNOWN");
        assertThat(TenantContext.get().getTenantId()).isEqualTo("main-thread");
    }
}
