// FileDownloadHandler.java
package com.grpc.server.fileupload.server.service;

import com.devProblems.FileDownloadRequest;
import com.devProblems.FileDownloadResponse;
import com.devProblems.File;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.grpc.server.fileupload.server.utils.Hash;
import de.uniba.wiai.lspi.chord.service.Chord;
import de.uniba.wiai.lspi.chord.service.Key;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Set;


@Slf4j
@Service
public class FileDownloadService {
    private final Chord chord;

    @Autowired
    public FileDownloadService(Chord chord) {
        this.chord = chord;
    }


    public void downloadFile(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String filename = request.getFileName();
        log.info("Received download request for filename: " + filename);

        try {
            // Generate the key for the filename
            Key key = Hash.hash(filename);

            // Retrieve the data from the Chord network
            Set<Serializable> dataSet = chord.retrieve(key);

            if (dataSet.isEmpty()) {
                log.error("File not found on the Chord network: " + filename);
                responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
                return;
            }
            log.info("File found on the Chord network: " + filename);
            // Get the file data
            byte[] fileData = (byte[]) dataSet.iterator().next();

            // Send the data to the client
            ByteString content = ByteString.copyFrom(fileData);
            File fileMessage = File.newBuilder()
                    .setContent(content)
                    .build();

            FileDownloadResponse response = FileDownloadResponse.newBuilder()
                    .setFile(fileMessage)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error retrieving file: " + filename, e);
            responseObserver.onError(Status.INTERNAL.withDescription("Error retrieving file: " + e.getMessage()).asRuntimeException());
        }
    }
}
