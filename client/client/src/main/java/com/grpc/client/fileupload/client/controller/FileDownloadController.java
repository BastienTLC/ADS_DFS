    package com.grpc.client.fileupload.client.controller;

    import com.grpc.client.fileupload.client.model.User;
    import com.grpc.client.fileupload.client.repository.UserRepository;
    import com.grpc.client.fileupload.client.service.FileDownloadService;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.http.*;
    import org.springframework.security.core.context.SecurityContextHolder;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestParam;
    import org.springframework.web.bind.annotation.RestController;

    import java.io.ByteArrayOutputStream;

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
        public ResponseEntity<byte[]> downloadFile(@RequestParam("file") String filename) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));

            boolean fileExists = user.getFiles().stream()
                    .anyMatch(file -> file.getFileName().equals(filename));

            if (!fileExists) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Error: The specified file does not belong to the user.".getBytes());
            }

            ByteArrayOutputStream outputStream = fileDownloadService.downloadFile(filename, username);

            if (outputStream == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred while downloading the file.".getBytes());
            }

            byte[] fileData = outputStream.toByteArray();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileData);
        }
    }
