package com.grpc.client.fileupload.client.service;

import com.devProblems.FileOperations;
import com.devProblems.FileOperationsServiceGrpc;
import com.shared.proto.Constants;
import com.devProblems.FileUploadServiceGrpc;
import com.devProblems.FileMetadata;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

@Service
public abstract class BaseFileService {

    // this annotation will search the key "file-upload" in the application.yml file, and get the server
    // information to this particular stub
    @GrpcClient(value = "file-upload")
    protected FileUploadServiceGrpc.FileUploadServiceStub client;

    @GrpcClient(value = "file-operations")
    protected FileOperationsServiceGrpc.FileOperationsServiceBlockingStub blockingClient;



    // MetadataUtils attaches the metadata to the stub
    protected Metadata createMetadata(String fileName, int fileSize) {
        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .setContentLength(fileSize)
                        .build()
                        .toByteArray());
        return metadata;
    }

    // createMetadata method for downloadFile
    protected Metadata createMetadata(String fileName) {
        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .build()
                        .toByteArray());
        return metadata;
    }
}
