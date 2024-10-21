package org.example.types;

public class NodeHeader{
    private String ip;
    private String port;
    private String nodeId;

    public NodeHeader(String ip, int port, String nodeId) {
        this.ip = ip;
        this.port = Integer.toString(port);
        this.nodeId = nodeId;
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

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}