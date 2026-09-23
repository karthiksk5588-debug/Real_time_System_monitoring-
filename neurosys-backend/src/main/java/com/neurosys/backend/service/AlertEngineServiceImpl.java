package com.neurosys.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.backend.config.AlertEngineConfig;
import com.neurosys.backend.dto.response.AlertDto;
import com.neurosys.backend.entity.Alert;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.DiagnosticEvent;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.enums.AlertSeverity;
import com.neurosys.backend.enums.AlertStatus;
import com.neurosys.backend.enums.AlertType;
import com.neurosys.backend.enums.DiagnosticCategory;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.AlertRepository;
import com.neurosys.backend.repository.DiagnosticEventRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngineServiceImpl implements AlertEngineService {

    private final AlertRepository alertRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final DiagnosticEventRepository diagnosticEventRepository;
    private final EmailNotificationService emailNotificationService;
    private final ObjectMapper objectMapper;
    private final AlertEngineConfig alertConfig;

    private static final List<AlertStatus> ACTIVE_STATUSES = List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED);

    // In-memory sliding window buffer per computer ID (stores up to 600 1-second telemetry samples = 10 minutes)
    private final Map<String, Deque<SystemMetric>> liveWindowMap = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    @Transactional
    public List<AlertDto> evaluateAndTriggerAlerts(Computer computer, SystemMetric metric) {
        List<AlertDto> triggeredAlerts = new ArrayList<>();

        if (computer == null || metric == null) {
            return triggeredAlerts;
        }

        // Auto-resolve offline alert if computer is active and sending telemetry
        resolveOfflineAlert(computer);

        // Update in-memory sliding telemetry window (keep up to 600 seconds)
        Deque<SystemMetric> window = liveWindowMap.computeIfAbsent(computer.getId(), k -> new ArrayDeque<>());
        synchronized (window) {
            window.addFirst(metric);
            while (window.size() > 600) {
                window.removeLast();
            }
        }

        List<SystemMetric> samples;
        synchronized (window) {
            samples = new ArrayList<>(window);
        }

        Instant now = Instant.now();
        Instant oldestSampleTime = samples.get(samples.size() - 1).getRecordedAt();
        long totalWindowSeconds = Math.max(1, Duration.between(oldestSampleTime, now).getSeconds());

        // ----------------------------------------------------
        // 1. SUSTAINED CPU DEGRADATION EVALUATION
        // ----------------------------------------------------
        AlertEngineConfig.CpuConfig cpuCfg = alertConfig.getCpu();
        long cpuWarnSec = cpuCfg.getWarningDurationSeconds();
        long cpuCritSec = cpuCfg.getCriticalDurationSeconds();

        List<SystemMetric> cpuWarnSamples = samples.stream()
                .filter(s -> Duration.between(s.getRecordedAt(), now).getSeconds() <= cpuWarnSec)
                .toList();

        List<SystemMetric> cpuCritSamples = samples.stream()
                .filter(s -> Duration.between(s.getRecordedAt(), now).getSeconds() <= cpuCritSec)
                .toList();

        long cpuWarnWindowSeconds = cpuWarnSamples.isEmpty() ? 0 : Duration.between(cpuWarnSamples.get(cpuWarnSamples.size() - 1).getRecordedAt(), now).getSeconds();
        long cpuCritWindowSeconds = cpuCritSamples.isEmpty() ? 0 : Duration.between(cpuCritSamples.get(cpuCritSamples.size() - 1).getRecordedAt(), now).getSeconds();

        long highCpuWarnCount = cpuWarnSamples.stream()
                .filter(s -> s.getCpuUsagePercent() != null && s.getCpuUsagePercent() >= cpuCfg.getWarningThreshold())
                .count();

        long highCpuCritCount = cpuCritSamples.stream()
                .filter(s -> s.getCpuUsagePercent() != null && s.getCpuUsagePercent() >= cpuCfg.getCriticalThreshold())
                .count();

        // Check recent samples for recovery below hysteresis bound
        long recentLowCpuCount = samples.stream().limit(5)
                .filter(s -> s.getCpuUsagePercent() != null && s.getCpuUsagePercent() <= cpuCfg.getRecoveryThreshold())
                .count();

        // Requires sustained load: window spans required duration AND >= 80% of samples exceed threshold AND recent samples haven't recovered
        boolean isCpuWarning = cpuWarnWindowSeconds >= Math.min(60, cpuWarnSec) &&
                cpuWarnSamples.size() >= 5 &&
                highCpuWarnCount >= (long) (cpuWarnSamples.size() * 0.8) &&
                recentLowCpuCount == 0;

        boolean isCpuCritical = cpuCritWindowSeconds >= Math.min(120, cpuCritSec) &&
                cpuCritSamples.size() >= 10 &&
                highCpuCritCount >= (long) (cpuCritSamples.size() * 0.8) &&
                recentLowCpuCount == 0;

        boolean isCpuRecovery = recentLowCpuCount >= 3;

        AlertSeverity cpuSeverity = isCpuCritical ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        double cpuThreshold = isCpuCritical ? cpuCfg.getCriticalThreshold() : cpuCfg.getWarningThreshold();
        long targetCpuDuration = isCpuCritical ? cpuCritSec : cpuWarnSec;
        long currentCpuSustainedMinutes = Math.max(1, cpuWarnWindowSeconds / 60);

        List<String> cpuEvidence = List.of(
                String.format("Latest recorded CPU load: %.1f%%.", metric.getCpuUsagePercent()),
                String.format("CPU usage sustained above %.0f%% in %d of the last %d telemetry samples (%d minutes).",
                        cpuThreshold, isCpuCritical ? highCpuCritCount : highCpuWarnCount,
                        isCpuCritical ? cpuCritSamples.size() : cpuWarnSamples.size(), currentCpuSustainedMinutes),
                String.format("Persistence criteria: Required >= %.0f%% for %d minutes continuously.", cpuThreshold, targetCpuDuration / 60)
        );

        evaluateAlertLifecycle(
                computer,
                AlertType.CPU_SUSTAINED_HIGH,
                "CPU",
                isCpuWarning || isCpuCritical,
                isCpuRecovery,
                isCpuCritical ? String.format("%s - CRITICAL Sustained CPU Load", computer.getHostname()) : String.format("%s - Sustained High CPU Usage", computer.getHostname()),
                String.format("Processor on %s has remained continuously under high load (%.1f%%) for %d minutes.",
                        computer.getHostname(), metric.getCpuUsagePercent(), currentCpuSustainedMinutes),
                "Check for runaway background processes, virus scanners, or unoptimized worker threads.",
                cpuEvidence,
                cpuSeverity,
                metric.getCpuUsagePercent(),
                cpuThreshold,
                triggeredAlerts
        );

        // ----------------------------------------------------
        // 2. SUSTAINED MEMORY (RAM) PRESSURE EVALUATION
        // ----------------------------------------------------
        AlertEngineConfig.MemoryConfig ramCfg = alertConfig.getMemory();
        long ramWarnSec = ramCfg.getWarningDurationSeconds();
        long ramCritSec = ramCfg.getCriticalDurationSeconds();

        List<SystemMetric> ramWarnSamples = samples.stream()
                .filter(s -> Duration.between(s.getRecordedAt(), now).getSeconds() <= ramWarnSec)
                .toList();

        List<SystemMetric> ramCritSamples = samples.stream()
                .filter(s -> Duration.between(s.getRecordedAt(), now).getSeconds() <= ramCritSec)
                .toList();

        long ramWarnWindowSeconds = ramWarnSamples.isEmpty() ? 0 : Duration.between(ramWarnSamples.get(ramWarnSamples.size() - 1).getRecordedAt(), now).getSeconds();
        long ramCritWindowSeconds = ramCritSamples.isEmpty() ? 0 : Duration.between(ramCritSamples.get(ramCritSamples.size() - 1).getRecordedAt(), now).getSeconds();

        long highRamWarnCount = ramWarnSamples.stream()
                .filter(s -> s.getMemoryUsagePercent() != null && s.getMemoryUsagePercent() >= ramCfg.getWarningThreshold())
                .count();

        long highRamCritCount = ramCritSamples.stream()
                .filter(s -> s.getMemoryUsagePercent() != null && s.getMemoryUsagePercent() >= ramCfg.getCriticalThreshold())
                .count();

        long recentLowRamCount = samples.stream().limit(5)
                .filter(s -> s.getMemoryUsagePercent() != null && s.getMemoryUsagePercent() <= ramCfg.getRecoveryThreshold())
                .count();

        double availableRamMb = metric.getMemoryFreeMb() != null ? metric.getMemoryFreeMb() : 2048.0;

        boolean isRamWarning = ramWarnWindowSeconds >= Math.min(60, ramWarnSec) &&
                ramWarnSamples.size() >= 5 &&
                highRamWarnCount >= (long) (ramWarnSamples.size() * 0.8) &&
                recentLowRamCount == 0;

        boolean isRamCritical = ramCritWindowSeconds >= Math.min(120, ramCritSec) &&
                ramCritSamples.size() >= 10 &&
                highRamCritCount >= (long) (ramCritSamples.size() * 0.8) &&
                availableRamMb <= ramCfg.getCriticalFreeMb() &&
                recentLowRamCount == 0;

        boolean isRamRecovery = recentLowRamCount >= 3;

        AlertSeverity ramSeverity = isRamCritical ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
        double ramThreshold = isRamCritical ? ramCfg.getCriticalThreshold() : ramCfg.getWarningThreshold();
        long currentRamSustainedMinutes = Math.max(1, ramWarnWindowSeconds / 60);

        List<String> ramEvidence = List.of(
                String.format("Latest recorded RAM usage: %.1f%% (Available Free: %.0f MB).", metric.getMemoryUsagePercent(), availableRamMb),
                String.format("Memory allocation sustained above %.0f%% in %d of the last %d telemetry samples (%d minutes).",
                        ramThreshold, isRamCritical ? highRamCritCount : highRamWarnCount,
                        isRamCritical ? ramCritSamples.size() : ramWarnSamples.size(), currentRamSustainedMinutes),
                String.format("Available system RAM remains at %.1f GB.", availableRamMb / 1024.0)
        );

        evaluateAlertLifecycle(
                computer,
                AlertType.MEMORY_PRESSURE,
                "RAM",
                isRamWarning || isRamCritical,
                isRamRecovery,
                isRamCritical ? String.format("%s - CRITICAL Memory Pressure", computer.getHostname()) : String.format("%s - Sustained Memory Pressure", computer.getHostname()),
                String.format("RAM allocation on %s has remained continuously high (%.1f%%, %.0f MB free) for %d minutes.",
                        computer.getHostname(), metric.getMemoryUsagePercent(), availableRamMb, currentRamSustainedMinutes),
                "Check for memory leaks or close memory-intensive applications.",
                ramEvidence,
                ramSeverity,
                metric.getMemoryUsagePercent(),
                ramThreshold,
                triggeredAlerts
        );

        // ----------------------------------------------------
        // 3. STORAGE CAPACITY EVALUATION
        // ----------------------------------------------------
        AlertEngineConfig.DiskConfig diskCfg = alertConfig.getDisk();
        double diskPercent = metric.getDiskUsagePercent() != null ? metric.getDiskUsagePercent() : 0.0;
        double freeDiskGb = metric.getDiskFreeGb() != null ? metric.getDiskFreeGb() : 100.0;
        double usedDiskGb = metric.getDiskUsedGb() != null ? metric.getDiskUsedGb() : 0.0;
        double totalDiskGb = usedDiskGb + freeDiskGb;

        boolean isDiskUrgent = diskPercent >= diskCfg.getUrgentThreshold();
        boolean isDiskCritical = diskPercent >= diskCfg.getCriticalThreshold();
        boolean isDiskWarning = diskPercent >= diskCfg.getWarningThreshold();
        boolean isDiskInfo = diskPercent >= diskCfg.getInfoThreshold();

        boolean isDiskActive = isDiskInfo || isDiskWarning || isDiskCritical || isDiskUrgent;
        boolean isDiskRecovery = diskPercent < diskCfg.getRecoveryThreshold();

        AlertSeverity diskSeverity = isDiskUrgent || isDiskCritical ? AlertSeverity.CRITICAL : (isDiskWarning ? AlertSeverity.WARNING : AlertSeverity.INFO);
        AlertType diskType = (isDiskUrgent || isDiskCritical) ? AlertType.DISK_SPACE_CRITICAL : AlertType.DISK_SPACE_LOW;

        List<String> diskEvidence = List.of(
                "Drive: C:",
                String.format("Total Capacity: %.1f GB", totalDiskGb > 0 ? totalDiskGb : 500.0),
                String.format("Used Capacity: %.1f GB", usedDiskGb),
                String.format("Free Capacity: %.1f GB", freeDiskGb),
                String.format("Percentage Used: %.1f%%", diskPercent)
        );

        String diskMsg = String.format("Drive C: on %s is %.1f%% full. Total: %.1f GB, Used: %.1f GB, Free: %.1f GB.",
                computer.getHostname(), diskPercent, totalDiskGb > 0 ? totalDiskGb : 500.0, usedDiskGb, freeDiskGb);

        evaluateAlertLifecycle(
                computer,
                diskType,
                "Drive C:",
                isDiskActive,
                isDiskRecovery,
                isDiskCritical ? String.format("%s - Critical Storage Capacity (Drive C:)", computer.getHostname()) : String.format("%s - Storage Space Approaching Capacity (Drive C:)", computer.getHostname()),
                diskMsg,
                "Remove unnecessary files, clean system temporary caches, or increase available storage volume.",
                diskEvidence,
                diskSeverity,
                diskPercent,
                isDiskCritical ? diskCfg.getCriticalThreshold() : diskCfg.getWarningThreshold(),
                triggeredAlerts
        );

        // ----------------------------------------------------
        // 4. MULTI-SIGNAL HIGH INSTABILITY RISK EVALUATION
        // ----------------------------------------------------
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        List<DiagnosticEvent> recentCrashes = diagnosticEventRepository.findByComputerIdOrderByOccurredAtDesc(
                computer.getId(), PageRequest.of(0, 10)).stream()
                .filter(e -> e.getOccurredAt().isAfter(sevenDaysAgo) &&
                        (e.getCategory() == DiagnosticCategory.GRAPHICS || e.getCategory() == DiagnosticCategory.UNEXPECTED_SHUTDOWN || e.getCategory() == DiagnosticCategory.SYSTEM_CRASH))
                .toList();

        boolean isHighTemp = metric.getCpuTemperature() != null && metric.getCpuTemperature() >= 85.0;
        int activeFailureFactors = 0;
        if (isCpuWarning || isCpuCritical) activeFailureFactors++;
        if (isRamWarning || isRamCritical) activeFailureFactors++;
        if (isHighTemp) activeFailureFactors++;
        if (!recentCrashes.isEmpty()) activeFailureFactors += recentCrashes.size();

        boolean isHighRisk = activeFailureFactors >= 3;
        boolean isHighRiskRecovery = activeFailureFactors < 2;

        List<String> riskEvidence = List.of(
                String.format("CPU sustained load condition active: %s", (isCpuWarning || isCpuCritical) ? "YES" : "NO"),
                String.format("RAM memory pressure condition active: %s", (isRamWarning || isRamCritical) ? "YES" : "NO"),
                String.format("System/application crashes in last 7 days: %d events.", recentCrashes.size()),
                String.format("Processor thermal workload: %s.", isHighTemp ? String.format("%.1f°C", metric.getCpuTemperature()) : "Normal")
        );

        evaluateAlertLifecycle(
                computer,
                AlertType.HIGH_RISK,
                "INSTABILITY",
                isHighRisk,
                isHighRiskRecovery,
                String.format("%s - URGENT SYSTEM INSTABILITY RISK", computer.getHostname()),
                String.format("%s is exhibiting multi-vector degradation (sustained load + system crashes/thermals).", computer.getHostname()),
                "Inspect cooling hardware, perform memory integrity diagnostics, and review Windows system logs.",
                riskEvidence,
                AlertSeverity.CRITICAL,
                (double) activeFailureFactors,
                3.0,
                triggeredAlerts
        );

        return triggeredAlerts;
    }

    @Override
    @Transactional
    public void triggerOfflineAlert(Computer computer) {
        long defaultOfflineSec = alertConfig.getOffline().getAlertDurationSeconds();
        triggerOfflineAlert(computer, defaultOfflineSec);
    }

    @Override
    @Transactional
    public void triggerOfflineAlert(Computer computer, long offlineDurationSeconds) {
        if (computer == null) return;

        Instant lastSeen = computer.getLastSeenAt() != null ? computer.getLastSeenAt() : Instant.now().minusSeconds(offlineDurationSeconds);
        String lastSeenStr = TIME_FORMATTER.format(lastSeen);
        long minutesOffline = Math.max(1, offlineDurationSeconds / 60);

        List<String> evidence = List.of(
                String.format("Last heartbeat received: %s.", lastSeenStr),
                "Last known status: ONLINE",
                String.format("Offline duration: %d minutes (%d seconds).", minutesOffline, offlineDurationSeconds),
                String.format("Threshold rule: No heartbeat for >= %d minutes.", alertConfig.getOffline().getAlertDurationSeconds() / 60)
        );

        List<AlertDto> dummyList = new ArrayList<>();
        evaluateAlertLifecycle(
                computer,
                AlertType.ENDPOINT_OFFLINE,
                "ENDPOINT",
                true,
                false,
                String.format("%s - Endpoint Offline", computer.getHostname()),
                String.format("%s missed telemetry heartbeat for %d minutes (last seen %s).", computer.getHostname(), minutesOffline, lastSeenStr),
                "Check computer power supply, physical network cable, or local agent service state.",
                evidence,
                AlertSeverity.WARNING,
                (double) offlineDurationSeconds,
                (double) alertConfig.getOffline().getAlertDurationSeconds(),
                dummyList
        );
    }

    @Override
    @Transactional
    public void resolveOfflineAlert(Computer computer) {
        if (computer == null) return;
        List<AlertDto> dummyList = new ArrayList<>();

        // Resolve both ENDPOINT_OFFLINE and legacy OFFLINE alert types
        evaluateAlertLifecycle(
                computer,
                AlertType.ENDPOINT_OFFLINE,
                "ENDPOINT",
                false,
                true,
                "Endpoint Offline",
                "",
                "",
                List.of(),
                AlertSeverity.WARNING,
                0.0,
                1.0,
                dummyList
        );

        evaluateAlertLifecycle(
                computer,
                AlertType.OFFLINE,
                "ENDPOINT",
                false,
                true,
                "Endpoint Offline",
                "",
                "",
                List.of(),
                AlertSeverity.WARNING,
                0.0,
                1.0,
                dummyList
        );
    }

    private void evaluateAlertLifecycle(
            Computer computer,
            AlertType alertType,
            String resourceKey,
            boolean isPersistentCondition,
            boolean isRecoveryCondition,
            String title,
            String message,
            String recommendedAction,
            List<String> evidenceList,
            AlertSeverity severity,
            Double triggeredValue,
            Double thresholdValue,
            List<AlertDto> triggeredAlerts
    ) {
        // Query active incident by computerId + alertType + resourceKey + ACTIVE_STATUSES
        Optional<Alert> activeAlert = alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(
                computer.getId(), alertType, resourceKey, ACTIVE_STATUSES
        );

        if (activeAlert.isEmpty()) {
            activeAlert = alertRepository.findFirstByComputerIdAndAlertTypeAndStatusIn(
                    computer.getId(), alertType, ACTIVE_STATUSES
            );
        }

        // Check if administrator manually resolved an alert for this computer & alertType recently (snooze window: 15 min)
        Instant fifteenMinutesAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
        boolean recentlyResolvedByAdmin = alertRepository.existsByComputerIdAndAlertTypeAndResourceKeyAndStatusAndResolvedAtAfter(
                computer.getId(), alertType, resourceKey, AlertStatus.RESOLVED, fifteenMinutesAgo
        );

        String evidenceJson = null;
        if (evidenceList != null && !evidenceList.isEmpty()) {
            try {
                evidenceJson = objectMapper.writeValueAsString(evidenceList);
            } catch (Exception e) {
                evidenceJson = "[]";
            }
        }

        if (isPersistentCondition) {
            if (activeAlert.isEmpty()) {
                if (recentlyResolvedByAdmin) {
                    log.debug("Alert {} ({}) for {} was recently resolved by admin. Respecting resolution.", alertType, resourceKey, computer.getHostname());
                    return;
                }

                // Persistent condition confirmed -> Create ONE active incident alert record (SINGLE INCIDENT PRINCIPLE)
                Alert alert = Alert.builder()
                        .computer(computer)
                        .title(title)
                        .message(message)
                        .recommendedAction(recommendedAction)
                        .evidenceJson(evidenceJson)
                        .severity(severity)
                        .alertType(alertType)
                        .resourceKey(resourceKey)
                        .status(AlertStatus.OPEN)
                        .triggeredValue(triggeredValue)
                        .thresholdValue(thresholdValue)
                        .occurrenceCount(1)
                        .firstDetectedAt(Instant.now())
                        .lastDetectedAt(Instant.now())
                        .triggeredAt(Instant.now())
                        .build();

                alert = alertRepository.save(alert);
                log.info("[INFO] Persistent Degradation Alert Triggered [Type: {}, Resource: {}, Computer: {}]: {}",
                        alertType, resourceKey, computer.getHostname(), title);

                emailNotificationService.sendCriticalAlertEmail(alert);
                triggeredAlerts.add(mapToDto(alert));
            } else {
                // Problem remains ACTIVE -> Update existing active incident (DEDUPLICATION GUARANTEE)
                Alert existing = activeAlert.get();
                existing.setOccurrenceCount((existing.getOccurrenceCount() != null ? existing.getOccurrenceCount() : 1) + 1);
                existing.setLastDetectedAt(Instant.now());
                existing.setTriggeredValue(triggeredValue);
                if (evidenceJson != null) existing.setEvidenceJson(evidenceJson);
                existing.setSeverity(severity);
                existing.setMessage(message);
                existing.setResourceKey(resourceKey);

                alertRepository.save(existing);
                log.debug("[INFO] Updated active incident [Type: {}, Resource: {}, Computer: {}] (Occurrences: {})",
                        alertType, resourceKey, computer.getHostname(), existing.getOccurrenceCount());
            }
        } else if (isRecoveryCondition) {
            if (activeAlert.isPresent()) {
                // Condition Recovered -> Automatically resolve existing active incident
                Alert alertToResolve = activeAlert.get();
                alertToResolve.setStatus(AlertStatus.RESOLVED);
                alertToResolve.setResolvedAt(Instant.now());
                if (!alertToResolve.getMessage().contains("returned to normal")) {
                    alertToResolve.setMessage(alertToResolve.getMessage() + " (Condition returned to normal)");
                }
                alertRepository.save(alertToResolve);

                log.info("[INFO] Alert Condition Recovered. Resolved active incident [Type: {}, Resource: {}, Computer: {}]",
                        alertType, resourceKey, computer.getHostname());

                emailNotificationService.sendAlertRecoveryEmail(alertToResolve);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDto> getAllAlerts() {
        return alertRepository.findAll().stream().map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDto> getAlertsByLabId(String labId) {
        if (labId == null || labId.isEmpty() || "ALL".equalsIgnoreCase(labId)) {
            return getAllAlerts();
        }
        return alertRepository.findAll().stream()
                .filter(a -> a.getComputer() != null && a.getComputer().getLab() != null && labId.equals(a.getComputer().getLab().getId()))
                .map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertDto> getComputerAlerts(String computerId) {
        return alertRepository.findByComputerIdOrderByTriggeredAtDesc(computerId)
                .stream().map(this::mapToDto).toList();
    }

    @Override
    @Transactional
    public AlertDto acknowledgeAlert(String alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", alertId));
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert = alertRepository.save(alert);
        return mapToDto(alert);
    }

    @Override
    @Transactional
    public AlertDto resolveAlert(String alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", alertId));
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(Instant.now());
        alert = alertRepository.save(alert);
        log.info("[INFO] Manually resolved alert {} for computer {}", alertId, alert.getComputer().getHostname());
        return mapToDto(alert);
    }

    private AlertDto mapToDto(Alert alert) {
        List<String> evidence = new ArrayList<>();
        if (alert.getEvidenceJson() != null && !alert.getEvidenceJson().isEmpty()) {
            try {
                evidence = objectMapper.readValue(alert.getEvidenceJson(), List.class);
            } catch (Exception ignored) {
            }
        }

        Computer comp = alert.getComputer();
        String cName = comp != null && comp.getDisplayName() != null && !comp.getDisplayName().isEmpty() ? comp.getDisplayName() : (comp != null ? comp.getHostname() : "");
        String lId = comp != null && comp.getLab() != null ? comp.getLab().getId() : "";
        String lCode = comp != null && comp.getLab() != null ? comp.getLab().getCode() : "LAB";
        String lName = comp != null && comp.getLab() != null ? comp.getLab().getName() : (comp != null && comp.getLabName() != null ? comp.getLabName() : "Computer Lab 1");

        return AlertDto.builder()
                .id(alert.getId())
                .computerId(comp != null ? comp.getId() : "")
                .hostname(comp != null ? comp.getHostname() : "")
                .computerName(cName)
                .labId(lId)
                .labCode(lCode)
                .labName(lName)
                .title(alert.getTitle())
                .message(alert.getMessage())
                .recommendedAction(alert.getRecommendedAction())
                .evidence(evidence)
                .severity(alert.getSeverity().name())
                .alertType(alert.getAlertType().name())
                .resourceKey(alert.getResourceKey() != null ? alert.getResourceKey() : "SYSTEM")
                .status(alert.getStatus().name())
                .triggeredValue(alert.getTriggeredValue())
                .thresholdValue(alert.getThresholdValue())
                .occurrenceCount(alert.getOccurrenceCount() != null ? alert.getOccurrenceCount() : 1)
                .firstDetectedAt(alert.getFirstDetectedAt() != null ? alert.getFirstDetectedAt() : alert.getTriggeredAt())
                .lastDetectedAt(alert.getLastDetectedAt() != null ? alert.getLastDetectedAt() : alert.getTriggeredAt())
                .triggeredAt(alert.getTriggeredAt())
                .resolvedAt(alert.getResolvedAt())
                .build();
    }
}
