package com.grpc.server.fileupload.server.impl;

import com.devProblems.*;
import com.google.protobuf.Empty;
import com.grpc.server.fileupload.server.service.FileManagementService;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Autowired;


@Slf4j
@GrpcService
public class FileOperationsServiceImpl extends FileOperationsServiceGrpc.FileOperationsServiceImplBase {

    private final FileManagementService fileManagementService;

    @Autowired
    public FileOperationsServiceImpl(FileManagementService fileManagementService) {
        this.fileManagementService = fileManagementService;
    }

    // Implementing listFiles RPC
    @Override
    public void listFiles(Empty request, StreamObserver<ListFilesResponse> responseObserver) {
        fileManagementService.listFiles(request, responseObserver);
    }

    // Implementing getFileMetadata RPC
    @Override
    public void getFileMetadata(FileDownloadRequest request, StreamObserver<FileMetadata> responseObserver) {
        fileManagementService.getFileMetadata(request, responseObserver);
    }

    // Implementing healthCheck RPC
    @Override
    public void healthCheck(Empty request, StreamObserver<HealthCheckResponse> responseObserver) {
        fileManagementService.healthCheck(request, responseObserver);
    }
}
