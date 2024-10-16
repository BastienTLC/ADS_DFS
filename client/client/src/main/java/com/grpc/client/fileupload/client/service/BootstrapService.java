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

    public NodeInfo getNodeByHash(String fileName) {
        String url = bootstrapUrl + "/getNodeForFile?fileName=" + fileName;
        try {
            return restTemplate.getForObject(url, NodeInfo.class);
        } catch (Exception e) {
            System.err.println("Failed to get a node by hash from bootstrap: " + e.getMessage());
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
