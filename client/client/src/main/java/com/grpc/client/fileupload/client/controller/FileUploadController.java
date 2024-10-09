package com.grpc.client.fileupload.client.controller;

import com.grpc.client.fileupload.client.service.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    // Below are the various endpoints which we call from Postman
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile multipartFile)  {
        return this.fileUploadService.uploadFile(multipartFile);
    }

    // pretty sure we can use POST for all these methods
    @PostMapping("/download")
    // change to return byte[] downloadFile later
    public String downloadFile(@RequestParam("file") String filename)  {
        return this.fileUploadService.downloadFile(filename);
    }

    @PostMapping("/test")
    // change to return byte[] downloadFile later
    public String testSendFile(@RequestParam("file") String filename)  {
        return this.fileUploadService.testMethodCall(filename);
    }

}
