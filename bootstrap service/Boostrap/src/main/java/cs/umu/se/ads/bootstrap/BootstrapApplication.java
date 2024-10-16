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

    public static void main(String[] args)
    {
        int port = 8085;
        SpringApplication app = new SpringApplication(BootstrapApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", String.valueOf(port)));
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

        System.out.println("Returning random node: '" + randomNode.getIp() + ":" + randomNode.getPort()  + "'");

        return ResponseEntity.ok(randomNode);
    }


    @GetMapping("/getNodeForFile")
    public ResponseEntity<NodeInfo> getNodeForFile(@RequestParam String fileName) {
        if (currentNodes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // Compute hash of the file name
        int fileHash = Objects.hash(fileName);
        // Find the closest node to the hash by comparing the hash of the node
        NodeInfo selectedNode = findClosestNode(fileHash);

        System.out.println("Returning the node: '" + selectedNode.getIp() + ":" + selectedNode.getPort()  + "'"
            + " for the file: '" + fileName + "'" + "with the hash: " + fileHash);

        return ResponseEntity.ok(selectedNode);
    }

    //function to get the replica nodes for a file
    //The function will return all the nodes except the selected node
    //Need to implement other strategies for replica selection
    @GetMapping("/getReplicaNodes")
    public ResponseEntity<List<NodeInfo>> getReplicaNodes(@RequestParam String fileName) {
        if (currentNodes.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        // Compute hash of the file name
        int fileHash = Objects.hash(fileName);
        // Find the closest node to the hash by comparing the hash of the node
        NodeInfo selectedNode = findClosestNode(fileHash);
        List<NodeInfo> replicaNodes = new ArrayList<>();
        for (NodeInfo node : currentNodes.values()) {
            if (!node.equals(selectedNode)) {
                replicaNodes.add(node);
            }
        }

        return ResponseEntity.ok(replicaNodes);
    }

    @GetMapping("/getReplicaNode")
    public ResponseEntity<List<NodeInfo>> getReplicaNodes(@RequestParam String fileName, @RequestParam int replicaNb){
        //need to be implemented
        return ResponseEntity.ok(new ArrayList<>(currentNodes.values()));
    }



    private NodeInfo findClosestNode(int fileHash) {
        NodeInfo closestNode = null;
        int closestDistance = Integer.MAX_VALUE;

        for (NodeInfo node : currentNodes.values()) {
            int nodeHash = node.getHashCode();
            int distance = Math.abs(nodeHash - fileHash);

            if (distance < closestDistance) {
                closestDistance = distance;
                closestNode = node;
            }
        }

        return closestNode;
    }


}
