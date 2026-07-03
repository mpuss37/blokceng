package org.example.domain.crypto;

import java.util.List;

public interface ThresholdEncryptionProvider {

    record EncryptedData(byte[] ciphertext, byte[] nonce, List<byte[]> keyShares) {}

    EncryptedData encrypt(byte[] plaintext, List<byte[]> publicKeys, int threshold);

    byte[] decrypt(EncryptedData encrypted, List<byte[]> privateKeyShares);
}
