// FileUploadHandler.java
package com.grpc.server.fileupload.server.service;

import com.devProblems.*;
import com.google.protobuf.Empty;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.shared.proto.Constants;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Service class for handling file uploads.
 */
@Slf4j
@Service
public class FileUploadService {

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
}
