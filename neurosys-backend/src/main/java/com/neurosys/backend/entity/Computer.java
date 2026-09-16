package com.neurosys.backend.entity;

import com.neurosys.backend.enums.ComputerStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "computers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Computer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "agent_id", nullable = false, unique = true, length = 100)
    private String agentId;

    @Column(name = "hostname", nullable = false, length = 150)
    private String hostname;

    @Column(name = "computer_name", length = 150)
    private String computerName;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "mac_address", nullable = false, unique = true, length = 50)
    private String macAddress;

    @Column(name = "os_name", nullable = false, length = 100)
    private String osName;

    @Column(name = "os_version", length = 100)
    private String osVersion;

    @Column(name = "lab_name", length = 100)
    private String labName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_id")
    private Lab lab;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "cpu_model", length = 150)
    private String cpuModel;

    @Column(name = "total_ram_mb")
    private Double totalRamMb;

    @Column(name = "agent_version", length = 50)
    private String agentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ComputerStatus status = ComputerStatus.ONLINE;

    @Column(name = "internet_connected")
    @Builder.Default
    private Boolean internetConnected = true;

    @Column(name = "uptime_seconds")
    @Builder.Default
    private Long uptimeSeconds = 0L;

    @Column(name = "last_seen_at")
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @Column(name = "last_cpu_usage")
    private Double lastCpuUsage;

    @Column(name = "last_ram_usage")
    private Double lastRamUsage;

    @Column(name = "last_disk_usage")
    private Double lastDiskUsage;
}
