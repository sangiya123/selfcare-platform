package com.omobio.reporting.service;

import com.omobio.platform.common.tenant.TenantContext;
import com.omobio.reporting.domain.ReportDefinition;
import com.omobio.reporting.repository.ReportDefinitionRepository;
import com.omobio.reporting.repository.ReportExecutionRepository;
import com.omobio.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Scheduler for recurring reports.
 *
 * On startup, scans for ACTIVE report definitions with cron schedules and
 * registers them with the Spring {@link TaskScheduler}.
 *
 * Every minute, also checks for any newly-created ACTIVE scheduled reports
 * that aren't yet registered and registers them. Stale registrations are
 * not removed automatically (TODO: add a cancellation mechanism).
 *
 * @see ReportDefinition
 * @see ReportService#triggerExecution
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class ReportScheduler {

    private final ReportDefinitionRepository definitionRepository;
    private final ReportExecutionRepository executionRepository;
    private final ReportService reportService;
    private final TaskScheduler taskScheduler;

    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * Initial scan on application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("ReportScheduler initializing...");
        refreshSchedules();
    }

    /**
     * Periodic refresh — runs every minute, picks up newly created/modified schedules.
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void refreshSchedules() {
        try {
            List<ReportDefinition> scheduled = definitionRepository.findAllScheduledActive();
            log.debug("Found {} scheduled report definitions", scheduled.size());
            for (ReportDefinition def : scheduled) {
                if (!scheduledTasks.containsKey(def.getId())) {
                    register(def);
                }
            }
        } catch (Exception e) {
            log.error("Failed to refresh schedules", e);
        }
    }

    /**
     * Register a cron-based schedule for a report definition.
     */
    private void register(ReportDefinition definition) {
        try {
            CronTrigger trigger = new CronTrigger(definition.getSchedule());
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> runScheduledReport(definition.getId()), trigger);
            scheduledTasks.put(definition.getId(), future);
            log.info("Scheduled report: id={}, name={}, cron='{}'",
                    definition.getId(), definition.getName(), definition.getSchedule());
        } catch (Exception e) {
            log.error("Failed to register schedule for report {}: {}",
                    definition.getId(), e.getMessage());
        }
    }

    /**
     * Cancel a previously registered schedule.
     */
    public void cancelSchedule(String reportId) {
        ScheduledFuture<?> future = scheduledTasks.remove(reportId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled schedule for report: {}", reportId);
        }
    }

    /**
     * Run a scheduled report. Bound to a tenant context based on the report's tenant.
     */
    private void runScheduledReport(String reportId) {
        try {
            ReportDefinition def = definitionRepository.findById(reportId).orElse(null);
            if (def == null) {
                log.warn("Scheduled report not found, cancelling: {}", reportId);
                cancelSchedule(reportId);
                return;
            }

            // Bind tenant context
            TenantContext ctx = TenantContext.get();
            ctx.setTenantId(def.getTenantId());
            TenantContext.set(ctx);

            log.info("Running scheduled report: id={}, name={}", def.getId(), def.getName());
            reportService.triggerExecution(reportId, "{}", "scheduler");

        } catch (Exception e) {
            log.error("Scheduled report execution failed: reportId={}", reportId, e);
        } finally {
            TenantContext.clear();
        }
    }
}
