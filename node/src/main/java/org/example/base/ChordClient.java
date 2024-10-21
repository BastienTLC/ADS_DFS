package org.example.base;

import com.example.grpc.chord.*;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import com.example.grpc.chord.ChordProto.*;
import org.example.types.NodeHeader;

public class ChordClient {
    private final ChordGrpc.ChordBlockingStub blockingStub;
    private final ManagedChannel channel;

    public ChordClient(String ip, int port) {
        String target = ip + ":" + port;
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        blockingStub = ChordGrpc.newBlockingStub(channel);
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


    public boolean storeMessage(String key, Message message) {
        StoreMessageRequest request = StoreMessageRequest.newBuilder()
                .setKey(key)
                .setMessage(message)
                .build();
        try {
            StoreMessageResponse response = blockingStub.storeMessage(request);
            return response.getSuccess();
        } catch (StatusRuntimeException e) {
            System.err.println("store failed");
            return false;
        }
    }



    public Message retrieveMessage(String key) {
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
    }
}
