package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;

@SpringBootApplication

public class ServerApplication {

	public static void main(String[] args) {
		// TODO:
		// Send localhost:port to the bootstrap service upon booting up the server (the client functionality is implemented for this is implemented)
		// Modify so that the port is generated randomly (so that we can boot up multiple servers without specifying port)
		int grpcPort = generateRandomPort();
		SpringApplication app = new SpringApplication(ServerApplication.class);
		app.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(grpcPort))); // port for spring boot
		app.setDefaultProperties(Collections.singletonMap("grpc.server.port", String.valueOf(grpcPort))); // grpc port same as the one of spring boot
		app.run(args);
		registerServer(grpcPort);

	}

	private static void registerServer(int grpcPort) {
		RestTemplate restTemplate = new RestTemplate();
		String bootstrapUrl = "http://127.0.0.1:8085/addServer";  // URL du service Bootstrap

		try {
			String serverIp = InetAddress.getLocalHost().getHostAddress();

			String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, serverIp, grpcPort);

			String response = restTemplate.postForObject(url, null, String.class);
		} catch (UnknownHostException e) {
			System.out.println("Error while registering server to the bootstrap service");
		} catch (Exception e) {
			System.out.println("Error while registering server to the bootstrap service");
		}
	}

	private static int generateRandomPort() {
		return (int) (Math.random() * 1000) + 9000;
	}


}
