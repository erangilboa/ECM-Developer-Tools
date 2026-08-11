package com.dctm.workbench.server.store;

import com.dctm.workbench.core.SessionException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

final class AesGcm {

    private static final int IV = 12;
    private static final int TAG = 128;

    private AesGcm() {
    }

    static SecretKey loadOrCreate(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                byte[] raw = Files.readAllBytes(keyFile);
                return new SecretKeySpec(raw, "AES");
            }
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(256);
            SecretKey key = gen.generateKey();
            Files.createDirectories(keyFile.getParent());
            Files.write(keyFile, key.getEncoded());
            return key;
        } catch (Exception e) {
            throw new SessionException("Cannot init local keystore: " + e.getMessage(), e);
        }
    }

    static String encrypt(SecretKey key, String plaintext) {
        try {
            byte[] iv = new byte[IV];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new SessionException("Encrypt failed", e);
        }
    }

    static String decrypt(SecretKey key, String blob) {
        try {
            byte[] all = Base64.getDecoder().decode(blob);
            ByteBuffer buf = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV];
            buf.get(iv);
            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG, iv));
            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SessionException("Decrypt failed", e);
        }
    }
}
