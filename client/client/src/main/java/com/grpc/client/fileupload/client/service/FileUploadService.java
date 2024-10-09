package com.grpc.client.fileupload.client.service;

import com.devProblems.*;
import com.google.protobuf.ByteString;
import com.shared.proto.Constants;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.grpc.client.fileupload.client.utils.DiskFileStorage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;


@Slf4j
@Service
// perhaps we should rename this to FileManagementService later,
// since we now have both an upload and download method
public class FileUploadService {
    private final FileUploadServiceGrpc.FileUploadServiceStub client;

    // this annotation will search the key "file-upload" in the application.yml file, and get the server
    // information to this particular stub
    public FileUploadService(@GrpcClient(value = "file-upload") FileUploadServiceGrpc.FileUploadServiceStub client) {
        this.client = client;
    }

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

        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .setContentLength(fileSize)
                        .build()
                        .toByteArray());

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

    // this function simply sends a string, receives a string back and returns it to Postman
    // we don't do anything with the metadata that is sent alongside the main request here,
    // which otherwise is used to verify that data was sent correctly
    public String testMethodCall(String fileName) {
        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .build()
                        .toByteArray());

        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(fileName)
                .build();

        client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
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
        }

        return response.toString();
    }


    // Have not added proper verification of metadata yet like we do when uploading file yet
    // Otherwise it seems to work
    public String downloadFile(String fileName) {
        StringBuilder response = new StringBuilder();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .build()
                        .toByteArray());

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
