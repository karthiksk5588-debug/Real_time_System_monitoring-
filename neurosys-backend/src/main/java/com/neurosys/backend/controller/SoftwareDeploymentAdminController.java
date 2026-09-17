package com.neurosys.backend.controller;

import com.neurosys.backend.dto.request.CreateDeploymentRequest;
import com.neurosys.backend.dto.response.ApiResponse;
import com.neurosys.backend.dto.response.DeploymentResponseDto;
import com.neurosys.backend.dto.response.SoftwarePackageDto;
import com.neurosys.backend.service.SoftwareDeploymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping({"/api/v1/admin/deployments", "/api/admin/deployments"})
@RequiredArgsConstructor
public class SoftwareDeploymentAdminController {

    private final SoftwareDeploymentService deploymentService;

    @GetMapping("/packages")
    public ResponseEntity<ApiResponse<List<SoftwarePackageDto>>> getAvailablePackages() {
        List<SoftwarePackageDto> packages = deploymentService.getAvailablePackages();
        return ResponseEntity.ok(ApiResponse.success("Available software packages fetched successfully", packages));
    }

    @PostMapping("/packages")
    public ResponseEntity<ApiResponse<SoftwarePackageDto>> createPackage(@Valid @RequestBody SoftwarePackageDto dto) {
        SoftwarePackageDto created = deploymentService.createPackage(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Software package added to catalog", created));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeploymentResponseDto>> createDeployment(
            @Valid @RequestBody CreateDeploymentRequest request,
            Authentication authentication) {
        String username = (authentication != null && authentication.getName() != null) ? authentication.getName() : "ADMIN";
        DeploymentResponseDto deployment = deploymentService.createDeployment(request, username);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Software deployment task created successfully", deployment));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeploymentResponseDto>>> getAllDeployments() {
        List<DeploymentResponseDto> deployments = deploymentService.getAllDeployments();
        return ResponseEntity.ok(ApiResponse.success("All software deployments fetched successfully", deployments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeploymentResponseDto>> getDeploymentById(@PathVariable String id) {
        DeploymentResponseDto deployment = deploymentService.getDeploymentById(id);
        return ResponseEntity.ok(ApiResponse.success("Deployment details fetched successfully", deployment));
    }

    @GetMapping("/lab/{labId}")
    public ResponseEntity<ApiResponse<List<DeploymentResponseDto>>> getDeploymentsByLab(@PathVariable String labId) {
        List<DeploymentResponseDto> deployments = deploymentService.getDeploymentsByLab(labId);
        return ResponseEntity.ok(ApiResponse.success("Lab deployment tasks fetched successfully", deployments));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<DeploymentResponseDto>> cancelDeployment(
            @PathVariable String id,
            Authentication authentication) {
        String username = (authentication != null && authentication.getName() != null) ? authentication.getName() : "ADMIN";
        DeploymentResponseDto cancelled = deploymentService.cancelDeployment(id, username);
        return ResponseEntity.ok(ApiResponse.success("Deployment cancelled successfully", cancelled));
    }
}
