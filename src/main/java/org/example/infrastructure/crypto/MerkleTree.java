package org.example.infrastructure.crypto;

import org.example.domain.crypto.MerkleTreeProvider;

import java.util.ArrayList;
import java.util.List;

public class MerkleTree implements MerkleTreeProvider {

    @Override
    public String computeRoot(List<String> leafHashes) {
        if (leafHashes == null || leafHashes.isEmpty()) {
            return HashUtil.sha256Hex("");
        }
        List<String> currentLevel = new ArrayList<>(leafHashes);
        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                nextLevel.add(HashUtil.sha256Hex(left + right));
            }
            currentLevel = nextLevel;
        }
        return currentLevel.get(0);
    }

    @Override
    public MerkleProof generateProof(List<String> leafHashes, int index) {
        if (index < 0 || index >= leafHashes.size()) {
            throw new IndexOutOfBoundsException("Leaf index out of range");
        }
        List<String> hashes = new ArrayList<>();
        List<Boolean> isLeft = new ArrayList<>();

        List<String> currentLevel = new ArrayList<>(leafHashes);
        int currentIndex = index;

        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;
                nextLevel.add(HashUtil.sha256Hex(left + right));
            }

            boolean isCurrentLeft = (currentIndex % 2 == 0);
            int siblingIndex = isCurrentLeft ? currentIndex + 1 : currentIndex - 1;
            if (siblingIndex < currentLevel.size()) {
                hashes.add(currentLevel.get(siblingIndex));
                isLeft.add(!isCurrentLeft);
            }

            currentIndex /= 2;
            currentLevel = nextLevel;
        }
        return new MerkleProof(hashes, isLeft);
    }

    @Override
    public boolean verifyProof(String leafHash, MerkleProof proof, String expectedRoot) {
        String current = leafHash;
        for (int i = 0; i < proof.hashes().size(); i++) {
            String sibling = proof.hashes().get(i);
            if (proof.isLeft().get(i)) {
                current = HashUtil.sha256Hex(sibling + current);
            } else {
                current = HashUtil.sha256Hex(current + sibling);
            }
        }
        return current.equals(expectedRoot);
    }
}
