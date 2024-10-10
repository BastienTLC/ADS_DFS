// FileDownloadHandler.java
package com.grpc.server.fileupload.server.service;

import com.devProblems.FileDownloadRequest;
import com.devProblems.FileDownloadResponse;
import com.devProblems.File;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


@Slf4j
@Service
public class FileDownloadService {


    public void downloadFile(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String filename = request.getFileName();
        log.info("Received download request for filename: " + filename);

        String filePath = "output/" + filename;
        java.io.File file = new java.io.File(filePath);

        if (!file.exists() || !file.isFile()) {
            log.error("File not found: " + filename);
            responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
            return;
        }

        byte[] fiveKB = new byte[5120];
        int length;

        try (InputStream inputStream = new FileInputStream(file)) {
            // Reading bytes and sending them to the client
            while ((length = inputStream.read(fiveKB)) > 0) {
                log.info(String.format("Sending %d length of data", length));
                File fileMessage = File.newBuilder()
                        .setContent(ByteString.copyFrom(fiveKB, 0, length))
                        .build();

                FileDownloadResponse response = FileDownloadResponse.newBuilder()
                        .setFile(fileMessage)  // Use the File message containing the content
                        .build();

                responseObserver.onNext(response);
            }

            // Response is completed once all data is sent
            responseObserver.onCompleted();

        } catch (IOException e) {
            log.error("Error reading file: " + filename, e);
            responseObserver.onError(Status.INTERNAL.withDescription("Error reading file").asRuntimeException());
        }
    }
}
