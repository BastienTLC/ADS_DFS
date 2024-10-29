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
    private static final String key = "aKZ8PKeLwTkUZqgg1s77hEVCGwetGiDJNP+5muWT/2Y=";

    // Load AES key from file
    public static SecretKey LoadSecretKey() {
        String encodedKey = key;
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(decodedKey, "AES");
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
