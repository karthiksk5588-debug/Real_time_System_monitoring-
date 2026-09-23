package com.neurosys.agent.tray;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.agent.config.AgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class AgentTrayManager {

    private static final Logger log = LoggerFactory.getLogger(AgentTrayManager.class);
    private static final AgentTrayManager INSTANCE = new AgentTrayManager();

    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private MenuItem headerItem;
    private MenuItem statusItem;
    private MenuItem serverItem;
    private MenuItem heartbeatItem;

    private boolean isTraySupported = false;
    private boolean isConnected = false;
    private Instant lastHeartbeatTime = null;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File statusFile = new File("cache/agent-status.json");

    private AgentTrayManager() {
        initTray();
    }

    public static AgentTrayManager getInstance() {
        return INSTANCE;
    }

    private void initTray() {
        try {
            if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
                this.systemTray = SystemTray.getSystemTray();

                PopupMenu popup = new PopupMenu();

                headerItem = new MenuItem("NeuroSys Telemetry Agent");
                headerItem.setEnabled(false);
                popup.add(headerItem);

                statusItem = new MenuItem("Status: Running");
                statusItem.setEnabled(false);
                popup.add(statusItem);

                serverItem = new MenuItem("Server: Initializing...");
                serverItem.setEnabled(false);
                popup.add(serverItem);

                heartbeatItem = new MenuItem("Last Heartbeat: Not connected");
                heartbeatItem.setEnabled(false);
                popup.add(heartbeatItem);

                popup.addSeparator();

                MenuItem openDashboardItem = new MenuItem("Open Dashboard");
                openDashboardItem.addActionListener(e -> openDashboard());
                popup.add(openDashboardItem);

                MenuItem restartItem = new MenuItem("Restart Agent");
                restartItem.addActionListener(e -> restartAgent());
                popup.add(restartItem);

                MenuItem stopAgentItem = new MenuItem("Stop & Exit Agent");
                stopAgentItem.addActionListener(e -> stopAgentAndExit());
                popup.add(stopAgentItem);

                Image defaultIcon = createStatusIcon(new Color(255, 193, 7)); // Orange initializing
                trayIcon = new TrayIcon(defaultIcon, "NeuroSys Agent — Reconnecting", popup);
                trayIcon.setImageAutoSize(true);

                systemTray.add(trayIcon);
                isTraySupported = true;
                log.info("[INFO] Windows System Tray icon initialized successfully.");
            } else {
                log.info("[INFO] System tray not supported or running headless. Tray icon disabled.");
            }
        } catch (Exception e) {
            log.warn("[WARN] Could not initialize Windows system tray icon: {}", e.getMessage());
            isTraySupported = false;
        }

        writeStatusFile();
    }

    public synchronized void updateStatus(boolean connected, Instant heartbeatTime) {
        this.isConnected = connected;
        if (heartbeatTime != null) {
            this.lastHeartbeatTime = heartbeatTime;
        }

        String timeStr = "Not connected";
        if (lastHeartbeatTime != null) {
            timeStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(lastHeartbeatTime);
        }

        if (isTraySupported && trayIcon != null) {
            Color iconColor = connected ? new Color(40, 167, 69) : new Color(255, 193, 7); // Green / Orange
            String toolTip = connected ? "🟢 NeuroSys Agent — Connected" : "🟠 NeuroSys Agent — Reconnecting";

            trayIcon.setImage(createStatusIcon(iconColor));
            trayIcon.setToolTip(toolTip);

            statusItem.setLabel("Status: Running");
            serverItem.setLabel(connected ? "Server: Connected" : "Server: Reconnecting");
            heartbeatItem.setLabel("Last Heartbeat: " + timeStr);
        }

        writeStatusFile();
    }

    private void openDashboard() {
        try {
            String url = AgentConfig.getServerUrl();
            if (url != null && url.contains("/api/v1")) {
                url = url.replace("/api/v1", "/dashboard");
            } else if (url == null || url.isEmpty() || url.contains("localhost")) {
                url = "https://realtimesystemmonitoring-production-7322.up.railway.app/dashboard";
            }
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec("cmd /c start " + url);
            }
        } catch (Exception e) {
            log.error("Failed to open NeuroSys Dashboard URL", e);
        }
    }

    private void restartAgent() {
        try {
            log.info("[INFO] Tray user requested Agent restart...");
            File stopFlag = new File("cache/stopped.flag");
            if (stopFlag.exists()) {
                stopFlag.delete();
            }
            File currentDir = new File(".").getAbsoluteFile();
            File restartScript = new File("start-agent.bat");
            if (restartScript.exists()) {
                Runtime.getRuntime().exec("cmd /c start-agent.bat", null, currentDir);
            } else {
                log.warn("start-agent.bat not found in current working directory.");
            }
        } catch (Exception e) {
            log.error("Failed to trigger agent restart from tray", e);
        }
    }

    public synchronized void stopAgentAndExit() {
        log.info("[INFO] User requested Agent shutdown. Terminating process...");
        removeTrayIcon();
        try {
            File cacheDir = new File("cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File stopFlag = new File(cacheDir, "stopped.flag");
            stopFlag.createNewFile();

            File statusFile = new File(cacheDir, "agent-status.json");
            if (statusFile.exists()) {
                statusFile.delete();
            }

            String cleanCmd = "powershell -NoProfile -ExecutionPolicy Bypass -Command \"" +
                    "try { Unregister-ScheduledTask -TaskName 'NeuroSysAgent' -Confirm:$false -ErrorAction SilentlyContinue } catch {}; " +
                    "try { $u = [Environment]::GetFolderPath('Startup'); $uL = Join-Path $u 'NeuroSysAgent.lnk'; if (Test-Path $uL) { Remove-Item $uL -Force } } catch {}; " +
                    "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*Run-NeuroSys-Agent*' -or $_.CommandLine -like '*Run-Silent*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }\"";

            Runtime.getRuntime().exec(cleanCmd);
        } catch (Exception e) {
            log.warn("Error during stop cleanup: {}", e.getMessage());
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {}

        System.exit(0);
    }

    public synchronized void removeTrayIcon() {
        if (isTraySupported && trayIcon != null && systemTray != null) {
            try {
                systemTray.remove(trayIcon);
                log.info("[INFO] Tray icon removed by user.");
            } catch (Exception e) {
                log.debug("Error removing tray icon: {}", e.getMessage());
            }
        }
    }

    private void writeStatusFile() {
        try {
            File dir = statusFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            Map<String, Object> statusData = new HashMap<>();
            statusData.put("pid", ProcessHandle.current().pid());
            statusData.put("status", "RUNNING");
            statusData.put("connected", isConnected);
            statusData.put("lastHeartbeat", lastHeartbeatTime != null ? lastHeartbeatTime.toString() : null);
            statusData.put("serverUrl", AgentConfig.getServerUrl());
            statusData.put("labName", AgentConfig.getLabName());

            objectMapper.writeValue(statusFile, statusData);
        } catch (Exception e) {
            // Quiet status file write
        }
    }

    private Image createStatusIcon(Color color) {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw outer dark border
        g2d.setColor(new Color(20, 20, 20));
        g2d.fillOval(0, 0, size, size);

        // Draw inner indicator circle
        g2d.setColor(color);
        g2d.fillOval(2, 2, size - 4, size - 4);

        g2d.dispose();
        return image;
    }
}
