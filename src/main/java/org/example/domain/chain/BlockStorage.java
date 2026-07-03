package org.example.domain.chain;

import org.example.domain.model.Block;
import org.example.domain.model.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BlockStorage {

    void appendBlock(Block block);

    Optional<Block> readBlock(int index);

    List<Block> readAllBlocks();

    int blockCount();

    void saveNullifier(String electionId, String nullifier);

    boolean isNullifierUsed(String electionId, String nullifier);

    Set<String> getUsedNullifiers(String electionId);

    void savePendingTransaction(Transaction transaction);

    List<Transaction> getPendingTransactions();

    void removePendingTransaction(String transactionId);

    void reloadPending();

    void reloadNullifiers();

    void createSnapshot(String path);

    void restoreFromSnapshot(String path);
}
