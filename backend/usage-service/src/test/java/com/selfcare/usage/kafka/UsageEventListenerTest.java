package com.selfcare.usage.kafka;

import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.usage.service.AllowanceService;
import com.selfcare.usage.service.UsageHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageEventListenerTest {

    @Mock private AllowanceService allowanceService;
    @Mock private UsageHistoryService usageHistoryService;
    private UsageEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new UsageEventListener(allowanceService, usageHistoryService, null);
        TenantContext.current().setTenantId("t1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("onUsageEvent records consumption when USAGE_RECORDED event has allowanceId")
    void onUsageEvent_recordsConsumption() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "USAGE_RECORDED");
        event.put("tenantId", "t1");
        event.put("connectionId", "conn-1");
        event.put("allowanceId", "alw-x");
        event.put("dataBytesDelta", 1024L);

        listener.onUsageEvent(event);

        verify(allowanceService).recordConsumption("conn-1", "alw-x", 1024L);
    }

    @Test
    @DisplayName("onUsageEvent ignores non-USAGE_RECORDED events")
    void onUsageEvent_ignoresOtherEvents() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "SOME_OTHER_EVENT");
        event.put("tenantId", "t1");
        event.put("connectionId", "conn-1");

        listener.onUsageEvent(event);

        verify(allowanceService, never()).recordConsumption(any(), any(), anyLong());
    }

    @Test
    @DisplayName("onRechargeEvent creates allowance on RECHARGE_COMPLETED")
    void onRechargeEvent_createsAllowance() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "RECHARGE_COMPLETED");
        event.put("tenantId", "t1");
        event.put("connectionId", "conn-1");
        event.put("allowanceId", "alw-new");
        event.put("allowanceType", "DATA");
        event.put("packageName", "Daily 1GB");
        event.put("totalUnits", 1_000_000_000L);
        event.put("unit", "BYTES");

        listener.onRechargeEvent(event);

        verify(allowanceService).upsertAllowance(argThat(a ->
                a.getAllowanceId().equals("alw-new") &&
                a.getConnectionId().equals("conn-1") &&
                a.getTotalUnits() == 1_000_000_000L &&
                "RECHARGE".equals(a.getSource())
        ));
    }

    @Test
    @DisplayName("onRechargeEvent ignores PACKAGE_ACTIVATED without totalUnits")
    void onRechargeEvent_requiresTotalUnits() {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "PACKAGE_ACTIVATED");
        event.put("tenantId", "t1");
        event.put("connectionId", "conn-1");
        // No totalUnits

        listener.onRechargeEvent(event);

        verify(allowanceService, never()).upsertAllowance(any());
    }
}
