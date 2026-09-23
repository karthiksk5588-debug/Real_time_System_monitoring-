package com.neurosys.backend.repository;

import com.neurosys.backend.entity.Alert;
import com.neurosys.backend.enums.AlertSeverity;
import com.neurosys.backend.enums.AlertStatus;
import com.neurosys.backend.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, String> {
    List<Alert> findByComputerIdOrderByTriggeredAtDesc(String computerId);
    Page<Alert> findByStatus(AlertStatus status, Pageable pageable);
    Page<Alert> findBySeverity(AlertSeverity severity, Pageable pageable);
    long countByStatus(AlertStatus status);
    long countBySeverity(AlertSeverity severity);
    Optional<Alert> findFirstByComputerIdAndTitleAndStatus(String computerId, String title, AlertStatus status);
    Optional<Alert> findFirstByComputerIdAndAlertTypeAndStatusIn(String computerId, AlertType alertType, List<AlertStatus> statuses);
    Optional<Alert> findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(String computerId, AlertType alertType, String resourceKey, List<AlertStatus> statuses);
    boolean existsByComputerIdAndAlertTypeAndStatusAndResolvedAtAfter(String computerId, AlertType alertType, AlertStatus status, Instant after);
    boolean existsByComputerIdAndAlertTypeAndResourceKeyAndStatusAndResolvedAtAfter(String computerId, AlertType alertType, String resourceKey, AlertStatus status, Instant after);
}
