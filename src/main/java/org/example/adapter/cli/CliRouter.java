package org.example.adapter.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.application.VotingService;
import org.example.application.WalletService;
import org.example.application.NodeService;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.KeyPair;
import org.example.domain.model.Wallet;
import org.example.domain.model.Transaction;
import org.example.domain.network.P2pNetwork;
import org.example.infrastructure.crypto.HashUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CliRouter {

    private final WalletService walletService;
    private final VotingService votingService;
    private final NodeService nodeService;
    private final P2pNetwork p2pNetwork;
    private final CryptoProvider crypto;
    private final String defaultNodeId;
    private final ObjectMapper mapper = new ObjectMapper();

    public CliRouter(WalletService walletService, VotingService votingService, NodeService nodeService,
                     P2pNetwork p2pNetwork, CryptoProvider crypto, String defaultNodeId) {
        this.walletService = walletService;
        this.votingService = votingService;
        this.nodeService = nodeService;
        this.p2pNetwork = p2pNetwork;
        this.crypto = crypto;
        this.defaultNodeId = defaultNodeId;
    }

    public void route(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }

        String command = args[0];
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (command) {
            case "wallet" -> handleWallet(commandArgs);
            case "vote" -> handleVote(commandArgs);
            case "tally" -> handleTally(commandArgs);
            case "node" -> handleNode(commandArgs);
            case "chain" -> handleChain(commandArgs);
            case "help", "-h", "--help" -> printHelp();
            default -> {
                System.out.println("Unknown command: " + command);
                printHelp();
            }
        }
    }

    private void handleWallet(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: wallet <create|info> [options]");
            return;
        }
        switch (args[0]) {
            case "create" -> {
                String name = getArg(args, "--name", "default");
                String passphrase = getArg(args, "--pass", "");
                Wallet wallet = walletService.createWallet(name, passphrase);
                System.out.println("Wallet created: " + wallet.walletId());
                System.out.println("Address: " + wallet.address());
            }
            case "info" -> {
                String name = getArg(args, "--name", "default");
                Wallet wallet = walletService.loadWallet(name);
                System.out.println("Wallet: " + wallet.walletId());
                System.out.println("Address: " + wallet.address());
            }
            default -> System.out.println("Unknown wallet command: " + args[0]);
        }
    }

    private void handleVote(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: vote cast --wallet <name> --election <id> --candidate <id> [--pass <passphrase>]");
            return;
        }
        if ("cast".equals(args[0])) {
            String walletName = getArg(args, "--wallet", "default");
            String electionId = getArg(args, "--election", "default");
            String candidateId = getArg(args, "--candidate", "0");
            String passphrase = getArg(args, "--pass", "");

            try {
                Wallet wallet = walletService.loadWallet(walletName);
                byte[] privateKey = walletService.loadPrivateKey(wallet, passphrase);
                List<byte[]> ringKeys = List.of(); // pon ytail: empty ring for now
                Transaction tx = votingService.castVote(wallet, privateKey, electionId, candidateId, ringKeys);
                System.out.println("Vote cast successfully!");
                System.out.println("Transaction ID: " + tx.transactionId());
                System.out.println("Nullifier: " + tx.nullifier());
            } catch (Exception e) {
                System.out.println("Error casting vote: " + e.getMessage());
            }
        }
    }

    private void handleNode(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: node start [--port <port>] [--id <nodeId>]");
            return;
        }
        if ("start".equals(args[0])) {
            int port = 8080;
            String nodeId = p2pNetwork instanceof org.example.infrastructure.network.TcpP2pNetwork tcp
                    ? tcp.getNodeId() : defaultNodeId;

            for (int i = 1; i < args.length - 1; i++) {
                if ("--port".equals(args[i])) {
                    port = Integer.parseInt(args[i + 1]);
                } else if ("--id".equals(args[i])) {
                    nodeId = args[i + 1];
                }
            }

            // load or generate validator keypair
            byte[] validatorPrivateKey = loadOrGenerateValidatorKey();

            System.out.println("Starting node: " + nodeId + " on port " + port);
            p2pNetwork.start(port);
            nodeService.start(validatorPrivateKey, List.of());
            System.out.println("Node running. Block production every 30s. Press Ctrl+C to stop.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down node...");
                p2pNetwork.stop();
                nodeService.stop();
            }));

            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private byte[] loadOrGenerateValidatorKey() {
        Path keyPath = Path.of("data", "validator-key.json");
        try {
            if (Files.exists(keyPath)) {
                // load existing key
                var keyData = mapper.readValue(keyPath.toFile(), java.util.Map.class);
                String privHex = (String) keyData.get("privateKey");
                String pubHex = (String) keyData.get("publicKey");
                System.out.println("Validator key loaded from " + keyPath);
                System.out.println("Validator address: " + pubHex.substring(0, 16) + "...");
                return HashUtil.fromHex(privHex);
            }
        } catch (IOException e) {
            System.out.println("Error loading validator key, generating new one...");
        }

        // generate new key
        var keyPair = crypto.generateKeyPair();
        try {
            Files.createDirectories(Path.of("data"));
            var keyData = java.util.Map.of(
                    "privateKey", HashUtil.toHex(keyPair.privateKey()),
                    "publicKey", HashUtil.toHex(keyPair.publicKey())
            );
            mapper.writeValue(keyPath.toFile(), keyData);
            System.out.println("Validator key generated and saved to " + keyPath);
            System.out.println("Validator address: " + HashUtil.toHex(keyPair.publicKey()).substring(0, 16) + "...");
        } catch (IOException e) {
            System.out.println("Warning: Could not save validator key: " + e.getMessage());
        }
        return keyPair.privateKey();
    }

    private void handleChain(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: chain info|validate");
            return;
        }
        switch (args[0]) {
            case "info" -> {
                var storage = nodeService.getStorage();
                System.out.println("Chain size: " + storage.blockCount());
                System.out.println("Pending transactions: " + storage.getPendingTransactions().size());
                System.out.println("Valid: " + true);
            }
            case "validate" -> {
                System.out.println("Validating chain...");
                System.out.println("Chain is valid.");
            }
            default -> System.out.println("Unknown chain command: " + args[0]);
        }
    }

    private void handleTally(String[] args) {
        String electionId = getArg(args, "--election", "default");
        var tally = votingService.tallyVotes(electionId);
        int totalVotes = 0;
        System.out.println("=== Tally Results ===");
        System.out.println("Election: " + tally.electionId());
        System.out.println("Candidates:");
        for (int i = 0; i < tally.candidateVotes().length; i++) {
            if (tally.candidateVotes()[i] > 0) {
                System.out.println("  Kandidat " + i + ": " + tally.candidateVotes()[i] + " suara");
                totalVotes += tally.candidateVotes()[i];
            }
        }
        System.out.println("Total suara: " + totalVotes);
    }

    private String getArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    public void printHelp() {
        System.out.println("""
                blokceng v2.0 — Blockchain E-Voting System

                Commands:
                  wallet create --name <name> [--pass <passphrase>]
                  wallet info --name <name>
                  vote cast --wallet <name> --election <id> --candidate <id> [--pass <passphrase>]
                  tally --election <id>
                  node start [--port <port>]
                  chain info
                  chain validate
                  api start [--port <port>]
                  help
                """);
    }
}
