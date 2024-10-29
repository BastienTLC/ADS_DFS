    package com.grpc.client.fileupload.client.controller;

    import com.grpc.client.fileupload.client.model.User;
    import com.grpc.client.fileupload.client.repository.UserRepository;
    import com.grpc.client.fileupload.client.service.FileDownloadService;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestParam;
    import org.springframework.web.bind.annotation.RestController;

    @Slf4j
    @RestController
    public class FileDownloadController {

        private final FileDownloadService fileDownloadService;

        @Autowired
        private UserRepository userRepository;

        public FileDownloadController(FileDownloadService fileDownloadService) {
            this.fileDownloadService = fileDownloadService;
        }

        @PostMapping("/download")
        public String downloadFile(@RequestParam("file") String filename) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));

            // Check if the filename exists in the users list of files
            boolean fileExists = user.getFiles().stream()
                    .anyMatch(file -> file.getFileName().equals(filename));

            if (!fileExists) {
                return "Error: The specified file does not belong to the user.";
            }

            // If the file exists download
            return this.fileDownloadService.downloadFile(filename, username);
        }
    }
