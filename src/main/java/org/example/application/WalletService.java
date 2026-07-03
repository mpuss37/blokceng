package org.example.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.KeyPair;
import org.example.domain.model.Wallet;
import org.example.infrastructure.crypto.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

public class WalletService {

    private final CryptoProvider crypto;
    private final ObjectMapper mapper = new ObjectMapper();

    public WalletService(CryptoProvider crypto) {
        this.crypto = crypto;
    }

    public Wallet createWallet(String name, String passphrase) {
        KeyPair keyPair = crypto.generateKeyPair();

        // encrypt private key with passphrase
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        byte[] encryptedPrivKey = xorEncrypt(keyPair.privateKey(), passphrase.getBytes(), iv);

        String address = HashUtil.sha256Hex(keyPair.publicKey());

        Wallet wallet = new Wallet(
                name,
                HashUtil.toHex(keyPair.publicKey()),
                encryptedPrivKey,
                iv,
                address
        );

        // save wallet to file
        try {
            String walletFile = name + ".wallet.json";
            mapper.writeValue(Path.of(walletFile).toFile(), wallet);
            System.out.println("Wallet created: " + walletFile);
            System.out.println("Address: " + address);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save wallet", e);
        }

        return wallet;
    }

    public byte[] loadPrivateKey(Wallet wallet, String passphrase) {
        return xorEncrypt(wallet.encryptedPrivateKey(), passphrase.getBytes(), wallet.iv());
    }

    public Wallet loadWallet(String name) {
        String walletFile = name + ".wallet.json";
        try {
            return mapper.readValue(Path.of(walletFile).toFile(), Wallet.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load wallet: " + walletFile, e);
        }
    }

    // pon ytail: XOR "encryption" — replace with AES-GCM for production
    private byte[] xorEncrypt(byte[] data, byte[] key, byte[] iv) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length] ^ iv[i % iv.length]);
        }
        return result;
    }
}
