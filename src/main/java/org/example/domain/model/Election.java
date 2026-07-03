package org.example.domain.model;

import java.util.List;

public record Election(
        String electionId,
        String name,
        List<String> candidateIds,
        long startTime,
        long endTime,
        List<String> adminPublicKeys,
        int requiredSignatures,
        ElectionStatus status
) {
    public Election {
        candidateIds = List.copyOf(candidateIds);
        adminPublicKeys = List.copyOf(adminPublicKeys);
    }
}
