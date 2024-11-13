package com.grpc.server.fileupload.server.utils;

import java.util.*;

public class TreeBasedReplication {

    private final int M;
    private final int MODULO;


    public TreeBasedReplication(int m) {
        this.M = m;
        this.MODULO = (int) Math.pow(2, M);
    }


    public Map<Integer, List<Integer>> generateReplicaTree(int rootKey, int maxDepth) {
        Map<Integer, List<Integer>> replicaTree = new HashMap<>();

        if (maxDepth < 1) {
            replicaTree.put(0, new ArrayList<>(Collections.singletonList(rootKey)));
            return replicaTree;
        }

        generateReplicaTreeRecursive(replicaTree, rootKey, 0, maxDepth-1, MODULO / 2);

        return replicaTree;
    }

    private void generateReplicaTreeRecursive(Map<Integer, List<Integer>> replicaTree, int parentKey, int depth, int maxDepth, int distance) {
        if (depth > maxDepth) {
            return;
        }

        int leftChild = (parentKey - distance + MODULO) % MODULO;
        int rightChild = parentKey;

        replicaTree.putIfAbsent(depth, new ArrayList<>());
        replicaTree.get(depth).add(leftChild);
        replicaTree.get(depth).add(rightChild);

        generateReplicaTreeRecursive(replicaTree, leftChild, depth + 1, maxDepth, distance / 2);
        generateReplicaTreeRecursive(replicaTree, rightChild, depth + 1, maxDepth, distance / 2);
    }

    public void displayTree(Map<Integer, List<Integer>> replicaTree) {
        for (Map.Entry<Integer, List<Integer>> entry : replicaTree.entrySet()) {
            int depth = entry.getKey();
            List<Integer> keys = entry.getValue();
            System.out.println("Depth " + depth + ": " + keys);
        }
    }

    public List<Integer> getLeafNodes(Map<Integer, List<Integer>> replicaTree) {
        // finding max depth
        int maxDepth = Collections.max(replicaTree.keySet());

        // returning nodes at max depth
        return replicaTree.getOrDefault(maxDepth, Collections.emptyList());
    }



}