package com.selfcare.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.platform.common.adapter.NotificationChannelProvider;
import com.selfcare.platform.common.adapter.NotificationChannelProvider.Channel;
import com.selfcare.platform.common.adapter.NotificationChannelProvider.SendResult;
import com.selfcare.notification.domain.Notification;
import com.selfcare.notification.repository.NotificationRepository;
import com.selfcare.platform.common.adapter.ApiAdapterRegistry;
import com.selfcare.platform.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService.
 *
 * Verifies:
 * - send (SMS, EMAIL, PUSH, IN_APP channels)
 * - unsupported channel throws exception
 * - getRecord returns notification by ID
 * - markDelivered updates status
 * - Template rendering integration
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ApiAdapterRegistry<NotificationChannelProvider> providerRegistry;
    @Mock private TemplateService templateService;
    @Mock private NotificationChannelProvider smsProvider;
    @Mock private NotificationChannelProvider emailProvider;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new NotificationService(notificationRepository, providerRegistry,
                templateService, objectMapper);

        TenantContext ctx = new TenantContext();
        ctx.setTenantId("dialog-lk");
        ctx.setCorrelationId("corr-123");
        TenantContext.set(ctx);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ======================================================================
    // send — SMS
    // ======================================================================

    @Test
    @DisplayName("send SMS persists notification with QUEUED status then marks SENT on success")
    void send_sms_success() {
        // Template rendering
        when(templateService.render(eq("welcome-sms"), anyMap(), eq("en")))
                .thenReturn(new TemplateService.RenderedTemplate(
                        null, "Welcome to Dialog!", "welcome-sms", "en"));

        // Provider lookup
        when(providerRegistry.getProvider("dialog-lk")).thenReturn(smsProvider);
        when(smsProvider.channel()).thenReturn(Channel.SMS);
        when(smsProvider.send(anyString(), any(), any(), anyMap()))
                .thenReturn(new SendResult(true, "msg-id-123", null, null));

        // Repository
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1",
                "SMS",
                "welcome-sms",
                "94771123456",
                Map.of("name", "John"),
                "en");

        assertThat(result.getChannel()).isEqualTo("SMS");
        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getProviderMessageId()).isEqualTo("msg-id-123");
        assertThat(result.getBody()).isEqualTo("Welcome to Dialog!");
    }

    // ======================================================================
    // send — EMAIL
    // ======================================================================

    @Test
    @DisplayName("send EMAIL with subject and body")
    void send_email_success() {
        when(templateService.render(eq("welcome-email"), anyMap(), eq("en")))
                .thenReturn(new TemplateService.RenderedTemplate(
                        "Welcome to Dialog!", "Your account is ready.", "welcome-email", "en"));

        when(providerRegistry.getProvider("dialog-lk")).thenReturn(emailProvider);
        when(emailProvider.channel()).thenReturn(Channel.EMAIL);
        when(emailProvider.send(anyString(), any(), any(), anyMap()))
                .thenReturn(new SendResult(true, "email-msg-id", null, null));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1", "EMAIL", "welcome-email",
                "john@example.com", Map.of("name", "John"), "en");

        assertThat(result.getChannel()).isEqualTo("EMAIL");
        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getSubject()).isEqualTo("Welcome to Dialog!");
    }

    // ======================================================================
    // send — PUSH
    // ======================================================================

    @Test
    @DisplayName("send PUSH notification")
    void send_push_success() {
        when(templateService.render(eq("alert-push"), anyMap(), eq("en")))
                .thenReturn(new TemplateService.RenderedTemplate(
                        "Alert", "Your bill is ready.", "alert-push", "en"));

        NotificationChannelProvider pushProvider = mock(NotificationChannelProvider.class);
        when(providerRegistry.getProvider("dialog-lk")).thenReturn(pushProvider);
        when(pushProvider.channel()).thenReturn(Channel.PUSH);
        when(pushProvider.send(anyString(), any(), any(), anyMap()))
                .thenReturn(new SendResult(true, "push-id-456", null, null));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1", "PUSH", "alert-push",
                "device-token-xyz", Map.of("billAmount", 1500), "en");

        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getChannel()).isEqualTo("PUSH");
    }

    // ======================================================================
    // send — Unsupported channel
    // ======================================================================

    @Test
    @DisplayName("send marks notification FAILED for unknown channel")
    void send_unsupportedChannel() {
        when(templateService.render(any(), any(), any())).thenReturn(
                new TemplateService.RenderedTemplate(null, "body", "template", "en"));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1", "TELEGRAM", "template", "recipient", null, "en");

        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    // ======================================================================
    // send — Provider failure → FAILED
    // ======================================================================

    @Test
    @DisplayName("send marks notification FAILED when provider throws")
    void send_providerFailure() {
        when(templateService.render(any(), any(), any())).thenReturn(
                new TemplateService.RenderedTemplate(null, "body", "template", "en"));
        when(providerRegistry.getProvider("dialog-lk")).thenReturn(smsProvider);
        when(smsProvider.channel()).thenReturn(Channel.SMS);
        when(smsProvider.send(anyString(), any(), any(), anyMap()))
                .thenThrow(new RuntimeException("SMS gateway unreachable"));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1", "SMS", "template", "94771123456", null, "en");

        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    // ======================================================================
    // getRecord
    // ======================================================================

    @Test
    @DisplayName("getNotification returns notification by ID")
    void getNotification_found() {
        Notification notification = Notification.builder()
                .notificationId("notif-1")
                .tenantId("dialog-lk")
                .userId("user-1")
                .channel("SMS")
                .status("SENT")
                .build();
        when(notificationRepository.findByTenantIdAndNotificationId("dialog-lk", "notif-1"))
                .thenReturn(Optional.of(notification));

        Notification result = service.getNotification("notif-1");

        assertThat(result.getNotificationId()).isEqualTo("notif-1");
    }

    @Test
    @DisplayName("getNotification throws NotFoundException for unknown ID")
    void getNotification_notFound() {
        when(notificationRepository.findByTenantIdAndNotificationId("dialog-lk", "unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotification("unknown"))
                .isInstanceOf(com.selfcare.platform.common.web.NotFoundException.class);
    }

    // ======================================================================
    // markDelivered
    // ======================================================================

    @Test
    @DisplayName("markDelivered updates notification status to DELIVERED")
    void markDelivered_success() {
        Notification notification = Notification.builder()
                .notificationId("notif-1")
                .tenantId("dialog-lk")
                .status("SENT")
                .build();
        when(notificationRepository.findByTenantIdAndNotificationId("dialog-lk", "notif-1"))
                .thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.markDelivered("notif-1");

        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(result.getDeliveredAt()).isNotNull();
    }

    // ======================================================================
    // IN_APP channel
    // ======================================================================

    @Test
    @DisplayName("send IN_APP notification")
    void send_inApp_success() {
        when(templateService.render(any(), any(), any())).thenReturn(
                new TemplateService.RenderedTemplate(null, "You have a new message", "new-message", "en"));

        NotificationChannelProvider inAppProvider = mock(NotificationChannelProvider.class);
        when(providerRegistry.getProvider("dialog-lk")).thenReturn(inAppProvider);
        when(inAppProvider.channel()).thenReturn(Channel.IN_APP);
        when(inAppProvider.send(anyString(), any(), any(), anyMap()))
                .thenReturn(new SendResult(true, "inapp-id", null, null));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.send(
                "user-1", "IN_APP", "new-message",
                "user-1", null, "en");

        assertThat(result.getStatus()).isEqualTo("SENT");
        assertThat(result.getChannel()).isEqualTo("IN_APP");
    }
}
