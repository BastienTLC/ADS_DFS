package com.grpc.client.fileupload.client.service;

import com.devProblems.ChordGrpc;
import com.devProblems.Fileupload.FileMetadata;
import com.devProblems.FileOperationsServiceGrpc;
import com.grpc.client.fileupload.client.Factory.GrpcChannelFactory;
import com.shared.proto.Constants;
import com.devProblems.FileUploadServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.grpc.client.fileupload.client.utils.NodeInfo;

public abstract class BaseFileService {

    @Autowired
    private GrpcChannelFactory grpcChannelFactory;

    @Autowired
    private BootstrapService bootstrapService;

    // This is the client stub for the FileUploadService used to upload and download files
    protected ChordGrpc.ChordStub client;
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
        client = ChordGrpc.newStub(channel);
    }

    // Set the client and blocking client channel to a random server
    protected void createRandomChannelFromBootstrap() {
        NodeInfo randomServer = bootstrapService.getRandomServer();
        if (randomServer == null) {
            throw new RuntimeException("Could not retrieve server information from the bootstrap service.");
        }

        this.setBlockingClient(randomServer);
        this.setClient(randomServer);
    }

    // Set the client and blocking client channel to a server with the given hash
    protected void createDeterministicChannelFromBootstrap(String fileName) {
        NodeInfo nodeInfo = bootstrapService.getNodeByHash(fileName);
        if (nodeInfo == null) {
            throw new RuntimeException("Could not retrieve server information from the bootstrap service.");
        }

        this.setBlockingClient(nodeInfo);
        this.setClient(nodeInfo);
    }

    // Set the client and blocking client channel to a server with the given IP and port
    protected void createChannel(String ip, String port) {
        ManagedChannel channel = grpcChannelFactory.getChannel(ip, Integer.parseInt(port));
        client = ChordGrpc.newStub(channel);
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
