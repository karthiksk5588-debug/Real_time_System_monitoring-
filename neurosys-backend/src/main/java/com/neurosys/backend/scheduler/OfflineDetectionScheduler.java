package com.neurosys.backend.scheduler;

import com.neurosys.backend.config.AlertEngineConfig;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.service.AlertEngineService;
import com.neurosys.backend.service.HeartbeatTrackerService;
import com.neurosys.backend.service.WebSocketMetricsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineDetectionScheduler {

    private final ComputerRepository computerRepository;
    private final AlertEngineService alertEngineService;
    private final HeartbeatTrackerService heartbeatTracker;
    private final WebSocketMetricsPublisher webSocketMetricsPublisher;
    private final AlertEngineConfig alertConfig;

    @Scheduled(fixedRate = 5000) // Runs every 5 seconds to evaluate endpoint heartbeat health
    @Transactional
    public void detectOfflineComputers() {
        try {
            Instant now = Instant.now();
            long warningStateSec = alertConfig.getOffline().getWarningStateSeconds();
            long alertDurationSec = alertConfig.getOffline().getAlertDurationSeconds();

            Map<String, Instant> heartbeatMap = heartbeatTracker.getLastHeartbeatMap();

            List<Computer> computers = computerRepository.findAll();

            for (Computer c : computers) {
                Instant lastSeen = heartbeatMap.get(c.getId());
                if (lastSeen == null) {
                    lastSeen = heartbeatMap.get(c.getAgentId());
                }
                if (lastSeen == null) {
                    lastSeen = c.getLastSeenAt();
                }

                if (lastSeen == null) {
                    continue;
                }

                long offlineDurationSeconds = Math.max(0, Duration.between(lastSeen, now).getSeconds());

                if (offlineDurationSeconds >= alertDurationSec) {
                    // Endpoint has been offline for >= 5 minutes (300s) -> Create/Update ONE active ENDPOINT_OFFLINE incident
                    if (c.getStatus() != ComputerStatus.OFFLINE) {
                        ComputerStatus oldStatus = c.getStatus();
                        log.info("[OFFLINE DETECT] PC {} ({}) missed heartbeat (>{}s). Status {} → OFFLINE",
                                c.getHostname(), c.getAgentId(), alertDurationSec, oldStatus);
                        c.setStatus(ComputerStatus.OFFLINE);
                        c.setUpdatedAt(now);
                        computerRepository.save(c);

                        webSocketMetricsPublisher.broadcastStatusChange(c, ComputerStatus.OFFLINE,
                                String.format("Telemetry heartbeat stopped for %d minutes", offlineDurationSeconds / 60));
                    }

                    alertEngineService.triggerOfflineAlert(c, offlineDurationSeconds);
                } else if (offlineDurationSeconds >= warningStateSec) {
                    // Endpoint missed heartbeat for >= 60 seconds -> Mark Warning state
                    if (c.getStatus() == ComputerStatus.ONLINE) {
                        c.setStatus(ComputerStatus.WARNING);
                        c.setUpdatedAt(now);
                        computerRepository.save(c);

                        webSocketMetricsPublisher.broadcastStatusChange(c, ComputerStatus.WARNING,
                                "Telemetry heartbeat delayed (>60s)");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[OFFLINE SCHEDULER] Error during offline check: {}", e.getMessage());
        }
    }
}
