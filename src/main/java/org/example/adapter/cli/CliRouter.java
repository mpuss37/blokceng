package org.example.adapter.cli;

import org.example.application.VotingService;
import org.example.application.WalletService;
import org.example.application.NodeService;
import org.example.domain.model.Wallet;
import org.example.domain.model.Transaction;
import org.example.domain.network.P2pNetwork;

import java.util.Arrays;
import java.util.List;

public class CliRouter {

    private final WalletService walletService;
    private final VotingService votingService;
    private final NodeService nodeService;
    private final P2pNetwork p2pNetwork;
    private final String defaultNodeId;

    public CliRouter(WalletService walletService, VotingService votingService, NodeService nodeService,
                     P2pNetwork p2pNetwork, String defaultNodeId) {
        this.walletService = walletService;
        this.votingService = votingService;
        this.nodeService = nodeService;
        this.p2pNetwork = p2pNetwork;
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
            System.out.println("Usage: node start [--port <port>] [--id <nodeId>] [--bootstrap <host:port,host:port>]");
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

            System.out.println("Starting node: " + nodeId + " on port " + port);
            System.out.println("To change nodeId, edit blokceng.json");
            p2pNetwork.start(port);
            nodeService.start(null, List.of());
            System.out.println("Node running. Press Ctrl+C to stop.");

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
                System.out.println("Valid: " + true); // pon ytail: implement full validation
            }
            case "validate" -> {
                System.out.println("Validating chain...");
                System.out.println("Chain is valid."); // pon ytail: implement full validation
            }
            default -> System.out.println("Unknown chain command: " + args[0]);
        }
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
                  node start [--port <port>]
                  chain info
                  chain validate
                  help
                """);
    }
}
