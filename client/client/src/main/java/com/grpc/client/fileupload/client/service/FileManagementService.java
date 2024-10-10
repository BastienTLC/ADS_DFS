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
        List<FileMetadata> files = new ArrayList<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Appel gRPC pour lister les fichiers
        client.listFiles(Empty.newBuilder().build(), new StreamObserver<ListFilesResponse>() {
            @Override
            public void onNext(ListFilesResponse listFilesResponse) {
                files.addAll(listFilesResponse.getFilesList());
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error occurred while listing files: {}", throwable.toString());
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("List files interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return files;
    }

    /**
     * Get the metadata of a file by calling the gRPC method getFileMetadata.
     *
     * @param request the request containing the file name.
     * @return the metadata of the file.
     */
    public FileMetadata getFileMetadata(FileDownloadRequest request) {
        final FileMetadata[] metadataHolder = new FileMetadata[1];
        CountDownLatch countDownLatch = new CountDownLatch(1);

        // Appel gRPC pour obtenir les métadonnées du fichier
        client.getFileMetadata(request, new StreamObserver<FileMetadata>() {
            @Override
            public void onNext(FileMetadata fileMetadata) {
                metadataHolder[0] = fileMetadata;
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("Error occurred while getting file metadata: {}", throwable.toString());
                countDownLatch.countDown();
            }

            @Override
            public void onCompleted() {
                countDownLatch.countDown();
            }
        });

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("Get file metadata interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return metadataHolder[0];
    }
}

