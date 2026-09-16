package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.AIInsightsResponseDto;

public interface AIInsightsService {
    AIInsightsResponseDto generateHistoricalInsights(String timeRange, String computerId, String labId);
}
