package com.neurosys.backend.dto.response;

import com.neurosys.backend.enums.DeploymentTaskStatus;
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
public class DeploymentResponseDto {
    private String id;
    private String deploymentNumber;
    private SoftwarePackageDto softwarePackage;
    private String labId;
    private String labName;
    private String createdByUser;
    private DeploymentTaskStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private List<DeploymentTargetDto> targets;
    private int totalTargets;
    private int completedTargets;
    private int failedTargets;
    private int pendingTargets;
}
