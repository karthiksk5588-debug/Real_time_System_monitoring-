package com.neurosys.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIInsightsResponseDto {

    private String timeRange;
    private String computerId;
    private String labId;

    private boolean isDataSufficient;
    private String insufficientDataReason;

    private int totalDataPoints;

    private Double cpuAveragePercent;
    private Double cpuPeakPercent;
    private String cpuTrend;

    private Double memoryAveragePercent;
    private Double memoryPeakPercent;
    private String memoryTrend;

    private Double diskAveragePercent;
    private Double diskGrowthRateGb;

    private Double networkAvgThroughputKbps;
    private boolean sustainedHighUsageDetected;

    private List<String> insights;
    private List<String> recommendations;

    private Instant evaluatedAt;
}
