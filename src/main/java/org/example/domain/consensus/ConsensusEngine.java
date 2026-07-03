package org.example.domain.consensus;

import org.example.domain.model.Block;
import org.example.domain.model.Transaction;

import java.util.List;

public interface ConsensusEngine {

    String selectValidator(List<String> candidatePublicKeys, byte[] seed);

    Block produceBlock(List<Transaction> transactions, String previousHash, byte[] validatorPrivateKey);

    boolean validateBlock(Block block, byte[] validatorPublicKey);
}
