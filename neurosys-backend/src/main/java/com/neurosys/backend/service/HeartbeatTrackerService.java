package com.neurosys.backend.service;

import com.neurosys.backend.dto.request.AgentHeartbeatRequest;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.repository.ComputerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatTrackerService {

    private final ComputerRepository computerRepository;
    private final WebSocketMetricsPublisher webSocketMetricsPublisher;
    private final AlertEngineService alertEngineService;
    
    // In-memory high performance tracking: Agent ID / Computer ID -> Last Heartbeat Instant
    private final Map<String, Instant> lastHeartbeatMap = new ConcurrentHashMap<>();

    public Map<String, Instant> getLastHeartbeatMap() {
        return lastHeartbeatMap;
    }

    public void updateHeartbeatTime(String computerId) {
        if (computerId != null) {
            lastHeartbeatMap.put(computerId, Instant.now());
        }
    }

    public void updateHeartbeatTime(String computerId, String agentId) {
        Instant now = Instant.now();
        if (computerId != null) lastHeartbeatMap.put(computerId, now);
        if (agentId != null) lastHeartbeatMap.put(agentId, now);
    }

    @Transactional
    public void processHeartbeat(AgentHeartbeatRequest request) {
        if (request.getAgentId() == null || request.getAgentId().isEmpty()) return;

        Optional<Computer> compOpt = computerRepository.findByAgentId(request.getAgentId());
        if (compOpt.isEmpty()) {
            // Auto-heal lookup by hostname fallback
            if (request.getHostname() != null) {
                compOpt = computerRepository.findByHostnameIgnoreCase(request.getHostname());
            }
        }

        if (compOpt.isPresent()) {
            Computer computer = compOpt.get();
            Instant now = Instant.now();
            lastHeartbeatMap.put(computer.getId(), now);
            lastHeartbeatMap.put(computer.getAgentId(), now);

            ComputerStatus oldStatus = computer.getStatus();

            // If computer was not ONLINE (e.g. WARNING, OFFLINE, PENDING), instantly restore status to ONLINE
            if (oldStatus != ComputerStatus.ONLINE && oldStatus != ComputerStatus.REJECTED) {
                computer.setStatus(ComputerStatus.ONLINE);
                computer.setLastSeenAt(now);
                computer.setUpdatedAt(now);
                computerRepository.save(computer);

                log.info("[REAL-TIME RESTORE] PC {} ({}) reconnected: {} → ONLINE", computer.getHostname(), computer.getAgentId(), oldStatus);
                webSocketMetricsPublisher.broadcastStatusChange(computer, ComputerStatus.ONLINE, "Connection restored by Agent heartbeat");
                try {
                    alertEngineService.resolveOfflineAlert(computer);
                } catch (Exception e) {
                    log.warn("Failed resolving offline alert on heartbeat for {}: {}", computer.getHostname(), e.getMessage());
                }
            } else {
                // Update lastSeenAt quietly without DB overhead on every ping
                computer.setLastSeenAt(now);
                computerRepository.save(computer);
            }
        }
    }
}
