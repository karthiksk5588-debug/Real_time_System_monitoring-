package com.neurosys.backend.dto.response;

import com.neurosys.backend.enums.InstallerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDeploymentTaskDto {
    private String targetId;
    private String deploymentId;
    private String deploymentNumber;
    private String packageName;
    private String packageVersion;
    private String installerUrl;
    private InstallerType installerType;
    private String silentArguments;
    private String checksum;
}
