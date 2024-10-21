package com.grpc.server.fileupload.server.chordUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// This was taken from the Chord implementation, previously named just Hash
public class ChordHash {

    public static String hashNode(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hashBytes = md.digest(input.getBytes());
        BigInteger hashInt = new BigInteger(1, hashBytes);
        BigInteger mod = BigInteger.valueOf(2);
        return hashInt.mod(mod).toString();
    }
}
