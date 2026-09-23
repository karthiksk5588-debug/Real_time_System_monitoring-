package com.neurosys.agent.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public class FileAnalyzerCollector {

    private static final Logger log = LoggerFactory.getLogger(FileAnalyzerCollector.class);
    private static final long LARGE_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L; // 100 MB

    public Map<String, Object> scanDiskForCleanup() {
        Map<String, Object> results = new HashMap<>();

        List<String> scannedLocations = new ArrayList<>();
        List<Map<String, Object>> largeFilesList = new ArrayList<>();
        Map<Long, List<File>> sizeCandidateMap = new HashMap<>();
        
        long totalScannedBytes = 0;
        long filesExamined = 0;
        long filesAccessible = 0;
        long filesSkipped = 0;

        long tempJunkSizeBytes = 0;
        int tempJunkFileCount = 0;

        String userHome = System.getProperty("user.home");
        List<File> targetDirs = new ArrayList<>();

        // Add user workspace locations
        addIfExists(targetDirs, new File(userHome, "Downloads"));
        addIfExists(targetDirs, new File(userHome, "Documents"));
        addIfExists(targetDirs, new File(userHome, "Desktop"));
        addIfExists(targetDirs, new File(userHome, "Pictures"));
        addIfExists(targetDirs, new File(userHome, "Videos"));

        // Add temporary directories
        File userTemp = new File(System.getProperty("java.io.tmpdir"));
        addIfExists(targetDirs, userTemp);
        File winTemp = new File("C:/Windows/Temp");
        addIfExists(targetDirs, winTemp);

        for (File dir : targetDirs) {
            scannedLocations.add(dir.getAbsolutePath());
            scanDirectoryRecursively(
                    dir,
                    0,
                    5, // Max depth
                    dir.equals(userTemp) || dir.equals(winTemp),
                    scannedLocations,
                    largeFilesList,
                    sizeCandidateMap,
                    new long[]{ totalScannedBytes, filesExamined, filesAccessible, filesSkipped, tempJunkSizeBytes, tempJunkFileCount }
            );
        }

        // Unpack counters
        // counters array order: [totalScannedBytes, filesExamined, filesAccessible, filesSkipped, tempJunkSizeBytes, tempJunkFileCount]
        // Note: Java array modification was updated inside recursion, let's track explicitly
        
        // 1. Process Large Files metrics
        long largeFilesSizeBytes = 0;
        for (Map<String, Object> lf : largeFilesList) {
            largeFilesSizeBytes += ((Number) lf.get("sizeBytes")).longValue();
        }

        // Sort large files descending by size
        largeFilesList.sort((a, b) -> Long.compare(((Number) b.get("sizeBytes")).longValue(), ((Number) a.get("sizeBytes")).longValue()));
        if (largeFilesList.size() > 25) {
            largeFilesList = new ArrayList<>(largeFilesList.subList(0, 25));
        }

        // 2. Process SHA-256 Duplicate File Detection
        long duplicateFilesSizeBytes = 0;
        int duplicateFilesCount = 0;
        List<Map<String, Object>> duplicateGroupsList = new ArrayList<>();

        for (Map.Entry<Long, List<File>> entry : sizeCandidateMap.entrySet()) {
            List<File> candidates = entry.getValue();
            if (candidates.size() < 2) continue; // Size must match at least 2 files

            Map<String, List<File>> hashMap = new HashMap<>();
            for (File f : candidates) {
                String hash = computeSha256(f);
                if (hash != null) {
                    hashMap.computeIfAbsent(hash, k -> new ArrayList<>()).add(f);
                }
            }

            for (Map.Entry<String, List<File>> hashEntry : hashMap.entrySet()) {
                List<File> dupFiles = hashEntry.getValue();
                if (dupFiles.size() >= 2) {
                    long singleSizeBytes = entry.getKey();
                    long recoverableBytes = (dupFiles.size() - 1) * singleSizeBytes;
                    duplicateFilesSizeBytes += recoverableBytes;
                    duplicateFilesCount += (dupFiles.size() - 1);

                    List<String> paths = new ArrayList<>();
                    for (File df : dupFiles) {
                        paths.add(df.getAbsolutePath());
                    }

                    Map<String, Object> group = new HashMap<>();
                    group.put("sha256", hashEntry.getKey());
                    group.put("fileCount", dupFiles.size());
                    group.put("fileSizeBytes", singleSizeBytes);
                    group.put("fileSizeMb", Math.round((singleSizeBytes / (1024.0 * 1024.0)) * 100.0) / 100.0);
                    group.put("recoverableBytes", recoverableBytes);
                    group.put("recoverableMb", Math.round((recoverableBytes / (1024.0 * 1024.0)) * 100.0) / 100.0);
                    group.put("filePaths", paths);
                    duplicateGroupsList.add(group);
                }
            }
        }

        // Calculate final stats
        long grandTotalScannedBytes = scanMetrics[0];
        long totalExamined = scanMetrics[1];
        long totalAccessible = scanMetrics[2];
        long totalSkipped = scanMetrics[3];
        long totalTempBytes = scanMetrics[4];
        int totalTempCount = (int) scanMetrics[5];

        double totalScannedGb = Math.round((grandTotalScannedBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double tempGb = Math.round((totalTempBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double largeGb = Math.round((largeFilesSizeBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;
        double dupGb = Math.round((duplicateFilesSizeBytes / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0;

        String scanStatus = totalSkipped > 0 ? "COMPLETED_WITH_WARNINGS" : "COMPLETED";

        results.put("scanStatus", scanStatus);
        results.put("scannedLocations", scannedLocations);
        results.put("filesExamined", totalExamined);
        results.put("filesAccessible", totalAccessible);
        results.put("filesSkipped", totalSkipped);
        results.put("skippedReason", totalSkipped > 0 ? "Permission denied or system-protected files" : "None");
        results.put("totalScannedSizeBytes", grandTotalScannedBytes);
        results.put("totalScannedSizeGb", totalScannedGb);

        results.put("tempJunkFilesSizeBytes", totalTempBytes);
        results.put("tempJunkFilesSizeGb", tempGb);
        results.put("tempJunkFilesCount", totalTempCount);

        results.put("largeFilesSizeBytes", largeFilesSizeBytes);
        results.put("largeFilesSizeGb", largeGb);
        results.put("largeFilesCount", largeFilesList.size());
        results.put("largeFilesList", largeFilesList);

        results.put("duplicateFilesSizeBytes", duplicateFilesSizeBytes);
        results.put("duplicateFilesSizeGb", dupGb);
        results.put("duplicateFilesCount", duplicateFilesCount);
        results.put("duplicateGroupsCount", duplicateGroupsList.size());
        results.put("duplicateGroupsList", duplicateGroupsList);

        log.info("[FILE ANALYZER] Real scan finished: Scanned {} files ({}) across {} locations. Large: {}, Duplicates: {}, Temp: {} MB",
                totalAccessible, totalScannedGb + " GB", scannedLocations.size(), largeFilesList.size(), duplicateGroupsList.size(), Math.round(totalTempBytes / (1024.0 * 1024.0)));

        return results;
    }

    private final long[] scanMetrics = new long[6];

    private void addIfExists(List<File> list, File dir) {
        if (dir != null && dir.exists() && dir.isDirectory()) {
            list.add(dir);
        }
    }

    private void scanDirectoryRecursively(
            File dir,
            int currentDepth,
            int maxDepth,
            boolean isTempDir,
            List<String> scannedLocations,
            List<Map<String, Object>> largeFilesList,
            Map<Long, List<File>> sizeCandidateMap,
            long[] counters
    ) {
        if (currentDepth > maxDepth || dir == null) return;

        File[] files;
        try {
            files = dir.listFiles();
        } catch (SecurityException e) {
            scanMetrics[3]++; // skipped
            return;
        }

        if (files == null) {
            scanMetrics[3]++; // skipped
            return;
        }

        for (File f : files) {
            scanMetrics[1]++; // filesExamined
            try {
                if (f.isDirectory()) {
                    if (!f.getName().startsWith(".") && !f.getName().equalsIgnoreCase("System Volume Information") && !f.getName().equalsIgnoreCase("$Recycle.Bin")) {
                        scanDirectoryRecursively(f, currentDepth + 1, maxDepth, isTempDir, scannedLocations, largeFilesList, sizeCandidateMap, counters);
                    }
                } else if (f.isFile()) {
                    scanMetrics[2]++; // filesAccessible
                    long len = f.length();
                    scanMetrics[0] += len; // totalScannedBytes

                    String name = f.getName();

                    // Check if temporary or junk file
                    if (isTempDir || isTempExtension(name)) {
                        scanMetrics[4] += len;
                        scanMetrics[5]++;
                    }

                    // Check Large File (>= 100 MB)
                    if (len >= LARGE_FILE_THRESHOLD_BYTES) {
                        Map<String, Object> lfMap = new HashMap<>();
                        lfMap.put("fileName", name);
                        lfMap.put("filePath", f.getAbsolutePath());
                        lfMap.put("sizeBytes", len);
                        lfMap.put("sizeMb", Math.round((len / (1024.0 * 1024.0)) * 100.0) / 100.0);
                        lfMap.put("sizeGb", Math.round((len / (1024.0 * 1024.0 * 1024.0)) * 100.0) / 100.0);
                        lfMap.put("lastModified", Instant.ofEpochMilli(f.lastModified()).toString());
                        lfMap.put("extension", getExtension(name));
                        largeFilesList.add(lfMap);
                    }

                    // Duplicate File Candidate (Non-zero file size)
                    if (!isTempDir && len > 0L) {
                        if (sizeCandidateMap.containsKey(len)) {
                            sizeCandidateMap.get(len).add(f);
                        } else if (sizeCandidateMap.size() < 10000) {
                            List<File> list = new ArrayList<>();
                            list.add(f);
                            sizeCandidateMap.put(len, list);
                        }
                    }
                }
            } catch (Exception e) {
                scanMetrics[3]++; // filesSkipped
            }
        }
    }

    private boolean isTempExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".bak")
                || lower.endsWith(".log") || lower.endsWith(".dmp") || lower.endsWith(".chk")
                || lower.contains("cache");
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(idx + 1).toLowerCase() : "";
    }

    private String computeSha256(File file) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            long totalRead = 0;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                totalRead += read;
                if (totalRead > 10L * 1024L * 1024L) break; // Hash first 10MB for fast comparison
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
