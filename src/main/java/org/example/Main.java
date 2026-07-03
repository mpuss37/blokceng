package org.example;

import org.example.adapter.api.ApiServer;
import org.example.adapter.cli.CliRouter;
import org.example.application.NodeService;
import org.example.application.VotingService;
import org.example.application.WalletService;
import org.example.domain.chain.BlockStorage;
import org.example.domain.consensus.ConsensusEngine;
import org.example.domain.crypto.*;
import org.example.infrastructure.chain.JsonBlockStorage;
import org.example.infrastructure.config.AppConfig;
import org.example.infrastructure.consensus.ProofOfAuthority;
import org.example.infrastructure.crypto.*;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        // load config
        AppConfig config = AppConfig.load();

        // --- dependency injection ---
        CryptoProvider crypto = new Ed25519CryptoProvider();
        MerkleTreeProvider merkleTree = new MerkleTree();
        VrfProvider vrf = new Ed25519VrfProvider(crypto);
        LinkableRingSignatureProvider lrs = new LinkableRingSignatureImpl(crypto);
        BlockStorage storage = new JsonBlockStorage();
        ConsensusEngine consensus = new ProofOfAuthority(crypto, vrf, merkleTree);

        // --- services ---
        WalletService walletService = new WalletService(crypto);
        VotingService votingService = new VotingService(crypto, lrs, storage);
        NodeService nodeService = new NodeService(crypto, consensus, storage);

        // --- CLI routing ---
        CliRouter cli = new CliRouter(walletService, votingService, nodeService);

        if (args.length == 0) {
            cli.printHelp();
            return;
        }

        // check if it's the API server command
        if ("api".equals(args[0]) && args.length > 1 && "start".equals(args[1])) {
            int port = config.apiPort();
            for (int i = 2; i < args.length - 1; i++) {
                if ("--port".equals(args[i])) {
                    port = Integer.parseInt(args[i + 1]);
                }
            }
            try {
                new ApiServer(storage).start(port);
            } catch (Exception e) {
                System.err.println("API server error: " + e.getMessage());
            }
            return;
        }

        // route all other commands
        cli.route(args);
    }
}
