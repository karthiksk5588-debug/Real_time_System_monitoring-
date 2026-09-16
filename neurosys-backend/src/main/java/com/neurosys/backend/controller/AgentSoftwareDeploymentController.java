package com.neurosys.backend.controller;

import com.neurosys.backend.dto.request.DeploymentStatusUpdateRequest;
import com.neurosys.backend.dto.response.AgentDeploymentTaskDto;
import com.neurosys.backend.dto.response.ApiResponse;
import com.neurosys.backend.dto.response.DeploymentTargetDto;
import com.neurosys.backend.service.SoftwareDeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/agent/deployments")
@RequiredArgsConstructor
public class AgentSoftwareDeploymentController {

    private final SoftwareDeploymentService deploymentService;

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<List<AgentDeploymentTaskDto>>> getPendingTasks(
            @RequestParam("agentId") String agentId) {
        List<AgentDeploymentTaskDto> tasks = deploymentService.getPendingTasksForAgent(agentId);
        return ResponseEntity.ok(ApiResponse.success("Pending deployment tasks fetched", tasks));
    }

    @PutMapping("/{deploymentId}/status")
    public ResponseEntity<ApiResponse<DeploymentTargetDto>> updateStatus(
            @PathVariable String deploymentId,
            @RequestParam("agentId") String agentId,
            @Valid @RequestBody DeploymentStatusUpdateRequest request) {
        DeploymentTargetDto updated = deploymentService.updateTargetStatus(deploymentId, agentId, request);
        return ResponseEntity.ok(ApiResponse.success("Deployment target status updated", updated));
    }
}
