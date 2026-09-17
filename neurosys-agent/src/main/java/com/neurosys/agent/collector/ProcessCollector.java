package com.neurosys.agent.collector;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessCollector {

    private final SystemInfo systemInfo;
    private final OperatingSystem os;

    public ProcessCollector(SystemInfo systemInfo) {
        this.systemInfo = systemInfo;
        this.os = systemInfo.getOperatingSystem();
    }

    public int getActiveProcessCount() {
        return os.getProcessCount();
    }

    public List<Map<String, Object>> getTopProcesses(int limit) {
        List<OSProcess> processes = os.getProcesses(null, OperatingSystem.ProcessSorting.CPU_DESC, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        long totalMemory = systemInfo.getHardware().getMemory().getTotal();
        int logicalCores = Math.max(1, systemInfo.getHardware().getProcessor().getLogicalProcessorCount());

        for (OSProcess p : processes) {
            Map<String, Object> map = new HashMap<>();
            map.put("pid", p.getProcessID());
            map.put("processName", p.getName());
            
            double rawCpu = p.getProcessCpuLoadBetweenTicks(p) * 100.0;
            if (rawCpu <= 0.0) {
                rawCpu = (p.getProcessCpuLoadCumulative() * 100.0) / logicalCores;
            }
            double normalizedCpu = Math.min(100.0, Math.max(0.0, Math.round(rawCpu * 10.0) / 10.0));

            map.put("cpuPercent", normalizedCpu);
            map.put("memoryPercent", totalMemory > 0 ? Math.round(((double) p.getResidentSetSize() / totalMemory) * 1000.0) / 10.0 : 0.0);
            map.put("memoryUsedMb", Math.round((p.getResidentSetSize() / (1024.0 * 1024.0)) * 10.0) / 10.0);
            map.put("status", p.getState().name());
            map.put("user", p.getUser() != null ? p.getUser() : "SYSTEM");
            result.add(map);
        }
        return result;
    }
}
