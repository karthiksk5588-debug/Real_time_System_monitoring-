package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.AlertDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemMetric;

import java.util.List;

public interface AlertEngineService {
    List<AlertDto> evaluateAndTriggerAlerts(Computer computer, SystemMetric metric);
    void triggerOfflineAlert(Computer computer);
    void triggerOfflineAlert(Computer computer, long offlineDurationSeconds);
    void resolveOfflineAlert(Computer computer);
    List<AlertDto> getAllAlerts();
    List<AlertDto> getAlertsByLabId(String labId);
    List<AlertDto> getComputerAlerts(String computerId);
    AlertDto acknowledgeAlert(String alertId);
    AlertDto resolveAlert(String alertId);
}
