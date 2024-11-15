package com.grpc.server.fileupload.server.base;

import com.devProblems.Fileupload.*;
import com.devProblems.ChordGrpc.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.shared.proto.Constants;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

import com.grpc.server.fileupload.server.types.NodeHeader;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;


public class ChordServiceImpl extends ChordImplBase {
    private final ChordNode chordNode;

    public ChordServiceImpl(ChordNode chordNode) {
        this.chordNode = chordNode;
    }

    @Override
    public void join(JoinRequest request, StreamObserver<JoinResponse> responseObserver) {
        responseObserver.onNext(JoinResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void stabilize(StabilizeRequest request, StreamObserver<StabilizeResponse> responseObserver) {
        try {
            chordNode.stabilize();
            responseObserver.onNext(StabilizeResponse.newBuilder().setSuccess(true).build());
        } catch (Exception e) {
            System.err.println("stabilize failed");
            responseObserver.onNext(StabilizeResponse.newBuilder().setSuccess(false).build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void notify(NotifyRequest request, StreamObserver<NotifyResponse> responseObserver) {
        NodeInfo callerInfo = request.getCaller();
        NodeHeader callerNode = new NodeHeader(callerInfo.getIp(), callerInfo.getPort(), callerInfo.getId());
        chordNode.notify(callerNode);
        responseObserver.onNext(NotifyResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void findSuccessor(NodeInfo request, StreamObserver<NodeInfo> responseObserver) {
        NodeHeader successor = chordNode.findSuccessor(request.getId());
        if (successor != null) {
            NodeInfo nodeInfo = NodeInfo.newBuilder()
                    .setId(successor.getNodeId())
                    .setIp(successor.getIp())
                    .setPort(Integer.parseInt(successor.getPort()))
                    .build();
            responseObserver.onNext(nodeInfo);
        } else {
            responseObserver.onNext(NodeInfo.newBuilder().build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getPredecessor(Empty request, StreamObserver<NodeInfo> responseObserver) {
        NodeHeader predecessor = chordNode.getPredecessor();
        if (predecessor != null) {
            NodeInfo nodeInfo = NodeInfo.newBuilder()
                    .setId(predecessor.getNodeId())
                    .setIp(predecessor.getIp())
                    .setPort(Integer.parseInt(predecessor.getPort()))
                    .build();
            responseObserver.onNext(nodeInfo);
        } else {
            responseObserver.onNext(NodeInfo.newBuilder().build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void setPredecessor(NodeInfo request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        NodeHeader predecessor = new NodeHeader(request.getIp(), request.getPort(), request.getId());
        chordNode.setPredecessor(predecessor);
        responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getSuccessor(Empty request, StreamObserver<NodeInfo> responseObserver) {
        NodeHeader successor = chordNode.getSuccessor();
        if (successor != null) {
            NodeInfo nodeInfo = NodeInfo.newBuilder()
                    .setId(successor.getNodeId())
                    .setIp(successor.getIp())
                    .setPort(Integer.parseInt(successor.getPort()))
                    .build();
            responseObserver.onNext(nodeInfo);
        } else {
            responseObserver.onNext(NodeInfo.newBuilder().build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public void setSuccessor(NodeInfo request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        NodeHeader successor = new NodeHeader(request.getIp(), request.getPort(), request.getId());
        chordNode.setSuccessor(successor);
        responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void updateFingerTable(UpdateFingerTableRequest request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        NodeInfo sNodeInfo = request.getS();
        int i = request.getI();
        this.chordNode.updateFingerTable(new NodeHeader(sNodeInfo.getIp(), sNodeInfo.getPort(), sNodeInfo.getId()), i);
//        System.out.println("Updated FingerTable is: ");
//        this.chordNode.printFingerTable();
        responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getNodeInfo(GetNodeInfoRequest request, StreamObserver<GetNodeInfoResponse> responseObserver) {
        NodeInfo nodeInfo = NodeInfo.newBuilder()
                .setId(chordNode.getNodeId())
                .setIp(chordNode.getIp())
                .setPort(chordNode.getPort())
                .build();
        responseObserver.onNext(GetNodeInfoResponse.newBuilder().setNode(nodeInfo).build());
        responseObserver.onCompleted();
    }

    @Override
    public void closestPrecedingFinger(ClosestRequest request, StreamObserver<NodeInfo> responseObserver) {
        String id = request.getId();
        NodeHeader closestPrecedingFinger = chordNode.closestPrecedingFinger(id);
        if (closestPrecedingFinger != null) {
            NodeInfo nodeInfo = NodeInfo.newBuilder()
                    .setId(closestPrecedingFinger.getNodeId())
                    .setIp(closestPrecedingFinger.getIp())
                    .setPort(Integer.parseInt(closestPrecedingFinger.getPort()))
                    .build();
            responseObserver.onNext(nodeInfo);
        } else {
            responseObserver.onNext(NodeInfo.newBuilder().build());
        }
        responseObserver.onCompleted();
    }


    // this function will transfer file to a node
    @Override
    public void retrieveFile(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String filename = request.getFileName();
        String requester = request.getRequester();
        String filePath = "output/" + this.chordNode.getNodeId() + "/" + requester + "/" + filename;
        java.io.File file = new java.io.File(filePath);
        java.io.File parentDir = file.getParentFile();

        if (!parentDir.exists()) {
            System.out.println(String.format("Directory not found: %s", parentDir.getPath()));
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Directory not found").asRuntimeException());
            return;
        }

        if (!file.exists() || !file.isFile()) {
            System.out.println(String.format("File not found: %s", filename));
            responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
            return;
        }


        byte[] fiveKB = new byte[5120];
        int length;

        try (InputStream inputStream = new FileInputStream(file)) {
            // Reading bytes and sending them to the client
            while ((length = inputStream.read(fiveKB)) > 0) {
                System.out.println(String.format("Sending %d length of data", length));
                File fileMessage = File.newBuilder()
                        .setContent(ByteString.copyFrom(fiveKB, 0, length))
                        .build();

                FileDownloadResponse response = FileDownloadResponse.newBuilder()
                        .setFile(fileMessage)  // Use the File message containing the content
                        .build();

                responseObserver.onNext(response);
            }

            // Response is completed once all data is sent
            responseObserver.onCompleted();

        } catch (IOException e) {
            System.out.println(String.format("Error reading file: " + filename, e));
            responseObserver.onError(Status.INTERNAL.withDescription("Error reading file").asRuntimeException());
        }
    }


    // New for this function is that we will never send same file twice to the joining, instead
    // the nodes receiving the files will be able to map them itself by constructing a tree
    public void retrieveFilesForSpan(FileRangeRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String startHash = request.getStartHash();  // Get startHash from the request
        String endHash = request.getEndHash();      // Get endHash from the request

        // Fetch the files within the specified hash range which will be transferred/removed
        Map<String, List<String>> filesInRange = chordNode.getFilesInRange(startHash, endHash);
        System.out.println("Preparing to transfer the files for range: " + startHash + "-" + endHash + "...");
        chordNode.printFilesInRange(startHash, endHash);

        // so if it transfers files in directory 21, 22, 23 etc it will remove them
        List<String> idsToRemove = new ArrayList<>();
        List<String> filesToDelete = new ArrayList<>();
        Set<String> filesSent = new HashSet<>(); // so we dont send file twice to same node

        // Loop through the files in the range and retrieve them one by one
        filesInRange.forEach((hash, fileKeys) -> {
            fileKeys.forEach(fileKey -> {
                String[] fileDetails = fileKey.split(":");
                String filename = fileDetails[0];
                String username = fileDetails[1];

                // Build the file path based on the filename and requester
                String filePath = "output/" + this.chordNode.getNodeId() + "/" + username + "/" + filename;
                Path path = Paths.get(filePath);


                if (filesSent.contains(fileKey)) {
                    if (!idsToRemove.contains(hash)) {
                        System.out.println("The mappings related to id: " + hash + " will be removed");
                        idsToRemove.add(hash);
                    }
                    System.out.printf("Skipping sending a file already sent: %s%n", filename);
                    return;
                }

                if (!Files.exists(path) || !Files.isRegularFile(path)) {
                    System.out.printf("File not found: %s%n", filename);
                    responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
                    return;
                }

                // Send the file content if it exists
                try (InputStream inputStream = new FileInputStream(path.toFile())) {
                    byte[] fiveKB = new byte[5120];
                    int length;

                    while ((length = inputStream.read(fiveKB)) > 0) {
                        System.out.println(String.format("Sending %d length of data for file: %s", length, filename));
                        File fileMessage = File.newBuilder()
                                .setContent(ByteString.copyFrom(fiveKB, 0, length))
                                .setFileName(filename)        // Set the file name
                                .setUsername(username)       // Set the username (requester)
                                .build();

                        FileDownloadResponse response = FileDownloadResponse.newBuilder()
                                .setFile(fileMessage)  // Use the File message containing the content
                                .build();

                        // Send each chunk as it is read
                        responseObserver.onNext(response);
                    }


                    filesSent.add(fileKey);


                    if (!idsToRemove.contains(hash)) {
                        System.out.println("The mappings related to id: " + hash + " will be removed");
                        idsToRemove.add(hash);
                    }

                    // Later we check and remove this file from the node if no mappings to it remain
                    filesToDelete.add(fileKey);


                } catch (IOException e) {
                    System.err.println("Error reading file " + filename + ": " + e.getMessage());
                    responseObserver.onError(Status.INTERNAL.withDescription("Error reading file").asRuntimeException());
                    // e.printStackTrace();
                    return;
                }
            });
        });


        // this will safely remove mappings we no longer need
        idsToRemove.forEach(chordNode::removeMappingForId);

        // deleting all files that we sent away (and the directory they are in if they are now empty)
        filesToDelete.forEach(fileIdentifier -> {
            if (chordNode.checkMappingForFileIdentifier(fileIdentifier)) {
                System.out.println("File '" + fileIdentifier + "' will not be deleted since it still exists in the mapping");
            }
            else {
                String[] fileDetails = fileIdentifier.split(":");
                String filename = fileDetails[0];
                String username = fileDetails[1];

                // Build the file path based on the filename and requester
                String filePath = "output/" + this.chordNode.getNodeId() + "/" + username + "/" + filename;
                try {
                    Path path = Paths.get(filePath);

                    System.out.println("Deleting file: " + path);

                    Files.deleteIfExists(path);

                    // Check if the parent directory is empty after file deletion
                    Path parentDir = path.getParent();
                    if (Files.isDirectory(parentDir) && Files.list(parentDir).count() == 0) {
                        Files.deleteIfExists(parentDir);
                        System.out.println("Deleted empty directory: " + parentDir);
                    }

                } catch (IOException e) {
                    System.err.println("Failed to delete file or directory: ");
                }
            }

        });


        // signalling that all files have been sent
        responseObserver.onCompleted();
    }



    public void storeFileMappings(FileMappingRequest request, StreamObserver<Empty> responseObserver) {
        Map<String, StringList> mappings = request.getFileMappingMap().getMappingsMap();

        Map<String, List<String>> convertedMap = mappings.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey(),
                        entry -> entry.getValue().getValuesList()
                ));

        System.out.println("Received the fileMap from the predecessor");
        convertedMap.forEach((key, value) -> {
            System.out.println("Key: " + key + ", Values: " + value);
        });

        chordNode.updatePredecessorReplicationMap(convertedMap);

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }


    public void getFileMappings(Empty request, StreamObserver<FileMappingRequest> responseObserver) {
        // Retrieve the file mappings stored in chordNode
        TreeMap<String, List<String>> serverFileMap = chordNode.getFileMap();

        // Convert serverFileMap to FileMappingRequest
        FileMappingMap.Builder mapBuilder = FileMappingMap.newBuilder();
        for (Map.Entry<String, List<String>> entry : serverFileMap.entrySet()) {
            StringList stringList = StringList.newBuilder()
                    .addAllValues(entry.getValue())
                    .build();
            mapBuilder.putMappings(entry.getKey(), stringList);
        }

        // Build the FileMappingRequest to send back to the client
        FileMappingRequest response = FileMappingRequest.newBuilder()
                .setFileMappingMap(mapBuilder.build())
                .build();

        // Send the response
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }



    @Override
    public StreamObserver<FileUploadRequest> storeFile(StreamObserver<FileUploadResponse> responseObserver) {
        FileMetadata fileMetadata = Constants.fileMetaContext.get();
        DiskFileStorage diskFileStorage = new DiskFileStorage();

        return new StreamObserver<>() {
            @Override
            // this method will write the bytes to the byte array stream that we have created in the diskFileStorage
            public void onNext(FileUploadRequest fileUploadRequest) {

                // called so we can compare that received data matches the data sent
                System.out.println(String.format("received %d length of data", fileUploadRequest.getFile().getContent().size()));
                try {
                    fileUploadRequest.getFile().getContent()
                            .writeTo(diskFileStorage.getStream());
                } catch (IOException e) {
                    // this is invoking the clients on error method
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("cannot write data due to : " + e.getMessage())
                            .asRuntimeException());
                }
            }

            @Override
            // called when client sends error
            public void onError(Throwable throwable) {
                System.out.println("Error occurred while uploading file: " + throwable.toString());
            }

            @Override
            // called when client has finished sending data
            public void onCompleted() {

                try {
                    int totalBytesReceived = diskFileStorage.getStream().size();
                    // the reason why we create the fileMetadata object was to validate here that
                    // it is the same that the server has received
                    if (totalBytesReceived == fileMetadata.getContentLength()) {
                        // if matches we write to the diskFileStorage
                        System.out.println("Writing the file to the following NodeId directory: " + chordNode.getNodeId());
                        diskFileStorage.write(fileMetadata.getFileNameWithType(),fileMetadata.getAuthor(), chordNode.getNodeId());
                        diskFileStorage.close();

                        chordNode.addMapping(fileMetadata.getFileNameWithType(), fileMetadata.getAuthor());
                    } else {
                        // notifying the client with error
                        responseObserver.onError(
                                io.grpc.Status.FAILED_PRECONDITION
                                        .withDescription(String.format("expected %d but received %d", fileMetadata.getContentLength(), totalBytesReceived))
                                        .asRuntimeException()
                        );
                        return;
                    }
                } catch (IOException e) {
                    // notifying the client with error
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("cannot save data due to : " + e.getMessage())
                            .asRuntimeException());
                    return;
                }

                // if there were no errors, we notify the client by calling the onNext method and sending
                // the FileUploadResponse
                responseObserver.onNext(
                        FileUploadResponse
                                .newBuilder()
                                .setFileName(fileMetadata.getFileNameWithType())
                                .setUploadStatus(UploadStatus.SUCCESS)
                                .build()
                );
                responseObserver.onCompleted();
            }
        };
    }

    public void deleteFile(FileDownloadRequest request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        String filename = request.getFileName();
        String requester = request.getRequester();
        String filePath = "output/" + this.chordNode.getNodeId() + "/" + requester + "/" + filename;
        java.io.File file = new java.io.File(filePath);
        java.io.File parentDir = file.getParentFile();

        if (!parentDir.exists()) {
            System.out.println(String.format("Directory not found: %s", parentDir.getPath()));
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Directory not found").asRuntimeException());
            return;
        }

        if (!file.exists() || !file.isFile()) {
            System.out.println(String.format("File not found: %s", filename));
            responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
            return;
        }

        if (!file.delete()) {
            System.out.println(String.format("Failed to delete file: %s", filename));
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to delete file").asRuntimeException());
            return;
        }

        responseObserver.onNext(com.google.protobuf.Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<FileUploadRequest> storeFileInChord(StreamObserver<FileUploadResponse> responseObserver) {
        FileMetadata fileMetadata = Constants.fileMetaContext.get(); // this is used at the end of the function to verify meta data
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        return new StreamObserver<>() {

            @Override
            // this method will write the bytes to the byte array stream that we have created in the diskFileStorage
            public void onNext(FileUploadRequest fileUploadRequest) {
                // called so we can compare that received data matches the data sent
                System.out.println(String.format("received %d length of data", fileUploadRequest.getFile().getContent().size()));
                try {
                    fileUploadRequest.getFile().getContent()
                            .writeTo(byteArrayOutputStream);
                } catch (IOException e) {
                    // this is invoking the clients on error method
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("cannot write data due to : " + e.getMessage())
                            .asRuntimeException());
                }
            }

            @Override
            // called when client sends error
            public void onError(Throwable throwable) {
                System.out.println("Error occurred while uploading file: " + throwable.toString());
            }

            @Override
            // called when client has finished sending data
            public void onCompleted() {

                try {
                    int totalBytesReceived = byteArrayOutputStream.size();
                    if (totalBytesReceived == fileMetadata.getContentLength()) {
                        String name = fileMetadata.getFileNameWithType();
                        String author = fileMetadata.getAuthor();
                        byteArrayOutputStream.close();
                        byte[] fileContent = byteArrayOutputStream.toByteArray();
                        chordNode.storeFileInChord(name, author, fileContent);
                    } else {
                        responseObserver.onError(
                                io.grpc.Status.FAILED_PRECONDITION
                                        .withDescription(String.format("Expected %d bytes but received %d bytes", fileMetadata.getContentLength(), totalBytesReceived))
                                        .asRuntimeException()
                        );
                        return;
                    }
                } catch (IOException e) {
                    responseObserver.onError(io.grpc.Status.INTERNAL
                            .withDescription("Cannot save data due to: " + e.getMessage())
                            .asRuntimeException());
                    return;
                }

                responseObserver.onNext(
                        FileUploadResponse
                                .newBuilder()
                                .setFileName(fileMetadata.getFileNameWithType())
                                .setUploadStatus(UploadStatus.SUCCESS)
                                .build()
                );
                responseObserver.onCompleted();
            }
        };
    }



    @Override
    public void retrieveFileFromChord(FileDownloadRequest request, StreamObserver<FileDownloadResponse> responseObserver) {
        String filename = request.getFileName();
        String requester = request.getRequester();

        String fileIdentifier = filename + ":" + requester;

        // Respond immediately if it is storing the file
        if (chordNode.checkMappingForFileIdentifier(fileIdentifier)) {
            System.out.println("The file " + filename + " was found without having to check other nodes!");
            String filePath = "output/" + this.chordNode.getNodeId() + "/" + requester + "/" + filename;
            java.io.File file = new java.io.File(filePath);
            java.io.File parentDir = file.getParentFile();

            if (!parentDir.exists()) {
                System.out.println(String.format("Directory not found: %s", parentDir.getPath()));
                responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Directory not found").asRuntimeException());
                return;
            }

            if (!file.exists() || !file.isFile()) {
                System.out.println(String.format("File not found: %s", filename));
                responseObserver.onError(Status.NOT_FOUND.withDescription("File not found").asRuntimeException());
                return;
            }


            byte[] fiveKB = new byte[5120];
            int length;

            try (InputStream inputStream = new FileInputStream(file)) {
                // Reading bytes and sending them to the client
                while ((length = inputStream.read(fiveKB)) > 0) {
                    System.out.println(String.format("Sending %d length of data", length));
                    File fileMessage = File.newBuilder()
                            .setContent(ByteString.copyFrom(fiveKB, 0, length))
                            .build();

                    FileDownloadResponse response = FileDownloadResponse.newBuilder()
                            .setFile(fileMessage)  // Use the File message containing the content
                            .build();

                    responseObserver.onNext(response);
                }

                // Response is completed once all data is sent
                responseObserver.onCompleted();

            } catch (IOException e) {
                System.out.println(String.format("Error reading file: " + filename, e));
                responseObserver.onError(Status.INTERNAL.withDescription("Error reading file").asRuntimeException());
            }
        }
        else {
            chordNode.retrieveMessageFromChord(filename, requester, responseObserver);
        }
    }

    public void deleteFileFromChord(FileDownloadRequest request, StreamObserver<com.google.protobuf.Empty> responseObserver) {
        String filename = request.getFileName();
        String requester = request.getRequester();
        chordNode.deleteFileFromChord(filename, requester, responseObserver);
    }

}