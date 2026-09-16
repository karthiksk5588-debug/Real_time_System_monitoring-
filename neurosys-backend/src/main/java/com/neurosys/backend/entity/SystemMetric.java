package com.neurosys.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "system_metrics", indexes = {
        @Index(name = "idx_system_metric_computer_id", columnList = "computer_id"),
        @Index(name = "idx_system_metric_recorded_at", columnList = "recorded_at"),
        @Index(name = "idx_system_metric_comp_recorded", columnList = "computer_id, recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemMetric extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computer_id", nullable = false)
    private Computer computer;

    @Column(name = "cpu_usage_percent", nullable = false)
    private Double cpuUsagePercent;

    @Column(name = "memory_usage_percent", nullable = false)
    private Double memoryUsagePercent;

    @Column(name = "memory_used_mb")
    private Double memoryUsedMb;

    @Column(name = "memory_free_mb")
    private Double memoryFreeMb;

    @Column(name = "disk_usage_percent", nullable = false)
    private Double diskUsagePercent;

    @Column(name = "disk_used_gb")
    private Double diskUsedGb;

    @Column(name = "disk_free_gb")
    private Double diskFreeGb;

    @Column(name = "disk_read_bytes_sec")
    private Double diskReadBytesSec;

    @Column(name = "disk_write_bytes_sec")
    private Double diskWriteBytesSec;

    @Column(name = "network_rx_bytes_sec")
    private Double networkRxBytesSec;

    @Column(name = "network_tx_bytes_sec")
    private Double networkTxBytesSec;

    @Column(name = "cpu_temperature")
    private Double cpuTemperature;

    @Column(name = "active_process_count")
    private Integer activeProcessCount;

    @Column(name = "top_processes_json", columnDefinition = "TEXT")
    private String topProcessesJson;

    @Column(name = "recorded_at", nullable = false)
    @Builder.Default
    private Instant recordedAt = Instant.now();
}
