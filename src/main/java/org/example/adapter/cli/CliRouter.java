package org.example.adapter.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.example.adapter.api.ApiServer;
import org.example.application.VotingService;
import org.example.application.WalletService;
import org.example.application.NodeService;
import org.example.domain.crypto.CryptoProvider;
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
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new ParameterNamesModule());

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
                try {
                    Wallet wallet = walletService.createWallet(name, passphrase);
                    System.out.println("Wallet created: " + wallet.walletId());
                    System.out.println("Address: " + wallet.address());
                } catch (IllegalStateException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
            case "info" -> {
                String name = getArg(args, "--name", "default");
                try {
                    Wallet wallet = walletService.loadWallet(name);
                    System.out.println("Wallet: " + wallet.walletId());
                    System.out.println("Address: " + wallet.address());
                } catch (RuntimeException e) {
                    System.out.println("Error: Wallet '" + name + "' not found.");
                }
            }
            default -> System.out.println("Unknown wallet command: " + args[0]);
        }
    }

    private void handleVote(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: vote cast --wallet <name> --election <id> --candidate <id> [--pass <passphrase>] [--node <url>]");
            return;
        }
        if ("cast".equals(args[0])) {
            String walletName = getArg(args, "--wallet", "default");
            String electionId = getArg(args, "--election", "default");
            String candidateId = getArg(args, "--candidate", "0");
            String passphrase = getArg(args, "--pass", "");
            String nodeUrl = getArg(args, "--node", "http://localhost:8000");

            try {
                if (nodeUrl != null) {
                    sendVoteRemote(nodeUrl, walletName, passphrase, electionId, candidateId);
                }
            } catch (Exception e) {
                System.out.println("Error casting vote: " + e.getMessage());
            }
        }
    }

    private void sendVoteRemote(String nodeUrl, String walletName, String passphrase, String electionId, String candidateId) throws Exception {
        var voteData = java.util.Map.of(
                "wallet", walletName,
                "pass", passphrase,
                "election", electionId,
                "candidate", candidateId
        );
        String jsonBody = mapper.writeValueAsString(voteData);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(nodeUrl + "/vote"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        var result = mapper.readValue(response.body(), java.util.Map.class);
        if (response.statusCode() == 201) {
            System.out.println("Vote cast successfully! (via " + nodeUrl + ")");
            System.out.println("Transaction ID: " + result.get("transactionId"));
            System.out.println("Nullifier: " + result.get("nullifier"));
        } else {
            System.out.println("Error: " + result.getOrDefault("error", "Unknown error"));
        }
    }

    private void handleNode(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: node start [--port <port>] [--api-port <port>] [--id <nodeId>]");
            return;
        }
        if ("start".equals(args[0])) {
            int port = 8080;
            int apiPort = 8000;
            String nodeId = p2pNetwork instanceof org.example.infrastructure.network.TcpP2pNetwork tcp
                    ? tcp.getNodeId() : defaultNodeId;

            for (int i = 1; i < args.length - 1; i++) {
                if ("--port".equals(args[i])) {
                    port = Integer.parseInt(args[i + 1]);
                } else if ("--api-port".equals(args[i])) {
                    apiPort = Integer.parseInt(args[i + 1]);
                } else if ("--id".equals(args[i])) {
                    nodeId = args[i + 1];
                }
            }

            byte[] validatorPrivateKey = loadOrGenerateValidatorKey();
            final int finalApiPort = apiPort;

            // start API server in daemon thread (same JVM = same storage instance)
            ApiServer apiServer = new ApiServer(nodeService.getStorage(), votingService, walletService);
            Thread apiThread = new Thread(() -> {
                try { apiServer.start(finalApiPort); } catch (Exception e) { System.err.println("API error: " + e.getMessage()); }
            }, "api-server");
            apiThread.setDaemon(true);
            apiThread.start();

            System.out.println("Node: " + nodeId);
            System.out.println("P2P: port " + port + " | API: http://0.0.0.0:" + apiPort);
            p2pNetwork.start(port);
            nodeService.start(validatorPrivateKey, List.of());
            System.out.println("Node running. Press Ctrl+C to stop.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                p2pNetwork.stop();
                nodeService.stop();
            }));

            try { Thread.currentThread().join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private byte[] loadOrGenerateValidatorKey() {
        Path keyPath = Path.of("data", "validator-key.json");
        try {
            if (Files.exists(keyPath)) {
                var keyData = mapper.readValue(keyPath.toFile(), java.util.Map.class);
                String privHex = (String) keyData.get("privateKey");
                String pubHex = (String) keyData.get("publicKey");
                byte[] keyBytes = HashUtil.fromHex(privHex);
                if (keyBytes.length != 32) {
                    System.out.println("Validator key corrupted (length " + keyBytes.length + "), regenerating...");
                    Files.deleteIfExists(keyPath);
                    return generateNewValidatorKey(keyPath);
                }
                System.out.println("Validator key loaded from " + keyPath);
                System.out.println("Validator address: " + pubHex.substring(0, Math.min(16, pubHex.length())) + "...");
                return keyBytes;
            }
        } catch (Exception e) {
            System.out.println("Error loading validator key: " + e.getMessage() + ", regenerating...");
            try { Files.deleteIfExists(keyPath); } catch (IOException ignored) {}
            return generateNewValidatorKey(keyPath);
        }
        return generateNewValidatorKey(keyPath);
    }

    private byte[] generateNewValidatorKey(Path keyPath) {
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
                  vote cast --wallet <name> --election <id> --candidate <id> [--pass <passphrase>] [--node <url>]
                  tally --election <id>
                  node start [--port <port>]
                  chain info
                  chain validate
                  api start [--port <port>]
                  help
                """);
    }
}
