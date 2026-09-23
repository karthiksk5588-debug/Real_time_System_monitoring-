package com.neurosys.backend.entity;

import com.neurosys.backend.enums.AlertSeverity;
import com.neurosys.backend.enums.AlertStatus;
import com.neurosys.backend.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computer_id", nullable = false)
    private Computer computer;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "recommended_action", columnDefinition = "TEXT")
    private String recommendedAction;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 50)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    @Column(name = "resource_key", length = 100)
    private String resourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "triggered_value")
    private Double triggeredValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "occurrence_count")
    @Builder.Default
    private Integer occurrenceCount = 1;

    @Column(name = "first_detected_at")
    @Builder.Default
    private Instant firstDetectedAt = Instant.now();

    @Column(name = "last_detected_at")
    @Builder.Default
    private Instant lastDetectedAt = Instant.now();

    @Column(name = "triggered_at")
    @Builder.Default
    private Instant triggeredAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
