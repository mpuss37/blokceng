package org.example.infrastructure.consensus;

import org.example.domain.consensus.ConsensusEngine;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.crypto.VrfProvider;
import org.example.domain.crypto.MerkleTreeProvider;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.example.infrastructure.crypto.HashUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ProofOfAuthority implements ConsensusEngine {

    private final CryptoProvider crypto;
    private final VrfProvider vrf;
    private final MerkleTreeProvider merkleTree;

    public ProofOfAuthority(CryptoProvider crypto, VrfProvider vrf, MerkleTreeProvider merkleTree) {
        this.crypto = crypto;
        this.vrf = vrf;
        this.merkleTree = merkleTree;
    }

    @Override
    public String selectValidator(List<String> candidatePublicKeys, byte[] seed) {
        // use VRF to select validator deterministically
        // the candidate whose VRF output is smallest wins
        String bestPubKey = null;
        byte[] bestOutput = null;
        for (String pubKeyHex : candidatePublicKeys) {
            byte[] pubKeyBytes = HashUtil.fromHex(pubKeyHex);
            VrfProvider.VrfProof proof = vrf.generateProof(pubKeyBytes, seed);
            if (bestOutput == null || compareBytes(proof.output(), bestOutput) < 0) {
                bestOutput = proof.output();
                bestPubKey = pubKeyHex;
            }
        }
        return bestPubKey;
    }

    @Override
    public Block produceBlock(List<Transaction> transactions, String previousHash, byte[] validatorPrivateKey) {
        // compute merkle root from transactions
        List<String> txHashes = new ArrayList<>();
        for (Transaction tx : transactions) {
            txHashes.add(HashUtil.sha256Hex(tx.transactionId()));
        }
        String merkleRoot = merkleTree.computeRoot(txHashes);

        // determine block index
        int index = 0; // caller should provide this, but simplified here

        // generate VRF proof
        String seed = previousHash + "|" + System.currentTimeMillis();
        byte[] vrfProofBytes = crypto.sign(validatorPrivateKey, seed.getBytes(StandardCharsets.UTF_8));
        String vrfProofHex = HashUtil.toHex(vrfProofBytes);

        // get validator public key
        byte[] validatorPubKey = derivePublicKey(validatorPrivateKey);
        String validatorId = HashUtil.toHex(validatorPubKey);

        // sign the block
        String blockContent = index + "|" + previousHash + "|" + merkleRoot + "|" + validatorId;
        byte[] signature = crypto.sign(validatorPrivateKey, blockContent.getBytes(StandardCharsets.UTF_8));

        // compute block hash
        String hash = HashUtil.sha256Hex(blockContent);

        return new Block(
                index,
                System.currentTimeMillis(),
                List.copyOf(transactions),
                previousHash,
                merkleRoot,
                validatorId,
                vrfProofHex,
                HashUtil.toHex(signature),
                hash
        );
    }

    @Override
    public boolean validateBlock(Block block, byte[] validatorPublicKey) {
        // verify block hash
        String blockContent = block.index() + "|" + block.previousHash() + "|" + block.merkleRoot() + "|" + block.validatorId();
        String expectedHash = HashUtil.sha256Hex(blockContent);
        if (!expectedHash.equals(block.hash())) return false;

        // verify merkle root
        List<String> txHashes = new ArrayList<>();
        for (Transaction tx : block.transactions()) {
            txHashes.add(HashUtil.sha256Hex(tx.transactionId()));
        }
        String expectedMerkle = merkleTree.computeRoot(txHashes);
        if (!expectedMerkle.equals(block.merkleRoot())) return false;

        // verify validator signature
        byte[] sigBytes = HashUtil.fromHex(block.validatorSignature());
        return crypto.verify(validatorPublicKey, blockContent.getBytes(StandardCharsets.UTF_8), sigBytes);
    }

    private byte[] derivePublicKey(byte[] privateKey) {
        // pon ytail: in real impl, derive from private key
        // for now, sign a known value and use that as identity
        return crypto.sign(privateKey, "pubkey-derive".getBytes(StandardCharsets.UTF_8));
    }

    private int compareBytes(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = Integer.compare(a[i] & 0xFF, b[i] & 0xFF);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.length, b.length);
    }
}
