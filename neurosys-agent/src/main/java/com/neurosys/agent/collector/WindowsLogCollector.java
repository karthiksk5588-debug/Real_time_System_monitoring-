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
        try {
            // Query actual recent Windows System events via native wevtutil tool
            Process process = Runtime.getRuntime().exec("cmd.exe /c wevtutil qe System /c:5 /rd:true /f:text");
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
                        events.add(currentEvent);
                    }
                    currentEvent = new HashMap<>();
                    currentEvent.put("eventSource", "Windows System Log");
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
                events.add(currentEvent);
            }
        } catch (Exception e) {
            log.debug("Windows Event log collector error: {}", e.getMessage());
        }

        return events;
    }
}
