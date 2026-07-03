package org.example.infrastructure.crypto;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.KeyPair;

import java.security.SecureRandom;

public class Ed25519CryptoProvider implements CryptoProvider {

    private final SecureRandom random = new SecureRandom();

    @Override
    public KeyPair generateKeyPair() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(random));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        Ed25519PublicKeyParameters pub = (Ed25519PublicKeyParameters) pair.getPublic();
        Ed25519PrivateKeyParameters priv = (Ed25519PrivateKeyParameters) pair.getPrivate();
        return new KeyPair(pub.getEncoded(), priv.getEncoded());
    }

    @Override
    public byte[] sign(byte[] privateKey, byte[] data) {
        Ed25519PrivateKeyParameters privKey = new Ed25519PrivateKeyParameters(privateKey);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privKey);
        signer.update(data, 0, data.length);
        return signer.generateSignature();
    }

    @Override
    public boolean verify(byte[] publicKey, byte[] data, byte[] signature) {
        Ed25519PublicKeyParameters pubKey = new Ed25519PublicKeyParameters(publicKey);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, pubKey);
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(signature);
    }

    @Override
    public byte[] publicKeyToBytes(java.security.PublicKey key) {
        throw new UnsupportedOperationException("Use raw byte arrays directly with Ed25519");
    }

    @Override
    public byte[] privateKeyToBytes(java.security.PrivateKey key) {
        throw new UnsupportedOperationException("Use raw byte arrays directly with Ed25519");
    }

    @Override
    public java.security.PublicKey bytesToPublicKey(byte[] bytes) {
        throw new UnsupportedOperationException("Use raw byte arrays directly with Ed25519");
    }

    @Override
    public java.security.PrivateKey bytesToPrivateKey(byte[] bytes) {
        throw new UnsupportedOperationException("Use raw byte arrays directly with Ed25519");
    }
}
