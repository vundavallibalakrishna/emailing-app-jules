package com.wisestep.emailing.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Arrays;

@Service
public class TokenEncryptionService {

    private static final Logger logger = LoggerFactory.getLogger(TokenEncryptionService.class);
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    // AES block size is 16 bytes (128 bits) for the IV
    private static final int AES_IV_LENGTH = 16;


    private final SecretKeySpec secretKeySpec;
    private final String encryptionKeyString; // Store the original key string for logging/debugging only if necessary

    public TokenEncryptionService(@Value("${email.oauth.encryption.key}") String encryptionKey) {
        this.encryptionKeyString = encryptionKey; // For potential debugging, not for direct use in crypto
        if (encryptionKey == null || encryptionKey.isEmpty() || "YOUR_SECURE_32_BYTE_ENCRYPTION_KEY_HERE".equals(encryptionKey)) {
            logger.error("CRITICAL: OAuth token encryption key is not configured or is using the default placeholder. Data will not be securely encrypted.");
            // In a real app, you might throw an exception here to prevent startup,
            // or use a default (less secure, logged) key for dev only.
            // For this example, we'll proceed but log heavily.
            this.secretKeySpec = new SecretKeySpec("DefaultKeyMustBe32BytesExactly!!".getBytes(StandardCharsets.UTF_8), "AES"); // Unsafe, for placeholder only
        } else {
            this.secretKeySpec = generateKey(encryptionKey);
        }
    }

    private SecretKeySpec generateKey(String myKey) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 32); // Use first 32 bytes (256 bits) for AES-256
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            logger.error("Error generating secret key spec from application property", e);
            throw new RuntimeException("Failed to initialize encryption key", e);
        }
    }

    // Generates a new random IV for each encryption operation
    private byte[] generateIv() {
        byte[] iv = new byte[AES_IV_LENGTH];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }

    public String encrypt(String strToEncrypt) {
        if (strToEncrypt == null) return null;
        if ("YOUR_SECURE_32_BYTE_ENCRYPTION_KEY_HERE".equals(this.encryptionKeyString)) {
            logger.warn("Attempting to encrypt with placeholder key. This is insecure.");
        }
        try {
            byte[] iv = generateIv();
            IvParameterSpec ivspec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivspec);

            byte[] encryptedBytes = cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8));
            // Prepend IV to the ciphertext for use during decryption
            byte[] combinedPayload = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, combinedPayload, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, combinedPayload, iv.length, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combinedPayload);
        } catch (Exception e) {
            logger.error("Error encrypting token: {}", e.getMessage(), e);
            // Depending on policy, could return null, throw exception, or return original string (unsafe)
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String strToDecrypt) {
        if (strToDecrypt == null) return null;
         if ("YOUR_SECURE_32_BYTE_ENCRYPTION_KEY_HERE".equals(this.encryptionKeyString)) {
            logger.warn("Attempting to decrypt with placeholder key. This is insecure if data was encrypted with a different key.");
        }
        try {
            byte[] combinedPayload = Base64.getDecoder().decode(strToDecrypt);

            if (combinedPayload.length < AES_IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted string: too short to contain IV.");
            }

            byte[] iv = Arrays.copyOfRange(combinedPayload, 0, AES_IV_LENGTH);
            byte[] encryptedBytes = Arrays.copyOfRange(combinedPayload, AES_IV_LENGTH, combinedPayload.length);

            IvParameterSpec ivspec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivspec);

            byte[] originalBytes = cipher.doFinal(encryptedBytes);
            return new String(originalBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error decrypting token: {}", e.getMessage(), e);
            // Depending on policy, could return null or throw exception
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
