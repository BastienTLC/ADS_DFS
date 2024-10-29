package com.grpc.client.fileupload.client.controller;

import com.grpc.client.fileupload.client.model.User;
import com.grpc.client.fileupload.client.repository.UserRepository;
import com.grpc.client.fileupload.client.service.FileUploadService;
import com.grpc.client.fileupload.client.model.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;

@Slf4j
@RestController
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final UserRepository userRepository;

    @Autowired
    public FileUploadController(FileUploadService fileUploadService, UserRepository userRepository) {
        this.fileUploadService = fileUploadService;
        this.userRepository = userRepository;
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile multipartFile) {
        // Get the authenticated user's username
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));

        String fileName = multipartFile.getOriginalFilename();

        // Use the service to upload the file
        String uploadStatus = fileUploadService.uploadFile(multipartFile, username);

        if (!uploadStatus.contains("SUCCESS")) {
            return "File upload failed!";
        }

        // If upload is successful, save the file info in the database
        try {
            File file = new File();
            file.setFileSize((long) multipartFile.getSize());
            file.setFileName(fileName);
            file.setUser(user);

            if (user.getFiles() == null) {
                user.setFiles(new ArrayList<>());
            }
            user.getFiles().add(file);
            userRepository.save(user);
            log.info("File successfully saved to the database and associated with user");
            return "File upload and save successful!";
        } catch (Exception e) {
            log.error("Failed to save file info to the database", e);
            return "File uploaded but failed to save to the database.";
        }
    }
}
