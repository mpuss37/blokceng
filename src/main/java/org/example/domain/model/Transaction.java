package org.example.domain.model;

import java.util.List;

public record Transaction(
        String transactionId,
        String electionId,
        String candidateId,
        Credential credential,
        String nonce,
        String nullifier,
        String linkableRingSignature,
        List<String> ringMembers,
        long timestamp
) {}
