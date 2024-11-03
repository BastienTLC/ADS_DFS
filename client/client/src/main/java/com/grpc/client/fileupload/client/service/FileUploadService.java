// FileUploadService.java
package com.grpc.client.fileupload.client.service;

import com.devProblems.Fileupload.*;
import com.google.protobuf.ByteString;
import com.grpc.client.fileupload.client.utils.CrypteUtil;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
// since we now have both an upload and download method
public class FileUploadService extends BaseFileService {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;



    public String uploadFile(final MultipartFile multipartFile, String username) {
        String fileName; // provided by client
        int fileSize;
        InputStream inputStream;
        byte[] fileData;
        fileName = multipartFile.getOriginalFilename();

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


        if (super.bootstrapIsAvailable()){
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
