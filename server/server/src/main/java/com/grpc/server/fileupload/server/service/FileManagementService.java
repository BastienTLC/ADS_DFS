// FileManagementHandler.java
package com.grpc.server.fileupload.server.service;

import com.devProblems.FileDownloadRequest;
import com.devProblems.FileMetadata;
import com.devProblems.HealthCheckResponse;
import com.devProblems.ListFilesResponse;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class FileManagementService {


    public void listFiles(Empty request, StreamObserver<ListFilesResponse> responseObserver) {
        List<FileMetadata> files = new ArrayList<>();
        String directoryPath = "output/";
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] fileList = directory.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (file.isFile()) {
                        FileMetadata metadata = FileMetadata.newBuilder()
                                .setFileNameWithType(file.getName())
                                .setContentLength((int) file.length())
                                .build();
                        files.add(metadata);
                    }
                }
            }
        }

        ListFilesResponse response = ListFilesResponse.newBuilder()
                .addAllFiles(files)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    public void getFileMetadata(FileDownloadRequest request, StreamObserver<FileMetadata> responseObserver) {
        String filename = request.getFileName();
        String filePath = "output/" + filename;
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            log.error("File not found: " + filename);
            responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
            return;
        }

        FileMetadata metadata = FileMetadata.newBuilder()
                .setFileNameWithType(file.getName())
                .setContentLength((int) file.length())
                .build();

        responseObserver.onNext(metadata);
        responseObserver.onCompleted();
    }

    public void healthCheck(Empty request, StreamObserver<HealthCheckResponse> responseObserver) {
        responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus("Server is healthy").build());
        responseObserver.onCompleted();
    }
}

