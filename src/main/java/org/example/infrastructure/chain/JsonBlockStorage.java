package org.example.infrastructure.chain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.domain.chain.BlockStorage;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class JsonBlockStorage implements BlockStorage {

    private static final String BLOCKS_FILE = "data/blocks.json";
    private static final String NULLIFIERS_FILE = "data/nullifiers.json";
    private static final String PENDING_FILE = "data/pending-tx.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final List<Block> blocks = new CopyOnWriteArrayList<>();
    private final Map<String, Set<String>> nullifiers = new ConcurrentHashMap<>();
    private final List<Transaction> pendingTransactions = new CopyOnWriteArrayList<>();

    public JsonBlockStorage() {
        ensureDataDir();
        loadBlocks();
        loadNullifiers();
        loadPending();
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data directory", e);
        }
    }

    @Override
    public void appendBlock(Block block) {
        blocks.add(block);
        // register nullifiers from block transactions
        for (Transaction tx : block.transactions()) {
            if (tx.nullifier() != null) {
                nullifiers
                        .computeIfAbsent(tx.electionId(), k -> ConcurrentHashMap.newKeySet())
                        .add(tx.nullifier());
            }
        }
        saveBlocks();
        saveNullifiers();
    }

    @Override
    public Optional<Block> readBlock(int index) {
        if (index < 0 || index >= blocks.size()) return Optional.empty();
        return Optional.of(blocks.get(index));
    }

    @Override
    public List<Block> readAllBlocks() {
        return List.copyOf(blocks);
    }

    @Override
    public int blockCount() {
        return blocks.size();
    }

    @Override
    public void saveNullifier(String electionId, String nullifier) {
        nullifiers.computeIfAbsent(electionId, k -> ConcurrentHashMap.newKeySet()).add(nullifier);
        saveNullifiers();
    }

    @Override
    public boolean isNullifierUsed(String electionId, String nullifier) {
        return nullifiers.getOrDefault(electionId, Set.of()).contains(nullifier);
    }

    @Override
    public Set<String> getUsedNullifiers(String electionId) {
        return Set.copyOf(nullifiers.getOrDefault(electionId, Set.of()));
    }

    @Override
    public void savePendingTransaction(Transaction transaction) {
        pendingTransactions.add(transaction);
        savePending();
    }

    @Override
    public List<Transaction> getPendingTransactions() {
        return List.copyOf(pendingTransactions);
    }

    @Override
    public void removePendingTransaction(String transactionId) {
        pendingTransactions.removeIf(tx -> tx.transactionId().equals(transactionId));
        savePending();
    }

    @Override
    public void reloadPending() {
        pendingTransactions.clear();
        loadPending();
    }

    @Override
    public void createSnapshot(String path) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("blocks", blocks);
            snapshot.put("nullifiers", nullifiers);
            mapper.writeValue(Path.of(path).toFile(), snapshot);
        } catch (IOException e) {
            throw new RuntimeException("Snapshot creation failed", e);
        }
    }

    @Override
    public void restoreFromSnapshot(String path) {
        try {
            Map<String, Object> snapshot = mapper.readValue(
                    Path.of(path).toFile(),
                    new TypeReference<>() {}
            );
            blocks.clear();
            nullifiers.clear();
            if (snapshot.containsKey("blocks")) {
                List<Block> loaded = mapper.convertValue(
                        snapshot.get("blocks"),
                        new TypeReference<>() {}
                );
                blocks.addAll(loaded);
            }
            if (snapshot.containsKey("nullifiers")) {
                Map<String, Set<String>> loaded = mapper.convertValue(
                        snapshot.get("nullifiers"),
                        new TypeReference<>() {}
                );
                nullifiers.putAll(loaded);
            }
            saveBlocks();
            saveNullifiers();
        } catch (IOException e) {
            throw new RuntimeException("Snapshot restore failed", e);
        }
    }

    // --- persistence helpers ---

    private void saveBlocks() {
        try {
            mapper.writeValue(Path.of(BLOCKS_FILE).toFile(), blocks);
        } catch (IOException e) {
            System.err.println("Error saving blocks: " + e.getMessage());
        }
    }

    private void loadBlocks() {
        Path path = Path.of(BLOCKS_FILE);
        if (!Files.exists(path)) return;
        try {
            List<Block> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {});
            blocks.addAll(loaded);
        } catch (IOException e) {
            System.err.println("Error loading blocks: " + e.getMessage());
        }
    }

    private void saveNullifiers() {
        try {
            mapper.writeValue(Path.of(NULLIFIERS_FILE).toFile(), nullifiers);
        } catch (IOException e) {
            System.err.println("Error saving nullifiers: " + e.getMessage());
        }
    }

    private void loadNullifiers() {
        Path path = Path.of(NULLIFIERS_FILE);
        if (!Files.exists(path)) return;
        try {
            Map<String, Set<String>> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {});
            nullifiers.putAll(loaded);
        } catch (IOException e) {
            System.err.println("Error loading nullifiers: " + e.getMessage());
        }
    }

    private void savePending() {
        try {
            mapper.writeValue(Path.of(PENDING_FILE).toFile(), pendingTransactions);
        } catch (IOException e) {
            System.err.println("Error saving pending tx: " + e.getMessage());
        }
    }

    private void loadPending() {
        Path path = Path.of(PENDING_FILE);
        if (!Files.exists(path)) return;
        try {
            List<Transaction> loaded = mapper.readValue(path.toFile(), new TypeReference<>() {});
            pendingTransactions.addAll(loaded);
        } catch (IOException e) {
            System.err.println("Error loading pending tx: " + e.getMessage());
        }
    }
}
