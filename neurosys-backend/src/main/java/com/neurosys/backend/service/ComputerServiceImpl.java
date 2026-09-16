package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.ComputerDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.HealthScore;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.HealthScoreRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ComputerServiceImpl implements ComputerService {

    private final ComputerRepository computerRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final HealthScoreRepository healthScoreRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ComputerDto> getAllComputers() {
        return computerRepository.findAll().stream()
                .filter(c -> c.getStatus() != ComputerStatus.PENDING && c.getStatus() != ComputerStatus.REJECTED)
                .map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComputerDto> getPendingComputers() {
        return computerRepository.findByStatus(ComputerStatus.PENDING).stream().map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ComputerDto getComputerById(String id) {
        Computer computer = computerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", id));
        return mapToDto(computer);
    }

    @Override
    @Transactional(readOnly = true)
    public ComputerDto getComputerByAgentId(String agentId) {
        Computer computer = computerRepository.findByAgentId(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer Agent", "agentId", agentId));
        return mapToDto(computer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComputerDto> getComputersByLab(String labName) {
        return computerRepository.findByLabName(labName).stream().map(this::mapToDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ComputerDto> getComputersByLabId(String labId) {
        if (labId == null || labId.isEmpty() || "ALL".equalsIgnoreCase(labId)) {
            return getAllComputers();
        }
        return computerRepository.findByLabId(labId).stream()
                .filter(c -> c.getStatus() != ComputerStatus.PENDING && c.getStatus() != ComputerStatus.REJECTED)
                .map(this::mapToDto).toList();
    }

    @Override
    @Transactional
    public ComputerDto approveComputer(String computerId) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));
        computer.setStatus(ComputerStatus.ONLINE);
        computer.setLastSeenAt(Instant.now());
        computer = computerRepository.save(computer);
        return mapToDto(computer);
    }

    @Override
    @Transactional
    public ComputerDto rejectComputer(String computerId) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));
        computer.setStatus(ComputerStatus.REJECTED);
        computer = computerRepository.save(computer);
        return mapToDto(computer);
    }

    @Override
    @Transactional(readOnly = true)
    public String getAgentStatus(String agentId) {
        return computerRepository.findByAgentId(agentId)
                .map(c -> c.getStatus().name())
                .orElse("NOT_REGISTERED");
    }

    private ComputerDto mapToDto(Computer computer) {
        SystemMetric metric = systemMetricRepository.findLatestByComputerId(computer.getId()).orElse(null);
        HealthScore healthScore = healthScoreRepository.findLatestByComputerId(computer.getId()).orElse(null);

        boolean isLive = computer.getStatus() == ComputerStatus.ONLINE 
                || computer.getStatus() == ComputerStatus.WARNING 
                || computer.getStatus() == ComputerStatus.CRITICAL;

        Double rx = metric != null && metric.getNetworkRxBytesSec() != null ? metric.getNetworkRxBytesSec() : 0.0;
        Double tx = metric != null && metric.getNetworkTxBytesSec() != null ? metric.getNetworkTxBytesSec() : 0.0;
        double totalBytesSec = rx + tx;
        double speedMbps = Math.round((totalBytesSec * 8.0 / 1_000_000.0) * 100.0) / 100.0;

        return ComputerDto.builder()
                .id(computer.getId())
                .agentId(computer.getAgentId())
                .hostname(computer.getHostname())
                .computerName(computer.getComputerName())
                .ipAddress(computer.getIpAddress())
                .macAddress(computer.getMacAddress())
                .osName(computer.getOsName())
                .osVersion(computer.getOsVersion())
                .labId(computer.getLab() != null ? computer.getLab().getId() : null)
                .labCode(computer.getLab() != null ? computer.getLab().getCode() : null)
                .labName(computer.getLab() != null ? computer.getLab().getName() : (computer.getLabName() != null ? computer.getLabName() : "Computer Lab 1"))
                .displayName(computer.getDisplayName() != null ? computer.getDisplayName() : computer.getComputerName())
                .cpuModel(computer.getCpuModel())
                .totalRamMb(computer.getTotalRamMb())
                .agentVersion(computer.getAgentVersion())
                .status(computer.getStatus().name())
                .internetConnected(isLive && computer.getInternetConnected() != null ? computer.getInternetConnected() : false)
                .uptimeSeconds(computer.getUptimeSeconds() != null ? computer.getUptimeSeconds() : 0L)
                .lastSeenAt(computer.getLastSeenAt())
                .currentCpuUsage(isLive && computer.getLastCpuUsage() != null ? computer.getLastCpuUsage() : (metric != null ? metric.getCpuUsagePercent() : null))
                .currentRamUsage(isLive && computer.getLastRamUsage() != null ? computer.getLastRamUsage() : (metric != null ? metric.getMemoryUsagePercent() : null))
                .currentDiskUsage(isLive && computer.getLastDiskUsage() != null ? computer.getLastDiskUsage() : (metric != null ? metric.getDiskUsagePercent() : null))
                .currentHealthScore(healthScore != null ? healthScore.getOverallScore() : 100.0)
                .currentNetworkRxBytesSec(isLive ? rx : null)
                .currentNetworkTxBytesSec(isLive ? tx : null)
                .currentNetworkSpeedMbps(isLive ? speedMbps : null)
                .lastRecordedCpuUsage(metric != null ? metric.getCpuUsagePercent() : null)
                .lastRecordedRamUsage(metric != null ? metric.getMemoryUsagePercent() : null)
                .lastRecordedDiskUsage(metric != null ? metric.getDiskUsagePercent() : null)
                .lastRecordedAt(metric != null ? metric.getRecordedAt() : null)
                .build();
    }
}
