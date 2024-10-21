package org.example.base;

import com.example.grpc.chord.*;
import com.example.grpc.chord.ChordGrpc.ChordImplBase;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import java.util.Arrays;

import com.example.grpc.chord.ChordProto.*;
import org.example.types.NodeHeader;
import org.example.utils.Wrapper;

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




    @Override
    public void storeMessage(StoreMessageRequest request, StreamObserver<StoreMessageResponse> responseObserver) {
        String key = request.getKey();
        Message messageRpc = request.getMessage();
        org.example.types.Message message = Wrapper.wrapGrpcMessageToMessage(messageRpc);

        // stpre
        chordNode.getMessageStore().storeMessage(key, message);

        StoreMessageResponse response = StoreMessageResponse.newBuilder()
                .setSuccess(true)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void retrieveMessage(RetrieveMessageRequest request, StreamObserver<RetrieveMessageResponse> responseObserver) {
        String key = request.getKey();

        org.example.types.Message message = chordNode.getMessageStore().retrieveMessage(key);

        Message messageRpc = message != null ? Wrapper.wrapMessageToGrpcMessage(message) : null;

        RetrieveMessageResponse.Builder responseBuilder = RetrieveMessageResponse.newBuilder();

        if (message != null) {
            responseBuilder.setFound(true)
                    .setMessage(messageRpc);
        } else {
            responseBuilder.setFound(false);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void storeMessageInChord(StoreMessageRequest request, StreamObserver<StoreMessageResponse> responseObserver) {
        String key = request.getKey();
        Message messageRpc = request.getMessage();
        org.example.types.Message message = Wrapper.wrapGrpcMessageToMessage(messageRpc);

        chordNode.storeMessageInChord(key, message);

        StoreMessageResponse response = StoreMessageResponse.newBuilder()
                .setSuccess(true)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void retrieveMessageFromChord(RetrieveMessageRequest request, StreamObserver<RetrieveMessageResponse> responseObserver) {
        String key = request.getKey();

        org.example.types.Message message = chordNode.retrieveMessageFromChord(key);

        Message messageRpc = message != null ? Wrapper.wrapMessageToGrpcMessage(message) : null;

        RetrieveMessageResponse.Builder responseBuilder = RetrieveMessageResponse.newBuilder();

        if (message != null) {
            responseBuilder.setFound(true)
                    .setMessage(messageRpc);
        } else {
            responseBuilder.setFound(false);
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    @Override
    public void leave(com.google.protobuf.Empty request, StreamObserver<LeaveResponse> responseObserver) {
        //chordNode.leave();
        responseObserver.onNext(LeaveResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
        System.exit(0);
    }




    @Override
    public void getChordNodeInfo(com.google.protobuf.Empty request, StreamObserver<Node> responseObserver) {
        ChordProto.MessageStore messageStore = ChordProto.MessageStore.newBuilder()
                .addAllMessages(chordNode.getMessageStore().getStorage().values().stream().map(message -> Message.newBuilder()
                        .setId(message.getId())
                        .setTimestamp(message.getTimestamp())
                        .setAuthor(message.getAuthor())
                        .setTopic(message.getTopic())
                        .setContent(message.getContent())
                        .setData(ByteString.copyFrom(message.getData()))
                        .build()).toList())
                .build();
        Node node = Node.newBuilder()
                .setIp(chordNode.getIp())
                .setPort(chordNode.getPort())
                .setId(chordNode.getNodeId())
                .setPredecessor(NodeInfo.newBuilder()
                        .setIp(chordNode.getPredecessor().getIp())
                        .setPort(Integer.parseInt(chordNode.getPredecessor().getPort()))
                        .setId(chordNode.getPredecessor().getNodeId())
                        .build())
                .setSuccessor(NodeInfo.newBuilder()
                        .setIp(chordNode.getSuccessor().getIp())
                        .setPort(Integer.parseInt(chordNode.getSuccessor().getPort()))
                        .setId(chordNode.getSuccessor().getNodeId())
                        .build())
                .setFingerTable(ChordProto.FingerTable.newBuilder()
                        .addAllFinger(chordNode.getFingerTable().getFingers().stream().map(finger -> NodeInfo.newBuilder()
                                .setIp(finger.getIp())
                                .setPort(Integer.parseInt(finger.getPort()))
                                .setId(finger.getNodeId())
                                .build()).toList())
                        .build())
                .setMessageStore(messageStore)
                .build();
        responseObserver.onNext(node);
        responseObserver.onCompleted();
    }
}
