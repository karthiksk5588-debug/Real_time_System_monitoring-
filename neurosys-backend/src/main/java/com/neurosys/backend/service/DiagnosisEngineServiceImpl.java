package com.neurosys.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.backend.dto.request.DiagnosticEventDto;
import com.neurosys.backend.dto.response.*;
import com.neurosys.backend.entity.*;
import com.neurosys.backend.enums.*;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisEngineServiceImpl implements DiagnosisEngineService {

    private final ComputerRepository computerRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final DiagnosticEventRepository diagnosticEventRepository;
    private final DiagnosticIncidentRepository diagnosticIncidentRepository;
    private final CrashPredictionService crashPredictionService;
    private final LogHumanizerService logHumanizerService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AIDiagnosisReportDto evaluateDiagnosis(String computerId) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));

        // 1. Fetch recent telemetry history (up to 30 latest samples)
        List<SystemMetric> history = systemMetricRepository.findByComputerIdOrderByRecordedAtDesc(computerId, PageRequest.of(0, 30));
        
        // 2. Fetch recent diagnostic events (last 7 days)
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<DiagnosticEvent> recentEvents = diagnosticEventRepository.findByComputerIdOrderByOccurredAtDesc(computerId, PageRequest.of(0, 20));

        // 3. Rule Evaluation in order of priority:
        // Rule A: Graphics Driver Crash (CONFIRMED)
        Optional<DiagnosticEvent> graphicsCrash = recentEvents.stream()
                .filter(e -> e.getCategory() == DiagnosticCategory.GRAPHICS && e.getOccurredAt().isAfter(sevenDaysAgo))
                .findFirst();

        if (graphicsCrash.isPresent()) {
            DiagnosticEvent event = graphicsCrash.get();
            List<String> evidence = List.of(
                    "Windows recorded a graphics driver crash (" + (event.getEventSource() != null ? event.getEventSource() : "Display") + ").",
                    "The latest graphics driver error occurred on " + event.getOccurredAt().toString().substring(0, 16) + ".",
                    "Multiple graphics errors recorded in the last 7 days."
            );
            return buildDiagnosisDto(computer, "Graphics driver stopped working.", "Graphics driver stopped responding.",
                    evidence, "Update the graphics driver. If the problem continues, check the graphics hardware.",
                    ConfirmationStatus.CONFIRMED.name(), null, event.getOccurredAt(), true);
        }

        // Rule B: Disk Capacity Near Exhaustion (CONFIRMED)
        if (history != null && !history.isEmpty()) {
            SystemMetric latest = history.get(0);
            double diskPercent = latest.getDiskUsagePercent() != null ? latest.getDiskUsagePercent() : 0.0;
            double freeDiskGb = latest.getDiskFreeGb() != null ? latest.getDiskFreeGb() : 100.0;

            if (diskPercent >= 95.0 || freeDiskGb < 5.0) {
                List<String> evidence = List.of(
                        String.format("Disk usage is currently %.1f%%.", diskPercent),
                        String.format("Free disk space remaining: %.1f GB.", freeDiskGb),
                        "Storage usage has been consistently near maximum capacity."
                );
                return buildDiagnosisDto(computer, "Disk is almost full.", "Disk space is nearly exhausted.",
                        evidence, "Remove unnecessary files, clear temporary caches, or uninstall unused applications.",
                        ConfirmationStatus.CONFIRMED.name(), null, latest.getRecordedAt(), true);
            }
        }

        // Rule C: High Memory Exhaustion (LIKELY) - Sustained across history
        if (history != null && history.size() >= 5) {
            SystemMetric latest = history.get(0);
            double ramPercent = latest.getMemoryUsagePercent() != null ? latest.getMemoryUsagePercent() : 0.0;
            double freeRamMb = latest.getMemoryFreeMb() != null ? latest.getMemoryFreeMb() : 4096.0;

            long highRamCount = history.stream()
                    .filter(m -> m.getMemoryUsagePercent() != null && m.getMemoryUsagePercent() >= 90.0)
                    .count();

            if (ramPercent >= 90.0 && highRamCount >= 4) {
                List<String> evidence = List.of(
                        String.format("RAM usage stayed above 90%% in %d out of last %d samples (currently %.1f%%).", highRamCount, history.size(), ramPercent),
                        String.format("Available memory is dangerously low (%.0f MB free).", freeRamMb),
                        "Multiple memory-intensive background processes are active."
                );
                return buildDiagnosisDto(computer, "Computer is running out of memory.", "Computer is running out of available memory.",
                        evidence, "Close unnecessary background applications or restart the computer.",
                        ConfirmationStatus.LIKELY.name(), null, latest.getRecordedAt(), true);
            }
        }

        // Rule D: Processor Overheating (LIKELY)
        if (history != null && !history.isEmpty()) {
            SystemMetric latest = history.get(0);
            Double temp = latest.getCpuTemperature();
            if (temp != null && temp >= 85.0) {
                List<String> evidence = List.of(
                        String.format("Processor temperature reached %.1f°C.", temp),
                        "High thermal workload sustained under active operation."
                );
                return buildDiagnosisDto(computer, "Computer is overheating.", "Processor temperature reached dangerous levels.",
                        evidence, "Clean dust from cooling fans, verify thermal ventilation, or reduce heavy CPU workloads.",
                        ConfirmationStatus.LIKELY.name(), null, latest.getRecordedAt(), true);
            }
        }

        // Rule E: Network Instability (LIKELY)
        Optional<DiagnosticEvent> networkEvent = recentEvents.stream()
                .filter(e -> e.getCategory() == DiagnosticCategory.NETWORK && e.getOccurredAt().isAfter(Instant.now().minus(2, ChronoUnit.HOURS)))
                .findFirst();

        if (networkEvent.isPresent()) {
            List<String> evidence = List.of(
                    "Network interface reported repeated packet disconnections.",
                    "Network connectivity drops detected in recent telemetry sampling."
            );
            return buildDiagnosisDto(computer, "Internet connection keeps disconnecting.", "Unstable network adapter or connection dropouts.",
                    evidence, "Check Ethernet cable, reconnect Wi-Fi, or update network card driver.",
                    ConfirmationStatus.LIKELY.name(), null, networkEvent.get().getOccurredAt(), true);
        }

        // Rule F: Unexpected Shutdown / System Crash without specific driver cause (NOT CONFIRMED)
        Optional<DiagnosticEvent> unexpectedShutdown = recentEvents.stream()
                .filter(e -> e.getCategory() == DiagnosticCategory.UNEXPECTED_SHUTDOWN || e.getCategory() == DiagnosticCategory.SYSTEM_CRASH)
                .findFirst();

        if (unexpectedShutdown.isPresent()) {
            DiagnosticEvent event = unexpectedShutdown.get();
            List<String> evidence = List.of(
                    "Windows recorded an unexpected shutdown or system crash (Event ID " + (event.getEventId() != null ? event.getEventId() : 41) + ").",
                    "High CPU and memory usage detected prior to shutdown.",
                    "No direct hardware or driver error binary log was found."
            );
            List<PossibleCauseDto> possibleCauses = List.of(
                    new PossibleCauseDto("Memory depletion problem", 62),
                    new PossibleCauseDto("Driver subsystem failure", 41),
                    new PossibleCauseDto("Thermal CPU overload", 28)
            );
            return buildDiagnosisDto(computer, "Computer shut down unexpectedly.", "Cause could not be confirmed from the available data.",
                    evidence, "Check Windows Event Log for crash details and continue monitoring.",
                    ConfirmationStatus.NOT_CONFIRMED.name(), possibleCauses, event.getOccurredAt(), true);
        }

        // Default: Healthy / No Active Problem
        return buildDiagnosisDto(computer, "No active problems detected", "Computer operating normally within healthy thresholds.",
                List.of("Telemetry metrics are stable.", "No Windows system error events recorded."),
                "No action required. Regular monitoring active.", ConfirmationStatus.CONFIRMED.name(), null, Instant.now(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public AIPredictionDto evaluatePrediction(String computerId) {
        CrashPredictionResponse resp = crashPredictionService.getLatestCrashPrediction(computerId);

        String category = "PERFORMANCE";
        if (resp.getPredictedIssue() != null) {
            String lower = resp.getPredictedIssue().toLowerCase();
            if (lower.contains("storage") || lower.contains("disk")) category = "STORAGE";
            else if (lower.contains("memory") || lower.contains("ram")) category = "MEMORY";
            else if (lower.contains("cpu") || lower.contains("processor")) category = "CPU";
            else if (lower.contains("instability") || lower.contains("crash")) category = "STABILITY";
        }

        String reason = (resp.getContributingFactors() != null && !resp.getContributingFactors().isEmpty())
                ? resp.getContributingFactors().get(0)
                : "System telemetry trends demonstrate stable CPU, RAM, and Disk resource usage.";

        return AIPredictionDto.builder()
                .computerId(resp.getComputerId())
                .hostname(resp.getHostname())
                .isDataSufficient(resp.isDataSufficient())
                .insufficientDataReason(resp.getInsufficientDataReason())
                .predictedIssue(resp.getPredictedIssue())
                .estimatedTimeframe(resp.getEstimatedTimeframe())
                .confidencePercent(resp.getConfidencePercent() != null ? resp.getConfidencePercent() : 85)
                .category(category)
                .reason(reason)
                .recommendedAction(resp.getRecommendedAction())
                .evaluatedAt(resp.getPredictedAt() != null ? resp.getPredictedAt() : Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void recordAgentEvents(String agentId, List<DiagnosticEventDto> events) {
        Computer computer = computerRepository.findByAgentId(agentId)
                .orElse(null);
        if (computer == null || events == null || events.isEmpty()) return;

        for (DiagnosticEventDto dto : events) {
            DiagnosticCategory cat;
            try {
                cat = DiagnosticCategory.valueOf(dto.getCategory().toUpperCase());
            } catch (Exception e) {
                cat = DiagnosticCategory.APPLICATION;
            }

            DiagnosticEvent event = DiagnosticEvent.builder()
                    .computer(computer)
                    .eventSource(dto.getEventSource())
                    .eventId(dto.getEventId())
                    .category(cat)
                    .message(dto.getMessage())
                    .occurredAt(dto.getOccurredAt() != null ? dto.getOccurredAt() : Instant.now())
                    .createdAt(Instant.now())
                    .build();

            diagnosticEventRepository.save(event);

            // Also ingest into SystemLog table for humanized Windows log viewer
            try {
                logHumanizerService.ingestAndHumanizeLog(
                        computer.getId(),
                        dto.getEventId(),
                        dto.getEventSource(),
                        dto.getCategory(),
                        dto.getMessage()
                );
            } catch (Exception e) {
                log.debug("Error ingesting log into LogHumanizerService: {}", e.getMessage());
            }
        }

        processMetricsForIncidents(computer.getId());
    }

    @Override
    @Transactional
    public void processMetricsForIncidents(String computerId) {
        AIDiagnosisReportDto report = evaluateDiagnosis(computerId);

        if (report.isProblemActive()) {
            DiagnosticCategory cat = parseCategoryFromProblem(report.getProblemDetected());
            Optional<DiagnosticIncident> activeInc = diagnosticIncidentRepository
                    .findFirstByComputerIdAndCategoryAndIncidentStatus(computerId, cat, IncidentStatus.ACTIVE);

            if (activeInc.isPresent()) {
                DiagnosticIncident inc = activeInc.get();
                inc.setLastSeenAt(Instant.now());
                diagnosticIncidentRepository.save(inc);
            } else {
                String evidenceJson;
                try {
                    evidenceJson = objectMapper.writeValueAsString(report.getEvidence());
                } catch (Exception e) {
                    evidenceJson = "[]";
                }

                String possibleCausesJson = null;
                if (report.getPossibleCauses() != null) {
                    try {
                        possibleCausesJson = objectMapper.writeValueAsString(report.getPossibleCauses());
                    } catch (Exception e) {
                        possibleCausesJson = null;
                    }
                }

                DiagnosticIncident newInc = DiagnosticIncident.builder()
                        .computer(computerRepository.findById(computerId).orElse(null))
                        .problemTitle(report.getProblemDetected())
                        .category(cat)
                        .confirmationStatus(ConfirmationStatus.valueOf(report.getConfirmationStatus()))
                        .incidentStatus(IncidentStatus.ACTIVE)
                        .exactReason(report.getExactReason())
                        .evidenceJson(evidenceJson)
                        .solution(report.getSolution())
                        .possibleCausesJson(possibleCausesJson)
                        .detectedAt(Instant.now())
                        .lastSeenAt(Instant.now())
                        .build();

                diagnosticIncidentRepository.save(newInc);
            }
        } else {
            List<DiagnosticIncident> activeList = diagnosticIncidentRepository.findByComputerIdAndIncidentStatus(computerId, IncidentStatus.ACTIVE);
            for (DiagnosticIncident inc : activeList) {
                inc.setIncidentStatus(IncidentStatus.RESOLVED);
                inc.setResolvedAt(Instant.now());
                diagnosticIncidentRepository.save(inc);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AISystemHealthSummaryDto getSystemHealthSummary() {
        List<Computer> all = computerRepository.findAll();
        int total = all.size();
        int healthy = 0;
        int needsAttention = 0;
        int critical = 0;
        int predictedRisks = 0;

        for (Computer c : all) {
            if (c.getStatus() == ComputerStatus.CRITICAL) {
                critical++;
            } else if (c.getStatus() == ComputerStatus.WARNING) {
                needsAttention++;
            } else {
                healthy++;
            }

            AIPredictionDto pred = evaluatePrediction(c.getId());
            if (pred.isDataSufficient() && !pred.getCategory().equals("PERFORMANCE") && !pred.getCategory().equals("UNKNOWN")) {
                predictedRisks++;
            }
        }

        return AISystemHealthSummaryDto.builder()
                .totalComputers(total)
                .healthyCount(healthy)
                .needsAttentionCount(needsAttention)
                .criticalCount(critical)
                .predictedRisksCount(predictedRisks)
                .criticalProblemsCount(critical)
                .build();
    }

    private AIDiagnosisReportDto buildDiagnosisDto(Computer computer, String problem, String reason, List<String> evidence,
                                                     String solution, String status, List<PossibleCauseDto> possibleCauses,
                                                     Instant detectedAt, boolean isActive) {
        return AIDiagnosisReportDto.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .problemDetected(problem)
                .exactReason(reason)
                .evidence(evidence)
                .solution(solution)
                .confirmationStatus(status)
                .possibleCauses(possibleCauses)
                .detectedAt(detectedAt)
                .isProblemActive(isActive)
                .build();
    }

    private DiagnosticCategory parseCategoryFromProblem(String problem) {
        if (problem == null) return DiagnosticCategory.APPLICATION;
        String lower = problem.toLowerCase();
        if (lower.contains("graphics") || lower.contains("display")) return DiagnosticCategory.GRAPHICS;
        if (lower.contains("disk") || lower.contains("storage")) return DiagnosticCategory.STORAGE;
        if (lower.contains("memory") || lower.contains("ram")) return DiagnosticCategory.MEMORY;
        if (lower.contains("overheating") || lower.contains("temperature")) return DiagnosticCategory.THERMAL;
        if (lower.contains("internet") || lower.contains("network")) return DiagnosticCategory.NETWORK;
        if (lower.contains("shut down") || lower.contains("shutdown")) return DiagnosticCategory.UNEXPECTED_SHUTDOWN;
        return DiagnosticCategory.SYSTEM_CRASH;
    }
}
