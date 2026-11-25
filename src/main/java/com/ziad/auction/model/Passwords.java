package com.ziad.auction.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Salted PBKDF2-HMAC-SHA256 password hashing (JDK only). Stored format: iterations:salt:hash. */
public final class Passwords {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return ITERATIONS + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(hash);
    }

    public static boolean matches(char[] password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 3 || password == null) {
            return false;
        }
        Base64.Decoder b64 = Base64.getDecoder();
        byte[] expected = b64.decode(parts[2]);
        byte[] actual = derive(password, b64.decode(parts[1]), Integer.parseInt(parts[0]));
        return MessageDigest.isEqual(expected, actual);  // constant-time comparison
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        } finally {
            spec.clearPassword();  // wipe the password copy; the salt must be kept, it is stored with the hash
        }
    }
}
