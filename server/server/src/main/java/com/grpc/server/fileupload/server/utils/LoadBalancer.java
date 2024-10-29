package com.grpc.server.fileupload.server.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class LoadBalancer {

    @Value("${loadBalancer.url}")  // Retrieve from application.yml
    private String loadBalancerUrl;

    @Value("${node.ip}")
    private String ownIp;

    public LoadBalancer() {
        // Default constructor
    }

    public void registerNode(String serverIp, int port) {
        RestTemplate restTemplate = new RestTemplate();
        String bootstrapUrl = loadBalancerUrl + "/addServer";

        try {
            String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, ownIp, port);
            restTemplate.postForObject(url, null, String.class);
        } catch (Exception e) {
            System.out.println("Error while registering node with load balancer: " + e.getMessage());
        }
    }

    public void deregisterNode(String serverIp, int port) {
        RestTemplate restTemplate = new RestTemplate();
        String bootstrapUrl = loadBalancerUrl + "/removeServer";

        try {
            String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, ownIp, port);
            restTemplate.delete(url);
        } catch (Exception e) {
            System.out.println("Error while deregistering node with load balancer: " + e.getMessage());
        }
    }
}
