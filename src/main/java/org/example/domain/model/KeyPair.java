package org.example.domain.model;

public record KeyPair(
        byte[] publicKey,
        byte[] privateKey
) {}
