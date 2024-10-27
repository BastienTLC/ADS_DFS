package com.grpc.client.fileupload.client.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Base64;

public class CrypteUtil {
    private static final String KEY_FILE_PATH = "key.txt";

    // Load AES key from file
    public static SecretKey LoadSecretKey() {
        try {
            String encodedKey = new String(Files.readAllBytes(Paths.get(KEY_FILE_PATH))).trim();
            byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
            return new SecretKeySpec(decodedKey, "AES");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Encrypt data using AES
    public static byte[] EncryptData(byte[] data, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Decrypt data using AES
    public static byte[] DecryptData(byte[] encryptedData, SecretKey secretKey) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher.doFinal(encryptedData);
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
            return null;
        }
    }
}
