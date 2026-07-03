package org.example.domain.crypto;

import org.example.domain.model.KeyPair;

public interface CryptoProvider {

    KeyPair generateKeyPair();

    byte[] sign(byte[] privateKey, byte[] data);

    boolean verify(byte[] publicKey, byte[] data, byte[] signature);

    byte[] publicKeyToBytes(java.security.PublicKey key);

    byte[] privateKeyToBytes(java.security.PrivateKey key);

    java.security.PublicKey bytesToPublicKey(byte[] bytes);

    java.security.PrivateKey bytesToPrivateKey(byte[] bytes);
}
