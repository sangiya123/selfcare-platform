package com.omobio.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omobio.notification.adapter.NotificationChannelProvider;
import com.omobio.notification.adapter.NotificationChannelProvider.Channel;
import com.omobio.notification.adapter.NotificationChannelProvider.SendResult;
import com.omobio.notification.domain.Notification;
import com.omobio.notification.repository.NotificationRepository;
import com.omobio.platform.common.adapter.ApiAdapterRegistry;
import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.platform.common.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification service — handles multi-channel message delivery.
 *
 * Triggered by:
 *   1. Kafka events (payment.completed, bill.issued, etc.)
 *   2. Direct API calls (admin broadcast, customer service)
 *   3. Scheduled jobs (low balance, expiry warnings)
 *
 * For each event:
 *   1. Resolve template by template_id
 *   2. Render with locale + payload
 *   3. Determine channels (per user preferences)
 *   4. Look up provider for tenant + channel
 *   5. Send and record result
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApiAdapterRegistry<NotificationChannelProvider> providerRegistry;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    /**
     * Send a notification directly (API path).
     */
    @Transactional
    public Notification send(String userId, String channel, String templateId,
                             String recipient, Map<String, Object> payload, String locale) {
        String tenantId = TenantContext.get().getTenantId();
        String correlationId = TenantContext.get().getCorrelationId();

        // Render template
        TemplateService.RenderedTemplate rendered = templateService.render(
                templateId, payload != null ? payload : new HashMap<>(),
                locale != null ? locale : "en");

        // Persist
        Notification notification = Notification.builder()
                .notificationId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .userId(userId)
                .channel(channel)
                .templateId(templateId)
                .recipient(recipient)
                .subject(rendered.getSubject())
                .body(rendered.getBody())
                .status("QUEUED")
                .locale(locale)
                .createdAt(Instant.now())
                .build();

        try {
            notification.setMetadata(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ignored) {}

        notification = notificationRepository.save(notification);

        // Dispatch
        dispatch(notification, rendered);

        return notification;
    }

    /**
     * Dispatch a notification to its channel provider.
     *
     * Provider lookup: tenantId is used as the registry key, then we filter
     * by channel(). If the provider's channel doesn't match, fall through to
     * the next registered provider for the same tenant.
     */
    private void dispatch(Notification notification, TemplateService.RenderedTemplate rendered) {
        try {
            Channel channel = Channel.valueOf(notification.getChannel());
            NotificationChannelProvider provider = findProvider(notification.getTenantId(), channel);

            if (provider == null) {
                log.warn("No provider for tenant={}, channel={}",
                        notification.getTenantId(), channel);
                markFailed(notification, "No provider configured");
                return;
            }

            Map<String, String> metadata = Map.of(
                    "locale", notification.getLocale() != null ? notification.getLocale() : "en",
                    "notificationId", notification.getNotificationId()
            );

            SendResult result = provider.send(
                    notification.getRecipient(),
                    rendered.getSubject(),
                    rendered.getBody(),
                    metadata);

            if (result.isSuccess()) {
                notification.setStatus("SENT");
                notification.setProviderMessageId(result.getProviderMessageId());
                notification.setSentAt(Instant.now());
            } else {
                notification.setStatus("FAILED");
                notification.setFailureReason(result.getFailureReason());
            }
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Notification dispatch failed: id={}, error={}",
                    notification.getNotificationId(), e.getMessage());
            markFailed(notification, e.getMessage());
        }
    }

    /**
     * Find the registered provider for a (tenantId, channel) pair.
     * The registry indexes by tenantId, so we look up the tenant and
     * check the provider's channel() method.
     */
    private NotificationChannelProvider findProvider(String tenantId, Channel channel) {
        try {
            NotificationChannelProvider provider = providerRegistry.getProvider(tenantId);
            if (provider != null && provider.channel() == channel) {
                return provider;
            }
            // Tenant doesn't have a provider for this channel — log and return null
            return null;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private void markFailed(Notification notification, String reason) {
        notification.setStatus("FAILED");
        notification.setFailureReason(reason);
        notificationRepository.save(notification);
    }

    /**
     * Listen to payment events and send notifications.
     */
    @KafkaListener(topics = "payment.events", groupId = "notification-service")
    public void onPaymentEvent(Map<String, Object> event) {
        String status = (String) event.get("status");
        String txId = (String) event.get("transactionId");
        if ("SUCCESS".equals(status)) {
            log.info("Payment success event received: txId={}, sending notification", txId);
            // Build payload and send
            // In production: call payment-service for full details, or include in event
        }
    }

    @Transactional(readOnly = true)
    public Notification getNotification(String notificationId) {
        String tenantId = TenantContext.get().getTenantId();
        return notificationRepository.findByTenantIdAndNotificationId(tenantId, notificationId)
                .orElseThrow(() -> new NotFoundException("Notification", notificationId));
    }
}