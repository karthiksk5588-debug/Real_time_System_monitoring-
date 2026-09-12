package com.neurosys.backend.controller;

import com.neurosys.backend.scheduler.DataRetentionScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@RequiredArgsConstructor
@Tag(name = "System Maintenance & Volume Cleanup", description = "APIs for running database volume retention cleanup and emergency disk optimization")
public class SystemMaintenanceController {

    private final DataRetentionScheduler dataRetentionScheduler;

    @PostMapping("/cleanup")
    @Operation(summary = "Run Database Volume Retention Cleanup", description = "Purges metrics older than configured retention window (2h) and logs older than 7 days")
    public ResponseEntity<Map<String, Object>> triggerEmergencyCleanup() {
        log.info("[ADMIN MAINTENANCE] Manual emergency database volume cleanup triggered...");
        Map<String, Object> stats = dataRetentionScheduler.performRetentionCleanup();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/truncate-metrics")
    @Operation(summary = "Emergency Truncate Metrics Table", description = "Immediately truncates system_metrics table to drop .ibd files and free up 100% of metric storage on MySQL disk volume")
    public ResponseEntity<Map<String, Object>> truncateMetricsTable() {
        log.warn("[ADMIN MAINTENANCE] Emergency metric table truncation requested...");
        Map<String, Object> result = dataRetentionScheduler.truncateMetricsTable();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/optimize-tables")
    @Operation(summary = "Optimize MySQL InnoDB Tables", description = "Executes native OPTIMIZE TABLE queries to shrink MySQL disk volume space")
    public ResponseEntity<Map<String, Object>> optimizeTables() {
        log.info("[ADMIN MAINTENANCE] Manual table optimization requested...");
        List<String> optimized = dataRetentionScheduler.optimizeTables();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("optimizedTables", optimized);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/purge-stale-computers")
    @Operation(summary = "Purge Stale Unused Computers and Telemetry", description = "Selectively purges specified stale computers and their metrics, logs, health scores, and alerts without affecting active computers or system config.")
    public ResponseEntity<Map<String, Object>> purgeStaleComputers(@org.springframework.web.bind.annotation.RequestBody(required = false) List<String> staleComputerIds) {
        if (staleComputerIds == null || staleComputerIds.isEmpty()) {
            // Default target stale computer IDs identified during DB inspection
            staleComputerIds = List.of("446529e2-d335-458e-b88a-80aea13e95a2", "b388c2c5-d6d2-4b2c-b730-d2fd7224412e");
        }
        log.warn("[ADMIN MAINTENANCE] Selectively purging stale computers: {}", staleComputerIds);
        Map<String, Object> result = dataRetentionScheduler.purgeStaleComputers(staleComputerIds);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/chunked-purge")
    @Operation(summary = "Emergency Chunked Metrics Purge", description = "Deletes high-frequency metric records in tiny 500-row chunks to release MySQL disk space when disk is 100% full.")
    public ResponseEntity<Map<String, Object>> chunkedPurge() {
        log.warn("[ADMIN MAINTENANCE] Manual chunked metric purge requested...");
        Map<String, Object> result = dataRetentionScheduler.chunkedPurgeMetrics();
        return ResponseEntity.ok(result);
    }
}



