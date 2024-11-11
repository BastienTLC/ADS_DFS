package com.grpc.server.fileupload.server.base;

import com.devProblems.ChordGrpc;
import com.devProblems.ChordGrpc.*;
import com.devProblems.FileUploadServiceGrpc;
import com.devProblems.Fileupload;
import com.devProblems.Fileupload.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.shared.proto.Constants;
import io.grpc.*;
import com.grpc.server.fileupload.server.types.NodeHeader;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

//    public NodeHeader getPredecessor() {
//        try {
//            NodeInfo nodeInfo = blockingStub.getPredecessor(Empty.getDefaultInstance());
//            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
//        } catch (StatusRuntimeException e) {
//            System.err.println("getPredecessor failed: " + e.getStatus());
//            return null;
//        }
//    }

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
        NodeInfo nodeInfo = blockingStub.getSuccessor(Empty.getDefaultInstance());
        return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
    }

//    public NodeHeader getSuccessor() {
//        try {
//            NodeInfo nodeInfo = blockingStub.getSuccessor(Empty.getDefaultInstance());
//            return new NodeHeader(nodeInfo.getIp(), nodeInfo.getPort(), nodeInfo.getId());
//        } catch (StatusRuntimeException e) {
//            System.err.println("getSuccessor failed: " + e.getStatus());
//            return null;
//        }
//    }

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


    public void sendFileMappings(TreeMap<String, List<String>> predecessorFileMap) {
        // Create a map builder to construct the request
        FileMappingMap.Builder mapBuilder = FileMappingMap.newBuilder();

        // Convert TreeMap to the proto format
        for (Map.Entry<String, List<String>> entry : predecessorFileMap.entrySet()) {
            StringList stringList = StringList.newBuilder()
                    .addAllValues(entry.getValue())  // Add all values from the list to StringList
                    .build();

            // System.out.println("Adding entry to map: " + entry.getKey() + " -> " + entry.getValue());

            mapBuilder.putMappings(entry.getKey(), stringList);  // Add the key-value pair to the map builder
        }

        FileMappingRequest request = FileMappingRequest.newBuilder()
                .setFileMappingMap(mapBuilder)
                .build();

        // System.out.println("FileMappingRequest being sent: " + request);

        // Send the request to the server
        try {
            blockingStub.storeFileMappings(request);  // sending our fileMappings to the successor
            System.out.println("File mappings sent successfully to successor.");
        } catch (Exception e) {
            System.err.println("Failed to send file mappings: " + e.getMessage());
        }
    }

    public Map<String, List<String>> fetchNewPredecessorFileMappings() {
        Empty request = Empty.newBuilder().build();

        try {
            // Call the GetFileMappings RPC and receive the response
            FileMappingRequest response = blockingStub.getFileMappings(request);

            Map<String, StringList> mappings = response.getFileMappingMap().getMappingsMap();

            Map<String, List<String>> convertedMap = mappings.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey(),
                            entry -> entry.getValue().getValuesList()
                    ));

            System.out.println("Received the fileMap from the predecessor");
            convertedMap.forEach((key, value) -> {
                System.out.println("Key: " + key + ", Values: " + value);
            });

            return convertedMap;

        } catch (Exception e) {
            System.err.println("Failed to fetch file mappings: " + e.getMessage());
        }

        return null;
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

    public void retrieveCopyOfFile(String filename, String username) {
        System.out.println("retrieveCopyOfFile called for file: " + filename + " and user: " + username);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ChordGrpc.ChordStub stub = ChordGrpc.newStub(channel);

        FileDownloadRequest request = FileDownloadRequest.newBuilder()
                .setFileName(filename)
                .setRequester(username)
                .build();

        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<FileDownloadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileDownloadResponse fileDownloadResponse) {
                System.out.println("Received file chunk for file: " + filename);
                try {
                    fileDownloadResponse.getFile().getContent().writeTo(byteArrayOutputStream);
                } catch (IOException e) {
                    System.err.println("Couldn't write data in retrieveCopyOfFile!");
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Failed to retrieve file: " + t.getMessage());
                t.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                System.out.println("File download completed for: " + filename);

                File finalFile = File.newBuilder()
                        .setContent(ByteString.copyFrom(byteArrayOutputStream.toByteArray()))
                        .setFileName(filename)
                        .build();

                System.out.println("File " + filename + " retrieved successfully for user " + username);
                latch.countDown();
            }
        };

        stub.retrieveFile(request, responseObserver);

        try {
            latch.await();
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for file retrieval to complete.");
            e.printStackTrace();
        }
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


    public void retrieveFiles(String startHash, String endHash, ChordNode chordNode) {
        ChordGrpc.ChordStub stub = ChordGrpc.newStub(channel);

        FileRangeRequest fileRangeRequest = FileRangeRequest.newBuilder()
                .setStartHash(startHash)
                .setEndHash(endHash)
                .build();

        // map to keep track of ByteArrayOutputStream for each file being downloaded
        Map<String, DiskFileStorage> inProgressFiles = new HashMap<>();

        StreamObserver<FileDownloadResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(FileDownloadResponse fileDownloadResponse) {
                String fileName = fileDownloadResponse.getFile().getFileName();
                String username = fileDownloadResponse.getFile().getUsername();
                byte[] fileContent = fileDownloadResponse.getFile().getContent().toByteArray();

                String fileKey = fileName + ":" + username;

                // Get or create DiskFileStorage for the file
                DiskFileStorage diskFileStorage = inProgressFiles.computeIfAbsent(fileKey, k -> new DiskFileStorage());

                try {
                    // writing chunk to file
                    diskFileStorage.getStream().write(fileContent);
                } catch (IOException e) {
                    System.err.println("Error writing data for file: " + fileName + " | " + e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error during file download: " + t.getMessage());
                t.printStackTrace();
                inProgressFiles.forEach((key, diskFileStorage) -> {
                    try {
                        diskFileStorage.close();
                    } catch (IOException e) {
                        System.err.println("Failed to close stream for file: " + key);
                    }
                });
            }

            @Override
            public void onCompleted() {
                // writing each file to disk
                inProgressFiles.forEach((fileKey, diskFileStorage) -> {
                    try {
                        System.out.println("Received '" + fileKey + "', storing to disk...");

                        // Extract filename and username from fileKey
                        String[] fileDetails = fileKey.split(":");
                        String fileName = fileDetails[0];
                        String username = fileDetails[1];

                        diskFileStorage.write(fileName, username, chordNode.getNodeId());
                        diskFileStorage.close();

                        // this needs to be called here, since the function called upon joining will only add mapping for existing files
                        chordNode.addMapping(fileName, username);
                    } catch (IOException e) {
                        System.err.println("Error writing file to disk for file: " + fileKey + " | " + e.getMessage());
                    }
                });

                // clearing map since all files have been stored
                inProgressFiles.clear();
            }
        };

        stub.retrieveFilesForSpan(fileRangeRequest, responseObserver);
    }



    public void storeFile(String key, byte[] fileContent) {
        // This must be modified so that the key is included, because
        // if we generated values 14, 22, 30, 42, it is important that the receiving node is
        // aware that this is the id 42, and not hashNode(filename)=22, when storing the file
        // this will make sending files much easier to other nodes upon/join leave
        // upon being asked to retrieve file it must loop through the items its holding


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
                // we dont need to set username and filename since we are passing it with metadata
                // however I added it to send nodeId, which is generated by the tree algorithm to know where to store
                FileUploadRequest request = FileUploadRequest.newBuilder()
                        .setFile(File.newBuilder().setContent(content))
                        .setKey(key) // new, now being passed for each upload
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