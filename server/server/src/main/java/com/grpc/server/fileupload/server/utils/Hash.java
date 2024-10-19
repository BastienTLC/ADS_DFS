package com.grpc.server.fileupload.server.utils;

import de.uniba.wiai.lspi.chord.service.Key;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash implements Key {

        private final byte[] hash;

        public Hash(byte[] hash) {
            this.hash = hash;
        }

        public static Hash hash(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                return new Hash(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

    @Override
    public byte[] getBytes() {
        return hash;
    }
}
