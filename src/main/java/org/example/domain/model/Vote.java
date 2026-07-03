package org.example.domain.model;

public record Vote(
        String electionId,
        String candidateId,
        String nullifier,
        long timestamp
) {}
