package com.grpc.server.fileupload.server.base;

import com.devProblems.ChordGrpc;
import com.devProblems.ChordGrpc.*;
import com.devProblems.FileUploadServiceGrpc;
import com.devProblems.Fileupload;
import com.devProblems.Fileupload.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.shared.proto.Constants;
import io.grpc.*;
import com.grpc.server.fileupload.server.types.NodeHeader;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.devProblems.ChordGrpc.newBlockingStub;

public class ChordClient {
    private final ChordBlockingStub blockingStub;
    private final ManagedChannel channel;

    public ChordClient(String ip, int port) {
        String target = ip + ":" + port;
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        blockingStub = newBlockingStub(channel);
    }


    public void shutdown() {
        channel.shutdown();
    }

    private ChordGrpc.ChordStub getStubWithMetadata(FileMetadata fileMetadata) {
        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey, fileMetadata.toByteArray());
        ClientInterceptor headerInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        return ChordGrpc.newStub(channel).withInterceptors(headerInterceptor);
    }

    public NodeHeader findSuccessor(String keyId) {
        NodeInfo request = NodeInfo.newBuilder().setId(keyId).build();

        try {
            NodeInfo nodeInfo = blockingStub.findSuccessor(request);
            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
        } catch (StatusRuntimeException e) {
            System.err.println("findSuccessor failed");
            return null;
        }
    }

    public NodeHeader getPredecessor() {
        NodeInfo nodeInfo = blockingStub.getPredecessor(Empty.getDefaultInstance());
        return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
    }

    public void setPredecessor(NodeHeader node) {
        NodeInfo nodeInfo = NodeInfo.newBuilder()
                .setId(node.getNodeId())
                .setIp(node.getIp())
                .setPort(Integer.parseInt(node.getPort()))
                .build();
        try {
            blockingStub.setPredecessor(nodeInfo);
        } catch (StatusRuntimeException e) {
            System.err.println("setPredecessor failed");
        }
    }

    public NodeHeader getSuccessor() {
        try {
            NodeInfo nodeInfo = blockingStub.getSuccessor(Empty.getDefaultInstance());
            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
        } catch (StatusRuntimeException e) {
            System.err.println("getSuccessor failed");
            return null;
        }
    }

    public List<NodeHeader> getSuccessorList() {
        Empty request = Empty.newBuilder().build();
        SuccessorListResponse response = blockingStub.getSuccessorList(request);

        List<NodeHeader> successorList = response.getSuccessorListList().stream()
                .map(nodeInfo -> new NodeHeader(
                        nodeInfo.getIp(),
                        nodeInfo.getPort(),
                        nodeInfo.getId()
                ))
                .collect(Collectors.toList());

        return successorList;
    }


    public void setSuccessor(NodeHeader node) {
        NodeInfo nodeInfo = NodeInfo.newBuilder()
                .setId(node.getNodeId())
                .setIp(node.getIp())
                .setPort(Integer.parseInt(node.getPort()))
                .build();
        try {
            blockingStub.setSuccessor(nodeInfo);
        } catch (StatusRuntimeException e) {
            System.err.println("setSuccessor failed");
        }
    }



    public void notify(NodeHeader node) {
        NodeInfo nodeInfo = NodeInfo.newBuilder()
                .setId(node.getNodeId())
                .setIp(node.getIp())
                .setPort(Integer.parseInt(node.getPort()))
                .build();

        NotifyRequest request = NotifyRequest.newBuilder()
                .setCaller(nodeInfo)
                .build();

        try {
            blockingStub.notify(request);
        } catch (StatusRuntimeException e) {
            System.err.println("notify failed");
        }
    }

    public void updateFingerTable(NodeHeader s, int i) {
        NodeInfo sNodeInfo = NodeInfo.newBuilder()
                .setId(s.getNodeId())
                .setIp(s.getIp())
                .setPort(Integer.parseInt(s.getPort()))
                .build();

        UpdateFingerTableRequest request = UpdateFingerTableRequest.newBuilder()
                .setS(sNodeInfo)
                .setI(i)
                .build();

        try {
            blockingStub.updateFingerTable(request);
        } catch (StatusRuntimeException e) {
            System.err.println("updateFingerTable failed");
        }
    }

    public NodeHeader closestPrecedingFinger(String id) {
        ClosestRequest request = ClosestRequest.newBuilder().setId(id).build();
        try {
            NodeInfo nodeInfo = blockingStub.closestPrecedingFinger(request);
            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
        } catch (StatusRuntimeException e) {
            System.err.println("closestPrecedingFinger failed");
            return null;
        }

    }

    public CompletableFuture<Boolean> retrieveFile(String key, String requester, StreamObserver<Fileupload.FileDownloadResponse> originalResponseObserver) {
        System.out.println("retrieveFile called");

        // not verifying meta data right now
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(); // data stored in RAM
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        ChordGrpc.ChordStub stub = ChordGrpc.newStub(channel);

        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(key)
                .setRequester(requester)
                .build();
        StreamObserver<FileDownloadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileDownloadResponse fileDownloadResponse) {
                System.out.println("Received file chunk...");
                try {
                    fileDownloadResponse.getFile().getContent()
                            .writeTo(byteArrayOutputStream);
                } catch (IOException e) {
                    System.err.println("Couldn't write data in retrieveFile!");
                    throw new RuntimeException(e);
                }

            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Failed to retrieve file: " + t.getMessage());
                t.printStackTrace();
                future.complete(false);
            }

            @Override
            public void onCompleted() {
                // building response to the client
                File finalFile = File.newBuilder()
                        .setContent(ByteString.copyFrom(byteArrayOutputStream.toByteArray()))
                        .setFileName(key)
                        .build();

                FileDownloadResponse finalResponse = FileDownloadResponse.newBuilder()
                        .setFile(finalFile)
                        .build();

                originalResponseObserver.onNext(finalResponse);
                originalResponseObserver.onCompleted();
                future.complete(true);
            }
        };

        stub.retrieveFile(request, responseObserver);
        return future;

    }

    public void deleteFile(String key, String requester, StreamObserver<com.google.protobuf.Empty> originalResponseObserver) {
        ChordGrpc.ChordBlockingStub stub = ChordGrpc.newBlockingStub(channel);

        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(key)
                .setRequester(requester)
                .build();

        try {
            stub.deleteFile(request);
            originalResponseObserver.onNext(Empty.getDefaultInstance());
            originalResponseObserver.onCompleted();
        } catch (StatusRuntimeException e) {
            System.err.println("deleteFile failed");
            originalResponseObserver.onError(e);
        }
    }


    public void storeFile(String key, byte[] fileContent) {
        // Create the stub for FileUploadService
        System.out.println("storeFile called");
        ChordGrpc.ChordStub stub = ChordGrpc.newStub(channel);

        FileMetadata fileMetadata = Constants.fileMetaContext.get();

        System.out.println("File metadata: " + fileMetadata.getFileNameWithType());

        Metadata metadata = new Metadata();
        metadata.put(Constants.fileMetadataKey,
                FileMetadata.newBuilder()
                        .setFileNameWithType(fileMetadata.getFileNameWithType())
                        .setContentLength(fileMetadata.getContentLength())
                        .setAuthor(fileMetadata.getAuthor())
                        .build()
                        .toByteArray());


        // Attach metadata using a custom interceptor
        ClientInterceptor headerInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        stub = stub.withInterceptors(headerInterceptor);

        // Create a response observer
        CountDownLatch finishLatch = new CountDownLatch(1);  // For synchronization
        StreamObserver<FileUploadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileUploadResponse value) {
                System.out.println("File upload status: " + value.getUploadStatus());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                finishLatch.countDown();  // Signal that the process failed
            }

            @Override
            public void onCompleted() {
                System.out.println("File upload completed.");
                finishLatch.countDown();  // Signal that the process is done
            }
        };

        // Start streaming the file content
        StreamObserver<FileUploadRequest> requestObserver = stub.storeFile(responseObserver);

        // Stream the file content in chunks
        int chunkSize = 5120;  // 5KB
        int offset = 0;
        try {
            while (offset < fileContent.length) {
                int length = Math.min(chunkSize, fileContent.length - offset);
                ByteString content = ByteString.copyFrom(fileContent, offset, length);
                FileUploadRequest request = FileUploadRequest.newBuilder()
                        .setFile(File.newBuilder().setContent(content))
                        .build();
                requestObserver.onNext(request);
                offset += length;
            }
            // Mark the end of the stream
            requestObserver.onCompleted();

            // Wait until the file upload is complete or an error occurs
            finishLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
            requestObserver.onError(e);  // Notify the server of the error
        }
    }

    public void storeFileWithMetadata(FileMetadata fileMetadata, byte[] fileContent) {
        // Create a stub with the file metadata attached to the headers
        ChordGrpc.ChordStub stub = getStubWithMetadata(fileMetadata);

        System.out.println("storeFileWithMetadata called with file: " + fileMetadata.getFileNameWithType());

        // Create a response observer
        CountDownLatch finishLatch = new CountDownLatch(1);  // For synchronization
        StreamObserver<FileUploadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileUploadResponse value) {
                System.out.println("File upload status: " + value.getUploadStatus());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
                finishLatch.countDown();  // Signal that the process failed
            }

            @Override
            public void onCompleted() {
                System.out.println("File upload completed.");
                finishLatch.countDown();  // Signal that the process is done
            }
        };

        // Start streaming the file content
        StreamObserver<FileUploadRequest> requestObserver = stub.storeFile(responseObserver);

        // Stream the file content in chunks
        int chunkSize = 5120;  // 5KB
        int offset = 0;
        try {
            while (offset < fileContent.length) {
                int length = Math.min(chunkSize, fileContent.length - offset);
                ByteString content = ByteString.copyFrom(fileContent, offset, length);
                FileUploadRequest request = FileUploadRequest.newBuilder()
                        .setFile(File.newBuilder().setContent(content))
                        .build();
                requestObserver.onNext(request);
                offset += length;
            }
            // Mark the end of the stream
            requestObserver.onCompleted();

            // Wait until the file upload is complete or an error occurs
            finishLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
            requestObserver.onError(e);  // Notify the server of the error
        }
    }
}