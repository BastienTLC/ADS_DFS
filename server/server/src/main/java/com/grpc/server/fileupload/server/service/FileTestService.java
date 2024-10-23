
package com.grpc.server.fileupload.server.service;

import com.devProblems.Fileupload.*;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FileTestService {

    public void testMethod(FileDownloadRequest request, StreamObserver<FileTesting> responseObserver) {
        String filename = request.getFileName();

        FileTesting response = FileTesting.newBuilder()
                .setText("Filename received: " + filename)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
