package org.example.infrastructure.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {}

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public static byte[] sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] input) {
        return toHex(sha256(input));
    }

    public static String sha256Hex(String input) {
        return toHex(sha256(input));
    }

    public static String toHex(byte[] bytes) {
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder hex = new StringBuilder(number.toString(16));
        while (hex.length() < 64) {
            hex.insert(0, '0');
        }
        return hex.toString();
    }

    public static byte[] fromHex(String hex) {
        return new BigInteger(hex, 16).toByteArray();
    }
}
