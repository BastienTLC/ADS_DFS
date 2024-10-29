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

    /**
     * Generates files only if parameters differ, otherwise uses cached files.
     */
    private List<MultipartFile> getOrGenerateFiles(int numberOfFiles, int fileSize) {
        String key = numberOfFiles + "_" + fileSize;

        // Check if files are already cached for these parameters
        if (!fileCache.containsKey(key)) {
            List<MultipartFile> files = FileFactory.generateBinaryFiles(numberOfFiles, fileSize);
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
    public Map<String, Object> testFileUploadAndDownload(int numberOfFiles, int fileSize) {
        AtomicReference<StringBuilder> result = new AtomicReference<>(new StringBuilder());
        Map<String, Object> metrics = new HashMap<>();

        // Retrieve or generate files
        List<MultipartFile> files = getOrGenerateFiles(numberOfFiles, fileSize);



        // Initialize download metrics
        AtomicLong totalDownloadSize = new AtomicLong();
        long uniqueFileSize = fileSize;
        long downloadStartTime = System.nanoTime();

        // Download each file and accumulate total download size
        files.forEach(file -> {
            String filename = file.getOriginalFilename();
            try {
                String fileContent = fileDownloadService.downloadFile(filename, "test");
                long fileSizeBytes = fileContent.getBytes().length;
                totalDownloadSize.addAndGet(fileSizeBytes);

                result.get().append("Downloaded file: ").append(filename).append("\n");
            } catch (Exception e) {
                log.error("Error downloading file: " + filename, e);
                result.get().append("Failed to download file: ").append(filename).append("\n");
            }
        });
        long downloadEndTime = System.nanoTime();

        // Calculate download metrics
        double downloadDurationSec = (downloadEndTime - downloadStartTime) / 1_000_000_000.0;// Convert to seconds;
        double downloadThroughputBytesPerSec = totalDownloadSize.get() / downloadDurationSec;


        metrics.put("downloadDuration_sec", downloadDurationSec);
        metrics.put("downloadThroughput_bytes_per_sec", downloadThroughputBytesPerSec);
        metrics.put("totalDownloadSize_bytes", totalDownloadSize.get());
        metrics.put("fileCount", files.size());
        metrics.put("uniqueFileSize_bytes", uniqueFileSize);

        log.info(result.get().toString());
        return metrics;
    }

    /**
     * Executes multiple tries of file upload and download, accumulating metrics.
     */
    public List<Map<String, Object>> testFileUploadAndDownloadMultipleTry(int numberOfFiles, int fileSize, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int i = 0; i < nt; i++) {
            Map<String, Object> metrics = testFileUploadAndDownload(numberOfFiles, fileSize);
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

    public List<Map<String, Object>> testWithFileSizeIncrement(int startSize, int endSize, int increment, int nbFile, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int fileSize = startSize; fileSize <= endSize; fileSize += increment) {
            List<Map<String, Object>> metricsForSize = testFileUploadAndDownloadMultipleTry(nbFile, fileSize, nt, nbNode);
            allMetrics.addAll(metricsForSize);
            deleteAllGeneratedFiles();
        }

        return allMetrics;
    }

    public List<Map<String, Object>> testWithFileCountIncrement(int startCount, int endCount, int increment, int fileSize, int nt, int nbNode) {
        List<Map<String, Object>> allMetrics = new ArrayList<>();

        for (int fileCount = startCount; fileCount <= endCount; fileCount += increment) {
            List<Map<String, Object>> metricsForCount = testFileUploadAndDownloadMultipleTry(fileCount, fileSize, nt, nbNode);
            allMetrics.addAll(metricsForCount);
            deleteAllGeneratedFiles();
        }

        return allMetrics;
    }
}
