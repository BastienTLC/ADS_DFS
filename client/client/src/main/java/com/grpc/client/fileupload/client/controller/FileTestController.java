package com.grpc.client.fileupload.client.controller;


import com.grpc.client.fileupload.client.service.FileTestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test")
public class FileTestController {

    private final FileTestService fileTestService;

    public FileTestController(FileTestService fileTestService) {
        this.fileTestService = fileTestService;
    }

    @PostMapping("/string")
    public String testSendFile(@RequestParam("file") String filename)  {
        return this.fileTestService.testMethodCall(filename);
    }
}
