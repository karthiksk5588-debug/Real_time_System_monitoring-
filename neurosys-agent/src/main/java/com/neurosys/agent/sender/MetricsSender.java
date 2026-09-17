package com.neurosys.agent.sender;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.agent.config.AgentConfig;
import com.neurosys.agent.tray.AgentTrayManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsSender {

    private static final Logger log = LoggerFactory.getLogger(MetricsSender.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OfflineCacheManager cacheManager;
    private String agentAuthToken;
    private boolean wasOffline = false;

    public MetricsSender() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        this.cacheManager = new OfflineCacheManager();
    }

    public boolean registerWithServer(Map<String, Object> regData) {
        try {
            String jsonBody = objectMapper.writeValueAsString(regData);
            String targetUrl = AgentConfig.getServerUrl() + "/agent/register";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (body.startsWith("{")) {
                    Map<String, Object> respMap = objectMapper.readValue(body, Map.class);
                    if (respMap.containsKey("data")) {
                        Map<String, Object> data = (Map<String, Object>) respMap.get("data");
                        this.agentAuthToken = (String) data.get("agentAuthToken");
                        String status = (String) data.get("status");
                        log.info("[INFO] Agent registration successful: AgentID={}, Status={}", regData.get("agentId"), status);
                        if (wasOffline) {
                            log.info("[INFO] Agent connection restored. Server connection re-established.");
                            wasOffline = false;
                        }
                        AgentTrayManager.getInstance().updateStatus(true, Instant.now());
                        return true;
                    }
                }
            } else {
                if (!wasOffline) {
                    log.warn("[INFO] Server returned HTTP status {}. Retrying in next cycle...", response.statusCode());
                    wasOffline = true;
                }
                AgentTrayManager.getInstance().updateStatus(false, null);
            }
        } catch (Exception e) {
            if (!wasOffline) {
                log.warn("[INFO] Agent connection lost ({}). Retrying in next cycle...", e.getMessage());
                wasOffline = true;
            }
            AgentTrayManager.getInstance().updateStatus(false, null);
        }
        return false;
    }

    public String checkApprovalStatus(String agentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AgentConfig.getServerUrl() + "/agent/status/" + agentId))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().trim();
                if (body.startsWith("{")) {
                    Map<String, Object> respMap = objectMapper.readValue(body, Map.class);
                    if (respMap.containsKey("data")) {
                        return (String) respMap.get("data");
                    }
                }
            }
        } catch (Exception e) {
            // Quiet fallback during offline mode
        }
        return "ONLINE";
    }

    public boolean sendHeartbeat(Map<String, Object> heartbeat) {
        try {
            String jsonBody = objectMapper.writeValueAsString(heartbeat);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AgentConfig.getServerUrl() + "/agent/heartbeat"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                if (wasOffline) {
                    log.info("[INFO] Agent connection restored via fast heartbeat.");
                    wasOffline = false;
                }
                AgentTrayManager.getInstance().updateStatus(true, Instant.now());
                return true;
            } else {
                AgentTrayManager.getInstance().updateStatus(false, null);
            }
        } catch (Exception e) {
            if (!wasOffline) {
                log.debug("Heartbeat ping dropped: {}", e.getMessage());
                wasOffline = true;
            }
            AgentTrayManager.getInstance().updateStatus(false, null);
        }
        return false;
    }

    public void sendMetricsPayload(Map<String, Object> payload) {
        try {
            // First flush any offline cached metric files
            flushOfflineCache();

            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(AgentConfig.getServerUrl() + "/agent/metrics"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (agentAuthToken != null) {
                builder.header("X-Agent-Token", agentAuthToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                if (wasOffline) {
                    log.info("[INFO] Agent connection restored. Resuming live telemetry transmission.");
                    wasOffline = false;
                }
                AgentTrayManager.getInstance().updateStatus(true, Instant.now());
                log.info("Successfully transmitted metrics payload to server. CPU: {}%, RAM: {}%",
                        payload.get("cpuUsagePercent"), payload.get("memoryUsagePercent"));
            } else if (response.statusCode() == 404 || response.statusCode() == 401) {
                log.warn("[INFO] Server reported authentication/registration status {}. Triggering re-registration sequence.", response.statusCode());
                wasOffline = true;
                AgentTrayManager.getInstance().updateStatus(false, null);
                cacheManager.cacheUnsentPayload(payload);
            } else {
                // 5xx (500, 502, 503, 504) server error or network issue -> Cache & Retry, DO NOT re-register!
                if (!wasOffline) {
                    log.warn("Server returned transient HTTP status {}. Caching telemetry payload locally for retry.", response.statusCode());
                    wasOffline = true;
                }
                AgentTrayManager.getInstance().updateStatus(false, null);
                cacheManager.cacheUnsentPayload(payload);
            }
        } catch (Exception e) {
            if (!wasOffline) {
                log.warn("[INFO] Agent connection lost ({}). Caching metrics payload on local disk.", e.getMessage());
                wasOffline = true;
            }
            AgentTrayManager.getInstance().updateStatus(false, null);
            cacheManager.cacheUnsentPayload(payload);
        }
    }

    public void sendDiagnosticEvents(String agentId, List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) return;
        try {
            String jsonBody = objectMapper.writeValueAsString(events);
            String targetUrl = AgentConfig.getServerUrl() + "/agent/events?agentId=" + agentId;
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (agentAuthToken != null) {
                builder.header("X-Agent-Token", agentAuthToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Successfully synced {} Windows diagnostic event log records with server.", events.size());
            } else {
                log.warn("Server returned HTTP status {} when syncing diagnostic events.", response.statusCode());
            }
        } catch (Exception e) {
            log.debug("Failed to sync diagnostic events with server: {}", e.getMessage());
        }
    }

    public void sendSoftwarePayload(String agentId, List<Map<String, String>> softwareList) {
        sendSoftwarePayload(agentId, null, softwareList);
    }

    public void sendSoftwarePayload(String agentId, String hostname, List<Map<String, String>> softwareList) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("agentId", agentId);
            if (hostname != null) {
                payload.put("hostname", hostname);
            }
            payload.put("softwareList", softwareList);

            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(AgentConfig.getServerUrl() + "/agent/software"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (agentAuthToken != null) {
                builder.header("X-Agent-Token", agentAuthToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Successfully synced {} software inventory records with backend.", softwareList.size());
            } else {
                log.warn("Server returned HTTP status {} when syncing software inventory.", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Failed to sync software inventory with server: {}", e.getMessage());
        }
    }

    public void sendFileAnalysisPayload(String agentId, Map<String, Object> storageReport) {
        if (storageReport == null || storageReport.isEmpty()) return;
        try {
            Map<String, Object> payload = new HashMap<>(storageReport);
            payload.put("agentId", agentId);

            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(AgentConfig.getServerUrl() + "/agent/file-analyzer"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            if (agentAuthToken != null) {
                builder.header("X-Agent-Token", agentAuthToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("Successfully synced file storage analysis report with server.");
            } else {
                log.warn("Server returned HTTP status {} when syncing file analysis report.", response.statusCode());
            }
        } catch (Exception e) {
            log.debug("Failed to sync file analysis report: {}", e.getMessage());
        }
    }

    private void flushOfflineCache() {
        List<File> cachedFiles = cacheManager.getCachedFiles();
        if (!cachedFiles.isEmpty()) {
            log.info("Found {} cached metric payloads. Flushing up to 5 items to server...", cachedFiles.size());
            int count = 0;
            for (File file : cachedFiles) {
                if (count++ >= 5) break;
                try {
                    Map<String, Object> cachedData = cacheManager.readCachedFile(file);
                    String jsonBody = objectMapper.writeValueAsString(cachedData);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(AgentConfig.getServerUrl() + "/agent/metrics"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        cacheManager.deleteCachedFile(file);
                        log.info("Flushed cached metric file: {}", file.getName());
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    log.error("Error flushing cached metric file {}", file.getName(), e);
                    break;
                }
            }
        }
    }
}
