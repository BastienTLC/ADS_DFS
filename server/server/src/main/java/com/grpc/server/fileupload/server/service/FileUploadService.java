package com.grpc.server.fileupload.server.service;

import com.devProblems.*;
import com.google.protobuf.ByteString;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.shared.proto.Constants;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import java.io.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@GrpcService
public class FileUploadService extends FileUploadServiceGrpc.FileUploadServiceImplBase {

    // This method is invoked when uploadFile is called from the client side
    @Override
    public StreamObserver<FileUploadRequest> uploadFile(StreamObserver<FileUploadResponse> responseObserver) {
        FileMetadata fileMetadata = Constants.fileMetaContext.get(); // this is used at the end of the function to verify meta data
        DiskFileStorage diskFileStorage = new DiskFileStorage();
        return new StreamObserver<>() {
            @Override
            // this method will write the bytes to the byte array stream that we have created in the diskFileStorage
            public void onNext(FileUploadRequest fileUploadRequest) {
                // called so we can compare that received data matches the data sent
                log.info(String.format("received %d length of data", fileUploadRequest.getFile().getContent().size()));
                try {
                    fileUploadRequest.getFile().getContent()
                            .writeTo(diskFileStorage.getStream());
                } catch (IOException e) {
                    // this is invoking the clients on error method
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("cannot write data due to : " + e.getMessage())
                            .asRuntimeException());
                }
            }

            @Override
            // called when client sends error
            public void onError(Throwable throwable) {
                log.warn("{}", throwable.toString());
            }

            @Override
            // called when client has finished sending data
            public void onCompleted() {

                try {
                    int totalBytesReceived = diskFileStorage.getStream().size();
                    // the reason why we create the fileMetadata object was to validate here that
                    // it is the same that the server has received
                    if (totalBytesReceived == fileMetadata.getContentLength()) {
                        // if matches we write to the diskFileStorage
                        diskFileStorage.write(fileMetadata.getFileNameWithType());
                        diskFileStorage.close();
                    } else {
                        // notifying the client with error
                        responseObserver.onError(
                                io.grpc.Status.FAILED_PRECONDITION
                                        .withDescription(String.format("expected %d but received %d", fileMetadata.getContentLength(), totalBytesReceived))
                                        .asRuntimeException()
                        );
                        return;
                    }
                } catch (IOException e) {
                    // notifying the client with error
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("cannot save data due to : " + e.getMessage())
                            .asRuntimeException());
                    return;
                }

                // if there were no errors, we notify the client by calling the onNext method and sending
                // the FileUploadResponse
                responseObserver.onNext(
                        FileUploadResponse
                                .newBuilder()
                                .setFileName(fileMetadata.getFileNameWithType())
                                .setUploadStatus(UploadStatus.SUCCESS)
                                .build()
                );
                responseObserver.onCompleted();
            }
        };
    }

    // simple test method for testing various functionality
    @Override
    public void testMethod(FileDownloadRequest request, StreamObserver<FileTesting> responseObserver) {
        String filename = request.getFileName();

        FileTesting response = FileTesting.newBuilder()
                .setText("Filename received: " + request.getFileName())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void downloadFile(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String filename = request.getFileName();
        log.info("Received download request for filename: " + filename);

        String filePath = "output/" + filename;
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            log.error("File not found: " + filename);
            responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
            return;
        }

        byte[] fiveKB = new byte[5120];
        int length;

        try (InputStream inputStream = new FileInputStream(file)) {
            // reading bytes and sending them to the client
            while ((length = inputStream.read(fiveKB)) > 0) {
                log.info(String.format("Sending %d length of data", length));
                com.devProblems.File fileMessage = com.devProblems.File.newBuilder()
                        .setContent(ByteString.copyFrom(fiveKB, 0, length))
                        .build();

                FileDownloadResponse response = FileDownloadResponse.newBuilder()
                        .setFile(fileMessage)  // Use the File message containing the content
                        .build();

                responseObserver.onNext(response);
            }

            // response is completed once all data is sent
            responseObserver.onCompleted();

        } catch (IOException e) {
            log.error("Error reading file: " + filename, e);
            responseObserver.onError(io.grpc.Status.INTERNAL.withDescription("Error reading file").asRuntimeException());
        }
    }




}
