package com.grpc.server.fileupload.server.utils;

import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class LoadBalancer {
    private String loadBalancerUrl;

    public LoadBalancer(String loadBalancerUrl) {
        this.loadBalancerUrl = loadBalancerUrl;
    }


    public void registerNode(String serverIp,int port) {
        RestTemplate restTemplate = new RestTemplate();
        String bootstrapUrl = loadBalancerUrl + "/addServer";

        try {
            String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, serverIp, port);
            restTemplate.postForObject(url, null, String.class);
        } catch (Exception e) {
            System.out.println("Error while registering node with load balancer: " + e.getMessage());
        }
    }

    public void deregisterNode(String serverIp, int port) {
        RestTemplate restTemplate = new RestTemplate();
        String bootstrapUrl = loadBalancerUrl + "/removeServer";

        try {
            String url = String.format("%s?ip=%s&port=%d", bootstrapUrl, serverIp, port);
            restTemplate.delete(url);
        } catch (Exception e) {
            System.out.println("Error while deregistering node with load balancer: " + e.getMessage());
        }
    }
}
