package com.grpc.server.fileupload.server.impl;

import com.devProblems.FileUploadServiceGrpc;
import com.grpc.server.fileupload.server.service.FileDownloadService;
import com.grpc.server.fileupload.server.service.FileManagementService;
import com.grpc.server.fileupload.server.service.FileTestService;
import com.grpc.server.fileupload.server.service.FileUploadService;
import de.uniba.wiai.lspi.chord.service.Chord;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import com.devProblems.Fileupload.*;

@Slf4j
@GrpcService
public class FileUploadServiceImpl extends FileUploadServiceGrpc.FileUploadServiceImplBase {

    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;
    private final FileTestService fileTestService;


    public FileUploadServiceImpl(FileUploadService fileUploadService, FileDownloadService fileDownloadService, FileManagementService fileManagementService, FileTestService fileTestService, Chord chord) {
        this.fileUploadService = fileUploadService;
        this.fileDownloadService = fileDownloadService;
        this.fileTestService = fileTestService;
    }

    // This method is invoked when uploadFile is called from the client side
    @Override
    public StreamObserver<FileUploadRequest> uploadFile(StreamObserver<FileUploadResponse> responseObserver) {
        return fileUploadService.uploadFile(responseObserver);
    }

    // Called when client calls the test method
    @Override
    public void testMethod(FileDownloadRequest request, StreamObserver<FileTesting> responseObserver) {
        fileTestService.testMethod(request, responseObserver);
    }

    // This method is invoked when downloadFile is called from the client side
    @Override
    public void downloadFile(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        fileDownloadService.downloadFile(request, responseObserver);
    }

}
