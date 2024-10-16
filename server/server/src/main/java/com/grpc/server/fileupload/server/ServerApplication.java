package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.net.ServerSocket;

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
		registerServer(grpcPort);
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
