package org.example.domain.crypto;

import java.util.List;

public interface MerkleTreeProvider {

    record MerkleProof(List<String> hashes, List<Boolean> isLeft) {}

    String computeRoot(List<String> leafHashes);

    MerkleProof generateProof(List<String> leafHashes, int index);

    boolean verifyProof(String leafHash, MerkleProof proof, String expectedRoot);
}
