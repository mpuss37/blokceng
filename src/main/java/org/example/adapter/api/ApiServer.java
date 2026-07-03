package org.example.adapter.api;

import org.example.application.VotingService;
import org.example.application.WalletService;
import org.example.domain.chain.BlockStorage;
import org.example.domain.crypto.CryptoProvider;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.example.domain.model.Wallet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

public class ApiServer {

    private final BlockStorage storage;
    private final VotingService votingService;
    private final WalletService walletService;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public ApiServer(BlockStorage storage, VotingService votingService, WalletService walletService) {
        this.storage = storage;
        this.votingService = votingService;
        this.walletService = walletService;
    }

    public void start(int port) throws Exception {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        VoteServlet voteServlet = new VoteServlet();
        context.addServlet(new ServletHolder(voteServlet), "/vote");
        context.addServlet(new ServletHolder(new ChainServlet()), "/chain");
        context.addServlet(new ServletHolder(new BlocksServlet()), "/blocks");
        context.addServlet(new ServletHolder(new PendingServlet()), "/pending");
        context.addServlet(new ServletHolder(new StatusServlet()), "/status");

        server.start();
        System.out.println("API server started on port " + port);
        System.out.println("Endpoints:");
        System.out.println("  POST /vote        — submit a vote");
        System.out.println("  GET  /chain       — full blockchain");
        System.out.println("  GET  /blocks      — all blocks");
        System.out.println("  GET  /pending     — pending transactions");
        System.out.println("  GET  /status      — node status");
        server.join();
    }

    class VoteServlet extends HttpServlet {
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            try {
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                var json = mapper.readValue(sb.toString(), java.util.Map.class);

                String walletName = (String) json.get("wallet");
                String passphrase = (String) json.getOrDefault("pass", "");
                String electionId = (String) json.get("election");
                String candidateId = String.valueOf(json.get("candidate"));

                if (walletName == null || electionId == null || candidateId == null) {
                    resp.setStatus(400);
                    mapper.writeValue(resp.getWriter(), java.util.Map.of("error", "Missing required fields: wallet, election, candidate"));
                    return;
                }

                Wallet wallet = walletService.loadWallet(walletName);
                byte[] privateKey = walletService.loadPrivateKey(wallet, passphrase);
                Transaction tx = votingService.castVote(wallet, privateKey, electionId, candidateId, List.of());

                resp.setStatus(201);
                mapper.writeValue(resp.getWriter(), java.util.Map.of(
                        "status", "accepted",
                        "transactionId", tx.transactionId(),
                        "nullifier", tx.nullifier(),
                        "electionId", tx.electionId(),
                        "candidateId", tx.candidateId()
                ));
            } catch (IllegalStateException e) {
                resp.setStatus(409);
                mapper.writeValue(resp.getWriter(), java.util.Map.of("error", e.getMessage()));
            } catch (Exception e) {
                resp.setStatus(500);
                mapper.writeValue(resp.getWriter(), java.util.Map.of("error", e.getMessage()));
            }
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            storage.reloadPending();
            mapper.writeValue(resp.getWriter(), java.util.Map.of(
                    "message", "Use POST to submit a vote",
                    "pending", storage.getPendingTransactions().size()
            ));
        }
    }

    class ChainServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            storage.reloadBlocks();
            storage.reloadPending();
            var blocks = storage.readAllBlocks();
            var result = new java.util.LinkedHashMap<String, Object>();
            result.put("size", blocks.size());
            result.put("blocks", blocks);
            mapper.writeValue(resp.getWriter(), result);
        }
    }

    class BlocksServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            storage.reloadBlocks();
            String indexParam = req.getParameter("index");
            if (indexParam != null) {
                int index = Integer.parseInt(indexParam);
                var block = storage.readBlock(index);
                if (block.isPresent()) {
                    mapper.writeValue(resp.getWriter(), block.get());
                } else {
                    resp.setStatus(404);
                    mapper.writeValue(resp.getWriter(), java.util.Map.of("error", "Block not found"));
                }
            } else {
                mapper.writeValue(resp.getWriter(), storage.readAllBlocks());
            }
        }
    }

    class PendingServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            storage.reloadPending();
            mapper.writeValue(resp.getWriter(), storage.getPendingTransactions());
        }
    }

    class StatusServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            storage.reloadBlocks();
            storage.reloadPending();
            var status = new java.util.LinkedHashMap<String, Object>();
            status.put("chainSize", storage.blockCount());
            status.put("pendingTx", storage.getPendingTransactions().size());
            status.put("status", "running");
            mapper.writeValue(resp.getWriter(), status);
        }
    }
}
