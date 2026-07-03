package org.example.domain.chain;

import org.example.domain.model.Block;

import java.util.List;
import java.util.Optional;

public interface BlockchainRepository {

    void saveBlock(Block block);

    Optional<Block> getBlock(int index);

    Optional<Block> getLatestBlock();

    List<Block> getBlocks(int fromIndex, int toIndex);

    List<Block> getAllBlocks();

    int getChainSize();

    boolean isValidChain();
}
