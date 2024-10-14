package cs.umu.se.ads.bootstrap;

public class NodeInfo {
    private String ip;
    private String port;

    public NodeInfo(String ip, String port) {
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
        return "ServerInfo{" +
                "ip='" + ip + '\'' +
                ", port='" + port + '\'' +
                '}';
    }
}