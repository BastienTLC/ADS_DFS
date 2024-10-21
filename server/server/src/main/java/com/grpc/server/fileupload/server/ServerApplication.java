package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import de.uniba.wiai.lspi.chord.data.URL;
import de.uniba.wiai.lspi.chord.service.Chord;
import de.uniba.wiai.lspi.chord.service.PropertiesLoader;
import de.uniba.wiai.lspi.chord.service.ServiceException;
import de.uniba.wiai.lspi.chord.service.impl.ChordImpl;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		int httpPort = findAvailablePort(8000, 9000);
		System.setProperty("server.port", String.valueOf(httpPort+1));
		SpringApplication.run(ServerApplication.class, args);
	}

	@Bean
	public Chord chord(ApplicationArguments args) {
		boolean isBootstrapNode = false;
		String bootstrapAddress = null;

		// Parse command-line arguments
		for (String arg : args.getSourceArgs()) {
			if (arg.equalsIgnoreCase("bootstrap")) {
				isBootstrapNode = true;
			} else if (arg.startsWith("bootstrapAddress=")) {
				bootstrapAddress = arg.substring("bootstrapAddress=".length());
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

		Chord chord;
		if (isBootstrapNode) {
			chord = initializeChordNetwork(host, chordPort);
		} else {
			if (bootstrapAddress == null) {
				System.err.println("Bootstrap address must be provided to join an existing Chord network.");
				System.exit(1);
			}
			chord = joinChordNetwork(host, chordPort, bootstrapAddress);
		}
		registerServer(grpcPort);
		return chord;
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
