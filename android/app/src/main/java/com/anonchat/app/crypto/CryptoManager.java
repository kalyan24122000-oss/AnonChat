package com.anonchat.app.crypto;

import android.util.Base64;
import com.anonchat.app.utils.PreferenceHelper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * End-to-End Encryption Manager using established NIST P-256 (secp256r1) ECDH
 * Key Agreement and authenticated AES-256-GCM symmetric encryption.
 */
public class CryptoManager {
    private static final String EC_ALGORITHM = "EC";
    private static final String EC_CURVE = "secp256r1";
    private static final String ECDH_ALGORITHM = "ECDH";
    private static final String AES_GCM_CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_TAG_LENGTH_BYTES = 16;
    private static final String HKDF_HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] HKDF_SALT = "AnonChat_E2EE_v1_Salt".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "AnonChat_AES256GCM_KeyDerivation".getBytes(StandardCharsets.UTF_8);

    private final PreferenceHelper preferenceHelper;
    private final SecureRandom secureRandom;

    public static class EncryptedPayload {
        public final String ciphertextBase64;
        public final String ivBase64;
        public final String authTagBase64;

        public EncryptedPayload(String ciphertextBase64, String ivBase64, String authTagBase64) {
            this.ciphertextBase64 = ciphertextBase64;
            this.ivBase64 = ivBase64;
            this.authTagBase64 = authTagBase64;
        }
    }

    public CryptoManager(PreferenceHelper preferenceHelper) {
        this.preferenceHelper = preferenceHelper;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Ensure the user has an EC identity keypair. Generates one if missing.
     */
    public synchronized String getOrCreateIdentityPublicKey() throws Exception {
        return getOrCreateIdentityPublicKey(preferenceHelper.getUserId());
    }

    public synchronized String getOrCreateIdentityPublicKey(String userId) throws Exception {
        String existingPub = preferenceHelper.getPublicKey(userId);
        String existingPriv = preferenceHelper.getPrivateKey(userId);

        if (existingPub != null && existingPriv != null) {
            return existingPub;
        }

        // Generate NIST P-256 EC Keypair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(EC_ALGORITHM);
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(EC_CURVE);
        kpg.initialize(ecSpec, secureRandom);
        KeyPair keyPair = kpg.generateKeyPair();

        String pubBase64 = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.NO_WRAP);
        String privBase64 = Base64.encodeToString(keyPair.getPrivate().getEncoded(), Base64.NO_WRAP);

        preferenceHelper.saveKeys(userId, pubBase64, privBase64);
        return pubBase64;
    }

    /**
     * Encrypt a plaintext message for a recipient using ECDH + AES-256-GCM
     */
    public EncryptedPayload encryptMessage(String plaintext, String recipientPublicKeyBase64) throws Exception {
        if (plaintext == null || recipientPublicKeyBase64 == null) {
            throw new IllegalArgumentException("Plaintext and recipient public key cannot be null");
        }

        // 1. Load private key
        PrivateKey myPrivateKey = loadPrivateKey();
        // 2. Load recipient public key
        PublicKey recipientPublicKey = decodePublicKey(recipientPublicKeyBase64);

        // 3. Compute ECDH shared secret
        byte[] sharedSecret = computeECDHSharedSecret(myPrivateKey, recipientPublicKey);

        // 4. Derive 256-bit AES key using HKDF
        SecretKey aesKey = deriveAesKey(sharedSecret);

        // 5. Generate 12-byte random IV
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        // 6. Encrypt with AES-GCM
        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, parameterSpec);

        byte[] cipherWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Separate ciphertext and 16-byte auth tag
        int ciphertextLen = cipherWithTag.length - GCM_TAG_LENGTH_BYTES;
        byte[] ciphertext = Arrays.copyOfRange(cipherWithTag, 0, ciphertextLen);
        byte[] authTag = Arrays.copyOfRange(cipherWithTag, ciphertextLen, cipherWithTag.length);

        return new EncryptedPayload(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(authTag, Base64.NO_WRAP)
        );
    }

    /**
     * Decrypt an E2EE message from a sender using ECDH + AES-256-GCM
     */
    public String decryptMessage(String ciphertextBase64, String ivBase64, String authTagBase64, String senderPublicKeyBase64) throws Exception {
        if (ciphertextBase64 == null || ivBase64 == null || authTagBase64 == null || senderPublicKeyBase64 == null) {
            throw new IllegalArgumentException("Incomplete E2EE encryption payload");
        }

        PrivateKey myPrivateKey = loadPrivateKey();
        PublicKey senderPublicKey = decodePublicKey(senderPublicKeyBase64);

        // Compute ECDH shared secret
        byte[] sharedSecret = computeECDHSharedSecret(myPrivateKey, senderPublicKey);

        // Derive AES key
        SecretKey aesKey = deriveAesKey(sharedSecret);

        byte[] iv = Base64.decode(ivBase64, Base64.DEFAULT);
        byte[] ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT);
        byte[] authTag = Base64.decode(authTagBase64, Base64.DEFAULT);

        // Recombine ciphertext and auth tag for AES/GCM/NoPadding
        byte[] cipherWithTag = new byte[ciphertext.length + authTag.length];
        System.arraycopy(ciphertext, 0, cipherWithTag, 0, ciphertext.length);
        System.arraycopy(authTag, 0, cipherWithTag, ciphertext.length, authTag.length);

        Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, parameterSpec);

        byte[] decryptedBytes = cipher.doFinal(cipherWithTag);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private byte[] computeECDHSharedSecret(PrivateKey privateKey, PublicKey peerPublicKey) throws Exception {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(ECDH_ALGORITHM);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(peerPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * HKDF Extract-and-Expand (RFC 5869) to derive 256-bit AES key from shared secret
     */
    private SecretKey deriveAesKey(byte[] sharedSecret) throws Exception {
        // Step 1: HKDF-Extract
        Mac mac = Mac.getInstance(HKDF_HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(HKDF_SALT, HKDF_HMAC_ALGORITHM));
        byte[] prk = mac.doFinal(sharedSecret);

        // Step 2: HKDF-Expand
        mac.init(new SecretKeySpec(prk, HKDF_HMAC_ALGORITHM));
        mac.update(HKDF_INFO);
        mac.update((byte) 0x01); // 1-byte counter
        byte[] okm = mac.doFinal(); // 32 bytes (256 bits) for HmacSHA256

        return new SecretKeySpec(okm, "AES");
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String privBase64 = preferenceHelper.getPrivateKey();
        if (privBase64 == null) {
            throw new IllegalStateException("Local private key not found");
        }
        byte[] privBytes = Base64.decode(privBase64, Base64.DEFAULT);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privBytes);
        KeyFactory kf = KeyFactory.getInstance(EC_ALGORITHM);
        return kf.generatePrivate(spec);
    }

    private PublicKey decodePublicKey(String pubBase64) throws Exception {
        byte[] pubBytes = Base64.decode(pubBase64, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(pubBytes);
        KeyFactory kf = KeyFactory.getInstance(EC_ALGORITHM);
        return kf.generatePublic(spec);
    }
}
