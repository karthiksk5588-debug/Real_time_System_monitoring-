package com.neurosys.agent;

import com.neurosys.agent.config.AgentConfig;
import com.neurosys.agent.scheduler.MetricsScheduler;
import com.neurosys.agent.tray.AgentTrayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentApplication {

    private static final Logger log = LoggerFactory.getLogger(AgentApplication.class);

    public static void main(String[] args) {
        log.info("=================================================");
        log.info("  Starting NeuroSys Telemetry Agent");
        log.info("  Service Mode: Automatic Windows Background Service");
        log.info("  Agent ID: {}", AgentConfig.getAgentId());
        log.info("  Server URL: {}", AgentConfig.getServerUrl());
        log.info("  Lab Name: {}", AgentConfig.getLabName());
        log.info("=================================================");

        // Initialize Windows System Tray status indicator (if desktop UI supported)
        AgentTrayManager.getInstance();

        MetricsScheduler scheduler = new MetricsScheduler();
        scheduler.start();

        // Register JVM Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown initiated. Removing system tray icon...");
            AgentTrayManager.getInstance().removeTrayIcon();
        }));

        // Keep main service thread active
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("NeuroSys Agent service interrupted. Exiting gracefully.");
            AgentTrayManager.getInstance().removeTrayIcon();
            Thread.currentThread().interrupt();
        }
    }
}
