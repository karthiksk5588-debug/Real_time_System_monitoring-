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

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/maintenance")
@RequiredArgsConstructor
@Tag(name = "System Maintenance & Volume Cleanup", description = "APIs for running database volume retention cleanup and emergency disk optimization")
public class SystemMaintenanceController {

    private final DataRetentionScheduler dataRetentionScheduler;

    @PostMapping("/cleanup")
    @Operation(summary = "Run Emergency Database Volume Cleanup", description = "Purges high-frequency metrics older than 24h and logs older than 7 days to free up MySQL disk volume space")
    public ResponseEntity<Map<String, Object>> triggerEmergencyCleanup() {
        log.info("[ADMIN MAINTENANCE] Manual emergency database volume cleanup triggered...");
        Map<String, Object> stats = dataRetentionScheduler.performRetentionCleanup();
        return ResponseEntity.ok(stats);
    }
}
