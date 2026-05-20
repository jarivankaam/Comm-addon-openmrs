package com.azaricomm.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    // The settings for the encryption
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    // Prepares your secretkey, so java cryptografy-engine doesn't crash.
    public EncryptionService(@Value("${crypto.secret-key}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        byte[] finalKey = new byte[32];
        System.arraycopy(keyBytes, 0, finalKey, 0, Math.min(keyBytes.length, 32));
        this.keySpec = new SecretKeySpec(finalKey, "AES");
    }

    public String encrypt(String strToEncrypt) {
        if (strToEncrypt == null || strToEncrypt.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE]; // Generates unique series of 12 bytes.
            secureRandom.nextBytes(iv);

            // Java encryption machine
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            // Encrypts your string to unreadable bytes.
            byte[] encryptedBytes = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));

            // Adds the IV, so you can decrypt later
            byte[] combined = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
}