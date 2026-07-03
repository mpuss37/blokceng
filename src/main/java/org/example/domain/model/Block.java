package org.example.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record Block(
        int index,
        long timestamp,
        List<Transaction> transactions,
        String previousHash,
        String merkleRoot,
        String validatorId,
        String vrfProof,
        String validatorSignature,
        String hash
) {
    public Block {
        transactions = List.copyOf(transactions);
    }

    public String computeHash() {
        return org.example.infrastructure.crypto.HashUtil.sha256Hex(
                index + "|" + timestamp + "|" + merkleRoot + "|" + previousHash + "|" + validatorId
        );
    }
}
