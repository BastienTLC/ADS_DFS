package com.grpc.server.fileupload.server.base;


import com.devProblems.Fileupload;
import com.grpc.server.fileupload.server.types.NodeHeader;
import com.grpc.server.fileupload.server.utils.LoadBalancer;
import com.grpc.server.fileupload.server.utils.TreeBasedReplication;
import io.grpc.stub.StreamObserver;

import java.io.File;
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
    private boolean isBootstrap;

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
            e.printStackTrace();
            return null;
        }
    }

    public String getNodeId() { return this.nodeId; }
    public String getIp() { return this.ip; }
    public int getPort() { return this.port; }
    public NodeHeader getSuccessor() { return this.successor; }
    public void setSuccessor(NodeHeader successor) { this.successor = successor; }
    public NodeHeader getPredecessor() { return this.predecessor; }
    public void setPredecessor(NodeHeader predecessor) { this.predecessor = predecessor; }
    public FingerTable getFingerTable() { return this.fingerTable; }
    public FileStore getMessageStore() { return this.fileStore; }

    // Method to join the network
    public void join(String existingNodeIp, int existingNodePort) {
        if (existingNodeIp != null) {
            ChordClient chordClient = new ChordClient(existingNodeIp, existingNodePort);
            initFingerTable(chordClient);
            this.updateOthers();
            chordClient.shutdown();
            this.isBootstrap = false;
        } else {
            for (int i = 0; i < m; i++) {
                fingerTable.getFingers().set(i, currentHeader);
            }
            this.predecessor = currentHeader;
            this.successor = currentHeader;
            this.isBootstrap = true;
        }

        //call loadbalancer to register the node (ip, port)
        loadBalancer.registerNode(this.ip, this.port);

        // condition so that the bootstrap node wont enter
        if (!isBootstrap) {
            // this will get the data transferred to us from the successor
            // I think it will simply overwrite existing files if we have them already
            ChordClient successorClient = new ChordClient(this.successor.getIp(), Integer.parseInt(this.successor.getPort()));

            String[] span = getResponsibleSpan();
            successorClient.retrieveFiles(span[0], span[1], this); // not fully handling the wrap around case here yet
            successorClient.shutdown();
        }

        // looping through the directory where files are stored and adding mapping
        // we are doing this after having gotten the files from successor
        // for some reason this is called twice when joining
        addMappingForExistingFiles();

        printFingerTable();
        printResponsibleSpan();
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
            span[0] = this.predecessor.getNodeId();
            span[1] = this.nodeId;
        }

        return span;
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

    // key is filename:username, value is key generated from the filename
    public void addMapping(String filename, String username){
        String id = hashNode(filename);
        String value = filename + ":" + username;

        // could potentially add logic here so that user can store files with same name
        // but it's a realistic limitation to only allow same user to store files of different names
        fileMap.computeIfAbsent(id, k -> new ArrayList<>()).add(value);

        System.out.println("addMapping() called, id: " + id + " is now mapped to the value: " + value);

    }

    public void removeMappingForId(String id) {
        // Check if the list associated with the id exists in the map
        if (fileMap.containsKey(id) && fileMap.get(id) != null && !fileMap.get(id).isEmpty()) {
            // Remove the entire list of mappings associated with the id
            fileMap.remove(id);
            System.out.println("removeMappingForId() called, removed entire mapping for id: " + id);
        } else {
            // If no mappings exist for this id, log a message
            System.out.println("No mapping found for id: " + id);
        }
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
                // System.out.println("No files found for user: " + username);
                continue;
            }

            for (File file : files) {
                String filename = file.getName();
                addMapping(filename, username); // adding mapping for each file
            }
        }

        System.out.println("All existing files have been mapped.");
    }

    // 99% sure this needs to be rewritten, since if starthash = 40, endHash = 10, it won't find it properly
    public Map<String, List<String>> getFilesInRange(String startHash, String endHash) {
        return fileMap.subMap(startHash, true, endHash, true);
    }

//    public Map<String, List<String>> getFilesInRange(String startHash, String endHash) {
//        Map<String, List<String>> filesInRange = new HashMap<>();
//        for (Map.Entry<String, List<String>> entry : fileMap.entrySet()) {
//            String fileHash = entry.getKey();
//            if (fileHash.compareTo(startHash) >= 0 && fileHash.compareTo(endHash) <= 0) {
//                filesInRange.put(fileHash, entry.getValue());
//            }
//        }
//        return filesInRange;
//    }

    public void printFilesInRange(String startHash, String endHash) {
        Map<String, List<String>> filesInRange = getFilesInRange(startHash, endHash);
        System.out.println("Files in range " + startHash + "-" + endHash + ":");
        filesInRange.forEach((hash, fileKeys) -> {
            System.out.println("Hash: " + hash);
            fileKeys.forEach(fileKey -> System.out.println("    File: " + fileKey));
        });
    }


    public void leave() {
        loadBalancer.deregisterNode(this.ip, this.port);
        if (this.successor != null && !this.successor.equals(this.currentHeader)) {
            // No keys to transfer since this node doesn't store any files
        }

        // Update successor and predecessor pointers
        if (this.predecessor != null && this.successor != null) {
            final String predecessorIp = predecessor.getIp();
            final int predecessorPort = Integer.parseInt(predecessor.getPort());
            final String successorIp = successor.getIp();
            final int successorPort = Integer.parseInt(successor.getPort());

            Runnable task = () -> {
                ChordClient predecessorClient = new ChordClient(predecessorIp, predecessorPort);
                ChordClient successorClient = new ChordClient(successorIp, successorPort);

                predecessorClient.setSuccessor(successor);
                successorClient.setPredecessor(predecessor);

                predecessorClient.shutdown();
                successorClient.shutdown();
            };

            executeGrpcCall(task);
        }

        // Nullify own pointers
        this.successor = null;
        this.predecessor = null;

        // Shutdown the executor service if necessary
        shutdown();
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

        // Responsible span: (25, 48]
        // contact predecessor, provide range 25-48, have it loop through and transfer those files



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
//        System.out.println("---- Updating responsible span and finger table ---- ");
//        printResponsibleSpan();
//        printFingerTable();
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

    // Method to stabilize the node
    public void stabilize() {
        final String successorIp = successor.getIp();
        final int successorPort = Integer.parseInt(successor.getPort());

        Runnable task = () -> {
            ChordClient successorClient = new ChordClient(successorIp, successorPort);
            NodeHeader x = successorClient.getPredecessor();

            if (x != null && isInIntervalOpenOpen(x.getNodeId(), this.nodeId, successor.getNodeId())) {
                this.successor = x;
            }

            successorClient.notify(this.currentHeader);
            successorClient.shutdown();
        };

        executeGrpcCall(task);
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

    public void storeFileInChord(String key, byte[] fileContent) {
        Runnable task = () -> {
            // Hash the key to find the node responsible for storing the file
            String keyId = hashNode(key);
            System.out.println("Key is: " + key + " and keyId is: " + keyId);

            int maxDepth = 1;

            TreeBasedReplication treeReplication = new TreeBasedReplication(m);

            Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(keyId), maxDepth);
            // treeReplication.displayTree(replicaTree);
            List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);
            System.out.println("Leaf Nodes: " + leafNodes);

            // tracking nodes we've sent files to so same data not sent multiple times
            Set<String> storedNodeIdentifiers = new HashSet<>();

            for (Integer replicaKey : leafNodes) {
                NodeHeader responsibleNode = findSuccessor(replicaKey.toString());

                // line below is used to keep track which ip:ports we have sent files to already, so we don't do it again
                String nodeIdentifier = responsibleNode.getIp() + ":" + responsibleNode.getPort();

                // current implementation makes a GRPC call to itself if it is the responsible node
                if (storedNodeIdentifiers.add(nodeIdentifier)) {  // this returns false if already present
                    // if (responsibleNode.getIp().equals(this.ip) &&
                    //         Integer.parseInt(responsibleNode.getPort()) == (this.port)) {
                    // }

                    System.out.println("responsibleNode (" + responsibleNode.getIp() + ":" + Integer.parseInt(responsibleNode.getPort())
                            + ") found, responsible for key: " + replicaKey);
                    ChordClient responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));

                    responsibleNodeClient.storeFile(String.valueOf(replicaKey), fileContent);
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
        TreeBasedReplication treeReplication = new TreeBasedReplication(m);
        Map<Integer, List<Integer>> replicaTree = treeReplication.generateReplicaTree(Integer.parseInt(keyId), 1);
        List<Integer> leafNodes = treeReplication.getLeafNodes(replicaTree);
        boolean fileRetrieved = false;

        ChordClient responsibleNodeClient = null;
        for (Integer replicaKey : leafNodes) {
            NodeHeader responsibleNode = findSuccessor(replicaKey.toString());
            responsibleNodeClient = new ChordClient(responsibleNode.getIp(), Integer.parseInt(responsibleNode.getPort()));

            System.out.println("Attempting to retrieve file from replica: " + responsibleNode.getIp() + ":" + responsibleNode.getPort());

            CompletableFuture<Boolean> retrievalResult = responsibleNodeClient.retrieveFile(key, requester, originalResponseObserver);

            if (retrievalResult.join()) {
                fileRetrieved = true;
                break;
            }
        }

        if (!fileRetrieved) {
            System.out.println("File not found in any replicas.");
        }

        responsibleNodeClient.shutdown();
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




    // function for checking if current node is responsible for key (haven't checked if it works)
    public boolean isResponsibleForKey(String key) {
        String keyId = hashNode(key);
        System.out.println("Key is: " + key + " and keyId is: " + keyId);

        if (predecessor == null) {
            return true;
        } else {
            return isInIntervalClosedOpen(keyId, predecessor.getNodeId(), this.nodeId);
        }
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
