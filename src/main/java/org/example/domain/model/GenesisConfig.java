package org.example.domain.model;

import java.util.List;

public record GenesisConfig(
        List<String> adminPublicKeys,
        List<String> initialValidators,
        String consensusAlgorithm,
        String cryptoAlgorithm,
        int protocolVersion
) {
    public GenesisConfig {
        adminPublicKeys = List.copyOf(adminPublicKeys);
        initialValidators = List.copyOf(initialValidators);
    }
}
