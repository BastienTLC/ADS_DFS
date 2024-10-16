// FileUploadService.java
package com.grpc.client.fileupload.client.service;

import com.devProblems.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.grpc.client.fileupload.client.utils.NodeInfo;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
// since we now have both an upload and download method
public class FileUploadService extends BaseFileService {

    public String uploadFile(final MultipartFile multipartFile) {
        String fileName; // provided by client
        int fileSize;
        InputStream inputStream;
        fileName = multipartFile.getOriginalFilename();

        System.out.println("test!");

        try {
            fileSize = multipartFile.getBytes().length;
            inputStream = multipartFile.getInputStream();
        } catch (IOException e) {
            return "Unable to read the file!";
        }

        // So far we have only extracted information from the multipart file that we have gotten from Postman.
        // nothing related to gRPC yet.

        StringBuilder response = new StringBuilder();

        CountDownLatch countDownLatch = new CountDownLatch(1);

        Metadata metadata = createMetadata(fileName, fileSize);

        // setting up the channel
        super.createRandomChannelFromBootstrap();

        // using fileUploadRequestStreamObserver we will stream the file content to the server
        StreamObserver<FileUploadRequest> fileUploadRequestStreamObserver = this.client
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .uploadFile(
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
                                countDownLatch.countDown();
                            }
                        });

        byte[] fiveKB = new byte[5120];

        int length;

        // reading bytes and adding it to the byte array
        try {
            while ((length = inputStream.read(fiveKB)) > 0) {
                log.info(String.format("sending %d length of data", length));
                var request = FileUploadRequest
                        .newBuilder()
                        .setFile(File.newBuilder().setContent(ByteString.copyFrom(fiveKB, 0, length)))
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
