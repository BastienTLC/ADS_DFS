package com.grpc.server.fileupload.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Collections;

@SpringBootApplication

public class ServerApplication {

	public static void main(String[] args) {
		// TODO:
		// Send localhost:port to the bootstrap service upon booting up the server (the client functionality is implemented for this is implemented)
		// Modify so that the port is generated randomly (so that we can boot up multiple servers without specifying port)
		int port = 9090;
		SpringApplication app = new SpringApplication(ServerApplication.class);
		app.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port))); // port for spring boot
		app.setDefaultProperties(Collections.singletonMap("grpc.server.port", String.valueOf(port))); // grpc port same as the one of spring boot
		app.run(args);

	}

}
