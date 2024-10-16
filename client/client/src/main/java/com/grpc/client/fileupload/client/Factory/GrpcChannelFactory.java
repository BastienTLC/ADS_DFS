package com.grpc.client.fileupload.client.Factory;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class GrpcChannelFactory {

    private final Map<String, ManagedChannel> channels = new HashMap<>();

    // This method is used to create a channel to the server with the given IP and port
    // If a channel to the server with the given IP and port already exists, it returns the existing channel
    // Otherwise, it creates a new channel and returns it
    public ManagedChannel getChannel(String ip, int port) {
        String key = ip + ":" + port;
        if (channels.containsKey(key)) {
            return channels.get(key);
        } else {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(ip, port)
                    .usePlaintext()
                    .build();
            channels.put(key, channel);
            return channel;
        }
    }

    public void shutdownAll() {
        channels.values().forEach(ManagedChannel::shutdown);
    }
}

