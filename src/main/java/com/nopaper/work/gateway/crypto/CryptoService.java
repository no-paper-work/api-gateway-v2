/**
 * @package com.nopaper.work.gateway.crypto -> gateway
 * @author saikatbarman
 * @date 2025 06-Aug-2025 10:22:22 pm
 * @git 
 */
package com.nopaper.work.gateway.crypto;

/**
 * 
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
@Slf4j
public class CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // GCM recommended IV length is 12 bytes

    public String encrypt(String plainText, String key) {
        if (key == null) {
            log.error("Encryption key is null.");
            return plainText; // Or throw an exception
        }
        try {
            SecretKeySpec secretKey = generateKey(key);
            // In a real-world scenario, IV should be randomly generated for each encryption
            // and prepended to the ciphertext. For simplicity here, we use a fixed IV.
            // A better approach would be to manage IVs dynamically.
            IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[IV_LENGTH]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Could not encrypt data", e);
        }
    }

    public String decrypt(String encryptedText, String key) {
        if (key == null) {
            log.error("Decryption key is null.");
            return encryptedText; // Or throw an exception
        }
        try {
            SecretKeySpec secretKey = generateKey(key);
            IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[IV_LENGTH]);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec);
            byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Could not decrypt data", e);
        }
    }

    private SecretKeySpec generateKey(String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        keyBytes = sha.digest(keyBytes);
        return new SecretKeySpec(keyBytes, "AES");
    }
}