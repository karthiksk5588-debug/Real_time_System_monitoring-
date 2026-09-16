package com.neurosys.backend.controller;

import com.neurosys.backend.dto.response.AnalyticsSummaryDto;
import com.neurosys.backend.dto.response.ApiResponse;
import com.neurosys.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neurosys.backend.dto.response.AIInsightsResponseDto;
import com.neurosys.backend.service.AIInsightsService;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics Dashboard Endpoint", description = "REST API delivering executive fleet telemetry, Top Busy/Healthy computer leaderboards, AI Insights, and alert statistics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AIInsightsService aiInsightsService;

    @GetMapping("/summary")
    @Operation(summary = "Get Analytics Dashboard Summary", description = "Retrieve fleet summary tiles, Top Busy/Healthy computer cards, prediction summary, and alert breakdown")
    public ResponseEntity<ApiResponse<AnalyticsSummaryDto>> getAnalyticsSummary() {
        AnalyticsSummaryDto summary = analyticsService.getExecutiveAnalyticsSummary();
        return ResponseEntity.ok(ApiResponse.success("Analytics summary fetched successfully", summary));
    }

    @GetMapping("/ai-insights")
    @Operation(summary = "Get Historical Telemetry AI Insights", description = "Analyze database historical telemetry records over 1h, 6h, 24h, or 7d time ranges with lab/computer scoping")
    public ResponseEntity<ApiResponse<AIInsightsResponseDto>> getAIInsights(
            @RequestParam(defaultValue = "24h") String timeRange,
            @RequestParam(required = false) String computerId,
            @RequestParam(required = false) String labId) {
        AIInsightsResponseDto insights = aiInsightsService.generateHistoricalInsights(timeRange, computerId, labId);
        return ResponseEntity.ok(ApiResponse.success("AI Insights generated successfully from database telemetry", insights));
    }
}
