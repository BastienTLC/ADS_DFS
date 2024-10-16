package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		int grpcPort = generateRandomPort();
		SpringApplication app = new SpringApplication(ServerApplication.class);
		Map<String, Object> properties = new HashMap<>();
		properties.put("server.port", (grpcPort+1));
		properties.put("grpc.server.port", grpcPort);
		app.setDefaultProperties(properties);
		app.run(args);
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

	private static int generateRandomPort() {
		return (int) (Math.random() * 1000) + 9000;
	}

}
