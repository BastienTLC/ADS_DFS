// FileDownloadService.java
package com.grpc.client.fileupload.client.service;


import com.devProblems.FileDownloadRequest;
import com.devProblems.FileDownloadResponse;
import com.grpc.client.fileupload.client.utils.DiskFileStorage;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class FileDownloadService extends BaseFileService {

    // Have not added proper verification of metadata yet like we do when uploading file yet
    // Otherwise it seems to work
    public String downloadFile(String fileName) {
        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Metadata metadata = createMetadata(fileName);
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .build();

        DiskFileStorage diskFileStorage = new DiskFileStorage();

        client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .downloadFile(request, new StreamObserver<FileDownloadResponse>() {
                    @Override
                    public void onNext(FileDownloadResponse fileDownloadResponse) {
                        log.info(String.format("received %d length of data", fileDownloadResponse.getFile().getContent().size()));
                        try {
                            fileDownloadResponse.getFile().getContent().writeTo(diskFileStorage.getStream());
                        } catch (IOException e) {
                            onError(io.grpc.Status.INTERNAL
                                    .withDescription("cannot write data due to : " + e.getMessage())
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
                            // after receiving all data, writing it to file
                            diskFileStorage.write(fileName);
                            diskFileStorage.close();
                            response.append("File downloaded successfully: ").append(fileName);
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
