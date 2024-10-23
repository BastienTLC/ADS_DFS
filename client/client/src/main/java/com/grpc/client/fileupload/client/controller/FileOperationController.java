// FileManagementController.java
package com.grpc.client.fileupload.client.controller;

import com.devProblems.Fileupload.FileDownloadRequest;
import com.grpc.client.fileupload.client.model.FileMetadataModel;
import com.grpc.client.fileupload.client.service.FileOperationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/files")
public class FileOperationController {

    private final FileOperationService fileOperationService;

    public FileOperationController(FileOperationService fileOperationService) {
        this.fileOperationService = fileOperationService;
    }

    //endpoint to list all files
    @GetMapping("/list")
    public List<FileMetadataModel> listFiles() {
        return this.fileOperationService.listFiles();
    }

    @GetMapping("/health")
    public String health() {
        return this.fileOperationService.health();
    }

    @GetMapping("/healths")
    public List<String> healths() {
        return this.fileOperationService.healths();
    }

    //endpoint to get metadata of a specific file
    @GetMapping("/{fileName}/metadata")
    public FileMetadataModel getFileMetadata(@PathVariable("fileName") String fileName) {
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .build();
        return new FileMetadataModel(this.fileOperationService.getFileMetadata(request));
    }
}
