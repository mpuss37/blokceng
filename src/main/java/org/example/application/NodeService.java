package org.example.application;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.example.domain.chain.BlockStorage;
import org.example.domain.consensus.ConsensusEngine;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.example.domain.network.P2pNetwork;
import org.example.infrastructure.crypto.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeService implements P2pNetwork.NetworkMessageListener {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

    private final CryptoProvider crypto;
    private final ConsensusEngine consensus;
    private final BlockStorage storage;
    private P2pNetwork p2pNetwork;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private volatile boolean running = false;

    public NodeService(CryptoProvider crypto, ConsensusEngine consensus, BlockStorage storage) {
        this.crypto = crypto;
        this.consensus = consensus;
        this.storage = storage;
    }

    public void setP2pNetwork(P2pNetwork p2pNetwork) {
        this.p2pNetwork = p2pNetwork;
        this.p2pNetwork.addMessageListener(this);
    }

    public void start(byte[] validatorPrivateKey, List<String> validatorCandidates) {
        running = true;
        log.info("Node started, chain size: {}", storage.blockCount());

        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            try {
                produceBlock(validatorPrivateKey, validatorCandidates);
            } catch (Exception e) {
                log.error("Block production error: {}", e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            if (!running) return;
            log.debug("Heartbeat: chain={}, pending={}, peers={}",
                    storage.blockCount(),
                    storage.getPendingTransactions().size(),
                    p2pNetwork != null ? p2pNetwork.getConnectedPeerIds().size() : 0);
        }, 5, 10, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        scheduler.shutdown();
        log.info("Node stopped");
    }

    public void broadcastTransaction(Transaction transaction) {
        if (p2pNetwork != null) {
            p2pNetwork.broadcastTransaction(transaction);
        }
    }

    public void produceBlock(byte[] validatorPrivateKey, List<String> validatorCandidates) {
        storage.reloadPending();
        storage.reloadNullifiers();

        List<Transaction> pending = storage.getPendingTransactions();
        if (pending.isEmpty()) {
            log.debug("No pending transactions, skipping block production");
            return;
        }

        log.info("Producing block: {} pending txs, chain={}", pending.size(), storage.blockCount());

        int nextIndex = storage.blockCount();
        String previousHash = storage.readBlock(nextIndex - 1)
                .map(Block::hash)
                .orElse("0");

        Block block = consensus.produceBlock(nextIndex, pending, previousHash, validatorPrivateKey);

        Ed25519PrivateKeyParameters privKey = new Ed25519PrivateKeyParameters(validatorPrivateKey);
        byte[] validatorPubKey = privKey.generatePublicKey().getEncoded();
        boolean valid = consensus.validateBlock(block, validatorPubKey);

        if (valid) {
            storage.appendBlock(block);
            for (Transaction tx : pending) {
                storage.removePendingTransaction(tx.transactionId());
            }
            log.info("Block #{} produced, {} tx, hash: {}",
                    block.index(), block.transactions().size(), block.hash().substring(0, 16) + "...");

            if (p2pNetwork != null) {
                p2pNetwork.broadcastBlock(block);
            }
        } else {
            log.warn("Block #{} validation FAILED — not appending", block.index());
        }
    }

    // --- P2P message handlers ---

    @Override
    public void onTransactionReceived(Transaction transaction) {
        log.info("Received tx {} from peer", transaction.transactionId().substring(0, 16));
        if (storage.isNullifierUsed(transaction.electionId(), transaction.nullifier())) {
            log.debug("Tx {} already exists (nullifier used), skipping", transaction.transactionId().substring(0, 16));
            return;
        }
        storage.savePendingTransaction(transaction);
        storage.saveNullifier(transaction.electionId(), transaction.nullifier());
    }

    @Override
    public void onBlockReceived(Block block) {
        log.info("Received block #{} from peer, {} tx", block.index(), block.transactions().size());
        if (block.index() < storage.blockCount()) {
            log.debug("Block #{} already exists locally, skipping", block.index());
            return;
        }
        storage.reloadBlocks();
        if (block.index() == storage.blockCount()) {
            storage.appendBlock(block);
            for (Transaction tx : block.transactions()) {
                storage.removePendingTransaction(tx.transactionId());
            }
            log.info("Block #{} appended from peer", block.index());
        } else {
            log.warn("Received block #{}, expected #{} — sync needed", block.index(), storage.blockCount());
        }
    }

    @Override
    public void onPeerConnected(String peerId) {
        log.info("Peer connected: {}", peerId);
    }

    @Override
    public void onPeerDisconnected(String peerId) {
        log.info("Peer disconnected: {}", peerId);
    }

    public BlockStorage getStorage() {
        return storage;
    }
}
