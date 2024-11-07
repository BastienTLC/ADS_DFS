package com.grpc.server.fileupload.server.base;


import com.devProblems.Fileupload;
import com.grpc.server.fileupload.server.types.NodeHeader;
import com.grpc.server.fileupload.server.utils.DiskFileStorage;
import com.grpc.server.fileupload.server.utils.LoadBalancer;
import com.grpc.server.fileupload.server.utils.TreeBasedReplication;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private DiskFileStorage diskFileStorage;
    private final boolean multiThreadingEnabled;
    private final ExecutorService executorService;
    private final LoadBalancer loadBalancer;
    private List<NodeHeader> successorList;
    private final int successorListSize;

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
        this.diskFileStorage = new DiskFileStorage();
        this.loadBalancer = loadBalancer;
        this.successorListSize = 5;
        this.successorList = new ArrayList<>(Collections.nCopies(successorListSize, null));
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
    public DiskFileStorage getDiskFileStorage() { return this.diskFileStorage; }
    public List<NodeHeader> getSuccessorList() { return successorList; }
    public void setSuccessorList(List<NodeHeader> successorList) { this.successorList = successorList; }

    // Method to join the network
    public void join(String existingNodeIp, int existingNodePort) {
        if (existingNodeIp != null) {
            ChordClient chordClient = new ChordClient(existingNodeIp, existingNodePort);
            initFingerTable(chordClient);
            this.updateOthers();
            chordClient.shutdown();
        } else {
            for (int i = 0; i < m; i++) {
                fingerTable.getFingers().set(i, currentHeader);
            }
            this.predecessor = currentHeader;
            this.successor = currentHeader;
        }
        initializeSuccessorList();
        //call loadbalancer to register the node (ip, port)
        loadBalancer.registerNode(this.ip, this.port);

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

    public void printSuccessorList() {
        System.out.println("Successor List for Node ID: " + this.nodeId);

        for (int i = 0; i < successorListSize; i++) {
            NodeHeader successor = successorList.get(i);

            if (successor != null) {
                System.out.printf("Entry %d: Node ID = %s, Address = %s:%s%n",
                        i, successor.getNodeId(), successor.getIp(), successor.getPort());
            } else {
                System.out.printf("Entry %d: Node ID = null%n", i);
            }
        }

        System.out.println();
    }


    public void leave() {
        loadBalancer.deregisterNode(this.ip, this.port);
        if (this.successor != null && !this.successor.equals(this.currentHeader)) {
            transferFilesToSuccessor();
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
        System.out.println("---- Updating responsible span and finger table ---- ");
        printResponsibleSpan();
        printFingerTable();
    }


    public void updateSuccessorList() {
        ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
        List<NodeHeader> successorSuccessorList = successorClient.getSuccessorList();
        successorClient.shutdown();

        // Ensure successorList has the correct size
        while (successorList.size() < successorListSize) {
            successorList.add(null);
        }

        successorList.set(0, successor);
        for (int i = 1; i < successorListSize; i++) {
            if (i - 1 < successorSuccessorList.size()) {
                successorList.set(i, successorSuccessorList.get(i - 1));
            } else {
                successorList.set(i, null);
            }
        }
    }



    // Method to find the successor of a given ID
    public NodeHeader findSuccessor(String id) {
        NodeHeader predecessorNode = findPredecessor(id);
        NodeHeader successorNode = null;
        int attempts = 0;

        while (attempts < successorListSize) {
            try {
                ChordClient predecessorClient = new ChordClient(predecessorNode.getIp(), Integer.parseInt(predecessorNode.getPort()));
                successorNode = predecessorClient.getSuccessor();
                predecessorClient.shutdown();
                break;
            } catch (Exception e) {

                attempts++;
                if (attempts < successorListSize && successorList.get(attempts) != null) {
                    predecessorNode = successorList.get(attempts);
                } else {
                    throw new RuntimeException("Failed to find successor for ID: " + id);
                }
            }
        }

        return successorNode;
    }


    // Method to find the predecessor of a given ID
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
        Runnable task = () -> {
            ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
            try {
                NodeHeader x = successorClient.getPredecessor();

                if (x != null && isInIntervalOpenOpen(x.getNodeId(), this.nodeId, successor.getNodeId()) && !x.equals(this.currentHeader)) {
                    this.successor = x;
                }

                successorClient.notify(this.currentHeader);
            } catch (Exception e) {
                System.err.println("Unexpected error in stabilize(): " + e.getMessage());
                handleFailedSuccessor();
            } finally {
                if (successorClient != null) {
                    successorClient.shutdown();
                }
            }
        };

        executeGrpcCall(task);
    }


    private void handleFailedSuccessor() {
        printSuccessorList();
        if (this.successor.equals(this.currentHeader)) {
            return;
        }

        // Mark the current successor as failed
        if (!successorList.isEmpty()) {
            successorList.set(0, null);
        }

        // Find the next available successor
        NodeHeader nextSuccessor = null;
        for (NodeHeader node : successorList) {
            if (node != null && !node.equals(currentHeader)) {
                nextSuccessor = node;
                break;
            }
        }

        if (nextSuccessor != null) {
            successor = nextSuccessor;
        } else {
            successor = currentHeader;
        }

        // Update the successor list
        updateSuccessorList();
        printSuccessorList();
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

    private void initializeSuccessorList() {
        this.successorList.set(0, this.successor);
        ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));
        List<NodeHeader> successorSuccessorList = successorClient.getSuccessorList();
        successorClient.shutdown();

        int index = 1;
        for (NodeHeader nodeHeader : successorSuccessorList) {
            if (nodeHeader != null && index < successorListSize) {
                this.successorList.set(index, nodeHeader);
                index++;
            } else {
                break;
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


    public void transferFilesToSuccessor() {
        ChordClient successorClient = new ChordClient(successor.getIp(), Integer.parseInt(successor.getPort()));

        try {
            List<Path> files = this.diskFileStorage.getAllFiles(this.nodeId);
            System.out.println("Transferring " + files.size() + " files to successor: " + successor.getIp() + ":" + successor.getPort());
            for (Path filePath : files) {
                System.out.println("Transferring file: " + filePath.toString());
                // Extract the file name and author from the file path
                String fileName = filePath.getFileName().toString();
                String author = filePath.getParent().getFileName().toString(); // Assuming the parent directory is the author

                // Read the content of the file
                byte[] content = Files.readAllBytes(filePath);

                // Create metadata for the file
                Fileupload.FileMetadata fileMetadata = Fileupload.FileMetadata.newBuilder()
                        .setFileNameWithType(fileName)
                        .setContentLength(content.length)
                        .setAuthor(author)
                        .build();

                // Transfer the file to the successor node
                successorClient.storeFileWithMetadata(fileMetadata, content);

                // Delete the file from the current node
                diskFileStorage.deleteFile(fileName, author, nodeId);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            successorClient.shutdown();
        }
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
