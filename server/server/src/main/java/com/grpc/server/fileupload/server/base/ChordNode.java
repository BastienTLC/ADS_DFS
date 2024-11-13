package com.grpc.server.fileupload.server.base;


import com.devProblems.Fileupload;
import com.grpc.server.fileupload.server.types.NodeHeader;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.grpc.server.fileupload.server.utils.LoadBalancer;
import com.grpc.server.fileupload.server.utils.TreeBasedReplication;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChordNode {
    private final String ip;
    private final int port;
    private final String nodeId; // Unique node ID based on the hash of IP and port
    private NodeHeader successor; // Successor of this node
    private NodeHeader predecessor; // Predecessor of this node
    private final FingerTable fingerTable; // Finger table for routing
    private final int m; // Number of bits for the identifier space
    private NodeHeader currentHeader;
    private FileStore fileStore;
    private final boolean multiThreadingEnabled;
    private final ExecutorService executorService;
    private final LoadBalancer loadBalancer;
    private TreeMap<String, List<String>> fileMap; // when node joins fill this tree with existing files
    private TreeMap<String, List<String>> predecessorReplicationMap;
    private boolean crashFlag;


    public ChordNode(String ip, int port, boolean multiThreadingEnabled, LoadBalancer loadBalancer, int m) {
        this.ip = ip;
        this.port = port;
        this.m = m;
        this.multiThreadingEnabled = multiThreadingEnabled;
        if (this.multiThreadingEnabled) {
            this.executorService = Executors.newCachedThreadPool();
        } else {
            this.executorService = null;
        }
        this.nodeId = hashNode(ip + ":" + port);
        this.fingerTable = new FingerTable(this, m);
        this.currentHeader = new NodeHeader(ip, port, nodeId);
        this.fileStore = new FileStore();
        this.loadBalancer = loadBalancer;
        this.fileMap = new TreeMap<>();
        this.predecessorReplicationMap = new TreeMap<>(); // here copy of predecessors tree map will be stored

        this.crashFlag = false;
    }

    // Function to hash the node ID based on IP and port
    private String hashNode(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hashBytes = md.digest(input.getBytes());
            BigInteger hashInt = new BigInteger(1, hashBytes);
            BigInteger mod = BigInteger.valueOf(2).pow(m);
            return hashInt.mod(mod).toString();
        } catch (NoSuchAlgorithmException e) {
            // e.printStackTrace();
            return null;
        }
    }

    public String getNodeId() { return this.nodeId; }
    public String getIp() { return this.ip; }
    public int getPort() { return this.port; }
    public NodeHeader getSuccessor() { return this.successor; }
    public void setSuccessor(NodeHeader successor) { this.successor = successor; }
    public NodeHeader getPredecessor() { return this.predecessor; }
    public void setPredecessor(NodeHeader predecessor) {
        if (predecessor == null) {
            System.out.println("Updating predecessor... Received null predecessor.");
            this.predecessor = null;
            return;
        }

        if (this.predecessor == null && crashFlag) {
            System.out.println("New predecessor found: " + predecessor.getIp() + ":" + predecessor.getPort());
            this.predecessor = predecessor;

            printPredecessorMap();

            Set<String> retrievedFilesSet = new HashSet<>();
            for (Map.Entry<String, List<String>> entry : predecessorReplicationMap.entrySet()) {
                String key = entry.getKey(); // the key is not needed for this part
                List<String> files = entry.getValue();

                for (String file : files) {

                    String[] fileDetails = file.split(":");
                    String filename = fileDetails[0];
                    String username = fileDetails[1];

                    // 1. Node should check if we are already storing the file. In that case, we do not need to retrieve it
                    // (and remember mappings will be merged done later for the entire map)
                    if (checkMappingForFileIdentifier(file)) {
                        System.out.println("A replica of this file already exists, so retrieval of it will not be needed.");
                    }
                    // Check: If we've already retrieved this file before, skip it
                    else if (retrievedFilesSet.contains(file)) {
                        System.out.println("File '" + file + "' already retrieved. Skipping...");
                    }
                    // 2. Before calling retrieveCopyOfFile, it should create a tree using the filename.
                    else {
                        String id = hashNode(filename);

                        int depth = 2; // depth of the tree it will be doing replication for
                        TreeBasedReplication treeReplication = new TreeBasedReplication(m);
                        Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(id), depth);
                        List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);


                        // 3. Iterates over the tree, making sure it is not responsible for the id it will retrieve file from
                        for (Integer replicaKey : leafNodes) {
                            // only if it is not responsible for the node
                            if (!isIdWithinSpan(String.valueOf(replicaKey))) {
                                if (!retrievedFilesSet.contains(file)) {
                                    NodeHeader responsibleNode = findSuccessor(String.valueOf(replicaKey));
                                    System.out.println("Obtaining the file '" + file + "' from " + responsibleNode.getIp() + ":" + responsibleNode.getPort());
                                    ChordClient responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));
                                    responsibleNodeClient.retrieveCopyOfFile(filename, username, this); // this does not add any mapping I think
                                    responsibleNodeClient.shutdown();

                                    retrievedFilesSet.add(file);
                                }
                            }
                        }
                    }
                }
            }

            // 1. merging the fileMap with the precessorMap, then clearing predecessorMap (belonging to node that crashed)
            mergeFileMaps();
            System.out.println("Merged current fileMap with the predecessors map");
            printFileMap();

            // 2. send its updated fileMap to the successor
            if (this.successor != null) {
                if (successor.getIp() != this.getIp() || Integer.parseInt(successor.getPort()) != this.getPort()){
                    System.out.println("Sending the updated fileMap to successor...");
                    ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
                    successorClient.sendFileMappings(this.fileMap);
                    successorClient.shutdown();
                }
            }
            else {
                System.out.println("Update fileMap was not sent since successor was null!");
            }

            // 3. update its predecessorReplicationMap with the new predecessors replicationMap
            // Currently this node will actively ask its new predecessor for its replication map,
            // however, should probably try change it so that the map instead is included in the setPredecessor call
            if (predecessor.getIp() != this.getIp() || Integer.parseInt(predecessor.getPort()) != this.getPort()){
                System.out.println("Retrieving fileMap from new predecessor....");
                ChordClient predecessorClient = new ChordClient(predecessor.getIp(), Integer.parseInt(predecessor.getPort()));
                Map<String, List<String>> newPredecessorFileMap = predecessorClient.fetchNewPredecessorFileMappings();
                if (newPredecessorFileMap != null){
                    updatePredecessorReplicationMap(newPredecessorFileMap);
                }
                else {
                    System.out.println("New predecessor map was null!");
                }

                predecessorClient.shutdown();

            }

            crashFlag = false;

        }
        else{
            this.predecessor = predecessor;
        }
    }

    public FingerTable getFingerTable() { return this.fingerTable; }
    public FileStore getMessageStore() { return this.fileStore; }

    // Method to join the network
    public void join(String existingNodeIp, int existingNodePort) {
        boolean isBootstrap;
        if (existingNodeIp != null) {
            ChordClient chordClient = new ChordClient(existingNodeIp, existingNodePort);
            initFingerTable(chordClient);
            this.updateOthers();
            chordClient.shutdown();
            isBootstrap = false;
        } else {
            for (int i = 0; i < m; i++) {
                fingerTable.getFingers().set(i, currentHeader);
            }
            this.predecessor = currentHeader;
            this.successor = currentHeader;
            isBootstrap = true;
        }

        //call loadbalancer to register the node (ip, port)
        loadBalancer.registerNode(this.ip, this.port);

        // condition so that the bootstrap node won't enter
        if (!isBootstrap) {
            // this will get the data transferred to us from the successor
            // I think it will simply overwrite existing files if we have them already
            ChordClient successorClient = new ChordClient(this.successor.getIp(), Integer.parseInt(this.successor.getPort()));

            String[] span = getResponsibleSpan();

            // this is the span that the joining node will be responsible for
            System.out.println("Span: " + span[0] + " - " + span[1]);

            if (isWrappedAround()) {
                successorClient.retrieveFiles(span[0], String.valueOf((int)Math.pow(2, m) - 1), this);
                successorClient.retrieveFiles("0", span[1], this);
            } else {
                successorClient.retrieveFiles(span[0], span[1], this);
            }

            successorClient.shutdown();
        }

        addMappingForExistingFiles();

        printFingerTable();
        printResponsibleSpan();
    }

    private boolean isWrappedAround() {
        if (predecessor == null) return false;

        int start = Integer.parseInt(predecessor.getNodeId());
        int end = Integer.parseInt(this.nodeId);

        // no wrap-around
        if (start < end) {
            return false;
        }
        // wrap around
        else {
            return true;
        }

    }

    public void mergeFileMaps() {
        fileMap.putAll(predecessorReplicationMap);

        predecessorReplicationMap.clear();
    }

    public void printResponsibleSpan() {
        if (predecessor == null) {
            System.out.println("Responsible for entire range: 0 to " + (Math.pow(2, m) - 1));
        } else {
            int start = Integer.parseInt(predecessor.getNodeId());
            int end = Integer.parseInt(this.nodeId);

            if (start < end) {
                System.out.println("Responsible span: (" + start + ", " + end + "]");
            } else {
                // handling the wrap around case
                System.out.println("Responsible span: (" + start + ", " + (Math.pow(2, m) - 1) + "] and [0, " + end + "]");
            }
        }
    }

    public String[] getResponsibleSpan() {
        String[] span = new String[2];

        if (predecessor == null) {
            span[0] = "0";
            span[1] = String.valueOf((Math.pow(2, m) - 1));
        }
        else{
            int start = Integer.parseInt(predecessor.getNodeId());
            int end = Integer.parseInt(this.nodeId);
            // if start of span is not 2^m -1, the responsible span will be this.predecessor.getNodeId() + 1

            // no wrap-around
            if (start < end) {
                span[0] = String.valueOf(start + 1);
                span[1] = String.valueOf(end);
            }
            // if wrap-around
            else {
                // so if predecessor id is the highest number in the chord ring
                if (start == (Math.pow(2, m) - 1)) {
                    span[0] = "0";
                    span[1] = this.nodeId;
                }
                else {
                    span[0] = String.valueOf(start + 1);
                    span[1] = String.valueOf(end);
                }

            }
        }

        return span;
    }

    // currently this function only used when node does re-replication, might use it in other functions too
    public boolean isIdWithinSpan(String id) {
        String[] span = getResponsibleSpan();
        int start = Integer.parseInt(span[0]);
        int end = Integer.parseInt(span[1]);
        int targetId = Integer.parseInt(id);

        // Case 1: No wrap-around
        if (start <= end) {
            return (targetId >= start && targetId <= end);
        }
        // Case 2: Wrap-around
        else {
            return (targetId >= start || targetId <= end);
        }
    }

    public void printFingerTable() {
        System.out.println("Finger Table for Node ID: " + this.nodeId);

        for (int i = 0; i < m; i++) {
            NodeHeader finger = fingerTable.getFingers().get(i);
            String start = fingerTable.calculateFingerStart(i);

            if (finger != null) {
                System.out.printf("Entry %d: Start = %s, Node ID = %s, Address = %s:%s%n",
                        i, start, finger.getNodeId(), finger.getIp(), finger.getPort());
            } else {
                System.out.printf("Entry %d: Start = %s, Node ID = null%n", i, start);
            }
        }

        System.out.println();
    }


    public void printFileMap() {
        System.out.println("Printing fileMap of current node...");
        for (Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            System.out.println("Key: " + key);

            System.out.println("Values:");
            if (values != null && !values.isEmpty()) {
                for (String value : values) {
                    System.out.println(" - " + value);
                }
            } else {
                System.out.println(" - (No values)");
            }
        }
    }

    public void printPredecessorMap() {
        System.out.println("Printing predecessorMap of current node...");
        for (Map.Entry<String, List<String>> entry : predecessorReplicationMap.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            System.out.println("Key: " + key);

            System.out.println("Values:");
            if (values != null && !values.isEmpty()) {
                for (String value : values) {
                    System.out.println(" - " + value);
                }
            } else {
                System.out.println(" - (No values)");
            }
        }
    }

    public TreeMap<String, List<String>> getFileMap() {
        return new TreeMap<>(fileMap);
    }

    // in the map key is the id used when replicating, value is a list of filename:username
    // the span is a calculated wrong somewhere in regards to <=, since 25 can be stored on two nodes at once for example
    public void addMapping(String filename, String username){
        String id = hashNode(filename);
        String value = filename + ":" + username;

        int depth = 2;
        System.out.println("The ID of the filename used for generating the tree is: " + id);
        TreeBasedReplication treeReplication = new TreeBasedReplication(m);
        Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(id), depth);
        List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);
        System.out.println("Leaf nodes generated for the mapping: " + leafNodes);


        String[] span = getResponsibleSpan();
        int spanStart = Integer.parseInt(span[0]);
        int spanEnd = Integer.parseInt(span[1]);
        System.out.println("Adding mapping for " + value + ", based on which replicas fall into the span '" + spanStart + "-" + spanEnd + "'");

        for (Integer replicaKey : leafNodes) {
            // Case 1 : entire span is covered, for first node, 25-25 (this might break if node is only responsible for 1 id)
            if (spanStart == spanEnd) {
                fileMap.computeIfAbsent(String.valueOf(replicaKey), k -> new ArrayList<>());
                if (!fileMap.get(String.valueOf(replicaKey)).contains(value)) {
                    System.out.println("Replica " + replicaKey + " has been added to the fileMap");
                    fileMap.get(String.valueOf(replicaKey)).add(value);
                }
            }
            // Case 2: No wraparound
            else if (spanStart <= spanEnd) {
                if (replicaKey >= spanStart && replicaKey <= spanEnd) {
                    // replicaKey is within span
                    fileMap.computeIfAbsent(String.valueOf(replicaKey), k -> new ArrayList<>());
                    if (!fileMap.get(String.valueOf(replicaKey)).contains(value)) {
                        System.out.println("Replica " + replicaKey + " has been added to the fileMap");
                        fileMap.get(String.valueOf(replicaKey)).add(value);
                    }
                }
            }
            // Case 3: Wraparound
            else {
                int maxId = (int) Math.pow(2, m) - 1; // so for m =6 max is 63
                if ((replicaKey >= spanStart && replicaKey <= maxId) || (replicaKey >= 0 && replicaKey <= spanEnd)) {
                    // replicaKey is within span
                    fileMap.computeIfAbsent(String.valueOf(replicaKey), k -> new ArrayList<>());
                    if (!fileMap.get(String.valueOf(replicaKey)).contains(value)) {
                        System.out.println("Replica " + replicaKey + " has been added to the fileMap");
                        fileMap.get(String.valueOf(replicaKey)).add(value);
                    }
                }
            }
        }

        if (this.successor != null) {
            if (successor.getIp() != this.getIp() || Integer.parseInt(successor.getPort()) != this.getPort()){
                System.out.println("Sending the updated fileMap to successor...");
                ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
                printFileMap();
                successorClient.sendFileMappings(this.fileMap);
                successorClient.shutdown();
            }
        }
    }

    public synchronized void updatePredecessorReplicationMap(Map<String, List<String>> newMap) {
        this.predecessorReplicationMap.clear();
        this.predecessorReplicationMap.putAll(newMap);
        System.out.println("Updated predecessorReplicationMap!");
    }

    public synchronized Map<String, List<String>> getPredecessorReplicationMap() {
        return new HashMap<>(this.predecessorReplicationMap);
    }



    // id in this case is the id that was generated from replication tree
    public void removeMappingForId(String id) {
        // Check if the list associated with the id exists in the map
        if (fileMap.containsKey(id) && fileMap.get(id) != null && !fileMap.get(id).isEmpty()) {
            // Remove the entire list of mappings associated with the id
            fileMap.remove(id);
            System.out.println("removeMappingForId() called, removed entire mapping for id: " + id);
        } else {
            System.out.println("No mapping found for id: " + id);
        }
    }

    // we check if for example filename:user exists in mapping
    public boolean checkMappingForFileIdentifier(String identifier) {
        for (Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
            List<String> filesList = entry.getValue();

            if (filesList.contains(identifier)) {
                return true;
            }
        }
        return false; // not found
    }


    void addMappingForExistingFiles() {
        // Base directory path
        String baseDirPath = "output/" + this.nodeId;
        File baseDir = new File(baseDirPath);

        if (!baseDir.exists() || !baseDir.isDirectory()) {
            System.out.println("Base directory does not exist: " + baseDirPath);
            return;
        }

        // Iterate over each user's directory inside baseDir
        File[] userDirs = baseDir.listFiles(File::isDirectory);
        if (userDirs == null) {
            System.out.println("No user directories found.");
            return;
        }

        for (File userDir : userDirs) {
            String username = userDir.getName();

            // iterating over each file in the user's directory
            File[] files = userDir.listFiles(File::isFile);
            if (files == null) {
                System.out.println("No files found for user: " + username);
                continue;
            }

            for (File file : files) {
                String filename = file.getName();
                System.out.println("Adding mapping for '" + filename + ":" + username + "'");
                addMapping(filename, username); // adding mapping for each file, this will also send copy to successor of our fileMap
            }
        }

        System.out.println("All currently existing files have been mapped.");
        printFileMap();
    }

    public Map<String, List<String>> getFilesInRange(String startHash, String endHash) {
        Map<String, List<String>> filesInRange = new HashMap<>();

        int start = Integer.parseInt(startHash);
        int end = Integer.parseInt(endHash);
        int maxValue = (int) (Math.pow(2, m) - 1);

        for (Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
            String key = entry.getKey();
            int keyHash = Integer.parseInt(key);

            boolean inRange;
            if (start <= end) {
                // non-wraparound case
                inRange = keyHash >= start && keyHash <= end;
            } else {
                // wraparound case
                inRange = (keyHash >= start && keyHash <= maxValue) || (keyHash >= 0 && keyHash <= end);
            }

            if (inRange) {
                filesInRange.put(key, entry.getValue());
            }
        }

        return filesInRange;
    }

    public void printFilesInRange(String startHash, String endHash) {
        Map<String, List<String>> filesInRange = getFilesInRange(startHash, endHash);
        System.out.println("Files in range " + startHash + "-" + endHash + ":");
        filesInRange.forEach((hash, fileKeys) -> {
            System.out.println("Hash: " + hash);
            fileKeys.forEach(fileKey -> System.out.println("    File: " + fileKey));
        });
    }



    // Method to update other nodes finger tables
    public void updateOthers() {
        System.out.println("updateOthers called...");
        BigInteger mod = BigInteger.valueOf(2).pow(m);
        BigInteger nodeIdInt = new BigInteger(this.nodeId);

        for (int i = 0; i < m; i++) {
            BigInteger pow = BigInteger.valueOf(2).pow(i);
            BigInteger idInt = nodeIdInt.subtract(pow).mod(mod);
            String id = idInt.toString();

            NodeHeader predecessorNode = findPredecessor(id);

            final String predecessorIp = predecessorNode.getIp();
            final int predecessorPort = Integer.parseInt(predecessorNode.getPort());
            final int index = i;

            Runnable task = () -> {
                ChordClient predecessorClient = new ChordClient(predecessorIp, predecessorPort);
                predecessorClient.updateFingerTable(this.currentHeader, index);
                predecessorClient.shutdown();
            };

            executeGrpcCall(task);
        }

        System.out.println("---- Responsible span and finger table ---- ");
        printResponsibleSpan();
        printFingerTable();


    }

    public void updateFingerTable(NodeHeader s, int i) {
        NodeHeader currentFinger = fingerTable.getFingers().get(i);
        if (currentFinger == null || isInIntervalClosedOpen(s.getNodeId(), this.nodeId, currentFinger.getNodeId())) {
            fingerTable.getFingers().set(i, s);
            NodeHeader p = this.predecessor;
            if (p != null && !p.equals(this.currentHeader)) {
                final String predecessorIp = p.getIp();
                final int predecessorPort = Integer.parseInt(p.getPort());

                Runnable task = () -> {
                    ChordClient pClient = new ChordClient(predecessorIp, predecessorPort);
                    pClient.updateFingerTable(s, i);
                    pClient.shutdown();
                };

                executeGrpcCall(task);
            }
        }
    }

    // Method to find the successor of a given ID
    public NodeHeader findSuccessor(String id) {
        NodeHeader predecessorNode = findPredecessor(id);

        ChordClient predecessorClient = new ChordClient(predecessorNode.getIp(), Integer.parseInt(predecessorNode.getPort()));
        NodeHeader successor = predecessorClient.getSuccessor();
        predecessorClient.shutdown();

        return successor;
    }

    // Method to find the predecessor of a given ID
    public NodeHeader findPredecessor(String id) {
        NodeHeader nPrime = this.currentHeader;

        while (true) {
            NodeHeader nPrimeSuccessor;

            if (nPrime.equals(this.currentHeader)) {
                nPrimeSuccessor = this.getSuccessor();
            } else {
                ChordClient nPrimeClient = new ChordClient(nPrime.getIp(), Integer.parseInt(nPrime.getPort()));
                nPrimeSuccessor = nPrimeClient.getSuccessor();
                nPrimeClient.shutdown();
            }

            if (isInIntervalOpenClosed(id, nPrime.getNodeId(), nPrimeSuccessor.getNodeId())) {
                return nPrime;
            } else {
                NodeHeader closestFinger;
                if (nPrime.equals(this.currentHeader)) {
                    closestFinger = this.closestPrecedingFinger(id);
                } else {
                    ChordClient nPrimeClient = new ChordClient(nPrime.getIp(), Integer.parseInt(nPrime.getPort()));
                    closestFinger = nPrimeClient.closestPrecedingFinger(id);
                    nPrimeClient.shutdown();
                }

                if (closestFinger.equals(nPrime)) {
                    return nPrime;
                } else {
                    nPrime = closestFinger;
                }
            }
        }
    }

    // Method to find the closest preceding finger
    public NodeHeader closestPrecedingFinger(String id) {
        for (int i = m - 1; i >= 0; i--) {
            NodeHeader fingerNode = fingerTable.getFingers().get(i);
            if (fingerNode != null && isInIntervalOpenOpen(fingerNode.getNodeId(), this.nodeId, id)) {
                return fingerNode;
            }
        }
        return this.currentHeader;
    }

    public NodeHeader getSuccessorOfSuccessor() {
        for (int i = 0; i < m; i++) {
            NodeHeader fingerNode = fingerTable.getFingers().get(i);

            if (fingerNode != null && isNodeReachable(fingerNode)) {
                return fingerNode;
            }
        }

        return this.currentHeader;
    }

    private boolean isNodeReachable(NodeHeader node) {
        ChordClient client = null;
        try {
            client = new ChordClient(node.getIp(), Integer.parseInt(node.getPort()));
            NodeHeader response = client.getPredecessor();
            return response != null;
        } catch (Exception e) {
            return false;
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }


    // Method to stabilize the node
    public void stabilize() {
        Runnable task = () -> {
            ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
            try {
                NodeHeader x = successorClient.getPredecessor();

                if (x != null && isInIntervalOpenOpen(x.getNodeId(), this.nodeId, successor.getNodeId()) && !x.equals(this.currentHeader)) {
                    this.successor = x;
                }

                successorClient.notify(this.currentHeader);
            } catch (Exception e) {
                successorClient.shutdown();
                System.err.println("Unexpected error in stabilize(): " + e.getMessage());
                // Trigger the handleFailedSuccessor method if the successor is unreachable
                handleFailedSuccessor();
            } finally {
                successorClient.shutdown();
            }
        };

        executeGrpcCall(task);
    }

    private void handleFailedSuccessor() {
        System.err.println("Successor node failed. Attempting to find a new successor...");
        // Find a new successor
        NodeHeader newSuccessor = getSuccessorOfSuccessor();
        ChordClient newSuccessorClient = new ChordClient(newSuccessor.getIp(), Integer.parseInt(newSuccessor.getPort()));
        try {
            // Check if the new successor cannot reach its current predecessor like the current node
            NodeHeader x = newSuccessorClient.getPredecessor();
            if (!Objects.equals(x.getNodeId(), this.successor.getNodeId())) {
                loadBalancer.deregisterNode(this.successor.getIp(), Integer.parseInt(this.successor.getPort()));
                // Set the new successor's predecessor to the current node to eject the old successor
                newSuccessorClient.setPredecessor(this.currentHeader);
                this.successor = newSuccessor;
                System.out.println("New successor found: " + this.successor.getIp() + ":" + this.successor.getPort());
            } else {
                this.successor = x;
            }
        } catch (Exception e) {
            System.err.println("Failed to find a new successor. Current node is the only node in the network.");
        } finally {
            newSuccessorClient.shutdown();
        }
    }

    //checkpredecessor
    public void checkPredecessor() {
        if (this.predecessor != null) {
            ChordClient predecessorClient = new ChordClient(predecessor.getIp(), Integer.parseInt(predecessor.getPort()));
            try {
                predecessorClient.getSuccessor();
            } catch (Exception e) {
                System.err.println("Predecessor node failed. Setting predecessor to null.");
                this.predecessor = null;
                this.crashFlag = true;
            } finally {
                if (predecessorClient != null) {
                    predecessorClient.shutdown();
                }
            }
            //System.out.println("Predecessor is: " + this.predecessor.getIp() + ":" + this.predecessor.getPort());
        }
    }

    // Method to notify a node
    public void notify(NodeHeader n) {
        if (this.predecessor == null || isInIntervalOpenOpen(n.getNodeId(), this.predecessor.getNodeId(), this.nodeId)) {
            this.predecessor = n;
        }
    }

    // Method to fix fingers periodically
    public void fixFingers() {
        int i = new Random().nextInt(m);
        String start = fingerTable.calculateFingerStart(i);

        Runnable task = () -> {
            NodeHeader successorNode = findSuccessor(start);
            fingerTable.getFingers().set(i, successorNode);
        };

        executeGrpcCall(task);
    }

    public void initFingerTable(ChordClient n0Client) {
        //Initialize finger
        String start0 = fingerTable.calculateFingerStart(0);
        NodeHeader successorNode = n0Client.findSuccessor(start0);
        fingerTable.getFingers().set(0, successorNode);
        this.setSuccessor(successorNode);

        //Set predecessor
        ChordClient successorClient = new ChordClient(successorNode.getIp(), Integer.parseInt(successorNode.getPort()));
        NodeHeader successorPredecessor = successorClient.getPredecessor();
        this.setPredecessor(successorPredecessor);

        successorClient.setPredecessor(this.currentHeader);
        successorClient.shutdown();

        //Initialize other finger table
        for (int i = 1; i < m; i++) {
            String start = fingerTable.calculateFingerStart(i);
            if (isInIntervalClosedOpen(start, this.nodeId, fingerTable.getFingers().get(i - 1).getNodeId())) {
                fingerTable.getFingers().set(i, fingerTable.getFingers().get(i - 1));
            } else {
                NodeHeader successorNodeI = n0Client.findSuccessor(start);
                fingerTable.getFingers().set(i, successorNodeI);
            }
        }
    }

    // this will be called each time a file is sent to the chord network, since it has to be replicated
    public void storeFileInChord(String filename, String author, byte[] fileContent) {
        Runnable task = () -> {
            // Hash the key to find the node responsible for storing the file
            String keyId = hashNode(filename);

            int depth = 2;
            TreeBasedReplication treeReplication = new TreeBasedReplication(m);

            Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(keyId), depth);
            // treeReplication.displayTree(replicaTree);
            List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);
            System.out.println("Leaf Nodes: " + leafNodes);

            // tracking nodes we've sent files to so same data not sent multiple times
            Set<String> storedNodeIdentifiers = new HashSet<>();

            for (Integer replicaKey : leafNodes) {
                NodeHeader responsibleNode = findSuccessor(replicaKey.toString());

                // line below is used to keep track which ip:ports we have sent files to already, so we don't do it again
                String nodeIdentifier = responsibleNode.getIp() + ":" + responsibleNode.getPort();

                if (storedNodeIdentifiers.add(nodeIdentifier)) {  // this returns false if already present
                    // not sending grpc request if the responseNode itself, downloading the file directly.
                    if (currentHeader.getIp().equals(responsibleNode.getIp()) && currentHeader.getPort().equals(responsibleNode.getPort())) {
                        System.out.println("The responsibleNode was the node that received the file from client, storing file...");
                        DiskFileStorage diskFileStorage = new DiskFileStorage();
                        try {
                            diskFileStorage.write(filename, author, nodeId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        addMapping(filename, author);


                        continue;
                    }


                    System.out.println("responsibleNode (" + responsibleNode.getIp() + ":" + Integer.parseInt(responsibleNode.getPort())
                            + ") found, responsible for key: " + replicaKey);


                    ChordClient responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));

                    // this replicaKey was previously never used, which it is now when creating the mapping
                    responsibleNodeClient.storeFile(fileContent);
                    responsibleNodeClient.shutdown();
                }

            }
        };

        executeGrpcCall(task);
    }

    public void retrieveMessageFromChord(String key,String requester, StreamObserver<Fileupload.FileDownloadResponse> originalResponseObserver) {
        //async not implemented for retrieve
        String keyId = hashNode(key);


        // below is used for going through replica nodes if value is not found
        int depth = 2;
        TreeBasedReplication treeReplication = new TreeBasedReplication(m);
        Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(keyId), depth);
        List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);
        boolean fileRetrieved = false;

        for (Integer replicaKey : leafNodes) {
            NodeHeader responsibleNode = findSuccessor(replicaKey.toString());
            ChordClient responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));

            System.out.println("Attempting to retrieve file from replica: " + responsibleNode.getIp() + ":" + responsibleNode.getPort());

            CompletableFuture<Boolean> retrievalResult = responsibleNodeClient.retrieveFile(key, requester, originalResponseObserver);

            if (retrievalResult.join()) {
                fileRetrieved = true;
                responsibleNodeClient.shutdown();
                break;
            }
            responsibleNodeClient.shutdown();

        }

        if (!fileRetrieved) {
            System.out.println("File not found in any replicas.");
        }

        // return message;
    }

    public void deleteFileFromChord(String key, String requester, StreamObserver<com.google.protobuf.Empty> originalResponseObserver) {
        //async not implemented for delete
        String keyId = hashNode(key);

        // finding the responsible node

        // currently trying to understand why this returns IP and port of current node and not the one of the range we seek
        System.out.println("Finding responseNode of the keyId: " + keyId);
        NodeHeader responsibleNode = findSuccessor(keyId);

        ChordClient responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));
        System.out.println("deleteFileFromChord(): responsibleNode address is " + responsibleNode.getIp() + ":" + responsibleNode.getPort());

        responsibleNodeClient.deleteFile(key, requester, originalResponseObserver);


        responsibleNodeClient.shutdown();
        // return message;
    }


    // Interval checking methods
    private boolean isInIntervalOpenOpen(String id, String start, String end) {
        BigInteger idInt = new BigInteger(id);
        BigInteger startInt = new BigInteger(start);
        BigInteger endInt = new BigInteger(end);

        if (startInt.equals(endInt)) {
            return !idInt.equals(startInt);
        } else if (startInt.compareTo(endInt) < 0) {
            return idInt.compareTo(startInt) > 0 && idInt.compareTo(endInt) < 0;
        } else {
            return idInt.compareTo(startInt) > 0 || idInt.compareTo(endInt) < 0;
        }
    }

    private boolean isInIntervalOpenClosed(String id, String start, String end) {
        BigInteger idInt = new BigInteger(id);
        BigInteger startInt = new BigInteger(start);
        BigInteger endInt = new BigInteger(end);

        if (startInt.equals(endInt)) {
            return true;
        } else if (startInt.compareTo(endInt) < 0) {
            return idInt.compareTo(startInt) > 0 && idInt.compareTo(endInt) <= 0;
        } else {
            return idInt.compareTo(startInt) > 0 || idInt.compareTo(endInt) <= 0;
        }
    }

    private boolean isInIntervalClosedOpen(String id, String start, String end) {
        BigInteger idInt = new BigInteger(id);
        BigInteger startInt = new BigInteger(start);
        BigInteger endInt = new BigInteger(end);

        if (startInt.equals(endInt)) {
            return true;
        } else if (startInt.compareTo(endInt) < 0) {
            return idInt.compareTo(startInt) >= 0 && idInt.compareTo(endInt) < 0;
        } else {
            return idInt.compareTo(startInt) >= 0 || idInt.compareTo(endInt) < 0;
        }
    }


    // Helper method to execute gRPC calls with optional multi-threading
    private void executeGrpcCall(Runnable task) {
        if (multiThreadingEnabled && executorService != null) {
            executorService.submit(task);
        } else {
            task.run();
        }
    }

    // Method to shut down the executor service
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}