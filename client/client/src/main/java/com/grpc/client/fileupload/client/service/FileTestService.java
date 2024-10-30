package com.grpc.client.fileupload.client.service;

import com.grpc.client.fileupload.client.Factory.FileFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class FileTestService extends BaseFileService {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private FileDownloadService fileDownloadService;

    @Autowired
    private FileOperationService fileOperationService;

    private final Map<String, List<MultipartFile>> fileCache = new HashMap<>();
    private static final int MB_TO_BYTES = 1_048_576; // 1 MB = 1,048,576 bytes

    /**
     * Generates files only if parameters differ, otherwise uses cached files.
     */
    private List<MultipartFile> getOrGenerateFiles(int numberOfFiles, int fileSizeMB) {
        String key = numberOfFiles + "_" + fileSizeMB;

        // Check if files are already cached for these parameters
        if (!fileCache.containsKey(key)) {
            int fileSizeBytes = fileSizeMB * MB_TO_BYTES; // Convert MB to bytes
            List<MultipartFile> files = FileFactory.generateBinaryFiles(numberOfFiles, fileSizeBytes);

            // Upload files only if they are not already uploaded
            files.forEach(file -> {
                try {
                    fileUploadService.uploadFile(file, "test");
                } catch (Exception e) {
                    log.error("Error uploading file: " + file.getOriginalFilename(), e);
                }
            });
            fileCache.put(key, files);
        }
        return fileCache.get(key);
    }

    /**
     * Executes the file upload and download process, returning metrics only for downloads.
     */
    public Map<String, Object> testFileUploadAndDownload(int numberOfFiles, int fileSizeMB) {
        AtomicReference<StringBuilder> result = new AtomicReference<>(new StringBuilder());
        Map<String, Object> metrics = new HashMap<>();

        // Retrieve or generate files
        List<MultipartFile> files = getOrGenerateFiles(numberOfFiles, fileSizeMB);

        // Initialize download metrics
        AtomicLong totalDownloadSizeBytes = new AtomicLong();
        long uniqueFileSizeMB = fileSizeMB; // File size already in MB for metrics
        long downloadStartTime = System.nanoTime();

        // Download each file and accumulate total download size
        files.forEach(file -> {
            String filename = file.getOriginalFilename();
            try {
                String fileContent = fileDownloadService.downloadFile(filename, "test");
                long fileSizeBytes = fileContent.getBytes().length;
                totalDownloadSizeBytes.addAndGet(fileSizeBytes);

                result.get().append("Downloaded file: ").append(filename).append("\n");
            } catch (Exception e) {
                log.error("Error downloading file: " + filename, e);
                result.get().append("Failed to download file: ").append(filename).append("\n");
            }
        });
        long downloadEndTime = System.nanoTime();

        // Calculate total download size based on the number of files and file size
        double totalDownloadSizeMB = numberOfFiles * fileSizeMB;

        // Calculate download metrics
        double downloadDurationSec = (downloadEndTime - downloadStartTime) / 1_000_000_000.0; // Convert to seconds
        double downloadThroughputMBPerSec = totalDownloadSizeMB / downloadDurationSec;

        // Format values to 3 decimal places
        downloadDurationSec = Math.round(downloadDurationSec * 1000.0) / 1000.0;
        downloadThroughputMBPerSec = Math.round(downloadThroughputMBPerSec * 1000.0) / 1000.0;
        totalDownloadSizeMB = Math.round(totalDownloadSizeMB * 1000.0) / 1000.0;

        // Store download metrics in the map
        metrics.put("downloadDuration_sec", downloadDurationSec);
        metrics.put("downloadThroughput_MB_per_sec", downloadThroughputMBPerSec);
        metrics.put("totalDownloadSize_MB", totalDownloadSizeMB);
        metrics.put("fileCount", files.size());
        metrics.put("uniqueFileSize_MB", uniqueFileSizeMB);

        log.info(result.get().toString());
        return metrics;
    }

    /**
     * Executes multiple tries of file upload and download, accumulating metrics.
     */
    public List<Map<String, Object>> testFileUploadAndDownloadMultipleTry(int numberOfFiles, int fileSizeMB, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int i = 0; i < nt; i++) {
            Map<String, Object> metrics = testFileUploadAndDownload(numberOfFiles, fileSizeMB);
            metrics.put("nbTry", i + 1);
            metrics.put("nbNode", nbNode);
            allMetrics.add(metrics);
        }

        return allMetrics;
    }

    /**
     * Deletes all files created in tests.
     */
    public void deleteAllGeneratedFiles() {
        fileCache.values().forEach(files -> files.forEach(file -> {
            String filename = file.getOriginalFilename();
            try {
                fileOperationService.deleteFile(filename, "test");
                log.info("Deleted file: " + filename);
            } catch (Exception e) {
                log.error("Failed to delete file: " + filename, e);
            }
        }));
        fileCache.clear(); // Clear the cache after deletion
    }

    public List<Map<String, Object>> testWithFileSizeIncrement(int startSizeMB, int endSizeMB, int incrementMB, int nbFile, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int fileSizeMB = startSizeMB; fileSizeMB <= endSizeMB; fileSizeMB += incrementMB) {
            List<Map<String, Object>> metricsForSize = testFileUploadAndDownloadMultipleTry(nbFile, fileSizeMB, nt, nbNode);
            allMetrics.addAll(metricsForSize);
            deleteAllGeneratedFiles();
        }

        return allMetrics;
    }

    public List<Map<String, Object>> testWithFileCountIncrement(int startCount, int endCount, int increment, int fileSizeMB, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int fileCount = startCount; fileCount <= endCount; fileCount += increment) {
            List<Map<String, Object>> metricsForCount = testFileUploadAndDownloadMultipleTry(fileCount, fileSizeMB, nt, nbNode);
            allMetrics.addAll(metricsForCount);
            deleteAllGeneratedFiles();
        }

        return allMetrics;
    }
}
