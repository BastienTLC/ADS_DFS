// FileManagementController.java
package com.grpc.client.fileupload.client.controller;

import com.devProblems.Fileupload.FileDownloadRequest;
import com.grpc.client.fileupload.client.model.FileMetadataModel;
import com.grpc.client.fileupload.client.service.FileOperationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @DeleteMapping("/{filename}/delete")
    public String deleteFile(@PathVariable String filename) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return this.fileOperationService.deleteFile(filename, username);
    }

}
