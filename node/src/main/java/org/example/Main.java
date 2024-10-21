package org.example;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.example.base.ChordNode;
import org.example.base.ChordServiceImpl;
import org.example.base.ScheduledTask;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class Main {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, InterruptedException {
        // Default values
        String host = "localhost";
        int port = 8000;
        String existingNodeIp = null;
        int existingNodePort = -1;
        boolean multiThreadingEnabled = false;

        // Parsing arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-host":
                    if (i + 1 < args.length) {
                        host = args[++i];
                    }
                    break;
                case "-port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "-joinIp":
                    if (i + 1 < args.length) {
                        existingNodeIp = args[++i];
                    }
                    break;
                case "-joinPort":
                    if (i + 1 < args.length) {
                        try {
                            existingNodePort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid join port number: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "-multiThreading":
                    multiThreadingEnabled = true;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    break;
            }
        }

        // Create the ChordNode
        ChordNode node = new ChordNode(host, port, multiThreadingEnabled);
        ScheduledTask scheduledTask = new ScheduledTask(node);

        // Start the gRPC server
        ChordServiceImpl service = new ChordServiceImpl(node);
        Server server = ServerBuilder.forPort(port)
                .addService(service)
                .maxInboundMessageSize(1000000000 )
                .build();

        server.start();

        System.out.println("Node " + node.getNodeId() + " started on " + host + ":" + port);

        // Join the network
        if (existingNodeIp != null && existingNodePort != -1) {
            // Join the network existing node
            node.join(existingNodeIp, existingNodePort);
            System.out.println("Node joined the network via " + existingNodeIp + ":" + existingNodePort);
        } else {
            // First node in the network boorstrap
            node.join(null, -1);
            System.out.println("First node in the network initialized.");
        }

        scheduledTask.startScheduledTask();

        // Keep the server running
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down the gRPC server...");
            scheduledTask.stopScheduledTask();
            node.leave();
            node.shutdown();
            server.shutdown();
        }));

        server.awaitTermination();
    }
}
