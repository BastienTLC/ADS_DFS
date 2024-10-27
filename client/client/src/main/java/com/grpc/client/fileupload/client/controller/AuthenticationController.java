package com.grpc.client.fileupload.client.controller;

import com.grpc.client.fileupload.client.service.AuthenticationService;
import com.grpc.client.fileupload.client.model.User;
import com.grpc.client.fileupload.client.model.AuthenticationRequest;
import com.grpc.client.fileupload.client.model.AuthenticationResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        return authenticationService.register(user);
    }

    @PostMapping("/login")
    public AuthenticationResponse login(@RequestBody AuthenticationRequest authenticationRequest) {
        return authenticationService.login(authenticationRequest);
    }
}
