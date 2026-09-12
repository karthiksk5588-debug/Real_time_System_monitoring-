package com.neurosys.backend.scheduler;

import com.neurosys.backend.repository.DiagnosticEventRepository;
import com.neurosys.backend.repository.SystemLogRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final SystemMetricRepository metricRepository;
    private final DiagnosticEventRepository diagnosticEventRepository;
    private final SystemLogRepository systemLogRepository;
    private final JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private final EntityManager entityManager;

    @Value("${neurosys.retention.metrics-hours:2}")
    private int metricsRetentionHours;

    @Value("${neurosys.retention.logs-days:7}")
    private int logsRetentionDays;

    // Run automatically on backend startup
    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCleanup() {
        log.info("[RETENTION] Initializing database volume retention check on startup (Retention: {}h metrics)...", metricsRetentionHours);
        performRetentionCleanup();
    }

    // Run automatically every 15 minutes
    @Scheduled(cron = "0 */15 * * * *")
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

            // Attempt to optimize tables if rows were deleted to reclaim free space
            if (deletedMetrics > 0 || deletedEvents > 0 || deletedLogs > 0) {
                List<String> optimized = optimizeTables();
                stats.put("optimizedTables", optimized);
            }

        } catch (Exception e) {
            log.error("[RETENTION CLEANUP ERROR] Failed to perform database volume cleanup: {}", e.getMessage(), e);
            stats.put("error", e.getMessage());
        }
        return stats;
    }

    public List<String> optimizeTables() {
        List<String> optimized = new ArrayList<>();
        String[] tables = {"system_metrics", "diagnostic_events", "system_logs", "predictions", "alerts"};
        for (String table : tables) {
            try {
                // Execute MySQL OPTIMIZE TABLE to shrink physical disk .ibd files
                jdbcTemplate.execute("OPTIMIZE TABLE " + table);
                optimized.add(table);
                log.info("[VOLUME OPTIMIZE] Successfully executed OPTIMIZE TABLE {} to reclaim disk volume space.", table);
            } catch (Exception e) {
                // Silently absorb for non-MySQL or non-supported DB engines (e.g. H2 test DB)
                log.debug("[VOLUME OPTIMIZE SKIP] Could not execute OPTIMIZE TABLE for {}: {}", table, e.getMessage());
            }
        }
        return optimized;
    }

    public Map<String, Object> truncateMetricsTable() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.warn("[EMERGENCY VOLUME RESET] Executing TRUNCATE TABLE system_metrics to instantly reclaim physical disk space...");
            jdbcTemplate.execute("TRUNCATE TABLE system_metrics");
            result.put("status", "SUCCESS");
            result.put("message", "Successfully truncated system_metrics table and reclaimed MySQL physical disk volume.");
            log.info("[EMERGENCY VOLUME RESET] system_metrics table truncated successfully.");
        } catch (Exception e) {
            log.warn("[EMERGENCY VOLUME RESET FALLBACK] TRUNCATE TABLE failed (disk full), falling back to chunked row purging: {}", e.getMessage());
            return chunkedPurgeMetrics();
        }
        return result;
    }

    public Map<String, Object> chunkedPurgeMetrics() {
        Map<String, Object> result = new HashMap<>();
        int totalDeleted = 0;
        try {
            log.warn("[CHUNKED PURGE] Executing small 500-row batch deletions to release MySQL disk space...");
            for (int i = 0; i < 200; i++) {
                int count = jdbcTemplate.update("DELETE FROM system_metrics LIMIT 500");
                totalDeleted += count;
                if (count == 0) break;
            }
            result.put("status", "SUCCESS");
            result.put("totalDeletedMetrics", totalDeleted);
            log.info("[CHUNKED PURGE SUCCESS] Deleted {} metric rows in small 500-row chunks.", totalDeleted);
        } catch (Exception e) {
            log.error("[CHUNKED PURGE ERROR] Failed chunked deletion: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return result;
    }



    public Map<String, Object> purgeStaleComputers(List<String> staleComputerIds) {
        Map<String, Object> stats = new HashMap<>();
        try {
            if (staleComputerIds == null || staleComputerIds.isEmpty()) {
                staleComputerIds = jdbcTemplate.queryForList(
                        "SELECT id FROM computers WHERE UPPER(hostname) != 'LAPTOP-PALBUQS2' AND UPPER(agent_id) != 'AGENT-CE83D0C8'", String.class);
            }

            if (staleComputerIds == null || staleComputerIds.isEmpty()) {
                stats.put("status", "SKIPPED");
                stats.put("message", "No stale computers found in database for deletion.");
                return stats;
            }

            log.warn("[STALE COMPUTER PURGE] Executing selective deletion of stale computers: {}", staleComputerIds);
            stats.put("targetComputerIds", staleComputerIds);

            int metrics = executeJdbcDelete("system_metrics", "computer_id", staleComputerIds, stats);
            int health = executeJdbcDelete("health_scores", "computer_id", staleComputerIds, stats);
            int alerts = executeJdbcDelete("alerts", "computer_id", staleComputerIds, stats);
            int predictions = executeJdbcDelete("predictions", "computer_id", staleComputerIds, stats);
            int logs = executeJdbcDelete("logs", "computer_id", staleComputerIds, stats);
            int software = executeJdbcDelete("software_inventory", "computer_id", staleComputerIds, stats);
            int diagEvents = executeJdbcDelete("diagnostic_events", "computer_id", staleComputerIds, stats);
            int diagIncidents = executeJdbcDelete("diagnostic_incidents", "computer_id", staleComputerIds, stats);
            int powerCmds = executeJdbcDelete("remote_power_commands", "computer_id", staleComputerIds, stats);
            int powerAudits = executeJdbcDelete("remote_power_audits", "computer_id", staleComputerIds, stats);

            // Delete computer records
            int computers = executeJdbcDelete("computers", "id", staleComputerIds, stats);

            stats.put("deletedComputers", computers);
            stats.put("deletedMetrics", metrics);
            stats.put("deletedHealthScores", health);
            stats.put("deletedAlerts", alerts);
            stats.put("deletedPredictions", predictions);
            stats.put("deletedLogs", logs);
            stats.put("deletedSoftware", software);
            stats.put("deletedDiagEvents", diagEvents);
            stats.put("deletedDiagIncidents", diagIncidents);
            stats.put("deletedPowerCmds", powerCmds);
            stats.put("deletedPowerAudits", powerAudits);

            // Execute OPTIMIZE TABLE to shrink physical disk storage
            List<String> optimized = optimizeTables();
            stats.put("optimizedTables", optimized);
            stats.put("status", "SUCCESS");

            log.info("[STALE COMPUTER PURGE SUCCESS] Deleted {} stale computers, {} metrics, {} logs and optimized volume.", computers, metrics, logs);
        } catch (Exception e) {
            log.error("[STALE COMPUTER PURGE ERROR] Failed to purge stale computers: {}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }
        return stats;
    }


    private int executeJdbcDelete(String table, String column, List<String> ids, Map<String, Object> stats) {
        try {
            String idList = ids.stream().map(id -> "'" + id.replace("'", "") + "'").collect(Collectors.joining(","));
            String sql = "DELETE FROM " + table + " WHERE " + column + " IN (" + idList + ")";
            int count = jdbcTemplate.update(sql);
            log.info("[STALE COMPUTER PURGE] Deleted {} rows from {}", count, table);
            stats.put("deleted_" + table, count);
            return count;
        } catch (Exception e) {
            log.warn("[STALE COMPUTER PURGE SKIP] Could not delete from table {}: {}", table, e.getMessage());
            stats.put("error_" + table, e.getMessage());
            return 0;
        }
    }
}





