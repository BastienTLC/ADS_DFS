package com.grpc.client.fileupload.client.controller;


import com.grpc.client.fileupload.client.service.FileTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/test")
public class FileTestController {

    private final FileTestService fileTestService;

    public FileTestController(FileTestService fileTestService) {
        this.fileTestService = fileTestService;
    }

    @PostMapping("/upload-download")
    public String testUploadDownload(@RequestParam("number") int numberOfFiles, @RequestParam("size") int fileSize, @RequestParam("nt") int numberOfTry) {
        return this.fileTestService.testFileUploadAndDownload(numberOfFiles, fileSize, numberOfTry);
    }
}
