package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.security.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.net.ServerSocket;

import de.uniba.wiai.lspi.chord.com.Endpoint;
import de.uniba.wiai.lspi.chord.com.Proxy;
import de.uniba.wiai.lspi.chord.data.*;
import de.uniba.wiai.lspi.chord.service.Chord;
import de.uniba.wiai.lspi.chord.service.ServiceException;
import de.uniba.wiai.lspi.chord.service.PropertiesLoader;
import de.uniba.wiai.lspi.chord.service.impl.ChordImpl;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		//generate random port between 9000 and 9999
		int grpcPort = generateRandomPort();
		SpringApplication app = new SpringApplication(ServerApplication.class);
		Map<String, Object> properties = new HashMap<>();
		properties.put("server.port", (grpcPort+1));
		properties.put("grpc.server.port", grpcPort);
		app.setDefaultProperties(properties);
		app.run(args);
		//register server to the bootstrap service
		// registerServer(grpcPort);
		Chord chord = initializeChordNetwork("localhost", 8080); // this will only be done for the first node
	}

	// Chord functions, these can be broken into classes later

	// this is only done by the first node
	public static Chord initializeChordNetwork(String host, int port) {
		PropertiesLoader.loadPropertyFile(); // have not configured the properties file

		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL = null;
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

	public static Chord joinChordNetwork(String host, int port, String bootstrapHost, String bootstrapPort) {
		PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);

		URL localURL = null;
		try {
			localURL = new URL(protocol + "://" + host + ":" + port + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException("Invalid URL format for Chord node.", e);
		}

		URL bootstrapURL = null;
		try {
			bootstrapURL = new URL(protocol + "://" + bootstrapHost + ":" + bootstrapPort + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException("Invalid URL of bootstrap node", e);
		}

		Chord chord = new ChordImpl();
		try{
			chord.join(localURL, bootstrapURL);
			System.out.println("Chord network joined at: " + bootstrapURL);
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




	private static boolean isPortAvailable(int port) {
		try (ServerSocket socket = new ServerSocket(port)) {
			socket.setReuseAddress(true);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// generates random port and makes sure that it is available
	private static int generateRandomPort() {
		int port;
		do {
			port = (int) (Math.random() * 1000) + 9000;
		} while (!isPortAvailable(port));
		return port;
	}

}
