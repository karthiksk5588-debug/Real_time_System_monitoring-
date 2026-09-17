package com.neurosys.agent.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class FileAnalyzerCollector {

    private static final Logger log = LoggerFactory.getLogger(FileAnalyzerCollector.class);

    public Map<String, Object> scanDiskForCleanup() {
        Map<String, Object> results = new HashMap<>();

        long tempSize = 0;
        int tempCount = 0;

        // 1. Real Scan of Windows Temp Directory
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            tempSize += f.length();
                            tempCount++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Temp directory scan warning: {}", e.getMessage());
        }

        // 2. Real Scan of Large Files (>100MB) & Duplicate Files in User Workspace
        long largeFilesSize = 0;
        int largeFilesCount = 0;
        long dupSize = 0;
        int dupCount = 0;

        try {
            String userHome = System.getProperty("user.home");
            String[] targetDirs = { userHome + "/Downloads", userHome + "/Documents", userHome + "/Desktop" };

            Map<String, Long> seenFiles = new HashMap<>();

            for (String dirPath : targetDirs) {
                File dir = new File(dirPath);
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile()) {
                                long len = f.length();
                                // Large file check (>100MB)
                                if (len > 100L * 1024L * 1024L) {
                                    largeFilesSize += len;
                                    largeFilesCount++;
                                }
                                // Duplicate file check
                                String key = f.getName().toLowerCase() + "_" + len;
                                if (seenFiles.containsKey(key)) {
                                    dupCount++;
                                    dupSize += len;
                                } else {
                                    seenFiles.put(key, len);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("User workspace file scan warning: {}", e.getMessage());
        }

        double tempGb = Math.round((tempSize / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double largeGb = Math.round((largeFilesSize / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double dupGb = Math.round((dupSize / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double totalGb = Math.round(((tempSize + largeFilesSize + dupSize) / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;

        results.put("tempJunkFilesSizeGb", tempGb);
        results.put("tempJunkFilesCount", tempCount);
        results.put("largeFilesSizeGb", largeGb);
        results.put("largeFilesCount", largeFilesCount);
        results.put("duplicateFilesSizeGb", dupGb);
        results.put("duplicateFilesCount", dupCount);
        results.put("totalScannedSizeGb", totalGb);

        return results;
    }
}
