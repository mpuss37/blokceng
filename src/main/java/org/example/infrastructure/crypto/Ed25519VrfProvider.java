package org.example.infrastructure.crypto;

import org.example.domain.crypto.VrfProvider;

/**
 * Simplified VRF using Ed25519 sign + SHA256.
 * VRF output = SHA256(signature), deterministic and verifiable.
 */
public class Ed25519VrfProvider implements VrfProvider {

    private final org.example.domain.crypto.CryptoProvider crypto;

    public Ed25519VrfProvider(org.example.domain.crypto.CryptoProvider crypto) {
        this.crypto = crypto;
    }

    @Override
    public VrfProof generateProof(byte[] privateKey, byte[] input) {
        byte[] signature = crypto.sign(privateKey, input);
        byte[] output = HashUtil.sha256(signature);
        return new VrfProof(signature, output);
    }

    @Override
    public boolean verifyProof(byte[] publicKey, byte[] input, VrfProof proof) {
        boolean sigValid = crypto.verify(publicKey, input, proof.proof());
        if (!sigValid) return false;
        byte[] expectedOutput = HashUtil.sha256(proof.proof());
        return java.util.Arrays.equals(expectedOutput, proof.output());
    }
}
