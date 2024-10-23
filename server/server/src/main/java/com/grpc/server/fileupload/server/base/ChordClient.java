package com.grpc.server.fileupload.server.base;

import com.devProblems.ChordGrpc.*;
import com.devProblems.FileUploadServiceGrpc;
import com.devProblems.Fileupload.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.grpc.server.fileupload.server.types.FileMetadataModel;
import io.grpc.*;
import com.grpc.server.fileupload.server.types.NodeHeader;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

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
        try {
            NodeInfo nodeInfo = blockingStub.getPredecessor(Empty.getDefaultInstance());
            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
        } catch (StatusRuntimeException e) {
            System.err.println("getPredecessor failed");
            return null;
        }
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

    public void storeFile(String key, byte[] fileContent) {

        // Create the stub for FileUploadService
        FileUploadServiceGrpc.FileUploadServiceStub stub = FileUploadServiceGrpc.newStub(channel);

        // Prepare metadata with the file name and content length
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("fileName", Metadata.ASCII_STRING_MARSHALLER), key);
        metadata.put(Metadata.Key.of("contentLength", Metadata.ASCII_STRING_MARSHALLER), String.valueOf(fileContent.length));

        // Attach metadata using a custom interceptor
        ClientInterceptor headerInterceptor = MetadataUtils.newAttachHeadersInterceptor(metadata);
        stub = stub.withInterceptors(headerInterceptor);

        // Create a response observer
        StreamObserver<FileUploadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileUploadResponse value) {
                System.out.println("File upload status: " + value.getUploadStatus());
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                System.out.println("File upload completed.");
            }
        };

        // Create a request observer
        StreamObserver<FileUploadRequest> requestObserver = stub.uploadFile(responseObserver);

        // Stream the file content in chunks
        int chunkSize = 5120; // 5KB
        int offset = 0;
        while (offset < fileContent.length) {
            int length = Math.min(chunkSize, fileContent.length - offset);
            ByteString content = ByteString.copyFrom(fileContent, offset, length);
            FileUploadRequest request = FileUploadRequest.newBuilder()
                    .setFile(File.newBuilder().setContent(content))
                    .build();
            requestObserver.onNext(request);
            offset += length;
        }
        requestObserver.onCompleted();
    }

    /*public boolean storeFile(String key, File file) {
        StoreFileRequest request = StoreFileRequest.newBuilder()
                .setKey(key)
                .setFile(file)
                .build();
        try {
            // call upload streaming function
            return response.getSuccess();
        } catch (StatusRuntimeException e) {
            System.err.println("store failed");
            return false;
        }
    }*/



    /*public Message retrieveMessage(String key) {
        RetrieveMessageRequest request = RetrieveMessageRequest.newBuilder()
                .setKey(key)
                .build();
        try {
            RetrieveMessageResponse response = blockingStub.retrieveMessage(request);
            if (response.getFound()) {
                return response.getMessage();
            } else {
                return null;
            }
        } catch (StatusRuntimeException e) {
            System.err.println("Message not found");
            return null;
        }
    }*/
}