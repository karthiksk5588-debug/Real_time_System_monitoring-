package com.neurosys.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileAnalysisReportDto {
    private String computerId;
    private String hostname;
    
    // Audit & Scan Scope Fields
    private String scanStatus; // COMPLETED | COMPLETED_WITH_WARNINGS
    private List<String> scannedLocations;
    private Long filesExamined;
    private Long filesAccessible;
    private Long filesSkipped;
    private String skippedReason;

    // Actual Scanned File Metrics
    private Double totalScannedSizeGb;
    private Double duplicateFilesSizeGb;
    private int duplicateFilesCount;
    private int duplicateGroupsCount;
    private Double largeFilesSizeGb;
    private int largeFilesCount;
    private Double tempJunkFilesSizeGb;
    private int tempJunkFilesCount;

    // Physical Disk Capacity Fields (Distinct from File Scan)
    private Double diskTotalCapacityGb;
    private Double diskUsedGb;
    private Double diskFreeGb;

    // Detailed Item Breakdowns
    private List<Map<String, Object>> largeFilesList;
    private List<Map<String, Object>> duplicateGroupsList;
    private Map<String, Double> storageBreakdownGb;
    private List<String> optimizationSuggestions;
}
