package com.neurosys.agent.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.agent.collector.SoftwareCollector;
import com.neurosys.agent.collector.SystemInfoCollector;
import com.neurosys.agent.config.AgentConfig;
import com.neurosys.agent.sender.MetricsSender;
import oshi.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DeploymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentCommandHandler.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SoftwareCollector softwareCollector;
    private final MetricsSender metricsSender;
    private final SystemInfoCollector systemInfoCollector;

    public DeploymentCommandHandler() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.softwareCollector = new SoftwareCollector();
        this.metricsSender = new MetricsSender();
        this.systemInfoCollector = new SystemInfoCollector(new SystemInfo());
    }

    public void pollAndExecutePendingDeployments() {
        try {
            String url = AgentConfig.getServerUrl() + "/agent/deployments/tasks?agentId=" + AgentConfig.getAgentId();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (body.startsWith("{")) {
                    Map<String, Object> respMap = objectMapper.readValue(body, Map.class);
                    if (respMap.containsKey("data") && respMap.get("data") != null) {
                        List<Map<String, Object>> tasks = (List<Map<String, Object>>) respMap.get("data");
                        for (Map<String, Object> task : tasks) {
                            processTask(task);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Quiet exception fallback during network polling
        }
    }

    private void processTask(Map<String, Object> task) {
        String deploymentId = (String) task.get("deploymentId");
        String deploymentNumber = (String) task.get("deploymentNumber");
        String packageName = (String) task.get("packageName");
        String packageVersion = (String) task.get("packageVersion");
        String installerUrl = (String) task.get("installerUrl");
        String installerType = (String) task.get("installerType");
        String silentArguments = (String) task.get("silentArguments");
        String expectedChecksum = (String) task.get("checksum");

        log.info("Processing deployment task [{}] for package '{}' (Version: {})", deploymentNumber, packageName, packageVersion);

        // Step 1: Check if already installed
        List<Map<String, String>> installedList = softwareCollector.collectInstalledSoftware();
        boolean alreadyInstalled = installedList.stream().anyMatch(item -> {
            String name = item.get("name");
            return name != null && isNameMatch(name, packageName);
        });

        if (alreadyInstalled) {
            log.info("Package '{}' is ALREADY_INSTALLED on this machine.", packageName);
            updateStatusAsync(deploymentId, "ALREADY_INSTALLED", "Package " + packageName + " is already installed on target machine");
            return;
        }

        // Step 2: Download installer
        updateStatusAsync(deploymentId, "DOWNLOADING", "Downloading installer package from " + installerUrl);
        File tempInstaller = null;
        try {
            String ext = "MSI".equalsIgnoreCase(installerType) ? ".msi" : ".exe";
            tempInstaller = File.createTempFile("neurosys-installer-", ext);
            tempInstaller.deleteOnExit();

            HttpRequest downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(installerUrl))
                    .GET()
                    .build();

            HttpResponse<InputStream> downloadResp = httpClient.send(downloadReq, HttpResponse.BodyHandlers.ofInputStream());
            if (downloadResp.statusCode() != 200) {
                log.error("Failed to download installer. HTTP status {}", downloadResp.statusCode());
                updateStatusAsync(deploymentId, "FAILED", "HTTP download failed with status " + downloadResp.statusCode());
                return;
            }

            try (InputStream in = downloadResp.body()) {
                Files.copy(in, tempInstaller.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Downloaded installer to temporary file {} (Size: {} bytes)", tempInstaller.getAbsolutePath(), tempInstaller.length());

            // Step 3: Checksum verification
            if (expectedChecksum != null && !expectedChecksum.isBlank() &&
                !expectedChecksum.equalsIgnoreCase("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") &&
                expectedChecksum.length() == 64) {
                String calculatedHash = calculateSHA256(tempInstaller);
                if (!calculatedHash.equalsIgnoreCase(expectedChecksum.trim())) {
                    log.error("SHA-256 mismatch! Expected: {}, Calculated: {}", expectedChecksum, calculatedHash);
                    updateStatusAsync(deploymentId, "FAILED", "SHA-256 verification failed (Expected: " + expectedChecksum + ", Actual: " + calculatedHash + ")");
                    return;
                }
            }

            // Step 4: Silent Installation Execution
            updateStatusAsync(deploymentId, "INSTALLING", "Executing silent installation...");
            log.info("Executing silent installation for {}", packageName);

            ProcessBuilder pb;
            if ("MSI".equalsIgnoreCase(installerType)) {
                String args = (silentArguments != null && !silentArguments.isBlank()) ? silentArguments : "/qn /norestart";
                pb = new ProcessBuilder("msiexec.exe", "/i", tempInstaller.getAbsolutePath(), args);
            } else {
                String args = (silentArguments != null && !silentArguments.isBlank()) ? silentArguments : "/S";
                pb = new ProcessBuilder(tempInstaller.getAbsolutePath(), args);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                log.error("Silent installer execution timed out after 10 minutes");
                updateStatusAsync(deploymentId, "FAILED", "Installer timed out after 10 minutes");
                return;
            }

            int exitCode = process.exitValue();
            log.info("Installer exited with code {}", exitCode);

            // 0, 3010 (Reboot required), 1641 (Reboot initiated) are successful MSI/EXE codes
            if (exitCode == 0 || exitCode == 3010 || exitCode == 1641) {
                updateStatusAsync(deploymentId, "INSTALLED", "Software installed successfully (Exit Code: " + exitCode + ")");
                log.info("Deployment [{}] for package '{}' completed successfully!", deploymentNumber, packageName);

                // Trigger fresh software inventory scan and update backend
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(3000);
                        var freshSoftware = softwareCollector.collectInstalledSoftware();
                        metricsSender.sendSoftwarePayload(AgentConfig.getAgentId(), systemInfoCollector.getHostname(), freshSoftware);
                    } catch (Exception ignored) {}
                });
            } else {
                updateStatusAsync(deploymentId, "FAILED", "Installer failed with exit code " + exitCode);
            }

        } catch (Exception e) {
            log.error("Deployment process error", e);
            updateStatusAsync(deploymentId, "FAILED", "Error: " + e.getMessage());
        } finally {
            if (tempInstaller != null && tempInstaller.exists()) {
                try {
                    tempInstaller.delete();
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isNameMatch(String installedName, String targetName) {
        if (installedName == null || targetName == null) return false;
        String a = installedName.toLowerCase();
        String b = targetName.toLowerCase();
        return a.contains(b) || b.contains(a);
    }

    private String calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hash = digest.digest(fileBytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate SHA-256", e);
            return "";
        }
    }

    private void updateStatusAsync(String deploymentId, String status, String statusDetail) {
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("status", status);
                payload.put("statusDetail", statusDetail);

                String jsonBody = objectMapper.writeValueAsString(payload);
                String url = AgentConfig.getServerUrl() + "/agent/deployments/" + deploymentId + "/status?agentId=" + AgentConfig.getAgentId();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("Reported deployment [{}] status: {} ({})", deploymentId, status, statusDetail);
            } catch (Exception e) {
                log.error("Failed to report deployment status to server", e);
            }
        });
    }
}
