package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.AIInsightsResponseDto;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIInsightsServiceImpl implements AIInsightsService {

    private final SystemMetricRepository systemMetricRepository;

    @Override
    @Transactional(readOnly = true)
    public AIInsightsResponseDto generateHistoricalInsights(String timeRange, String computerId, String labId) {
        String cleanRange = (timeRange != null && !timeRange.trim().isEmpty()) ? timeRange.trim().toLowerCase() : "24h";

        Instant end = Instant.now();
        Instant start;

        switch (cleanRange) {
            case "1h":
                start = end.minus(1, ChronoUnit.HOURS);
                break;
            case "6h":
                start = end.minus(6, ChronoUnit.HOURS);
                break;
            case "7d":
                start = end.minus(7, ChronoUnit.DAYS);
                break;
            case "24h":
            default:
                cleanRange = "24h";
                start = end.minus(24, ChronoUnit.HOURS);
                break;
        }

        List<SystemMetric> metrics;
        if (computerId != null && !computerId.trim().isEmpty()) {
            metrics = systemMetricRepository.findByComputerIdAndRecordedAtBetweenOrderByRecordedAtAsc(computerId, start, end);
        } else if (labId != null && !labId.trim().isEmpty() && !"ALL".equalsIgnoreCase(labId)) {
            metrics = systemMetricRepository.findByComputerLabIdAndRecordedAtBetweenOrderByRecordedAtAsc(labId, start, end);
        } else {
            metrics = systemMetricRepository.findByRecordedAtBetweenOrderByRecordedAtAsc(start, end);
        }

        if (metrics == null || metrics.size() < 2) {
            int count = metrics != null ? metrics.size() : 0;
            log.info("[INFO] Insufficient historical telemetry data for AI Insights (Range: {}, Count: {}). Returning data sufficiency guard.",
                    cleanRange, count);

            return AIInsightsResponseDto.builder()
                    .timeRange(cleanRange)
                    .computerId(computerId)
                    .labId(labId)
                    .isDataSufficient(false)
                    .insufficientDataReason("Not enough historical telemetry data yet.")
                    .totalDataPoints(count)
                    .insights(List.of("Not enough historical telemetry data yet."))
                    .recommendations(List.of("Keep the Windows Agent active to accumulate historical telemetry records in the database."))
                    .evaluatedAt(Instant.now())
                    .build();
        }

        int n = metrics.size();
        double sumCpu = 0, peakCpu = 0;
        double sumRam = 0, peakRam = 0;
        double sumDisk = 0;
        double sumNetThroughputBytesSec = 0;
        int highCpuCount = 0;
        int highRamCount = 0;

        for (SystemMetric m : metrics) {
            double cpu = m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0;
            double ram = m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0;
            double disk = m.getDiskUsagePercent() != null ? m.getDiskUsagePercent() : 0.0;
            double rx = m.getNetworkRxBytesSec() != null ? m.getNetworkRxBytesSec() : 0.0;
            double tx = m.getNetworkTxBytesSec() != null ? m.getNetworkTxBytesSec() : 0.0;

            sumCpu += cpu;
            if (cpu > peakCpu) peakCpu = cpu;
            if (cpu >= 85.0) highCpuCount++;

            sumRam += ram;
            if (ram > peakRam) peakRam = ram;
            if (ram >= 88.0) highRamCount++;

            sumDisk += disk;
            sumNetThroughputBytesSec += (rx + tx);
        }

        double avgCpu = Math.round((sumCpu / n) * 10.0) / 10.0;
        double avgRam = Math.round((sumRam / n) * 10.0) / 10.0;
        double avgDisk = Math.round((sumDisk / n) * 10.0) / 10.0;
        peakCpu = Math.round(peakCpu * 10.0) / 10.0;
        peakRam = Math.round(peakRam * 10.0) / 10.0;

        double avgNetKbps = Math.round((sumNetThroughputBytesSec / n) * 8.0 / 1024.0 * 10.0) / 10.0;

        // Determine trend slopes (comparing first 20% vs last 20% of window)
        int sampleSize = Math.max(1, n / 5);
        double firstCpuAvg = metrics.stream().limit(sampleSize).mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0).average().orElse(avgCpu);
        double lastCpuAvg = metrics.stream().skip(n - sampleSize).mapToDouble(m -> m.getCpuUsagePercent() != null ? m.getCpuUsagePercent() : 0.0).average().orElse(avgCpu);

        String cpuTrend = (lastCpuAvg - firstCpuAvg > 5.0) ? "RISING" : ((firstCpuAvg - lastCpuAvg > 5.0) ? "FALLING" : "STABLE");

        double firstRamAvg = metrics.stream().limit(sampleSize).mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0).average().orElse(avgRam);
        double lastRamAvg = metrics.stream().skip(n - sampleSize).mapToDouble(m -> m.getMemoryUsagePercent() != null ? m.getMemoryUsagePercent() : 0.0).average().orElse(avgRam);

        String ramTrend = (lastRamAvg - firstRamAvg > 5.0) ? "RISING" : ((firstRamAvg - lastRamAvg > 5.0) ? "FALLING" : "STABLE");

        // Disk growth rate in GB over the window
        double oldestFreeGb = metrics.get(0).getDiskFreeGb() != null ? metrics.get(0).getDiskFreeGb() : 100.0;
        double latestFreeGb = metrics.get(n - 1).getDiskFreeGb() != null ? metrics.get(n - 1).getDiskFreeGb() : oldestFreeGb;
        double diskGrowthGb = Math.max(0.0, Math.round((oldestFreeGb - latestFreeGb) * 100.0) / 100.0);

        boolean sustainedHighUsage = (highCpuCount >= (n * 0.4)) || (highRamCount >= (n * 0.4));

        List<String> insights = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        insights.add(String.format("Analyzed %d historical database telemetry records over the past %s.", n, cleanRange.toUpperCase()));
        insights.add(String.format("Fleet CPU Average: %.1f%% (Peak: %.1f%%). Historical Trend: %s.", avgCpu, peakCpu, cpuTrend));
        insights.add(String.format("Fleet RAM Allocation Average: %.1f%% (Peak: %.1f%%). Historical Trend: %s.", avgRam, peakRam, ramTrend));
        insights.add(String.format("Storage Capacity Average: %.1f%% used. Estimated storage growth: +%.2f GB over %s.", avgDisk, diskGrowthGb, cleanRange));
        insights.add(String.format("Average Network Throughput: %.1f Kbps across active workstations.", avgNetKbps));

        if (sustainedHighUsage) {
            insights.add("⚠️ Sustained high resource usage detected in >= 40% of historical telemetry samples.");
            recommendations.add("Inspect resource-intensive background applications or scale workstation hardware specifications.");
        } else {
            insights.add("🟢 Resource utilization levels remained stable within normal operational parameters.");
            recommendations.add("Continue standard telemetry monitoring cycles.");
        }

        if (diskGrowthGb > 2.0) {
            recommendations.add("Clear temporary directory caches and check log rotation configs to preserve disk capacity.");
        }

        return AIInsightsResponseDto.builder()
                .timeRange(cleanRange)
                .computerId(computerId)
                .labId(labId)
                .isDataSufficient(true)
                .insufficientDataReason(null)
                .totalDataPoints(n)
                .cpuAveragePercent(avgCpu)
                .cpuPeakPercent(peakCpu)
                .cpuTrend(cpuTrend)
                .memoryAveragePercent(avgRam)
                .memoryPeakPercent(peakRam)
                .memoryTrend(ramTrend)
                .diskAveragePercent(avgDisk)
                .diskGrowthRateGb(diskGrowthGb)
                .networkAvgThroughputKbps(avgNetKbps)
                .sustainedHighUsageDetected(sustainedHighUsage)
                .insights(insights)
                .recommendations(recommendations)
                .evaluatedAt(Instant.now())
                .build();
    }
}
