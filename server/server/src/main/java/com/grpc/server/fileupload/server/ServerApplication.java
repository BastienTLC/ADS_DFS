package com.grpc.server.fileupload.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.grpc.server.fileupload.server.base.ChordNode;
import com.grpc.server.fileupload.server.base.ChordServiceImpl;
import com.grpc.server.fileupload.server.base.ScheduledTask;

import de.uniba.wiai.lspi.chord.data.URL;
import de.uniba.wiai.lspi.chord.service.Chord;
import de.uniba.wiai.lspi.chord.service.PropertiesLoader;
import de.uniba.wiai.lspi.chord.service.ServiceException;
import de.uniba.wiai.lspi.chord.service.impl.ChordImpl;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;


// import com.devProblems.example.grpc.chord.ChordGrpc.*; // this is how the generated chord proto files should be imported

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		int httpPort = findAvailablePort(8000, 9000);
		System.setProperty("server.port", String.valueOf(httpPort+1));
		SpringApplication.run(ServerApplication.class, args);
	}

	@Bean
	// changed to return void for now
	public void chord(ApplicationArguments args) {
		boolean isBootstrapNode = false;
		String joinIp = null;
		int joinPort = -1;
		boolean multiThreadingEnabled = false;

		// Parse command-line arguments
		for (String arg : args.getSourceArgs()) {
			switch (arg) {
				case "-bootstrap":
					isBootstrapNode = true;
					break;
				case "-joinIp":
					if (args.containsOption("-joinIp")) {
						joinIp = args.getOptionValues("-joinIp").get(0);
					}
					break;
				case "-joinPort":
					if (args.containsOption("-joinPort")) {
						try {
							joinPort = Integer.parseInt(args.getOptionValues("-joinPort").get(0));
						} catch (NumberFormatException e) {
							System.err.println("Invalid join port number: " + args.getOptionValues("-joinPort").get(0));
							System.exit(1);
						}
					}
					break;
				case "-multiThreading":
					multiThreadingEnabled = true;
					break;
				default:
					System.err.println("Unknown argument: " + arg);
					break;
			}
		}

		// Determine host address
		String host = "localhost"; // Default host
		try {
			host = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			System.err.println("Could not determine local host address, using 'localhost'");
		}

		// Find available ports
		int chordPort = findAvailablePort(8000, 9000);
		int grpcPort = findAvailablePort(9001, 10000);

		// Set grpc.server.port property so that gRPC server picks it up
		System.setProperty("grpc.server.port", String.valueOf(grpcPort));

		// Create the ChordNode
		ChordNode node = new ChordNode(host, chordPort, multiThreadingEnabled);
		ScheduledTask scheduledTask = new ScheduledTask(node);

		// Start the gRPC server
		ChordServiceImpl service = new ChordServiceImpl(node);
		Server server = ServerBuilder.forPort(chordPort)
				.addService(service)
				.maxInboundMessageSize(1000000000)
				.build();

		// server.start();

		System.out.println("Node " + node.getNodeId() + " started on " + host + ":" + chordPort);

		// Join the network
		if (joinIp != null && joinPort != -1) {
			// Join the network existing node
			// node.join(existingNodeIp, existingNodePort);
			// System.out.println("Node joined the network via " + existingNodeIp + ":" + existingNodePort);
		} else {
			if (!isBootstrapNode) {
				System.err.println("Bootstrap address must be provided to join an existing Chord network.");
				System.exit(1);
			}
			// First node in the network boorstrap
			// node.join(null, -1);
			System.out.println("First node in the network initialized.");
		}


		// scheduledTask.startScheduledTask();

		// Keep the server running
//		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//			System.out.println("Shutting down the gRPC server...");
//			scheduledTask.stopScheduledTask();
//			node.leave();
//			node.shutdown();
//			server.shutdown();
//		}));
//
//		server.awaitTermination();

//		registerServer(grpcPort);
//		return chord;

	}


	// Initialize the Chord network (only for the first node)
	private Chord initializeChordNetwork(String host, int port) {
		PropertiesLoader.loadPropertyFile(); // Ensure Chord properties are loaded

		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL;
		try {
			localURL = new URL(protocol + "://" + host + ":" + port + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException("Invalid URL format for Chord node.", e);
		}

		Chord chord = new ChordImpl();
		try {
			chord.create(localURL);
			System.out.println("Chord network created successfully at " + localURL);
		} catch (ServiceException e) {
			throw new RuntimeException("Could not create Chord network!", e);
		}

		return chord;
	}

	// Join an existing Chord network
	private Chord joinChordNetwork(String host, int port, String bootstrapAddress) {
		PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);

		URL localURL;
		try {
			localURL = new URL(protocol + "://" + host + ":" + port + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException("Invalid URL format for Chord node.", e);
		}

		URL bootstrapURL;
		try {
			bootstrapURL = new URL(protocol + "://" + bootstrapAddress + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException("Invalid URL of bootstrap node", e);
		}

		Chord chord = new ChordImpl();
		try {
			chord.join(localURL, bootstrapURL);
			System.out.println("Joined Chord network via bootstrap node at: " + bootstrapURL);
		} catch (ServiceException e) {
			throw new RuntimeException("Could not join Chord network!", e);
		}

		return chord;
	}

	private static void registerServer(int grpcPort) {
		RestTemplate restTemplate = new RestTemplate();
		String bootstrapUrl = "http://127.0.0.1:8085/addServer";

		try {
			String serverIp = InetAddress.getLocalHost().getHostAddress();
			String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, serverIp, grpcPort);
			restTemplate.postForObject(url, null, String.class);
		} catch (UnknownHostException e) {
			System.out.println("Error while registering server to the bootstrap service: " + e.getMessage());
		} catch (Exception e) {
			System.out.println("Error while registering server to the bootstrap service: " + e.getMessage());
		}
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
