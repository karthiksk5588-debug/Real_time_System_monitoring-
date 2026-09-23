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
public class AlertDto {
    private String id;
    private String computerId;
    private String hostname;
    private String computerName;
    private String labId;
    private String labCode;
    private String labName;
    private String title;
    private String message;
    private String recommendedAction;
    private List<String> evidence;
    private String severity;
    private String alertType;
    private String resourceKey;
    private String status;
    private Double triggeredValue;
    private Double thresholdValue;
    private Integer occurrenceCount;
    private Instant firstDetectedAt;
    private Instant lastDetectedAt;
    private Instant triggeredAt;
    private Instant resolvedAt;
}
