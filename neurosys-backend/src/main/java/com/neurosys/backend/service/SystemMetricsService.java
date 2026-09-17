package com.neurosys.backend.service;

import com.neurosys.backend.dto.request.ProcessInfoDto;
import com.neurosys.backend.dto.request.SystemMetricsIngestionRequest;
import com.neurosys.backend.dto.response.SystemMetricDto;

import java.util.List;

public interface SystemMetricsService {
    SystemMetricDto ingestMetrics(SystemMetricsIngestionRequest request);
    List<SystemMetricDto> getMetricHistory(String computerId, int limit);
    SystemMetricDto getLatestMetric(String computerId);
    List<ProcessInfoDto> getLatestProcesses(String computerId);
}
