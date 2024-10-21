package org.example.utils;

import com.example.grpc.chord.ChordProto;
import org.example.types.Message;

public class Wrapper {
    public static Message wrapGrpcMessageToMessage(ChordProto.Message grpcMessage) {
        return new org.example.types.Message(
            grpcMessage.getId(),
            grpcMessage.getTimestamp(),
            grpcMessage.getAuthor(),
            grpcMessage.getTopic(),
            grpcMessage.getContent(),
            grpcMessage.getData().toByteArray()
        );
    }

    public static ChordProto.Message wrapMessageToGrpcMessage(org.example.types.Message message) {
        return ChordProto.Message.newBuilder()
            .setId(message.getId())
            .setTimestamp(message.getTimestamp())
            .setAuthor(message.getAuthor())
            .setTopic(message.getTopic())
            .setContent(message.getContent())
            .setData(com.google.protobuf.ByteString.copyFrom(message.getData()))
            .build();
    }
}
