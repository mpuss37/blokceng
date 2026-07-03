package org.example.infrastructure.crypto;

import org.example.domain.crypto.LinkableRingSignatureProvider;
import org.example.domain.crypto.CryptoProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplified Linkable Ring Signature using Ed25519 key image.
 * Key image = SHA256(privateKey || "keyimage") ensures linkability:
 * same credential always produces same nullifier.
 */
public class LinkableRingSignatureImpl implements LinkableRingSignatureProvider {

    private final CryptoProvider crypto;

    public LinkableRingSignatureImpl(CryptoProvider crypto) {
        this.crypto = crypto;
    }

    @Override
    public RingSignature generate(
            byte[] signerPrivateKey,
            byte[] signerPublicKey,
            List<byte[]> ringPublicKeys,
            byte[] message,
            String electionId
    ) {
        // find signer index in ring
        int signerIndex = -1;
        String signerPubHex = HashUtil.toHex(signerPublicKey);
        for (int i = 0; i < ringPublicKeys.size(); i++) {
            if (HashUtil.toHex(ringPublicKeys.get(i)).equals(signerPubHex)) {
                signerIndex = i;
                break;
            }
        }
        if (signerIndex == -1) {
            throw new IllegalArgumentException("Signer public key not found in ring");
        }

        // generate key image (nullifier) deterministically from private key + election
        byte[] keyImageInput = HashUtil.sha256(
                HashUtil.toHex(signerPrivateKey) + "|" + electionId
        );
        String nullifier = HashUtil.sha256Hex(keyImageInput);

        // generate real signature for signer
        byte[] realSig = crypto.sign(signerPrivateKey, message);

        // build ring signature: real signature at signerIndex, random placeholders for others
        // pon ytail: simplified LRS, full implementation would use CLSAG or similar
        List<byte[]> ringSigs = new ArrayList<>();
        for (int i = 0; i < ringPublicKeys.size(); i++) {
            if (i == signerIndex) {
                ringSigs.add(realSig);
            } else {
                ringSigs.add(HashUtil.sha256(("ring_" + i + "_" + System.nanoTime()).getBytes(StandardCharsets.UTF_8)));
            }
        }

        // combine all into a single signature blob
        StringBuilder combined = new StringBuilder();
        combined.append(nullifier).append(":");
        for (byte[] sig : ringSigs) {
            combined.append(HashUtil.toHex(sig)).append(",");
        }
        return new RingSignature(
                combined.toString().getBytes(StandardCharsets.UTF_8),
                nullifier,
                ringPublicKeys
        );
    }

    @Override
    public boolean verify(RingSignature ringSignature, byte[] message) {
        // verify that at least one signature in the ring is valid
        String sigStr = new String(ringSignature.signature(), StandardCharsets.UTF_8);
        String[] parts = sigStr.split(":");
        if (parts.length < 2) return false;

        String nullifier = parts[0];
        if (nullifier.isEmpty()) return false;

        String[] sigs = parts[1].split(",");
        List<byte[]> ringKeys = ringSignature.ringPublicKeys();

        for (int i = 0; i < Math.min(sigs.length, ringKeys.size()); i++) {
            try {
                byte[] sigBytes = HashUtil.fromHex(sigs[i]);
                if (crypto.verify(ringKeys.get(i), message, sigBytes)) {
                    return true;
                }
            } catch (Exception e) {
                // try next
            }
        }
        return false;
    }
}
