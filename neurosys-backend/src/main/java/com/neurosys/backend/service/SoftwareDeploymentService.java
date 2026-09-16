package com.neurosys.backend.service;

import com.neurosys.backend.dto.request.CreateDeploymentRequest;
import com.neurosys.backend.dto.request.DeploymentStatusUpdateRequest;
import com.neurosys.backend.dto.response.AgentDeploymentTaskDto;
import com.neurosys.backend.dto.response.DeploymentResponseDto;
import com.neurosys.backend.dto.response.DeploymentTargetDto;
import com.neurosys.backend.dto.response.SoftwarePackageDto;

import java.util.List;

public interface SoftwareDeploymentService {
    List<SoftwarePackageDto> getAvailablePackages();
    SoftwarePackageDto getPackageById(String id);
    SoftwarePackageDto createPackage(SoftwarePackageDto dto);
    
    DeploymentResponseDto createDeployment(CreateDeploymentRequest request, String username);
    List<DeploymentResponseDto> getAllDeployments();
    DeploymentResponseDto getDeploymentById(String id);
    List<DeploymentResponseDto> getDeploymentsByLab(String labId);
    DeploymentResponseDto cancelDeployment(String id, String username);

    // Agent endpoints
    List<AgentDeploymentTaskDto> getPendingTasksForAgent(String agentId);
    DeploymentTargetDto updateTargetStatus(String deploymentId, String agentId, DeploymentStatusUpdateRequest request);
    
    // Scheduled tasks
    void checkAndExpireDeployments();
}
