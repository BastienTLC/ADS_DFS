package com.grpc.client.fileupload.client.service;

import com.devProblems.Fileupload.FileDownloadRequest;
import com.devProblems.Fileupload.FileDownloadResponse;
import com.grpc.client.fileupload.client.utils.CrypteUtil;
import com.grpc.client.fileupload.client.utils.DiskFileStorage;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
// Have not added proper verification of metadata yet like we do when uploading file yet
// Otherwise it seems to work
public class FileDownloadService extends BaseFileService {

    public ByteArrayOutputStream downloadFile(String fileName, String username) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Metadata metadata = createMetadata(fileName);
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .setRequester(username)
                .build();

        DiskFileStorage diskFileStorage = new DiskFileStorage();

        if (super.bootstrapIsAvailable()) {
            createChannelFromBootstrap();
        } else {
            createChannel();
        }

        SecretKey secretKey = CrypteUtil.LoadSecretKey();
        if (secretKey == null) {
            log.error("Failed to load decryption key!");
            return null;
        }

        client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .retrieveFileFromChord(request, new StreamObserver<FileDownloadResponse>() {
                    @Override
                    public void onNext(FileDownloadResponse fileDownloadResponse) {
                        try {
                            byte[] encryptedData = fileDownloadResponse.getFile().getContent().toByteArray();
                            byte[] decryptedData = CrypteUtil.DecryptData(encryptedData, secretKey);
                            if (decryptedData == null) {
                                throw new GeneralSecurityException("Failed to decrypt the data.");
                            }
                            outputStream.write(decryptedData);
                        } catch (IOException | GeneralSecurityException e) {
                            onError(io.grpc.Status.INTERNAL
                                    .withDescription("Cannot write data due to: " + e.getMessage())
                                    .asRuntimeException());
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Error occurred while downloading file: {}", throwable.toString());
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        countDownLatch.countDown();
                    }
                });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("Download interrupted: {}", e.getMessage());
        }

        return outputStream;
    }
}
