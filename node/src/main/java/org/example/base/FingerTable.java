package org.example.base;

import org.example.types.NodeHeader;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FingerTable {
    private final List<NodeHeader> fingers;
    private final ChordNode localNode;
    private final int m; // Nombre de bits finger table

    public FingerTable(ChordNode localNode, int m) {
        this.localNode = localNode;
        this.fingers = new ArrayList<>(Collections.nCopies(m, null));
        this.m = m;
    }

    public String calculateFingerStart(int i) {
        BigInteger nodeHash = new BigInteger(localNode.getNodeId());
        BigInteger twoPowerI = BigInteger.valueOf(2).pow(i);
        BigInteger twoPowerM = BigInteger.valueOf(2).pow(m);
        BigInteger start = nodeHash.add(twoPowerI).mod(twoPowerM);
        return start.toString();
    }


    public List<NodeHeader> getFingers() { return fingers; }
    public int getM() { return m; }
}
