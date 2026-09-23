package com.neurosys.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "neurosys.alerts")
public class AlertEngineConfig {

    private CpuConfig cpu = new CpuConfig();
    private MemoryConfig memory = new MemoryConfig();
    private DiskConfig disk = new DiskConfig();
    private OfflineConfig offline = new OfflineConfig();

    @Getter
    @Setter
    public static class CpuConfig {
        private double warningThreshold = 85.0;
        private long warningDurationSeconds = 300; // 5 minutes
        private double criticalThreshold = 95.0;
        private long criticalDurationSeconds = 600; // 10 minutes
        private double recoveryThreshold = 75.0;
    }

    @Getter
    @Setter
    public static class MemoryConfig {
        private double warningThreshold = 90.0;
        private long warningDurationSeconds = 300; // 5 minutes
        private double criticalThreshold = 95.0;
        private long criticalDurationSeconds = 600; // 10 minutes
        private double recoveryThreshold = 80.0;
        private double criticalFreeMb = 1024.0; // 1 GB free RAM
    }

    @Getter
    @Setter
    public static class DiskConfig {
        private double infoThreshold = 80.0;
        private double warningThreshold = 90.0;
        private double criticalThreshold = 95.0;
        private double urgentThreshold = 98.0;
        private double recoveryThreshold = 80.0;
    }

    @Getter
    @Setter
    public static class OfflineConfig {
        private long warningStateSeconds = 60; // 1 minute warning
        private long alertDurationSeconds = 300; // 5 minutes offline alert
    }
}
