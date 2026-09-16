package com.neurosys.backend.service;

import com.neurosys.backend.dto.request.SystemMetricsIngestionRequest;
import com.neurosys.backend.dto.response.AlertDto;
import com.neurosys.backend.dto.response.HealthScoreDto;
import com.neurosys.backend.dto.response.SystemMetricDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMetricsServiceImpl implements SystemMetricsService {

    private final SystemMetricRepository systemMetricRepository;
    private final ComputerRepository computerRepository;
    private final HealthScoreEngine healthScoreEngine;
    private final AlertEngineService alertEngineService;
    private final DiagnosisEngineService diagnosisEngineService;
    private final WebSocketMetricsPublisher webSocketMetricsPublisher;
    private final HeartbeatTrackerService heartbeatTracker;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${telemetry.history.interval:30s}")
    private String historyIntervalConfig;

    @Value("${telemetry.history.retention-days:7}")
    private int retentionDays;

    private final Map<String, Instant> lastHistoricalSaveMap = new ConcurrentHashMap<>();
    private final Map<String, Deque<SystemMetricDto>> liveBufferMap = new ConcurrentHashMap<>();

    private long getHistoryIntervalSeconds() {
        if (historyIntervalConfig == null) return 30L;
        String clean = historyIntervalConfig.replaceAll("[^0-9]", "").trim();
        try {
            return clean.isEmpty() ? 30L : Long.parseLong(clean);
        } catch (Exception e) {
            return 30L;
        }
    }

    @Override
    @Transactional
    public SystemMetricDto ingestMetrics(SystemMetricsIngestionRequest request) {
        Optional<Computer> compOpt = computerRepository.findByAgentId(request.getAgentId());
        
        Computer computer;
        if (compOpt.isPresent()) {
            computer = compOpt.get();
        } else {
            // Auto-heal / Auto-register computer if record missing on server restart
            log.info("[INFO] Auto-registering computer for incoming agent heartbeat: AgentID={}", request.getAgentId());
            computer = Computer.builder()
                    .agentId(request.getAgentId())
                    .hostname("PC-" + request.getAgentId().replaceAll("[^A-Za-z0-9]", ""))
                    .computerName("PC-" + request.getAgentId())
                    .labName("Computer Lab")
                    .status(ComputerStatus.ONLINE)
                    .lastSeenAt(Instant.now())
                    .build();
            computer.setCreatedAt(Instant.now());
            computer.setUpdatedAt(Instant.now());
            computer = computerRepository.save(computer);
            log.info("[INFO] Auto-registered new computer record: ID={}", computer.getId());
        }

        if (computer.getStatus() == ComputerStatus.PENDING || computer.getStatus() == ComputerStatus.REJECTED) {
            log.warn("Blocking metrics ingestion for unapproved computer {} with status {}", computer.getHostname(), computer.getStatus());
            throw new IllegalStateException("Computer endpoint onboarding is " + computer.getStatus() + ". Pending administrator approval.");
        }

        String topProcJson = null;
        if (request.getTopProcesses() != null && !request.getTopProcesses().isEmpty()) {
            try {
                topProcJson = objectMapper.writeValueAsString(request.getTopProcesses());
            } catch (Exception e) {
                log.warn("Failed to serialize top processes", e);
            }
        }

        SystemMetric metric = SystemMetric.builder()
                .computer(computer)
                .cpuUsagePercent(request.getCpuUsagePercent() != null ? request.getCpuUsagePercent() : 0.0)
                .memoryUsagePercent(request.getMemoryUsagePercent() != null ? request.getMemoryUsagePercent() : 0.0)
                .memoryUsedMb(request.getMemoryUsedMb())
                .memoryFreeMb(request.getMemoryFreeMb())
                .diskUsagePercent(request.getDiskUsagePercent() != null ? request.getDiskUsagePercent() : 0.0)
                .diskUsedGb(request.getDiskUsedGb())
                .diskFreeGb(request.getDiskFreeGb())
                .diskReadBytesSec(request.getDiskReadBytesSec())
                .diskWriteBytesSec(request.getDiskWriteBytesSec())
                .networkRxBytesSec(request.getNetworkRxBytesSec())
                .networkTxBytesSec(request.getNetworkTxBytesSec())
                .cpuTemperature(request.getCpuTemperature())
                .activeProcessCount(request.getActiveProcessCount())
                .topProcessesJson(topProcJson)
                .recordedAt(request.getTimestamp() != null ? request.getTimestamp() : Instant.now())
                .build();

        metric.setCreatedAt(Instant.now());
        metric.setUpdatedAt(Instant.now());

        // Sampled DB persistence: Save to MySQL only every historyIntervalSeconds (e.g. 30s) per computer
        Instant now = Instant.now();
        Instant lastSave = lastHistoricalSaveMap.get(computer.getId());
        long intervalSec = getHistoryIntervalSeconds();

        boolean shouldSaveToDb = (lastSave == null || Duration.between(lastSave, now).getSeconds() >= intervalSec);

        if (shouldSaveToDb) {
            metric = systemMetricRepository.save(metric);
            lastHistoricalSaveMap.put(computer.getId(), now);
            log.info("[INFO] Saved sampled telemetry metric to DB for computer ID={} ({})", computer.getId(), computer.getHostname());
        }

        // Structured Heartbeat & Reconnect Logging
        ComputerStatus oldStatus = computer.getStatus();
        log.info("[INFO] Heartbeat received from {} (Agent: {})", computer.getHostname(), computer.getAgentId());

        heartbeatTracker.updateHeartbeatTime(computer.getId());
        computer.setLastSeenAt(now);
        if (request.getInternetConnected() != null) {
            computer.setInternetConnected(request.getInternetConnected());
        }
        if (request.getUptimeSeconds() != null) {
            computer.setUptimeSeconds(request.getUptimeSeconds());
        }

        double cpu = request.getCpuUsagePercent() != null ? request.getCpuUsagePercent() : 0.0;
        double ram = request.getMemoryUsagePercent() != null ? request.getMemoryUsagePercent() : 0.0;
        double disk = request.getDiskUsagePercent() != null ? request.getDiskUsagePercent() : 0.0;

        ComputerStatus newStatus;
        if (cpu >= 99.0 || disk >= 99.0) {
            newStatus = ComputerStatus.CRITICAL;
        } else if (cpu >= 90.0 || ram >= 98.0) {
            newStatus = ComputerStatus.WARNING;
        } else {
            newStatus = ComputerStatus.ONLINE;
        }

        if (oldStatus != newStatus) {
            log.info("[INFO] PC {} status restored/changed {} → {}", computer.getHostname(), oldStatus, newStatus);
            webSocketMetricsPublisher.broadcastStatusChange(computer, newStatus, "Telemetry metric ingestion status change");
        }
        computer.setStatus(newStatus);
        computer.setLastSeenAt(now);
        computer.setLastCpuUsage(cpu);
        computer.setLastRamUsage(ram);
        computer.setLastDiskUsage(disk);
        computer.setUpdatedAt(now);
        computerRepository.save(computer);

        // Calculate Health Score
        HealthScoreDto healthScore = healthScoreEngine.calculateAndSaveHealthScore(computer, metric, 0.0);

        // Evaluate Alert Rules
        List<AlertDto> alerts = alertEngineService.evaluateAndTriggerAlerts(computer, metric);

        // Process Diagnosis Incidents & Resolution Detection
        try {
            diagnosisEngineService.processMetricsForIncidents(computer.getId());
        } catch (Exception e) {
            log.warn("Failed processing metrics for diagnosis incidents: {}", e.getMessage());
        }

        SystemMetricDto metricDto = mapToDto(metric);

        // Push into 1-second live in-memory telemetry buffer (keep last 60 seconds per computer)
        Deque<SystemMetricDto> buffer = liveBufferMap.computeIfAbsent(computer.getId(), k -> new ArrayDeque<>());
        synchronized (buffer) {
            buffer.addFirst(metricDto);
            while (buffer.size() > 60) {
                buffer.removeLast();
            }
        }

        // Broadcast to WebSocket clients
        webSocketMetricsPublisher.broadcastTelemetryUpdate(metricDto, healthScore, alerts);

        return metricDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemMetricDto> getMetricHistory(String computerId, int limit) {
        Deque<SystemMetricDto> buffer = liveBufferMap.get(computerId);
        if (buffer != null && !buffer.isEmpty()) {
            synchronized (buffer) {
                return buffer.stream().limit(limit).toList();
            }
        }
        return systemMetricRepository.findByComputerIdOrderByRecordedAtDesc(computerId, PageRequest.of(0, limit))
                .stream().map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemMetricDto getLatestMetric(String computerId) {
        return systemMetricRepository.findLatestByComputerId(computerId)
                .map(this::mapToDto)
                .orElse(null);
    }

    private SystemMetricDto mapToDto(SystemMetric metric) {
        return SystemMetricDto.builder()
                .id(metric.getId())
                .computerId(metric.getComputer().getId())
                .hostname(metric.getComputer().getHostname())
                .cpuUsagePercent(metric.getCpuUsagePercent())
                .memoryUsagePercent(metric.getMemoryUsagePercent())
                .memoryUsedMb(metric.getMemoryUsedMb())
                .memoryFreeMb(metric.getMemoryFreeMb())
                .diskUsagePercent(metric.getDiskUsagePercent())
                .diskUsedGb(metric.getDiskUsedGb())
                .diskFreeGb(metric.getDiskFreeGb())
                .diskReadBytesSec(metric.getDiskReadBytesSec())
                .diskWriteBytesSec(metric.getDiskWriteBytesSec())
                .networkRxBytesSec(metric.getNetworkRxBytesSec())
                .networkTxBytesSec(metric.getNetworkTxBytesSec())
                .cpuTemperature(metric.getCpuTemperature())
                .activeProcessCount(metric.getActiveProcessCount())
                .recordedAt(metric.getRecordedAt())
                .build();
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldTelemetryData() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedCount = systemMetricRepository.deleteMetricsOlderThan(cutoff);
        if (deletedCount > 0) {
            log.info("[INFO] Automated Telemetry Retention Cleanup: Deleted {} historical metrics older than {} (Retention: {} days)",
                    deletedCount, cutoff, retentionDays);
        }
    }
}
