package com.grpc.server.fileupload.server.client;

import com.devProblems.FileMetadata;
import com.devProblems.FileOperationsServiceGrpc;
import com.devProblems.FileUploadServiceGrpc;
import com.grpc.client.fileupload.client.Factory.GrpcChannelFactory;
import com.grpc.client.fileupload.client.utils.NodeInfo;
import com.shared.proto.Constants;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseFileService {

    @Autowired
    private GrpcChannelFactory grpcChannelFactory;

    @Autowired
    private BootstrapService bootstrapService;

    // This is the client stub for the FileUploadService used to upload and download files
    protected FileUploadServiceGrpc.FileUploadServiceStub client;
    // This is the blocking client stub for the FileOperationsService used to list files and get file metadata
    protected FileOperationsServiceGrpc.FileOperationsServiceBlockingStub blockingClient;

    // This method is used to create or changed channel to the server with the given IP and port
    protected void setBlockingClient(NodeInfo nodeInfo) {
        ManagedChannel channel = grpcChannelFactory.getChannel(nodeInfo.getIp(), Integer.parseInt(nodeInfo.getPort()));
        blockingClient = FileOperationsServiceGrpc.newBlockingStub(channel);
    }
    // same but for the client
    protected void setClient(NodeInfo nodeInfo) {
        ManagedChannel channel = grpcChannelFactory.getChannel(nodeInfo.getIp(), Integer.parseInt(nodeInfo.getPort()));
        client = FileUploadServiceGrpc.newStub(channel);
    }

    // Set the client and blocking client channel to a server with the given IP and port
    protected void createChannel(String ip, String port) {
        ManagedChannel channel = grpcChannelFactory.getChannel(ip, Integer.parseInt(port));
        client = FileUploadServiceGrpc.newStub(channel);
        blockingClient = FileOperationsServiceGrpc.newBlockingStub(channel);
    }

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
