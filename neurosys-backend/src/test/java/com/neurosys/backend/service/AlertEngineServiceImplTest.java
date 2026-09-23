package com.neurosys.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neurosys.backend.config.AlertEngineConfig;
import com.neurosys.backend.dto.response.AlertDto;
import com.neurosys.backend.entity.Alert;
import com.neurosys.backend.entity.Computer;
import com.neurosys.backend.entity.Lab;
import com.neurosys.backend.entity.SystemMetric;
import com.neurosys.backend.enums.AlertSeverity;
import com.neurosys.backend.enums.AlertStatus;
import com.neurosys.backend.enums.AlertType;
import com.neurosys.backend.enums.ComputerStatus;
import com.neurosys.backend.repository.AlertRepository;
import com.neurosys.backend.repository.DiagnosticEventRepository;
import com.neurosys.backend.repository.SystemMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertEngineServiceImplTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private SystemMetricRepository systemMetricRepository;

    @Mock
    private DiagnosticEventRepository diagnosticEventRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private AlertEngineConfig alertConfig = new AlertEngineConfig();

    private AlertEngineServiceImpl alertEngineService;

    private Computer testComputer;
    private Lab testLab;

    @BeforeEach
    void setUp() {
        alertEngineService = new AlertEngineServiceImpl(
                alertRepository,
                systemMetricRepository,
                diagnosticEventRepository,
                emailNotificationService,
                objectMapper,
                alertConfig
        );

        testLab = Lab.builder()
                .id("LAB-01")
                .name("Computer Lab A")
                .code("LABA")
                .build();

        testComputer = Computer.builder()
                .id("COMP-01")
                .hostname("LABA-PC01")
                .displayName("Lab Workstation 01")
                .agentId("AGENT-01")
                .lab(testLab)
                .status(ComputerStatus.ONLINE)
                .lastSeenAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("TEST 1: Temporary CPU spike (< 5 min) should NOT generate degradation alert")
    void testTemporaryCpuSpikeSuppressed() {
        // Single 95% CPU sample
        SystemMetric metric = SystemMetric.builder()
                .computer(testComputer)
                .cpuUsagePercent(95.0)
                .memoryUsagePercent(50.0)
                .diskUsagePercent(40.0)
                .recordedAt(Instant.now())
                .build();

        List<AlertDto> alerts = alertEngineService.evaluateAndTriggerAlerts(testComputer, metric);

        assertTrue(alerts.isEmpty(), "Temporary CPU spike should not trigger a degradation alert");
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    @DisplayName("TEST 2: Sustained CPU pressure (95% for > 5 mins) SHOULD trigger CPU_SUSTAINED_HIGH alert")
    void testSustainedCpuPressureTriggersAlert() {
        Instant now = Instant.now();
        List<SystemMetric> historySamples = new ArrayList<>();

        when(alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(anyString(), any(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> {
            Alert saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId("ALERT-CPU-01");
            return saved;
        });

        // 300 1-second samples with 95% CPU
        for (int i = 300; i >= 0; i--) {
            SystemMetric m = SystemMetric.builder()
                    .computer(testComputer)
                    .cpuUsagePercent(95.0)
                    .memoryUsagePercent(50.0)
                    .diskUsagePercent(40.0)
                    .recordedAt(now.minusSeconds(i))
                    .build();

            alertEngineService.evaluateAndTriggerAlerts(testComputer, m);
        }

        verify(alertRepository, atLeastOnce()).save(argThat(a ->
                a.getAlertType() == AlertType.CPU_SUSTAINED_HIGH &&
                        a.getResourceKey().equals("CPU") &&
                        a.getSeverity() == AlertSeverity.WARNING
        ));
    }

    @Test
    @DisplayName("TEST 3: CPU recovery below hysteresis threshold (75%) should resolve active alert")
    void testCpuRecoveryResolvesAlert() {
        Instant now = Instant.now();

        Alert activeCpuAlert = Alert.builder()
                .id("ALERT-CPU-01")
                .computer(testComputer)
                .title("LABA-PC01 - Sustained High CPU Usage")
                .message("High CPU usage")
                .severity(AlertSeverity.WARNING)
                .alertType(AlertType.CPU_SUSTAINED_HIGH)
                .resourceKey("CPU")
                .status(AlertStatus.OPEN)
                .build();

        when(alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(eq(testComputer.getId()), eq(AlertType.CPU_SUSTAINED_HIGH), eq("CPU"), anyList()))
                .thenReturn(Optional.of(activeCpuAlert));

        // Feed recent 5 samples with 60% CPU (below 75% recovery threshold)
        for (int i = 5; i >= 0; i--) {
            SystemMetric m = SystemMetric.builder()
                    .computer(testComputer)
                    .cpuUsagePercent(60.0)
                    .memoryUsagePercent(50.0)
                    .diskUsagePercent(40.0)
                    .recordedAt(now.minusSeconds(i))
                    .build();

            alertEngineService.evaluateAndTriggerAlerts(testComputer, m);
        }

        verify(alertRepository, atLeastOnce()).save(argThat(a ->
                a.getId().equals("ALERT-CPU-01") &&
                        a.getStatus() == AlertStatus.RESOLVED &&
                        a.getResolvedAt() != null
        ));
    }

    @Test
    @DisplayName("TEST 6: Disk space at 96% full should trigger DISK_SPACE_CRITICAL alert with drive details")
    void testDiskSpaceCriticalAlert() {
        SystemMetric metric = SystemMetric.builder()
                .computer(testComputer)
                .cpuUsagePercent(30.0)
                .memoryUsagePercent(50.0)
                .diskUsagePercent(96.0)
                .diskUsedGb(432.0)
                .diskFreeGb(18.0)
                .recordedAt(Instant.now())
                .build();

        when(alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(anyString(), any(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        List<AlertDto> triggered = alertEngineService.evaluateAndTriggerAlerts(testComputer, metric);

        assertFalse(triggered.isEmpty(), "Disk space critical alert should trigger");
        AlertDto diskAlert = triggered.stream().filter(a -> a.getAlertType().contains("DISK")).findFirst().orElse(null);
        assertNotNull(diskAlert);
        assertEquals("CRITICAL", diskAlert.getSeverity());
        assertTrue(diskAlert.getMessage().contains("96.0% full"));
    }

    @Test
    @DisplayName("TEST 8: Offline alert at 300s (5 minutes) should create ONE active incident")
    void testOfflineAlertAfter5Minutes() {
        when(alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(anyString(), any(), anyString(), anyList()))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

        alertEngineService.triggerOfflineAlert(testComputer, 312L); // 5 min 12 sec offline

        verify(alertRepository, times(1)).save(argThat(a ->
                a.getAlertType() == AlertType.ENDPOINT_OFFLINE &&
                        a.getResourceKey().equals("ENDPOINT") &&
                        a.getStatus() == AlertStatus.OPEN
        ));
    }

    @Test
    @DisplayName("TEST 9: Heartbeat resumption should automatically resolve active offline incident")
    void testOfflineRecoveryOnHeartbeat() {
        Alert activeOffline = Alert.builder()
                .id("ALERT-OFFLINE-01")
                .computer(testComputer)
                .title("LABA-PC01 - Endpoint Offline")
                .message("Offline for 5 minutes")
                .severity(AlertSeverity.WARNING)
                .alertType(AlertType.ENDPOINT_OFFLINE)
                .resourceKey("ENDPOINT")
                .status(AlertStatus.OPEN)
                .build();

        when(alertRepository.findFirstByComputerIdAndAlertTypeAndResourceKeyAndStatusIn(eq(testComputer.getId()), eq(AlertType.ENDPOINT_OFFLINE), eq("ENDPOINT"), anyList()))
                .thenReturn(Optional.of(activeOffline));

        alertEngineService.resolveOfflineAlert(testComputer);

        verify(alertRepository, atLeastOnce()).save(argThat(a ->
                a.getId().equals("ALERT-OFFLINE-01") &&
                        a.getStatus() == AlertStatus.RESOLVED
        ));
    }
}
