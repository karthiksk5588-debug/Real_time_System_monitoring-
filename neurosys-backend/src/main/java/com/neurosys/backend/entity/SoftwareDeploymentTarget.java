package com.neurosys.backend.entity;

import com.neurosys.backend.enums.DeploymentTargetStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.Instant;

@Entity
@Table(name = "software_deployment_targets")
@SQLDelete(sql = "UPDATE software_deployment_targets SET deleted = true, deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareDeploymentTarget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private SoftwareDeployment deployment;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "computer_id", nullable = false)
    private Computer computer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeploymentTargetStatus status = DeploymentTargetStatus.PENDING;

    @Column(name = "status_detail", length = 500)
    private String statusDetail;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
