package org.example.domain.crypto;

public interface BlindSignatureProvider {

    record BlindedMessage(byte[] blindedData, byte[] blindingFactor) {}

    BlindedMessage blind(byte[] message, byte[] publicKey);

    byte[] signBlinded(byte[] blindedMessage, byte[] signingPrivateKey);

    byte[] unblind(byte[] blindedSignature, byte[] blindingFactor, byte[] signerPublicKey);

    boolean verifyBlindSignature(byte[] message, byte[] unblindedSignature, byte[] signerPublicKey);
}
