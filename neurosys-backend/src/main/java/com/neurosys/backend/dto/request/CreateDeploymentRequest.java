package com.neurosys.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeploymentRequest {

    @NotBlank(message = "Software package ID is required")
    private String softwarePackageId;

    @NotBlank(message = "Lab ID is required")
    private String labId;

    private List<String> computerIds;

    @Builder.Default
    private Integer expirationHours = 24;
}
