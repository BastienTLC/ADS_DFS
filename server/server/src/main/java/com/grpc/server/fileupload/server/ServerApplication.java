package com.grpc.server.fileupload.server;

import com.grpc.server.fileupload.server.base.ScheduledTask;
import com.grpc.server.fileupload.server.interceptor.FileUploadInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.grpc.server.fileupload.server.base.ChordNode;
import com.grpc.server.fileupload.server.base.ChordServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;

@SpringBootApplication
public class ServerApplication implements CommandLineRunner {

	@Autowired
	private ApplicationArguments appArgs;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ServerApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE); // Disable embedded HTTP server
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		// Default values
		boolean isBootstrapNode = false;
		String joinIp = null;
		int joinPort = -1;
		boolean multiThreadingEnabled = false;

		// Parse command-line arguments
		if (appArgs.containsOption("bootstrap")) {
			isBootstrapNode = true;
		}
		if (appArgs.containsOption("joinIp")) {
			joinIp = appArgs.getOptionValues("joinIp").get(0);
		}
		if (appArgs.containsOption("joinPort")) {
			try {
				joinPort = Integer.parseInt(appArgs.getOptionValues("joinPort").get(0));
			} catch (NumberFormatException e) {
				System.err.println("Invalid join port number: " + appArgs.getOptionValues("joinPort").get(0));
				System.exit(1);
			}
		}
		if (appArgs.containsOption("multiThreading")) {
			multiThreadingEnabled = true;
		}

		// Determine host address
		String host;
		try {
			host = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			System.err.println("Could not determine local host address, using 'localhost'");
			host = "localhost";
		}

		// Determine the Chord node's port
		int chordPort = findAvailablePort(8000, 9000);

		// Create the ChordNode
		ChordNode node = new ChordNode(host, chordPort, multiThreadingEnabled);
		ScheduledTask scheduledTask = new ScheduledTask(node);

		// Start the gRPC server
		ChordServiceImpl chordService = new ChordServiceImpl(node);
		Server server = ServerBuilder.forPort(chordPort)
				.addService(chordService)
				.intercept(new FileUploadInterceptor())
				.maxInboundMessageSize(100 * 1024 * 1024) // 100 MB
				.build()
				.start();

		System.out.println("Node " + node.getNodeId() + " started on " + host + ":" + chordPort);

		// Join the network
		if (isBootstrapNode) {
			// First node in the network (bootstrap node)
			node.join(null, -1);
			System.out.println("Bootstrap node initialized.");
		} else {
			// Join via specified bootstrap node, or default to localhost:8000
			if (joinIp == null) {
				joinIp = "localhost";
			}
			if (joinPort == -1) {
				joinPort = 8000;
			}
			node.join(joinIp, joinPort);
			System.out.println("Node joined the network via " + joinIp + ":" + joinPort);
		}

		scheduledTask.startScheduledTask();

		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Shutting down the gRPC server...");
			node.leave();
			server.shutdown();
		}));

		// Keep the server running
		server.awaitTermination();
	}

	// Method to find an available port within a given range
	private static int findAvailablePort(int minPort, int maxPort) {
		for (int port = minPort; port <= maxPort; port++) {
			if (isPortAvailable(port)) {
				return port;
			}
		}
		throw new RuntimeException("No available port found in the range " + minPort + "-" + maxPort);
	}

	// Check if a port is available
	private static boolean isPortAvailable(int port) {
		try (ServerSocket socket = new ServerSocket(port)) {
			socket.setReuseAddress(true);
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}