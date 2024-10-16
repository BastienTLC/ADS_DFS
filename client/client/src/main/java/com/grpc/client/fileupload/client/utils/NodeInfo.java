package com.grpc.client.fileupload.client.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NodeInfo {
    @JsonProperty("ip")
    private String ip;

    @JsonProperty("port")
    private String port;

    public NodeInfo(@JsonProperty("ip") String ip, @JsonProperty("port") String port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "ip='" + ip + '\'' +
                ", port='" + port + '\'' +
                '}';
    }
}
