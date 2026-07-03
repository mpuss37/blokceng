package org.example.domain.crypto;

import java.util.List;

public interface LinkableRingSignatureProvider {

    record RingSignature(byte[] signature, String nullifier, List<byte[]> ringPublicKeys) {}

    RingSignature generate(
            byte[] signerPrivateKey,
            byte[] signerPublicKey,
            List<byte[]> ringPublicKeys,
            byte[] message,
            String electionId
    );

    boolean verify(
            RingSignature ringSignature,
            byte[] message
    );
}
