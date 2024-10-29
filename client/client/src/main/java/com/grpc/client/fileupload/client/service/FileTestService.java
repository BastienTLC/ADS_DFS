package com.grpc.client.fileupload.client.service;

import com.grpc.client.fileupload.client.Factory.FileFactory;
import io.grpc.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class FileTestService extends BaseFileService {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private FileDownloadService fileDownloadService;

    /**
     * Test method that generates n random binary files of a specified size, uploads them,
     * and then downloads them to verify functionality.
     *
     * @param numberOfFiles The number of files to create, upload, and download
     * @param fileSize      The size of each file in bytes
     * @return String result of file upload and download test
     */

    public String testFileUploadAndDownload(int numberOfFiles, int fileSize) {
        StringBuilder result = new StringBuilder();

        // Generate random binary files
        List<MultipartFile> files = FileFactory.generateBinaryFiles(numberOfFiles, fileSize);

        // Upload each file
        files.forEach(file -> {
            try {
                fileUploadService.uploadFile(file, "test");
                result.append("Uploaded file: ").append(file.getOriginalFilename()).append("\n");
            } catch (Exception e) {
                log.error("Error uploading file: " + file.getOriginalFilename(), e);
                result.append("Failed to upload file: ").append(file.getOriginalFilename()).append("\n");
            }
        });

        // Download each file
        files.forEach(file -> {
            String filename = file.getOriginalFilename();
            try {
                String string = fileDownloadService.downloadFile(filename, "test");
                result.append("Downloaded file: ").append(filename).append("\n");
            } catch (Exception e) {
                log.error("Error downloading file: " + filename, e);
                result.append("Failed to download file: ").append(filename).append("\n");
            }
        });

        return result.toString();
    }
}
