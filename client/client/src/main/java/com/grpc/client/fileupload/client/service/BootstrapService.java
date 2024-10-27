package com.grpc.client.fileupload.client.service;

import com.grpc.client.fileupload.client.utils.NodeInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

@Service
public class BootstrapService {

    private final RestTemplate restTemplate;
    private final String bootstrapUrl;
    // Here the service to interact with the bootstrap server is defined in application.yml
    public BootstrapService(RestTemplate restTemplate, @Value("${bootstrap.url}") String bootstrapUrl) {
        this.restTemplate = restTemplate;
        this.bootstrapUrl = bootstrapUrl;
    }

    public NodeInfo getRandomServer() {
        String url = bootstrapUrl + "/getRandomServer";
        try {
            return restTemplate.getForObject(url, NodeInfo.class);
        } catch (Exception e) {
            System.err.println("Failed to get a random server from bootstrap: " + e.getMessage());
            return null;
        }
    }

    public NodeInfo getRoundRobinServer() {
        String url = bootstrapUrl + "/getRoundRobinServer";
        try {
            return restTemplate.getForObject(url, NodeInfo.class);
        } catch (Exception e) {
            System.err.println("Failed to get a round robin server from bootstrap: " + e.getMessage());
            return null;
        }
    }

    public NodeInfo getServer(){
        String url = bootstrapUrl + "/getServer";
        try {
            return restTemplate.getForObject(url, NodeInfo.class);
        } catch (Exception e) {
            System.err.println("Failed to get a server from bootstrap: " + e.getMessage());
            return null;
        }
    }

    public List<NodeInfo> getAllServers() {
        String url = bootstrapUrl + "/getServers";
        try {
            NodeInfo[] nodeArray = restTemplate.getForObject(url, NodeInfo[].class);
            return Arrays.asList(nodeArray);
        } catch (Exception e) {
            System.err.println("Failed to get all servers from bootstrap: " + e.getMessage());
            return null;
        }
    }
}
