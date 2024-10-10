package com.grpc.client.fileupload.client.service;
import com.devProblems.*;
import com.shared.proto.*;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
@Service
public class FileManagementService extends BaseFileService {


    public List<FileMetadata> listFiles() {
        ListFilesResponse response = blockingClient.listFiles(Empty.newBuilder().build());
        return response.getFilesList();
    }

    /**
     * Get the metadata of a file by calling the gRPC method getFileMetadata.
     *
     * @param request the request containing the file name.
     * @return the metadata of the file.
     */
    public FileMetadata getFileMetadata(FileDownloadRequest request) {
        try {
            return blockingClient.getFileMetadata(request);
        } catch (Exception e) {
            log.error("Error occurred while getting file metadata: {}", e.toString());
            return null;
        }
    }

    public String health() {
        try {
            return blockingClient.healthCheck(Empty.newBuilder().build()).getStatus();
        } catch (Exception e) {
            log.error("Error occurred while checking health: {}", e.toString());
            return null;
        }
    }
}

