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
public class SoftwarePackageDto {
    private String id;
    private String name;
    private String version;
    private String installerUrl;
    private InstallerType installerType;
    private String silentArguments;
    private String checksum;
    private String supportedOs;
    private boolean active;
}
