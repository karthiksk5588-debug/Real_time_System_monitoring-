package com.neurosys.backend.dto.request;

import com.neurosys.backend.enums.DeploymentTargetStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private DeploymentTargetStatus status;

    private String statusDetail;
}
