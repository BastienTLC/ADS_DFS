
package com.grpc.server.fileupload.server.service;

import com.shared.proto.Constants;
import com.devProblems.FileUploadServiceGrpc;
import com.devProblems.FileMetadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.Metadata;
import org.springframework.stereotype.Service;

@Service
public abstract class BaseFileService {
    protected FileUploadServiceGrpc.FileUploadServiceImplBase fileUploadServiceGrpc;


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
