package com.grpc.client.fileupload.client.service;

import com.devProblems.Fileupload.FileDownloadRequest;
import com.devProblems.Fileupload.FileTesting;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;


@Slf4j
@Service
public class FileTestService extends BaseFileService {

    // this function simply sends a string, receives a string back and returns it to Postman
    // we don't do anything with the metadata that is sent alongside the main request here,
    // which otherwise is used to verify that data was sent correctly
    public String testMethodCall(String fileName) {
        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        createRandomChannelFromBootstrap(); // not fully implemented yet

        Metadata metadata = createMetadata(fileName);
        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .build();

        /*client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .testMethod(request, new StreamObserver<FileTesting>() {
                    @Override
                    public void onNext(FileTesting fileTesting) {
                        response.append(fileTesting.getText());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throwable.printStackTrace();
                        response.append("Error occurred while calling testMethod.");
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
            e.printStackTrace();
            return "Failed to get the response from the server.";
        }*/

        return response.toString();
    }
}
