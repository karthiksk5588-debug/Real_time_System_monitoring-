package com.neurosys.backend.service;

import com.neurosys.backend.dto.response.LogAnalysisDto;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.SystemLog;
import com.neurosys.backend.enums.LogLevel;
import com.neurosys.backend.exception.ResourceNotFoundException;
import com.neurosys.backend.repository.ComputerRepository;
import com.neurosys.backend.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogHumanizerServiceImpl implements LogHumanizerService {

    private final SystemLogRepository systemLogRepository;
    private final ComputerRepository computerRepository;

    @Override
    @Transactional
    public LogAnalysisDto ingestAndHumanizeLog(String computerId, Integer eventId, String providerName, String logLevelStr, String rawMessage) {
        Computer computer = computerRepository.findById(computerId)
                .orElseThrow(() -> new ResourceNotFoundException("Computer", "id", computerId));

        LogLevel logLevel = parseLogLevel(logLevelStr);
        String simplifiedEnglish = translateErrorToEnglish(eventId, rawMessage);
        String suggestedSolution = provideSuggestedSolution(eventId, rawMessage);

        SystemLog systemLog = SystemLog.builder()
                .computer(computer)
                .eventId(eventId)
                .providerName(providerName)
                .logLevel(logLevel)
                .sourceComponent(providerName != null ? providerName : "Windows System")
                .rawMessage(rawMessage)
                .simplifiedEnglish(simplifiedEnglish)
                .suggestedSolution(suggestedSolution)
                .timestamp(Instant.now())
                .build();

        systemLog = systemLogRepository.save(systemLog);
        return mapToDto(systemLog);
    }

    @Override
    @Transactional
    public Page<LogAnalysisDto> getComputerLogs(String computerId, String logLevelStr, Pageable pageable) {
        Computer computer = computerRepository.findById(computerId).orElse(null);
        if (computer == null) return Page.empty();

        Page<SystemLog> logs;
        if (logLevelStr != null && !logLevelStr.trim().isEmpty()) {
            LogLevel level = parseLogLevel(logLevelStr);
            logs = systemLogRepository.findByComputerIdAndLogLevel(computerId, level, pageable);
        } else {
            logs = systemLogRepository.findByComputerId(computerId, pageable);
        }
        return logs.map(this::mapToDto);
    }

    private LogLevel parseLogLevel(String level) {
        if (level == null) return LogLevel.Information;
        try {
            return LogLevel.valueOf(level);
        } catch (Exception e) {
            String u = level.trim().toUpperCase();
            if (u.contains("CRIT") || u.contains("ERR") || u.equals("1") || u.equals("2")) {
                return LogLevel.Critical;
            } else if (u.contains("WARN") || u.equals("3")) {
                return LogLevel.Warning;
            }
            return LogLevel.Information;
        }
    }

    private String translateErrorToEnglish(Integer eventId, String rawMessage) {
        if (eventId != null) {
            return switch (eventId) {
                case 7001 -> "The Windows Service Control Manager failed to start a required background service during boot.";
                case 41 -> "The computer unexpectedly rebooted or suffered a sudden power loss (BSOD or hard shutdown).";
                case 51 -> "An error was detected on the disk drive during a page or read operation.";
                case 1001 -> "The system recovered from a BugCheck (Blue Screen of Death crash).";
                default -> "A system component logged an event notice: " + (rawMessage != null ? rawMessage : "No description");
            };
        }
        return "Windows System Event Notice: " + (rawMessage != null ? rawMessage : "Standard operation");
    }

    private String provideSuggestedSolution(Integer eventId, String rawMessage) {
        if (eventId != null) {
            return switch (eventId) {
                case 7001 -> "Check services.msc to verify dependent services (e.g. Netlogon, Workstation) are set to Automatic startup.";
                case 41 -> "Inspect power supply hardware, verify CPU heatsink seating, and check Windows minidump logs for faulty drivers.";
                case 51 -> "Run `chkdsk /f /r` in Administrative Command Prompt to scan and repair bad disk sectors.";
                case 1001 -> "Update graphics and chipset drivers; run `sfc /scannow` to repair corrupted system files.";
                default -> "Review system event details and verify device driver updates in Windows Device Manager.";
            };
        }
        return "Ensure regular Windows updates and driver maintenance.";
    }

    private LogAnalysisDto mapToDto(SystemLog log) {
        return LogAnalysisDto.builder()
                .id(log.getId())
                .computerId(log.getComputer().getId())
                .hostname(log.getComputer().getHostname())
                .eventId(log.getEventId())
                .providerName(log.getProviderName())
                .logLevel(log.getLogLevel().name())
                .sourceComponent(log.getSourceComponent())
                .rawMessage(log.getRawMessage())
                .simplifiedEnglish(log.getSimplifiedEnglish())
                .suggestedSolution(log.getSuggestedSolution())
                .timestamp(log.getTimestamp())
                .build();
    }
}
