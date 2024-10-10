// FileManagementController.java
package com.grpc.client.fileupload.client.controller;

import com.devProblems.FileDownloadRequest;
import com.devProblems.FileMetadata;
import com.grpc.client.fileupload.client.service.FileManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.google.protobuf.Empty;

import java.util.List;

@Slf4j
@RestController
public class FileManagementController {

    private final FileManagementService fileManagementService;

    public FileManagementController(FileManagementService fileManagementService) {
        this.fileManagementService = fileManagementService;
    }

    //endpoint to list all files
    @GetMapping("/files")
    public List<FileMetadata> listFiles() {
        return this.fileManagementService.listFiles();
    }

    //endpoint to get metadata of a specific file
    @GetMapping("/files/{fileName}/metadata")
    public FileMetadata getFileMetadata(@PathVariable("fileName") String fileName) {
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .build();
        return this.fileManagementService.getFileMetadata(request);
    }
}
