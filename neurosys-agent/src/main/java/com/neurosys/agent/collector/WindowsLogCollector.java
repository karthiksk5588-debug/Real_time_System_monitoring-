package com.neurosys.agent.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WindowsLogCollector {

    private static final Logger log = LoggerFactory.getLogger(WindowsLogCollector.class);

    public List<Map<String, Object>> collectRecentWindowsEvents() {
        List<Map<String, Object>> events = new ArrayList<>();

        // Collect from both System and Application logs, querying only Warning(3), Error(2), and Critical(1) events
        events.addAll(queryLogChannel("System"));
        events.addAll(queryLogChannel("Application"));

        return events;
    }

    private List<Map<String, Object>> queryLogChannel(String channelName) {
        List<Map<String, Object>> channelEvents = new ArrayList<>();
        try {
            // Query only Level 1 (Critical), Level 2 (Error), and Level 3 (Warning) events via native wevtutil tool
            String cmd = String.format("cmd.exe /c wevtutil qe %s /c:15 /rd:true /q:\"*[System[(Level=1 or Level=2 or Level=3)]]\" /f:text", channelName);
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            Map<String, Object> currentEvent = null;
            StringBuilder msgBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("Event[")) {
                    if (currentEvent != null) {
                        if (msgBuilder.length() > 0) {
                            currentEvent.put("message", msgBuilder.toString().trim());
                        }
                        if (isValidProblemEvent(currentEvent)) {
                            channelEvents.add(currentEvent);
                        }
                    }
                    currentEvent = new HashMap<>();
                    currentEvent.put("eventSource", "Windows " + channelName + " Log");
                    currentEvent.put("occurredAt", Instant.now().toString());
                    msgBuilder = new StringBuilder();
                } else if (currentEvent != null) {
                    if (trimmed.startsWith("Event ID:")) {
                        try {
                            int id = Integer.parseInt(trimmed.replace("Event ID:", "").trim());
                            currentEvent.put("eventId", id);
                        } catch (Exception e) {
                            currentEvent.put("eventId", 100);
                        }
                    } else if (trimmed.startsWith("Source:") || trimmed.startsWith("Provider Name:")) {
                        String src = trimmed.replace("Source:", "").replace("Provider Name:", "").trim();
                        if (!src.isEmpty()) {
                            currentEvent.put("eventSource", src);
                        }
                    } else if (trimmed.startsWith("Level:")) {
                        currentEvent.put("category", trimmed.replace("Level:", "").trim());
                    } else if (trimmed.startsWith("Date:")) {
                        currentEvent.put("occurredAt", Instant.now().toString());
                    } else if (trimmed.startsWith("Description:")) {
                        msgBuilder.append(trimmed.replace("Description:", "").trim()).append(" ");
                    } else if (!trimmed.isEmpty() && !trimmed.contains(":") && msgBuilder.length() > 0) {
                        msgBuilder.append(trimmed).append(" ");
                    }
                }
            }

            if (currentEvent != null) {
                if (msgBuilder.length() > 0) {
                    currentEvent.put("message", msgBuilder.toString().trim());
                }
                if (isValidProblemEvent(currentEvent)) {
                    channelEvents.add(currentEvent);
                }
            }
        } catch (Exception e) {
            log.debug("Windows Event log channel {} query error: {}", channelName, e.getMessage());
        }

        return channelEvents;
    }

    private boolean isValidProblemEvent(Map<String, Object> event) {
        Integer eventId = (Integer) event.get("eventId");
        if (eventId == null) return true;

        // Ignore routine informational events that are not true system problems
        // Event ID 7040: Service configuration changes
        // Event ID 7036: Routine service start/stop state changes
        // Event ID 507 / 700 / 701: Routine Modern Standby / Power Manager input suppression events
        if (eventId == 7040 || eventId == 7036 || eventId == 507 || eventId == 700 || eventId == 701) {
            return false;
        }

        return true;
    }
}
