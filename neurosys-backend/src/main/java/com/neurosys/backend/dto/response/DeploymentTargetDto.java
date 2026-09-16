package com.neurosys.backend.dto.response;

import com.neurosys.backend.enums.DeploymentTargetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentTargetDto {
    private String id;
    private String computerId;
    private String computerName;
    private String hostname;
    private String ipAddress;
    private String agentId;
    private DeploymentTargetStatus status;
    private String statusDetail;
    private Instant startedAt;
    private Instant completedAt;
}
