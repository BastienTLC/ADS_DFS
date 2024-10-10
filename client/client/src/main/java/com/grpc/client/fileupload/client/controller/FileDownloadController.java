// FileDownloadController.java
package com.grpc.client.fileupload.client.controller;

import com.grpc.client.fileupload.client.service.FileDownloadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class FileDownloadController {

    private final FileDownloadService fileDownloadService;

    public FileDownloadController(FileDownloadService fileDownloadService) {
        this.fileDownloadService = fileDownloadService;
    }

    // pretty sure we can use POST for all these methods
    @PostMapping("/download")
    // change to return byte[] downloadFile later
    public String downloadFile(@RequestParam("file") String filename)  {
        return this.fileDownloadService.downloadFile(filename);
    }
}
