package com.neurosys.backend.repository;

import com.neurosys.backend.entity.SoftwareDeployment;
import com.neurosys.backend.enums.DeploymentTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SoftwareDeploymentRepository extends JpaRepository<SoftwareDeployment, String> {
    Optional<SoftwareDeployment> findByDeploymentNumber(String deploymentNumber);
    List<SoftwareDeployment> findByLabIdOrderByCreatedAtDesc(String labId);
    List<SoftwareDeployment> findAllByOrderByCreatedAtDesc();
    List<SoftwareDeployment> findByStatus(DeploymentTaskStatus status);
    List<SoftwareDeployment> findByStatusIn(Collection<DeploymentTaskStatus> statuses);
}
