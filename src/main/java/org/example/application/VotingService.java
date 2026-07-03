package org.example.application;

import org.example.domain.chain.BlockStorage;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.crypto.LinkableRingSignatureProvider;
import org.example.domain.model.*;
import org.example.infrastructure.crypto.HashUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class VotingService {

    private final CryptoProvider crypto;
    private final LinkableRingSignatureProvider lrs;
    private final BlockStorage storage;

    public VotingService(CryptoProvider crypto, LinkableRingSignatureProvider lrs, BlockStorage storage) {
        this.crypto = crypto;
        this.lrs = lrs;
        this.storage = storage;
    }

    public Transaction castVote(
            Wallet wallet,
            byte[] privateKey,
            String electionId,
            String candidateId,
            List<byte[]> ringPublicKeys
    ) {
        // check if credential already voted (nullifier check)
        String nullifierSeed = HashUtil.sha256Hex(
                wallet.address() + "|" + electionId
        );

        if (storage.isNullifierUsed(electionId, nullifierSeed)) {
            throw new IllegalStateException("Double voting detected: credential already used for this election");
        }

        // generate nonce to prevent replay
        String nonce = UUID.randomUUID().toString();

        // build message to sign
        String message = electionId + "|" + candidateId + "|" + nonce + "|" + System.currentTimeMillis();
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        // generate linkable ring signature
        LinkableRingSignatureProvider.RingSignature ringSig = lrs.generate(
                privateKey,
                HashUtil.fromHex(wallet.publicKey()),
                ringPublicKeys.isEmpty() ? List.of(HashUtil.fromHex(wallet.publicKey())) : ringPublicKeys,
                messageBytes,
                electionId
        );

        // create transaction
        String txId = HashUtil.sha256Hex(message + "|" + ringSig.nullifier());

        Transaction tx = new Transaction(
                txId,
                electionId,
                candidateId,
                null, // credential — would be blind-signed in full impl
                nonce,
                ringSig.nullifier(),
                new String(ringSig.signature(), StandardCharsets.UTF_8),
                ringPublicKeys.stream().map(HashUtil::toHex).toList(),
                System.currentTimeMillis()
        );

        // save to pending pool + register nullifier immediately
        storage.savePendingTransaction(tx);
        storage.saveNullifier(electionId, nullifierSeed);

        return tx;
    }

    public boolean validateTransaction(Transaction tx, List<byte[]> ringPublicKeys) {
        // check nullifier
        if (storage.isNullifierUsed(tx.electionId(), tx.nullifier())) {
            return false;
        }

        // verify ring signature
        byte[] message = (tx.electionId() + "|" + tx.candidateId() + "|" + tx.nonce() + "|" + tx.timestamp())
                .getBytes(StandardCharsets.UTF_8);

        LinkableRingSignatureProvider.RingSignature ringSig = new LinkableRingSignatureProvider.RingSignature(
                tx.linkableRingSignature().getBytes(StandardCharsets.UTF_8),
                tx.nullifier(),
                ringPublicKeys
        );

        return lrs.verify(ringSig, message);
    }

    public VoteTally tallyVotes(String electionId) {
        int[] counts = new int[100];
        Set<String> counted = new HashSet<>();

        // count from committed blocks
        List<Block> allBlocks = storage.readAllBlocks();
        for (Block block : allBlocks) {
            for (Transaction tx : block.transactions()) {
                if (counted.add(tx.transactionId())) {
                    countTx(tx, electionId, counts);
                }
            }
        }

        // also count from pending (not yet in any block)
        for (Transaction tx : storage.getPendingTransactions()) {
            if (counted.add(tx.transactionId())) {
                countTx(tx, electionId, counts);
            }
        }

        return new VoteTally(electionId, counts);
    }

    private void countTx(Transaction tx, String electionId, int[] counts) {
        if (tx.electionId().equals(electionId)) {
            try {
                int candidateIndex = Integer.parseInt(tx.candidateId());
                if (candidateIndex >= 0 && candidateIndex < counts.length) {
                    counts[candidateIndex]++;
                }
            } catch (NumberFormatException e) {
                // skip non-numeric candidate IDs
            }
        }
    }

    public record VoteTally(String electionId, int[] candidateVotes) {}
}
