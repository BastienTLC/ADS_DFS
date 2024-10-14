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
import org.springframework.web.client.RestTemplate;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import com.grpc.client.fileupload.client.utils.NodeInfo;

@Service
public abstract class BaseFileService {

    // this annotation will search the key "file-upload" in the application.yml file, and get the server
    // information to this particular stub
    @GrpcClient(value = "file-upload")
    protected FileUploadServiceGrpc.FileUploadServiceStub client;

    @GrpcClient(value = "file-operations")
    protected FileOperationsServiceGrpc.FileOperationsServiceBlockingStub blockingClient;

    // these are added to communicating with bootstrap service
    private final String bootstrapUrl = "http://127.0.0.1:8085";
    private final RestTemplate restTemplate = new RestTemplate();

    private NodeInfo getRandomServer() {
        String url = bootstrapUrl + "/getRandomServer";
        try {
            return restTemplate.getForObject(url, NodeInfo.class);
        } catch (Exception e) {
            System.err.println("Failed to get a random server from bootstrap: " + e.getMessage());
            return null;
        }
    }

    // This should be called upon initializing client.
    // Currently thinking that the server node will be chosen randomly for the client,
    // and all further requests will be with that node.
    // However, the distributed storing part will be managed after the receiving node has received the data,
    // and will be done based on the filename(?) where a distributed hashing table will be involved.
    protected void createChannelFromBootstrap() {
        NodeInfo randomServer = getRandomServer();
        if (randomServer == null) {
            throw new RuntimeException("Could not retrieve server information from the bootstrap service.");
        }

        String serverIp = randomServer.getIp();
        int serverPort = Integer.parseInt(randomServer.getPort());

        System.out.println("Successfully retrieved " + serverIp + ":" + serverPort);

//        return ManagedChannelBuilder.forAddress(serverIp, serverPort)
//                .usePlaintext()
//                .build();
    }



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
