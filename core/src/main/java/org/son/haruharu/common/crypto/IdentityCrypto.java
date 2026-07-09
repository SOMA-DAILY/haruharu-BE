package org.son.haruharu.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

// blindIndex는 GCM 암호문이 매번 달라 조회·유니크가 불가한 한계를 보완하는 결정적 인덱스다.
@Component
public class IdentityCrypto {

    private static final String AES = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_KEY_LENGTH = 32;

    private final SecretKey aesKey;
    private final SecretKey macKey;
    private final SecureRandom random = new SecureRandom();

    public IdentityCrypto(@Value("${identity.encryption.key:}") String masterKey) {
        if (masterKey == null || masterKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "IDENTITY_ENCRYPTION_KEY must be set and at least " + MIN_KEY_LENGTH + " characters.");
        }
        byte[] master = masterKey.getBytes(StandardCharsets.UTF_8);
        this.aesKey = new SecretKeySpec(derive(master, "aes"), AES);
        this.macKey = new SecretKeySpec(derive(master, "hmac"), HMAC);
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CI/DI 암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] ct = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("CI/DI 복호화에 실패했습니다.", e);
        }
    }

    public String blindIndex(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(macKey);
            byte[] digest = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("blind index 생성에 실패했습니다.", e);
        }
    }

    private static byte[] derive(byte[] master, String purpose) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(purpose.getBytes(StandardCharsets.UTF_8));
            return md.digest(master);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
