package org.example.domain.model;

public record Credential(
        String credentialId,
        String identityHash,
        String walletAddress,
        String electionId,
        String blindedSignature,
        long issuedAt,
        long expiresAt,
        boolean revoked
) {}
