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
import java.util.Locale;

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

        // Ignore routine informational events from database persistence
        if (isRoutineInformationalEvent(eventId, providerName, rawMessage, logLevel)) {
            return null;
        }

        EventClassification classification = classifyEvent(eventId, providerName, rawMessage, logLevel);

        SystemLog systemLog = SystemLog.builder()
                .computer(computer)
                .eventId(eventId)
                .providerName(providerName)
                .logLevel(logLevel)
                .sourceComponent(providerName != null ? providerName : "Windows System")
                .rawMessage(rawMessage)
                .simplifiedEnglish(classification.whatHappened)
                .suggestedSolution(classification.recommendedAction)
                .timestamp(Instant.now())
                .build();

        systemLog = systemLogRepository.save(systemLog);
        return mapToDtoWithClassification(systemLog, classification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LogAnalysisDto> getComputerLogs(String computerId, String logLevelStr, Pageable pageable) {
        Computer computer = computerRepository.findById(computerId).orElse(null);
        if (computer == null) return Page.empty();

        Page<SystemLog> logs;
        if (logLevelStr != null && !logLevelStr.trim().isEmpty() && !"ALL".equalsIgnoreCase(logLevelStr)) {
            LogLevel level = parseLogLevel(logLevelStr);
            logs = systemLogRepository.findByComputerIdAndLogLevel(computerId, level, pageable);
        } else {
            // Default query: exclude routine Information logs from main administrator view unless requested
            logs = systemLogRepository.findByComputerId(computerId, pageable);
        }
        return logs.map(this::mapToDto);
    }

    private LogLevel parseLogLevel(String level) {
        if (level == null) return LogLevel.Information;
        try {
            return LogLevel.valueOf(level);
        } catch (Exception e) {
            String u = level.trim().toUpperCase(Locale.ROOT);
            if (u.contains("CRIT") || u.contains("ERR") || u.equals("1") || u.equals("2")) {
                return LogLevel.Critical;
            } else if (u.contains("WARN") || u.equals("3")) {
                return LogLevel.Warning;
            }
            return LogLevel.Information;
        }
    }

    private boolean isRoutineInformationalEvent(Integer eventId, String providerName, String rawMessage, LogLevel logLevel) {
        if (logLevel == LogLevel.Information) {
            // Always suppress routine service start/stop notifications, scheduled tasks, and standby logs
            if (eventId != null && (eventId == 7040 || eventId == 7036 || eventId == 507 || eventId == 700 || eventId == 701)) {
                return true;
            }
        }
        return false;
    }

    private static class EventClassification {
        String category;
        String title;
        String whatHappened;
        String whyItMatters;
        String recommendedAction;
        String severity;

        EventClassification(String category, String title, String whatHappened, String whyItMatters, String recommendedAction, String severity) {
            this.category = category;
            this.title = title;
            this.whatHappened = whatHappened;
            this.whyItMatters = whyItMatters;
            this.recommendedAction = recommendedAction;
            this.severity = severity;
        }
    }

    private EventClassification classifyEvent(Integer eventId, String providerName, String rawMessage, LogLevel logLevel) {
        String prov = providerName != null ? providerName.toLowerCase(Locale.ROOT) : "";
        String msg = rawMessage != null ? rawMessage.toLowerCase(Locale.ROOT) : "";

        // 1. System Crash / BSOD (Event ID 41, 1001, BugCheck)
        if ((eventId != null && (eventId == 41 || eventId == 1001)) || msg.contains("bugcheck") || msg.contains("blue screen")) {
            return new EventClassification(
                    "System Crash",
                    "Unexpected Kernel Reboot / BSOD",
                    "The workstation experienced an unexpected reboot or Blue Screen of Death (BSOD) crash.",
                    "Sudden crashes interrupt user sessions, risk file corruption, and indicate potential driver faults or power supply issues.",
                    "Inspect device drivers, check CPU heatsink thermal seating, and run `sfc /scannow` in Command Prompt.",
                    "CRITICAL"
            );
        }

        // 2. Application Crash (Event ID 1000, 1002, AppCrash)
        if ((eventId != null && (eventId == 1000 || eventId == 1002)) || msg.contains("appcrash") || prov.contains("application error")) {
            return new EventClassification(
                    "Application Crash",
                    "Application Failure / Freeze",
                    "A software application or background process stopped working or crashed unexpectedly.",
                    "Repeated app crashes prevent users from completing tasks and may indicate software incompatibility or memory corruption.",
                    "Update the affected application to its latest build or repair corrupt application data files.",
                    "WARNING"
            );
        }

        // 3. Disk Error (Event ID 7, 11, 51, 55, 153)
        if ((eventId != null && (eventId == 7 || eventId == 11 || eventId == 51 || eventId == 55 || eventId == 153)) || msg.contains("disk") || msg.contains("bad block")) {
            return new EventClassification(
                    "Disk Error",
                    "Hard Drive / Storage Controller Fault",
                    "An I/O read or write operation failed on a disk drive sector.",
                    "Disk errors precede physical drive failure and cause permanent data loss if left unaddressed.",
                    "Run `chkdsk /f /r` in Administrative Command Prompt and back up critical user data immediately.",
                    "CRITICAL"
            );
        }

        // 4. Service Failure (Event ID 7001, 7023, 7031)
        if ((eventId != null && (eventId == 7001 || eventId == 7023 || eventId == 7031)) || msg.contains("service control manager")) {
            return new EventClassification(
                    "Service Failure",
                    "Windows Background Service Interrupted",
                    "A core system background service failed to start or terminated unexpectedly.",
                    "If dependent network or security services fail, system functionality or internet access may be degraded.",
                    "Open Services (`services.msc`), locate the affected service, and set its startup type to Automatic.",
                    "WARNING"
            );
        }

        // 5. Driver Problem (Event ID 219, 10016 DCOM)
        if ((eventId != null && (eventId == 219 || eventId == 10016)) || prov.contains("driver") || msg.contains("driver")) {
            return new EventClassification(
                    "Driver Problem",
                    "Hardware Driver / DCOM Configuration Error",
                    "A device driver failed to load or experienced permission initialization issues.",
                    "Missing or outdated drivers can disable peripheral hardware, graphics adapters, or network cards.",
                    "Open Device Manager (`devmgmt.msc`) and update hardware drivers to official manufacturer releases.",
                    "WARNING"
            );
        }

        // Fallback default classification based on LogLevel
        String sev = (logLevel == LogLevel.Critical) ? "CRITICAL" : (logLevel == LogLevel.Warning ? "WARNING" : "INFO");
        return new EventClassification(
                "System Event",
                "Workstation Diagnostic Notice",
                rawMessage != null ? rawMessage : "Windows operating system logged a diagnostic alert.",
                "Review log context if this workstation shows performance issues or abnormal behavior.",
                "Review Windows Event Viewer for additional provider context.",
                sev
        );
    }

    private LogAnalysisDto mapToDto(SystemLog logItem) {
        EventClassification c = classifyEvent(logItem.getEventId(), logItem.getProviderName(), logItem.getRawMessage(), logItem.getLogLevel());
        return mapToDtoWithClassification(logItem, c);
    }

    private LogAnalysisDto mapToDtoWithClassification(SystemLog logItem, EventClassification c) {
        return LogAnalysisDto.builder()
                .id(logItem.getId())
                .computerId(logItem.getComputer().getId())
                .hostname(logItem.getComputer().getHostname())
                .eventId(logItem.getEventId())
                .providerName(logItem.getProviderName())
                .logLevel(logItem.getLogLevel().name())
                .sourceComponent(logItem.getSourceComponent())
                .rawMessage(logItem.getRawMessage())
                .eventCategory(c.category)
                .title(c.title)
                .whatHappened(c.whatHappened)
                .whyItMatters(c.whyItMatters)
                .recommendedAction(c.recommendedAction)
                .severity(c.severity)
                .simplifiedEnglish(c.whatHappened)
                .suggestedSolution(c.recommendedAction)
                .timestamp(logItem.getTimestamp())
                .build();
    }
}
