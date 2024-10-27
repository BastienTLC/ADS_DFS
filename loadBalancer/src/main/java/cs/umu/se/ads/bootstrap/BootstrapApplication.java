package cs.umu.se.ads.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@SpringBootApplication
@RestController
public class BootstrapApplication {

    private static final Map<String, NodeInfo> currentNodes = new ConcurrentHashMap<>();
    private int roundRobinIndex = 0;
    private static String loadBalancingStrategy = "random"; // Default strategy is random

    public static void main(String[] args) {
        int port = 8085;
        SpringApplication app = new SpringApplication(BootstrapApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));

        // Set the load balancing strategy based on the program argument
        if (args.length > 0) {
            if ("roundRobin".equalsIgnoreCase(args[0])) {
                loadBalancingStrategy = "roundRobin";
            } else if ("random".equalsIgnoreCase(args[0])) {
                loadBalancingStrategy = "random";
            }
        }

        app.run(args);
    }

    @PostMapping("/addServer")
    public ResponseEntity<String> registerServer(@RequestParam String ip, @RequestParam String port) {
        String nodeKey = ip + ":" + port;

        System.out.println("Adding node '" + nodeKey + "'");

        if (currentNodes.containsKey(nodeKey)) {
            return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body("Server already added.");
        }

        NodeInfo nodeInfo = new NodeInfo(ip, port);
        currentNodes.put(nodeKey, nodeInfo);

        return ResponseEntity.ok("Server added successfully.");
    }

    @DeleteMapping("/removeServer")
    public ResponseEntity<String> deregisterServer(@RequestParam String ip, @RequestParam String port) {
        System.out.println("Removing node '" + ip + ":" + port + "'");

        String nodeKey = ip + ":" + port;

        if (currentNodes.remove(nodeKey) == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Server not found.");
        }

        return ResponseEntity.ok("Server removed successfully.");
    }

    @GetMapping("/getServers")
    public ResponseEntity<List<NodeInfo>> getRegisteredServers() {
        return ResponseEntity.ok(new ArrayList<>(currentNodes.values()));
    }

    @GetMapping("/getRandomServer")
    public ResponseEntity<NodeInfo> getRandomServer() {
        List<NodeInfo> serverList = new ArrayList<>(currentNodes.values());

        if (serverList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(serverList.size());
        NodeInfo randomNode = serverList.get(randomIndex);

        System.out.println("Returning random node: '" + randomNode.getIp() + ":" + randomNode.getPort() + "'");

        return ResponseEntity.ok(randomNode);
    }

    @GetMapping("/getRoundRobinServer")
    public ResponseEntity<NodeInfo> getRoundRobinServer() {
        List<NodeInfo> serverList = new ArrayList<>(currentNodes.values());

        if (serverList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        NodeInfo roundRobinNode = serverList.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % serverList.size();

        System.out.println("Returning round robin node: '" + roundRobinNode.getIp() + ":" + roundRobinNode.getPort() + "'");

        return ResponseEntity.ok(roundRobinNode);
    }

    // New endpoint to get a server based on the configured load balancing strategy
    @GetMapping("/getServer")
    public ResponseEntity<NodeInfo> getServer() {
        if ("roundRobin".equalsIgnoreCase(loadBalancingStrategy)) {
            // Use the round-robin strategy
            return getRoundRobinServer();
        } else {
            // Default to random strategy
            return getRandomServer();
        }
    }
}
