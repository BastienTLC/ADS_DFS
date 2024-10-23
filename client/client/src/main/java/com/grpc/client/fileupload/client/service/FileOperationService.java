package com.grpc.client.fileupload.client.service;
import com.devProblems.Fileupload.*;
import com.grpc.client.fileupload.client.model.FileMetadataModel;
import com.google.protobuf.Empty;
import com.grpc.client.fileupload.client.utils.NodeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FileOperationService extends BaseFileService {


    private final BootstrapService bootstrapService;

    public FileOperationService(BootstrapService bootstrapService) {
        super();
        this.bootstrapService = bootstrapService;
    }

    public List<FileMetadataModel> listFiles() {
        createRandomChannelFromBootstrap();
        ListFilesResponse response = blockingClient.listFiles(Empty.newBuilder().build());
        List<FileMetadataModel> files = new ArrayList<>();
        response.getFilesList().forEach(file -> {
            files.add(new FileMetadataModel(file.getFileNameWithType(), file.getContentLength()));
        });
        return files;
    }

    public FileMetadata getFileMetadata(FileDownloadRequest request) {
        try {
            createDeterministicChannelFromBootstrap(request.getFileName());
            return blockingClient.getFileMetadata(request);
        } catch (Exception e) {
            log.error("Error occurred while getting file metadata: {}", e.toString());
            return null;
        }
    }

    public String health() {
        createRandomChannelFromBootstrap();
        try {
            return blockingClient.healthCheck(Empty.newBuilder().build()).getStatus();
        } catch (Exception e) {
            log.error("Error occurred while checking health: {}", e.toString());
            return null;
        }
    }

    public List<String> healths() {
        List<NodeInfo> nodeInfos = bootstrapService.getAllServers();
        List<String> statuses = new ArrayList<>();
        for (NodeInfo nodeInfo : nodeInfos) {
            createChannel(nodeInfo.getIp(), nodeInfo.getPort());
            try {
                statuses.add(blockingClient.healthCheck(Empty.newBuilder().build()).getStatus());
            } catch (Exception e) {
                statuses.add("Error occurred while checking health: " + e.toString());
            }
        }
        return statuses;
    }
}

