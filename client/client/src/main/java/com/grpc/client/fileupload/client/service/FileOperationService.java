package com.grpc.client.fileupload.client.service;
import com.devProblems.Fileupload.*;
import com.devProblems.ChordGrpc.*;
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


    public String deleteFile(String filename, String username) {
        if (super.bootsrapIsAvailable()){
            createChannelFromBootstrap();
        }else {
            createChannel();
        }
        try {
            blockingClient.deleteFileFromChord(FileDownloadRequest.newBuilder().setFileName(filename).setRequester(username).build());
            return "File deleted successfully";
        } catch (Exception e) {
            log.error("Error occurred while deleting file: {}", e.toString());
            return null;
        }
    }
}

