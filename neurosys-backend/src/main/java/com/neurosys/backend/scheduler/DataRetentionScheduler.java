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

            final List<String> targetIds = staleComputerIds;
            log.warn("[STALE COMPUTER PURGE] Executing selective deletion of stale computers on single JDBC Connection: {}", targetIds);
            stats.put("targetComputerIds", targetIds);

            jdbcTemplate.execute((java.sql.Connection conn) -> {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

                    String idList = targetIds.stream().map(id -> "'" + id.replace("'", "") + "'").collect(Collectors.joining(","));
                    String[] tables = {"system_metrics", "health_scores", "alerts", "predictions", "logs", "software_inventory", "diagnostic_events", "diagnostic_incidents", "remote_power_commands", "remote_power_audits"};

                    for (String table : tables) {
                        try {
                            int count = stmt.executeUpdate("DELETE FROM " + table + " WHERE computer_id IN (" + idList + ")");
                            stats.put("deleted_" + table, count);
                            log.info("[SINGLE CONN PURGE] Deleted {} rows from {}", count, table);
                        } catch (Exception e) {
                            stats.put("error_" + table, e.getMessage());
                        }
                    }

                    try {
                        int compCount = stmt.executeUpdate("DELETE FROM computers WHERE id IN (" + idList + ")");
                        stats.put("deletedComputers", compCount);
                        log.info("[SINGLE CONN PURGE] Deleted {} stale computer metadata rows", compCount);
                    } catch (Exception e) {
                        stats.put("error_computers", e.getMessage());
                    }

                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                return null;
            });

            // Execute OPTIMIZE TABLE to shrink physical disk storage
            List<String> optimized = optimizeTables();
            stats.put("optimizedTables", optimized);
            stats.put("status", "SUCCESS");

            log.info("[STALE COMPUTER PURGE SUCCESS] Successfully purged stale computers and optimized MySQL volume.");
        } catch (Exception e) {
            log.error("[STALE COMPUTER PURGE ERROR] Failed to purge stale computers: {}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }
        return stats;
    }



    public Map<String, Object> purgeAllTelemetryAndLogs() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.warn("[FULL VOLUME RECOVERY] Truncating all non-essential time-series tables (health_scores, predictions, system_metrics, logs)...");
            String[] tables = {"system_metrics", "health_scores", "predictions", "logs", "diagnostic_events", "diagnostic_incidents", "alerts", "remote_power_commands", "remote_power_audits"};

            jdbcTemplate.execute((java.sql.Connection conn) -> {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String table : tables) {
                        try {
                            stmt.execute("TRUNCATE TABLE " + table);
                            result.put("truncated_" + table, true);
                            log.info("[VOLUME RECOVERY] Truncated {}", table);
                        } catch (Exception e) {
                            try {
                                int count = stmt.executeUpdate("DELETE FROM " + table);
                                result.put("deleted_" + table, count);
                                log.info("[VOLUME RECOVERY FALLBACK] Deleted {} rows from {}", count, table);
                            } catch (Exception ex) {
                                result.put("error_" + table, ex.getMessage());
                            }
                        }
                    }
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                return null;
            });

            List<String> optimized = optimizeTables();
            result.put("optimizedTables", optimized);
            result.put("status", "SUCCESS");
            result.put("message", "Purged all historical logs and time-series telemetry. Physical disk space reclaimed.");
        } catch (Exception e) {
            log.error("[FULL VOLUME RECOVERY ERROR] Failed: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return result;
    }

    public Map<String, Object> resetAllDataIncludingComputers() {

        Map<String, Object> result = new HashMap<>();
        try {
            log.warn("[COMPLETE DATA RESET] Purging all telemetry, metrics, logs, health scores, software inventory, and computer endpoints...");
            String[] tables = {"system_metrics", "health_scores", "predictions", "logs", "diagnostic_events", "diagnostic_incidents", "alerts", "software_inventory", "remote_power_commands", "remote_power_audits", "computers"};

            jdbcTemplate.execute((java.sql.Connection conn) -> {
                try (java.sql.Statement stmt = conn.createStatement()) {
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String table : tables) {
                        try {
                            stmt.execute("TRUNCATE TABLE " + table);
                            result.put("truncated_" + table, true);
                            log.info("[COMPLETE RESET] Truncated {}", table);
                        } catch (Exception e) {
                            try {
                                int count = stmt.executeUpdate("DELETE FROM " + table);
                                result.put("deleted_" + table, count);
                                log.info("[COMPLETE RESET FALLBACK] Deleted {} rows from {}", count, table);
                            } catch (Exception ex) {
                                result.put("error_" + table, ex.getMessage());
                            }
                        }
                    }
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
                return null;
            });

            List<String> optimized = optimizeTables();
            result.put("optimizedTables", optimized);
            result.put("status", "SUCCESS");
            result.put("message", "All telemetry, logs, metrics, and computer registrations reset to 0 bytes. Admin accounts and application structure preserved.");
        } catch (Exception e) {
            log.error("[COMPLETE RESET ERROR] Failed: {}", e.getMessage(), e);
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }
        return result;
    }
}







