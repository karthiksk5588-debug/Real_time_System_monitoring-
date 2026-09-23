package com.neurosys.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisDto {
    private String id;
    private String computerId;
    private String hostname;
    private Integer eventId;
    private String providerName;
    private String logLevel;
    private String sourceComponent;
    private String rawMessage;

    // Human-Readable Classification Fields
    private String eventCategory;    // Application Crash | System Crash | Service Failure | Disk Error | Driver Problem | Hardware Error | Security Problem
    private String title;            // Human-friendly title
    private String whatHappened;     // Plain English description of the event
    private String whyItMatters;     // Explanation of impact
    private String recommendedAction; // Step-by-step remediation advice
    private String severity;         // CRITICAL | WARNING | INFO

    private String simplifiedEnglish;
    private String suggestedSolution;
    private Instant timestamp;
}
