package com.neurosys.backend.entity;

import com.neurosys.backend.enums.DeploymentTaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "software_deployments")
@SQLDelete(sql = "UPDATE software_deployments SET deleted = true, deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoftwareDeployment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "deployment_number", nullable = false, unique = true, length = 50)
    private String deploymentNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "software_package_id", nullable = false)
    private SoftwarePackage softwarePackage;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_id", nullable = false)
    private Lab lab;

    @Column(name = "created_by_user", nullable = false, length = 100)
    private String createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private DeploymentTaskStatus status = DeploymentTaskStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SoftwareDeploymentTarget> targets = new ArrayList<>();
}
