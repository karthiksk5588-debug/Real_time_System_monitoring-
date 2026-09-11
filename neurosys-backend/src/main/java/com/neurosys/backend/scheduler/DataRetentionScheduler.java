package com.neurosys.backend.scheduler;

import com.neurosys.backend.repository.DiagnosticEventRepository;
import com.neurosys.backend.repository.SystemLogRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final SystemMetricRepository metricRepository;
    private final DiagnosticEventRepository diagnosticEventRepository;
    private final SystemLogRepository systemLogRepository;

    @Value("${neurosys.retention.metrics-hours:24}")
    private int metricsRetentionHours;

    @Value("${neurosys.retention.logs-days:7}")
    private int logsRetentionDays;

    // Run automatically on backend startup
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCleanup() {
        log.info("[RETENTION] Initializing database volume retention check on startup...");
        performRetentionCleanup();
    }

    // Run automatically every hour at minute 0
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void scheduledRetentionCleanup() {
        performRetentionCleanup();
    }

    @Transactional
    public Map<String, Object> performRetentionCleanup() {
        Map<String, Object> stats = new HashMap<>();
        try {
            Instant metricCutoff = Instant.now().minus(metricsRetentionHours, ChronoUnit.HOURS);
            Instant logCutoff = Instant.now().minus(logsRetentionDays, ChronoUnit.DAYS);

            int deletedMetrics = metricRepository.deleteMetricsOlderThan(metricCutoff);
            int deletedEvents = diagnosticEventRepository.deleteEventsOlderThan(logCutoff);
            int deletedLogs = systemLogRepository.deleteLogsOlderThan(logCutoff);

            stats.put("deletedMetrics", deletedMetrics);
            stats.put("deletedEvents", deletedEvents);
            stats.put("deletedLogs", deletedLogs);
            stats.put("metricCutoff", metricCutoff.toString());
            stats.put("logCutoff", logCutoff.toString());

            log.info("[RETENTION CLEANUP] Successfully purged {} metrics (>{}h), {} diagnostic events (>{}d), {} system logs (>{}d) from MySQL volume.",
                    deletedMetrics, metricsRetentionHours, deletedEvents, logsRetentionDays, deletedLogs, logsRetentionDays);

        } catch (Exception e) {
            log.error("[RETENTION CLEANUP ERROR] Failed to perform database volume cleanup: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}
