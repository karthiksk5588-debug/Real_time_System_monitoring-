package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.FileAnalysisReportDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileAnalyzerServiceImpl implements FileAnalyzerService {

    private final ComputerRepository computerRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final Map<String, FileAnalysisReportDto> latestFileReports = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public void recordAgentFileAnalysis(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey("agentId")) return;
        String agentId = (String) payload.get("agentId");

        Computer computer = computerRepository.findByAgentId(agentId).orElse(null);
        if (computer == null) return;

        SystemMetric latestMetric = systemMetricRepository.findLatestByComputerId(computer.getId()).orElse(null);
        double freeDiskGb = latestMetric != null && latestMetric.getDiskFreeGb() != null ? latestMetric.getDiskFreeGb() : 0.0;
        double usedDiskGb = latestMetric != null && latestMetric.getDiskUsedGb() != null ? latestMetric.getDiskUsedGb() : 0.0;
        double totalDiskGb = Math.round((usedDiskGb + freeDiskGb) * 10.0) / 10.0;
        double diskUsagePercent = latestMetric != null && latestMetric.getDiskUsagePercent() != null ? latestMetric.getDiskUsagePercent() : 0.0;

        String scanStatus = payload.containsKey("scanStatus") ? (String) payload.get("scanStatus") : "COMPLETED";
        List<String> scannedLocations = payload.containsKey("scannedLocations") ? (List<String>) payload.get("scannedLocations") : List.of();
        
        long filesExamined = payload.containsKey("filesExamined") ? ((Number) payload.get("filesExamined")).longValue() : 0L;
        long filesAccessible = payload.containsKey("filesAccessible") ? ((Number) payload.get("filesAccessible")).longValue() : 0L;
        long filesSkipped = payload.containsKey("filesSkipped") ? ((Number) payload.get("filesSkipped")).longValue() : 0L;
        String skippedReason = payload.containsKey("skippedReason") ? (String) payload.get("skippedReason") : "None";

        double tempGb = payload.containsKey("tempJunkFilesSizeGb") ? ((Number) payload.get("tempJunkFilesSizeGb")).doubleValue() : 0.0;
        int tempCount = payload.containsKey("tempJunkFilesCount") ? ((Number) payload.get("tempJunkFilesCount")).intValue() : 0;
        double dupGb = payload.containsKey("duplicateFilesSizeGb") ? ((Number) payload.get("duplicateFilesSizeGb")).doubleValue() : 0.0;
        int dupCount = payload.containsKey("duplicateFilesCount") ? ((Number) payload.get("duplicateFilesCount")).intValue() : 0;
        int dupGroups = payload.containsKey("duplicateGroupsCount") ? ((Number) payload.get("duplicateGroupsCount")).intValue() : 0;
        double largeGb = payload.containsKey("largeFilesSizeGb") ? ((Number) payload.get("largeFilesSizeGb")).doubleValue() : 0.0;
        int largeCount = payload.containsKey("largeFilesCount") ? ((Number) payload.get("largeFilesCount")).intValue() : 0;

        // Actual sum of scanned file sizes (distinct from physical disk capacity)
        double scannedFileGb = payload.containsKey("totalScannedSizeGb") ? ((Number) payload.get("totalScannedSizeGb")).doubleValue() : 0.0;

        List<Map<String, Object>> largeFilesList = payload.containsKey("largeFilesList") ? (List<Map<String, Object>>) payload.get("largeFilesList") : List.of();
        List<Map<String, Object>> duplicateGroupsList = payload.containsKey("duplicateGroupsList") ? (List<Map<String, Object>>) payload.get("duplicateGroupsList") : List.of();

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("System & OS Files", Math.round(usedDiskGb * 0.40 * 10.0) / 10.0);
        breakdown.put("Applications & Libraries", Math.round(usedDiskGb * 0.35 * 10.0) / 10.0);
        breakdown.put("User Workspace Data", Math.round(usedDiskGb * 0.25 * 10.0) / 10.0);

        // Generate REAL, actionable optimization recommendations based strictly on measurements
        List<String> suggestions = new ArrayList<>();
        if (diskUsagePercent >= 90.0 || freeDiskGb < 20.0) {
            suggestions.add(String.format("CRITICAL: Workstation storage capacity is %.1f%% full. Only %.1f GB remains available.", diskUsagePercent, freeDiskGb));
        }

        if (largeCount > 0 && largeGb > 0.0) {
            suggestions.add(String.format("%.1f GB is occupied by %d files larger than 100 MB. Review old installers, archives, and build artifacts.", largeGb, largeCount));
        }

        if (dupGroups > 0 && dupGb > 0.0) {
            suggestions.add(String.format("Found %d duplicate file groups (%d redundant files) occupying %.2f GB of recoverable disk space.", dupGroups, dupCount, dupGb));
        }

        if (tempCount > 0 && tempGb > 0.0) {
            suggestions.add(String.format("Detected %d temporary junk files consuming %.2f GB of disk space.", tempCount, tempGb));
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Storage is healthy. No significant cleanup candidates were found in the scanned locations.");
        }

        FileAnalysisReportDto report = FileAnalysisReportDto.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .scanStatus(scanStatus)
                .scannedLocations(scannedLocations)
                .filesExamined(filesExamined)
                .filesAccessible(filesAccessible)
                .filesSkipped(filesSkipped)
                .skippedReason(skippedReason)
                .totalScannedSizeGb(scannedFileGb)
                .duplicateFilesSizeGb(dupGb)
                .duplicateFilesCount(dupCount)
                .duplicateGroupsCount(dupGroups)
                .largeFilesSizeGb(largeGb)
                .largeFilesCount(largeCount)
                .tempJunkFilesSizeGb(tempGb)
                .tempJunkFilesCount(tempCount)
                .diskTotalCapacityGb(totalDiskGb)
                .diskUsedGb(usedDiskGb)
                .diskFreeGb(freeDiskGb)
                .largeFilesList(largeFilesList)
                .duplicateGroupsList(duplicateGroupsList)
                .storageBreakdownGb(breakdown)
                .optimizationSuggestions(suggestions)
                .build();

        latestFileReports.put(computer.getId(), report);
        log.info("[FILE ANALYZER] Recorded live scan report for {}: Scanned {} files ({} GB actual files). Status: {}",
                computer.getHostname(), filesAccessible, scannedFileGb, scanStatus);
    }

    @Override
    @Transactional(readOnly = true)
    public FileAnalysisReportDto scanComputerFiles(String computerId) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));

        if (latestFileReports.containsKey(computerId)) {
            return latestFileReports.get(computerId);
        }

        SystemMetric latestMetric = systemMetricRepository.findLatestByComputerId(computerId).orElse(null);
        double freeDiskGb = latestMetric != null && latestMetric.getDiskFreeGb() != null ? latestMetric.getDiskFreeGb() : 0.0;
        double usedDiskGb = latestMetric != null && latestMetric.getDiskUsedGb() != null ? latestMetric.getDiskUsedGb() : 0.0;
        double totalDiskGb = Math.round((usedDiskGb + freeDiskGb) * 10.0) / 10.0;

        return FileAnalysisReportDto.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .scanStatus("NOT_STARTED")
                .scannedLocations(List.of())
                .filesExamined(0L)
                .filesAccessible(0L)
                .filesSkipped(0L)
                .skippedReason("None")
                .totalScannedSizeGb(0.0)
                .duplicateFilesSizeGb(0.0)
                .duplicateFilesCount(0)
                .duplicateGroupsCount(0)
                .largeFilesSizeGb(0.0)
                .largeFilesCount(0)
                .tempJunkFilesSizeGb(0.0)
                .tempJunkFilesCount(0)
                .diskTotalCapacityGb(totalDiskGb)
                .diskUsedGb(usedDiskGb)
                .diskFreeGb(freeDiskGb)
                .largeFilesList(List.of())
                .duplicateGroupsList(List.of())
                .storageBreakdownGb(Map.of())
                .optimizationSuggestions(List.of("Waiting for workstation storage agent scan cycle..."))
                .build();
    }
}
