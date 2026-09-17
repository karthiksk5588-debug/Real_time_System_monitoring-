package com.neurosys.agent.scheduler;

import com.neurosys.agent.collector.*;
import com.neurosys.agent.command.DeploymentCommandHandler;
import com.neurosys.agent.command.PowerCommandHandler;
import com.neurosys.agent.config.AgentConfig;
import com.neurosys.agent.sender.MetricsSender;
import oshi.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricsScheduler {

    private static final Logger log = LoggerFactory.getLogger(MetricsScheduler.class);

    private final SystemInfo systemInfo;
    private final CpuCollector cpuCollector;
    private final MemoryCollector memoryCollector;
    private final DiskCollector diskCollector;
    private final NetworkCollector networkCollector;
    private final ProcessCollector processCollector;
    private final SystemInfoCollector systemInfoCollector;
    private final InternetCollector internetCollector;
    private final SoftwareCollector softwareCollector;
    private final WindowsLogCollector windowsLogCollector;
    private final FileAnalyzerCollector fileAnalyzerCollector;
    private final MetricsSender metricsSender;
    private final PowerCommandHandler powerCommandHandler;
    private final DeploymentCommandHandler deploymentCommandHandler;
    private final ScheduledExecutorService scheduler;
    private final ScheduledExecutorService powerCommandExecutor;
    private final ScheduledExecutorService deploymentExecutor;
    private boolean isRegistered = false;
    private int cycleCounter = 0;

    public MetricsScheduler() {
        this.systemInfo = new SystemInfo();
        this.cpuCollector = new CpuCollector(systemInfo);
        this.memoryCollector = new MemoryCollector(systemInfo);
        this.diskCollector = new DiskCollector(systemInfo);
        this.networkCollector = new NetworkCollector(systemInfo);
        this.processCollector = new ProcessCollector(systemInfo);
        this.systemInfoCollector = new SystemInfoCollector(systemInfo);
        this.internetCollector = new InternetCollector();
        this.softwareCollector = new SoftwareCollector();
        this.windowsLogCollector = new WindowsLogCollector();
        this.fileAnalyzerCollector = new FileAnalyzerCollector();
        this.metricsSender = new MetricsSender();
        this.powerCommandHandler = new PowerCommandHandler();
        this.deploymentCommandHandler = new DeploymentCommandHandler();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.powerCommandExecutor = Executors.newSingleThreadScheduledExecutor();
        this.deploymentExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        log.info("Starting NeuroSys Telemetry Agent (Heartbeat Interval: 1s, Power Command Polling: 500ms, Deployment Polling: 3s)...", AgentConfig.getIntervalSeconds());

        // 1. Perform immediate registration & instant initial heartbeat on startup
        attemptRegistration();
        sendInstantHeartbeat();

        // 2. Schedule dedicated 1-second lightweight heartbeat loop
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                if (!isRegistered) {
                    attemptRegistration();
                }
                sendInstantHeartbeat();
            } catch (Exception e) {
                log.debug("Heartbeat cycle error: {}", e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);

        // 3. Schedule periodic full metrics sampling
        scheduler.scheduleAtFixedRate(this::collectAndSendMetrics, 0, AgentConfig.getIntervalSeconds(), TimeUnit.SECONDS);

        // 4. Schedule dedicated 500ms fast polling loop for remote power commands (LOCK, RESTART, SHUTDOWN)
        powerCommandExecutor.scheduleAtFixedRate(() -> {
            try {
                if (isRegistered) {
                    powerCommandHandler.pollAndExecutePendingCommand();
                }
            } catch (Exception e) {
                // Catch silently during network polling
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        // 5. Schedule dedicated 3s polling loop for centralized software deployments
        deploymentExecutor.scheduleAtFixedRate(() -> {
            try {
                if (isRegistered) {
                    deploymentCommandHandler.pollAndExecutePendingDeployments();
                }
            } catch (Exception e) {
                // Catch silently during network polling
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void attemptRegistration() {
        boolean internetOk = internetCollector.isInternetReachable();
        Map<String, Object> regPayload = new HashMap<>();
        regPayload.put("agentId", AgentConfig.getAgentId());
        regPayload.put("hostname", systemInfoCollector.getHostname());
        regPayload.put("computerName", systemInfoCollector.getHostname());
        regPayload.put("ipAddress", networkCollector.getIpAddress());
        regPayload.put("macAddress", networkCollector.getMacAddress());
        regPayload.put("osName", systemInfoCollector.getOsName());
        regPayload.put("osVersion", systemInfoCollector.getOsVersion());
        regPayload.put("labName", AgentConfig.getLabName());
        regPayload.put("enrollmentCode", AgentConfig.getEnrollmentCode());
        regPayload.put("cpuModel", cpuCollector.getCpuName());
        regPayload.put("totalRamMb", memoryCollector.getTotalRamMb());
        regPayload.put("agentVersion", "1.0.0");
        regPayload.put("internetConnected", internetOk);

        this.isRegistered = metricsSender.registerWithServer(regPayload);
    }

    private void sendInstantHeartbeat() {
        if (!isRegistered) return;
        Map<String, Object> hb = new HashMap<>();
        hb.put("agentId", AgentConfig.getAgentId());
        hb.put("hostname", systemInfoCollector.getHostname());
        hb.put("status", "ONLINE");
        hb.put("uptimeSeconds", systemInfoCollector.getUptimeSeconds());
        hb.put("timestamp", System.currentTimeMillis());
        metricsSender.sendHeartbeat(hb);
    }

    private void collectAndSendMetrics() {
        try {
            if (!isRegistered) {
                attemptRegistration();
                return;
            }

            // Check onboarding approval status from central server
            String status = metricsSender.checkApprovalStatus(AgentConfig.getAgentId());
            if ("PENDING".equalsIgnoreCase(status)) {
                log.info("Device onboarding status is PENDING approval by Administrator. Pausing telemetry stream...");
                return;
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                log.warn("Device onboarding was REJECTED by Administrator. Telemetry transmission halted.");
                return;
            }

            cycleCounter++;
            boolean internetOk = internetCollector.isInternetReachable();

            Map<String, Object> payload = new HashMap<>();
            payload.put("agentId", AgentConfig.getAgentId());
            payload.put("cpuUsagePercent", cpuCollector.getCpuUsagePercent());
            payload.put("memoryUsagePercent", memoryCollector.getMemoryUsagePercent());
            payload.put("memoryUsedMb", memoryCollector.getMemoryUsedMb());
            payload.put("memoryFreeMb", memoryCollector.getMemoryFreeMb());
            payload.put("diskUsagePercent", diskCollector.getDiskUsagePercent());
            payload.put("diskUsedGb", diskCollector.getDiskUsedGb());
            payload.put("diskFreeGb", diskCollector.getDiskFreeGb());
            payload.put("networkRxBytesSec", networkCollector.getDownloadSpeedBytesSec());
            payload.put("networkTxBytesSec", networkCollector.getUploadSpeedBytesSec());
            payload.put("cpuTemperature", cpuCollector.getCpuTemperature());
            payload.put("activeProcessCount", processCollector.getActiveProcessCount());
            payload.put("uptimeSeconds", systemInfoCollector.getUptimeSeconds());
            payload.put("internetConnected", internetOk);
            payload.put("topProcesses", processCollector.getTopProcesses(10));

            metricsSender.sendMetricsPayload(payload);

            // Sync Windows Diagnostic Log events on cycle #1 or every 30 cycles (30 seconds)
            if (cycleCounter == 1 || cycleCounter % 30 == 0) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    var events = windowsLogCollector.collectRecentWindowsEvents();
                    metricsSender.sendDiagnosticEvents(AgentConfig.getAgentId(), events);
                });
            }

            // Sync file analyzer disk cleanup report on cycle #1 or every 30 cycles (30 seconds)
            if (cycleCounter == 1 || cycleCounter % 30 == 0) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    var storageReport = fileAnalyzerCollector.scanDiskForCleanup();
                    metricsSender.sendFileAnalysisPayload(AgentConfig.getAgentId(), storageReport);
                });
            }

            // Sync software inventory on initial cycle (#1) or every 30 cycles (30 seconds)
            if (cycleCounter == 1 || cycleCounter % 30 == 0) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    var softwareList = softwareCollector.collectInstalledSoftware();
                    metricsSender.sendSoftwarePayload(AgentConfig.getAgentId(), systemInfoCollector.getHostname(), softwareList);
                });
            }
        } catch (Exception e) {
            log.error("Error during periodic metric collection", e);
        }
    }
}
