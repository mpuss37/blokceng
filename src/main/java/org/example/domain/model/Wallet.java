package org.example.domain.model;

public record Wallet(
        String walletId,
        String publicKey,
        byte[] encryptedPrivateKey,
        byte[] iv,
        String address
) {}
