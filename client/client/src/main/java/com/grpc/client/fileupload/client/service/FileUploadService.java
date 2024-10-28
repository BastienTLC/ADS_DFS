// FileUploadService.java
package com.grpc.client.fileupload.client.service;

import com.devProblems.Fileupload.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.grpc.client.fileupload.client.model.User;
import com.grpc.client.fileupload.client.repository.UserRepository;
import com.grpc.client.fileupload.client.utils.CrypteUtil;
import com.grpc.client.fileupload.client.utils.NodeInfo;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
// since we now have both an upload and download method
public class FileUploadService extends BaseFileService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;



    public String uploadFile(final MultipartFile multipartFile) {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));



        String fileName; // provided by client
        int fileSize;
        InputStream inputStream;
        byte[] fileData;
        fileName = multipartFile.getOriginalFilename();

        System.out.println("test!");

        try {;
            fileData = multipartFile.getBytes();
            inputStream = multipartFile.getInputStream();
        } catch (IOException e) {
            return "Unable to read the file!";
        }

        // So far we have only extracted information from the multipart file that we have gotten from Postman.
        // nothing related to gRPC yet.

        StringBuilder response = new StringBuilder();

        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Load the AES encryption key
        SecretKey secretKey = CrypteUtil.LoadSecretKey();
        if (secretKey == null) {
            return "Failed to load encryption key!";
        }

        // Encrypt the entire file data
        byte[] encryptedData = CrypteUtil.EncryptData(fileData, secretKey);
        if (encryptedData == null) {
            return "Failed to encrypt the file!";
        }


        // Create metadata with the size of the encrypted data
        Metadata metadata = createMetadata(fileName, encryptedData.length, username);


        if (super.bootsrapIsAvailable()){
            createChannelFromBootstrap();
        }else {
            createChannel();
        }



        // using fileUploadRequestStreamObserver we will stream the file content to the server
        StreamObserver<FileUploadRequest> fileUploadRequestStreamObserver = this.client
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .storeFileInChord(
                        new StreamObserver<>() {
                            @Override
                            public void onNext(FileUploadResponse fileUploadResponse) {
                                // called when server sends the response
                                response.append(fileUploadResponse.getUploadStatus());
                            }

                            @Override
                            public void onError(Throwable throwable) {
                                // called when server sends an error
                                response.append(UploadStatus.FAILED);
                                throwable.printStackTrace();
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onCompleted() {
                                // called when server finished serving the request
                                try {
                                    com.grpc.client.fileupload.client.model.File file = new com.grpc.client.fileupload.client.model.File();
                                    file.setFileSize((long) encryptedData.length);
                                    file.setFileName(fileName);
                                    file.setUser(user);

                                    // Ensure the user's file list is initialized
                                    if (user.getFiles() == null) {
                                        user.setFiles(new ArrayList<>());
                                    }

                                    // Add the file to the user's list of files
                                    user.getFiles().add(file);

                                    // Save the user, which will also save the file due to CascadeType.ALL
                                    userRepository.save(user);

                                    response.append(UploadStatus.SUCCESS);
                                    log.info("File successfully uploaded and associated with user");
                                } catch (Exception e) {
                                    log.error("Failed to save file to the database", e);
                                    response.append("Failed to save file to the database.");
                                } finally {
                                    countDownLatch.countDown();
                                }
                            }
                        });

        int offset = 0;
        int chunkSize = 5120; // 5KB
        // reading bytes and adding it to the byte array
        try {
            while (offset < encryptedData.length) {
                int end = Math.min(offset + chunkSize, encryptedData.length);
                byte[] chunk = new byte[end - offset];
                System.arraycopy(encryptedData, offset, chunk, 0, end - offset);
                offset = end;
                log.info(String.format("Sending chunk of size %d", chunk.length));


                var request = FileUploadRequest
                        .newBuilder()
                        .setFile(File.newBuilder().setContent(ByteString.copyFrom(chunk)))
                        .build();
                // sending the request that contains the chunked data of file
                fileUploadRequestStreamObserver.onNext(request);
            }
            inputStream.close();
            fileUploadRequestStreamObserver.onCompleted();
            // waiting until countDownLatch.countDown(); is called
            countDownLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
            response.append(UploadStatus.FAILED);
        }
        return response.toString();
    }
}
