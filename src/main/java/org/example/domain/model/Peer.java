package org.example.domain.model;

public record Peer(
        String peerId,
        String host,
        int port,
        long lastSeen,
        int reputationScore,
        PeerStatus status
) {}
