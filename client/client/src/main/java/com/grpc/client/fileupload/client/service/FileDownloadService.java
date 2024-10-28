package com.grpc.client.fileupload.client.service;

import com.devProblems.Fileupload.FileDownloadRequest;
import com.devProblems.Fileupload.FileDownloadResponse;
import com.grpc.client.fileupload.client.utils.CrypteUtil;
import com.grpc.client.fileupload.client.utils.DiskFileStorage;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
// Have not added proper verification of metadata yet like we do when uploading file yet
// Otherwise it seems to work
public class FileDownloadService extends BaseFileService {

    public String downloadFile(String fileName) {
        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Metadata metadata = createMetadata(fileName);
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .setRequester(username)
                .build();

        DiskFileStorage diskFileStorage = new DiskFileStorage();

        // Setting up the channel
        // Later this will be changed where the user can specify the IP:port of any node in the network,
        // alternatively a bootstrap service can be used to get random ip:port
        if (super.bootsrapIsAvailable()){
            createChannelFromBootstrap();
        }else {
            createChannel();
        }

        // Load the AES decryption key
        SecretKey secretKey = CrypteUtil.LoadSecretKey();
        if (secretKey == null) {
            return "Failed to load decryption key!";
        }

        client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .retrieveFileFromChord(request, new StreamObserver<FileDownloadResponse>() {
                    @Override
                    public void onNext(FileDownloadResponse fileDownloadResponse) {
                        log.info(String.format("Received %d length of data", fileDownloadResponse.getFile().getContent().size()));
                        try {
                            // Decrypt the data received from the server
                            byte[] encryptedData = fileDownloadResponse.getFile().getContent().toByteArray();
                            byte[] decryptedData = CrypteUtil.DecryptData(encryptedData, secretKey);
                            if (decryptedData == null) {
                                throw new GeneralSecurityException("Failed to decrypt the data.");
                            }

                            // Write the decrypted data to the disk
                            diskFileStorage.getStream().write(decryptedData);
                        } catch (IOException | GeneralSecurityException e) {
                            onError(io.grpc.Status.INTERNAL
                                    .withDescription("Cannot write data due to: " + e.getMessage())
                                    .asRuntimeException());
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error occurred while downloading file: {}", throwable.toString());
                        response.append("Error occurred while downloading the file.");
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        try {
                            // After receiving all data, writing it to the file
                            diskFileStorage.write(fileName);
                            diskFileStorage.close();
                            response.append("File downloaded and decrypted successfully: ").append(fileName);
                        } catch (IOException e) {
                            log.error("Error occurred while saving the file: {}", e.getMessage());
                            response.append("Error occurred while saving the file.");
                        } finally {
                            countDownLatch.countDown();
                        }
                    }
                });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("Download interrupted: {}", e.getMessage());
            return "Failed to get the response from the server.";
        }

        return response.toString();
    }
}
