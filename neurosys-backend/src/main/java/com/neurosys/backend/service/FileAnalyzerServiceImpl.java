package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.FileAnalysisReportDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FileAnalyzerServiceImpl implements FileAnalyzerService {

    private final ComputerRepository computerRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final Map<String, FileAnalysisReportDto> latestFileReports = new java.util.concurrent.ConcurrentHashMap<>();

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

        double tempGb = payload.containsKey("tempJunkFilesSizeGb") ? ((Number) payload.get("tempJunkFilesSizeGb")).doubleValue() : 0.0;
        int tempCount = payload.containsKey("tempJunkFilesCount") ? ((Number) payload.get("tempJunkFilesCount")).intValue() : 0;
        double dupGb = payload.containsKey("duplicateFilesSizeGb") ? ((Number) payload.get("duplicateFilesSizeGb")).doubleValue() : 0.0;
        int dupCount = payload.containsKey("duplicateFilesCount") ? ((Number) payload.get("duplicateFilesCount")).intValue() : 0;
        double largeGb = payload.containsKey("largeFilesSizeGb") ? ((Number) payload.get("largeFilesSizeGb")).doubleValue() : 0.0;
        int largeCount = payload.containsKey("largeFilesCount") ? ((Number) payload.get("largeFilesCount")).intValue() : 0;
        double scannedGb = payload.containsKey("totalScannedSizeGb") && ((Number) payload.get("totalScannedSizeGb")).doubleValue() > 0
                ? ((Number) payload.get("totalScannedSizeGb")).doubleValue()
                : totalDiskGb;

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("System & OS Files", Math.round(usedDiskGb * 0.40 * 10.0) / 10.0);
        breakdown.put("Applications & Libraries", Math.round(usedDiskGb * 0.35 * 10.0) / 10.0);
        breakdown.put("User Workspace Data", Math.round(usedDiskGb * 0.25 * 10.0) / 10.0);

        List<String> suggestions = new ArrayList<>();
        if (diskUsagePercent > 85.0 || freeDiskGb < 15.0) {
            suggestions.add(String.format("CRITICAL: Storage capacity low (%.1f GB free space remaining). Clean temporary cache files.", freeDiskGb));
        } else {
            suggestions.add(String.format("Drive operating with %.1f GB free storage (%.1f%% utilized).", freeDiskGb, diskUsagePercent));
        }
        if (tempCount > 0) {
            suggestions.add(String.format("Clean %d temporary junk files to free up %.2f GB of space.", tempCount, tempGb));
        }
        if (dupCount > 0) {
            suggestions.add(String.format("Found %d duplicate files consuming %.2f GB of disk space.", dupCount, dupGb));
        }
        if (largeCount > 0) {
            suggestions.add(String.format("Detected %d large files (>100MB) occupying %.2f GB.", largeCount, largeGb));
        }

        FileAnalysisReportDto report = FileAnalysisReportDto.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .totalScannedSizeGb(scannedGb)
                .duplicateFilesSizeGb(dupGb)
                .duplicateFilesCount(dupCount)
                .largeFilesSizeGb(largeGb)
                .largeFilesCount(largeCount)
                .tempJunkFilesSizeGb(tempGb)
                .tempJunkFilesCount(tempCount)
                .storageBreakdownGb(breakdown)
                .optimizationSuggestions(suggestions)
                .build();

        latestFileReports.put(computer.getId(), report);
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

        if (latestMetric == null || latestMetric.getDiskFreeGb() == null || latestMetric.getDiskUsedGb() == null) {
            return FileAnalysisReportDto.builder()
                    .computerId(computer.getId())
                    .hostname(computer.getHostname())
                    .totalScannedSizeGb(0.0)
                    .duplicateFilesSizeGb(0.0)
                    .duplicateFilesCount(0)
                    .largeFilesSizeGb(0.0)
                    .largeFilesCount(0)
                    .tempJunkFilesSizeGb(0.0)
                    .tempJunkFilesCount(0)
                    .storageBreakdownGb(Map.of())
                    .optimizationSuggestions(List.of("Waiting for workstation storage telemetry..."))
                    .build();
        }

        double freeDiskGb = Math.round(latestMetric.getDiskFreeGb() * 10.0) / 10.0;
        double usedDiskGb = Math.round(latestMetric.getDiskUsedGb() * 10.0) / 10.0;
        double totalDiskGb = Math.round((usedDiskGb + freeDiskGb) * 10.0) / 10.0;
        double diskUsagePercent = latestMetric.getDiskUsagePercent() != null ? latestMetric.getDiskUsagePercent() : 0.0;

        Map<String, Double> breakdown = new HashMap<>();
        breakdown.put("System & OS Files", Math.round(usedDiskGb * 0.40 * 10.0) / 10.0);
        breakdown.put("Applications & Libraries", Math.round(usedDiskGb * 0.35 * 10.0) / 10.0);
        breakdown.put("User Workspace Data", Math.round(usedDiskGb * 0.25 * 10.0) / 10.0);

        List<String> suggestions = new ArrayList<>();
        if (diskUsagePercent > 85.0 || freeDiskGb < 15.0) {
            suggestions.add(String.format("CRITICAL: Storage capacity low (%.1f GB free space remaining). Clean temporary cache files.", freeDiskGb));
        } else {
            suggestions.add(String.format("Drive operating with %.1f GB free storage (%.1f%% utilized).", freeDiskGb, diskUsagePercent));
        }
        suggestions.add("Perform periodic cleanup of temporary build caches, browser downloads, and installer artifacts.");

        return FileAnalysisReportDto.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .totalScannedSizeGb(totalDiskGb)
                .duplicateFilesSizeGb(0.0)
                .duplicateFilesCount(0)
                .largeFilesSizeGb(0.0)
                .largeFilesCount(0)
                .tempJunkFilesSizeGb(0.0)
                .tempJunkFilesCount(0)
                .storageBreakdownGb(breakdown)
                .optimizationSuggestions(suggestions)
                .build();
    }
}
