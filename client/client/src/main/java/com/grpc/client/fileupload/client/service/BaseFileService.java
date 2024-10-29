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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${defaultPort}")
    private String defaultPort;

    @Value("${defaultIp}")
    private String defaultIp;

    // This is the client stub for the FileUploadService used to upload and download files
    protected ChordGrpc.ChordStub client;
    // This is the blocking client stub for the FileOperationsService used to list files and get file metadata
    protected ChordGrpc.ChordBlockingStub blockingClient;

    // This method is used to create or changed channel to the server with the given IP and port
    protected void setBlockingClient(NodeInfo nodeInfo) {
        ManagedChannel channel = grpcChannelFactory.getChannel(nodeInfo.getIp(), Integer.parseInt(nodeInfo.getPort()));
        blockingClient = ChordGrpc.newBlockingStub(channel);
    }
    // same but for the client
    protected void setClient(NodeInfo nodeInfo) {
        ManagedChannel channel = grpcChannelFactory.getChannel(nodeInfo.getIp(), Integer.parseInt(nodeInfo.getPort()));
        client = ChordGrpc.newStub(channel);
    }

    // Set the client and blocking client channel to a random server
    protected void createChannelFromBootstrap() {
        NodeInfo nodeInfo = bootstrapService.getServer();
        setBlockingClient(nodeInfo);
        setClient(nodeInfo);
    }

    protected boolean bootsrapIsAvailable() {
        return bootstrapService.healthCheck();
    }


    // Set the client and blocking client channel to a server with the given IP and port
    protected void createChannel() {
        ManagedChannel channel = grpcChannelFactory.getChannel(defaultIp, Integer.parseInt(defaultPort));
        client = ChordGrpc.newStub(channel);
        System.out.println(defaultPort);
        blockingClient = ChordGrpc.newBlockingStub(channel);
    }

    protected Metadata createMetadata(String fileName, int fileSize, String author) {
        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .setContentLength(fileSize)
                        .setAuthor(author)
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
