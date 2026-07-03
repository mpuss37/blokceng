package org.example.application;

import org.example.domain.chain.BlockStorage;
import org.example.domain.consensus.ConsensusEngine;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private final CryptoProvider crypto;
    private final ConsensusEngine consensus;
    private final BlockStorage storage;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = false;

    public NodeService(CryptoProvider crypto, ConsensusEngine consensus, BlockStorage storage) {
        this.crypto = crypto;
        this.consensus = consensus;
        this.storage = storage;
    }

    public void start(byte[] validatorPrivateKey, List<String> validatorCandidates) {
        running = true;
        log.info("Node started, chain size: {}", storage.blockCount());

        // schedule block production every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                produceBlock(validatorPrivateKey, validatorCandidates);
            } catch (Exception e) {
                log.error("Block production error: {}", e.getMessage());
            }
        }, 10, 30, TimeUnit.SECONDS);

        // schedule heartbeat
        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            log.debug("Heartbeat: chain={}, pending={}", storage.blockCount(), storage.getPendingTransactions().size());
        }, 5, 10, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
        log.info("Node stopped");
    }

    public void produceBlock(byte[] validatorPrivateKey, List<String> validatorCandidates) {
        List<Transaction> pending = storage.getPendingTransactions();
        if (pending.isEmpty()) {
            log.debug("No pending transactions, skipping block production");
            return;
        }

        // get previous hash and next block index
        int nextIndex = storage.blockCount();
        String previousHash = storage.readBlock(nextIndex - 1)
                .map(Block::hash)
                .orElse("0");

        // produce block
        Block block = consensus.produceBlock(nextIndex, pending, previousHash, validatorPrivateKey);

        // validate and append
        byte[] validatorPubKey = crypto.sign(validatorPrivateKey, "pubkey-derive".getBytes());
        if (consensus.validateBlock(block, validatorPubKey)) {
            storage.appendBlock(block);
            for (Transaction tx : pending) {
                storage.removePendingTransaction(tx.transactionId());
            }
            log.info("Block #{} produced, {} transactions, hash: {}",
                    block.index(), block.transactions().size(), block.hash().substring(0, 16) + "...");
        } else {
            log.warn("Block validation failed, not appending");
        }
    }

    public BlockStorage getStorage() {
        return storage;
    }
}
