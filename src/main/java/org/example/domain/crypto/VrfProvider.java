package org.example.domain.crypto;

public interface VrfProvider {

    record VrfProof(byte[] proof, byte[] output) {}

    VrfProof generateProof(byte[] privateKey, byte[] input);

    boolean verifyProof(byte[] publicKey, byte[] input, VrfProof proof);
}
