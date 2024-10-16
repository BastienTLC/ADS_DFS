package cs.umu.se.ads.bootstrap;

import java.util.Objects;

public class NodeInfo {
    private String ip;
    private String port;
    private int hashCode;

    public NodeInfo(String ip, String port) {
        this.ip = ip;
        this.port = port;
        this.hashCode = computeHashCode();

    }

    private int computeHashCode() {
        String key = ip + ":" + port;
        return Objects.hash(key);  // Utilisation du hash de la chaîne "ip:port"
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

    public int getHashCode() {
        return hashCode;
    }

    public void setHashCode(int hashCode) {
        this.hashCode = hashCode;
    }


    @Override
    public String toString() {
        return "NodeInfo{" +
                "ip='" + ip + '\'' +
                ", port='" + port + '\'' +
                ", hashCode=" + hashCode +
                '}';
    }
}