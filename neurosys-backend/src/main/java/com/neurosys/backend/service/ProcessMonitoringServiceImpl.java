package com.neurosys.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.backend.dto.request.ProcessInfoDto;
import com.neurosys.backend.dto.response.ProcessMonitoringResponse;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessMonitoringServiceImpl implements ProcessMonitoringService {

    private final ComputerRepository computerRepository;
    private final SystemMetricRepository systemMetricRepository;
    private final SystemMetricsService systemMetricsService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ProcessMonitoringResponse getComputerProcesses(String computerId, String search, String sortBy, String sortOrder, int page, int size) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));

        List<ProcessInfoDto> liveProcesses = new ArrayList<>(systemMetricsService.getLatestProcesses(computerId));

        if (liveProcesses.isEmpty()) {
            SystemMetric latestMetric = systemMetricRepository.findLatestByComputerId(computerId).orElse(null);
            if (latestMetric != null && latestMetric.getTopProcessesJson() != null && !latestMetric.getTopProcessesJson().isEmpty()) {
                try {
                    liveProcesses = objectMapper.readValue(latestMetric.getTopProcessesJson(), new TypeReference<List<ProcessInfoDto>>() {});
                } catch (Exception e) {
                    log.warn("Failed to parse real process JSON from database for computer {}", computerId, e);
                }
            }
        }

        // Filter by Search Query
        if (search != null && !search.trim().isEmpty()) {
            String query = search.toLowerCase().trim();
            liveProcesses = liveProcesses.stream()
                    .filter(p -> p.getProcessName().toLowerCase().contains(query) || String.valueOf(p.getPid()).contains(query))
                    .toList();
        }

        // Sorting
        Comparator<ProcessInfoDto> comparator = switch (sortBy != null ? sortBy.toLowerCase() : "cpu") {
            case "ram", "memory" -> Comparator.comparing(ProcessInfoDto::getMemoryPercent);
            case "name" -> Comparator.comparing(ProcessInfoDto::getProcessName);
            case "pid" -> Comparator.comparing(ProcessInfoDto::getPid);
            default -> Comparator.comparing(ProcessInfoDto::getCpuPercent);
        };

        if ("desc".equalsIgnoreCase(sortOrder)) {
            comparator = comparator.reversed();
        }

        liveProcesses = new ArrayList<>(liveProcesses);
        liveProcesses.sort(comparator);

        // Pagination
        int totalCount = liveProcesses.size();
        int totalPages = (int) Math.ceil((double) totalCount / Math.max(1, size));
        int fromIndex = Math.min(page * size, totalCount);
        int toIndex = Math.min(fromIndex + size, totalCount);

        List<ProcessInfoDto> pagedProcesses = liveProcesses.subList(fromIndex, toIndex);

        List<ProcessInfoDto> topCpu = liveProcesses.stream()
                .sorted(Comparator.comparing(ProcessInfoDto::getCpuPercent).reversed())
                .limit(5).toList();

        List<ProcessInfoDto> topRam = liveProcesses.stream()
                .sorted(Comparator.comparing(ProcessInfoDto::getMemoryPercent).reversed())
                .limit(5).toList();

        return ProcessMonitoringResponse.builder()
                .computerId(computer.getId())
                .hostname(computer.getHostname())
                .totalProcessesCount(totalCount)
                .topCpuProcesses(topCpu)
                .topRamProcesses(topRam)
                .processes(pagedProcesses)
                .currentPage(page)
                .totalPages(totalPages)
                .build();
    }
}
