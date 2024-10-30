package com.grpc.client.fileupload.client.controller;

import com.grpc.client.fileupload.client.service.FileTestService;
import com.grpc.client.fileupload.client.utils.CsvUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
public class FileTestController {

    private final FileTestService fileTestService;

    public FileTestController(FileTestService fileTestService) {
        this.fileTestService = fileTestService;
    }

    @PostMapping("/upload-download")
    public Map<String, Object> testUploadDownload(@RequestParam("number") int numberOfFiles, @RequestParam("size") int fileSize) {
        return this.fileTestService.testFileUploadAndDownload(numberOfFiles, fileSize);
    }

    /**
     * Endpoint for testing with file size increment
     */
    @PostMapping("/size-increment")
    public List<Map<String, Object>> testWithFileSizeIncrement(
            @RequestParam("startSize") int startSize,
            @RequestParam("endSize") int endSize,
            @RequestParam("increment") int increment,
            @RequestParam("nbFile") int nbFile,
            @RequestParam("nt") int nt,
            @RequestParam("nbNode") int nbNode) throws IOException {

        List<Map<String, Object>> allMetrics = fileTestService.testWithFileSizeIncrement(startSize, endSize, increment, nbFile, nt, nbNode);
        CsvUtil.writeResultsToCsv(allMetrics);

        return allMetrics;
    }

    /**
     * Endpoint for testing with file count increment
     */
    @PostMapping("/count-increment")
    public List<Map<String, Object>> testWithFileCountIncrement(
            @RequestParam("startCount") int startCount,
            @RequestParam("endCount") int endCount,
            @RequestParam("increment") int increment,
            @RequestParam("fileSize") int fileSize,
            @RequestParam("nt") int nt,
            @RequestParam("nbNode") int nbNode) throws IOException {

        List<Map<String, Object>> allMetrics = fileTestService.testWithFileCountIncrement(startCount, endCount, increment, fileSize, nt, nbNode);
        CsvUtil.writeResultsToCsv(allMetrics);
        return allMetrics;
    }
}
