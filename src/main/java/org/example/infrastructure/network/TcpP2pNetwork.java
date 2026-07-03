package org.example.infrastructure.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.example.domain.network.P2pNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpP2pNetwork implements P2pNetwork {

    private static final Logger log = LoggerFactory.getLogger(TcpP2pNetwork.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final String nodeId;
    private final List<String> bootstrapPeers;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final List<NetworkMessageListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private int port;

    public TcpP2pNetwork(String nodeId, List<String> bootstrapPeers) {
        this.nodeId = nodeId;
        this.bootstrapPeers = bootstrapPeers != null ? bootstrapPeers : List.of();
    }

    public String getNodeId() {
        return nodeId;
    }

    @Override
    public void start(int port) {
        this.port = port;
        running.set(true);

        // virtual threads for concurrent connections
        executor = Executors.newVirtualThreadPerTaskExecutor();
        scheduler = Executors.newScheduledThreadPool(2);

        try {
            serverSocket = new ServerSocket(port);
            log.info("Node [{}] listening on port {}", nodeId, port);

            // accept incoming connections
            executor.submit(this::acceptLoop);

            // connect to bootstrap peers
            connectToBootstrapPeers();

            // heartbeat scheduler
            scheduler.scheduleAtFixedRate(this::sendHeartbeats, 5, 30, TimeUnit.SECONDS);

            // peer cleanup
            scheduler.scheduleAtFixedRate(this::cleanupDeadPeers, 10, 60, TimeUnit.SECONDS);

        } catch (IOException e) {
            log.error("Failed to start P2P on port {}: {}", port, e.getMessage());
        }
    }

    @Override
    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing server socket: {}", e.getMessage());
        }
        peers.values().forEach(PeerConnection::close);
        peers.clear();
        if (executor != null) executor.shutdown();
        if (scheduler != null) scheduler.shutdown();
        log.info("Node [{}] stopped", nodeId);
    }

    @Override
    public void broadcastTransaction(Transaction transaction) {
        String json = toJson(Map.of("type", "TX", "payload", transaction));
        broadcast(json);
    }

    @Override
    public void broadcastBlock(Block block) {
        String json = toJson(Map.of("type", "BLOCK", "payload", block));
        broadcast(json);
    }

    @Override
    public void sendToPeer(String peerId, String message) {
        PeerConnection peer = peers.get(peerId);
        if (peer != null && peer.isAlive()) {
            peer.send(message);
        }
    }

    @Override
    public List<String> getConnectedPeerIds() {
        return List.copyOf(peers.keySet());
    }

    @Override
    public void addMessageListener(NetworkMessageListener listener) {
        listeners.add(listener);
    }

    // --- internal ---

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                String remoteId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                log.debug("Incoming connection from {}", remoteId);

                PeerConnection conn = new PeerConnection(remoteId, socket);
                peers.put(remoteId, conn);
                executor.submit(() -> handlePeer(conn));

                for (NetworkMessageListener listener : listeners) {
                    listener.onPeerConnected(remoteId);
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.debug("Accept error: {}", e.getMessage());
                }
            }
        }
    }

    private void handlePeer(PeerConnection conn) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                handleMessage(conn.peerId, line.trim());
            }
        } catch (IOException e) {
            log.debug("Peer {} disconnected: {}", conn.peerId, e.getMessage());
        } finally {
            conn.close();
            peers.remove(conn.peerId);
            for (NetworkMessageListener listener : listeners) {
                listener.onPeerDisconnected(conn.peerId);
            }
        }
    }

    private void handleMessage(String fromPeer, String message) {
        if (message.isEmpty()) return;
        try {
            Map<String, Object> msg = MAPPER.readValue(message, Map.class);
            String type = (String) msg.get("type");
            if (type == null) return;

            switch (type) {
                case "HANDSHAKE" -> {
                    log.debug("Handshake from {}", fromPeer);
                }
                case "HEARTBEAT" -> {
                    log.debug("Heartbeat from {}", fromPeer);
                }
                case "TX" -> {
                    Transaction tx = MAPPER.convertValue(msg.get("payload"), Transaction.class);
                    log.info("Received transaction {} from {}", tx.transactionId(), fromPeer);
                    for (NetworkMessageListener listener : listeners) {
                        listener.onTransactionReceived(tx);
                    }
                }
                case "BLOCK" -> {
                    Block block = MAPPER.convertValue(msg.get("payload"), Block.class);
                    log.info("Received block #{} from {}", block.index(), fromPeer);
                    for (NetworkMessageListener listener : listeners) {
                        listener.onBlockReceived(block);
                    }
                }
                default -> log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.debug("Error parsing message from {}: {}", fromPeer, e.getMessage());
        }
    }

    private void connectToBootstrapPeers() {
        for (String peer : bootstrapPeers) {
            String[] parts = peer.split(":");
            if (parts.length != 2) {
                log.warn("Invalid bootstrap peer format: {} (expected host:port)", peer);
                continue;
            }
            String host = parts[0];
            int peerPort;
            try {
                peerPort = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("Invalid port in bootstrap peer: {}", peer);
                continue;
            }
            connectToPeer(host, peerPort);
        }
    }

    private void connectToPeer(String host, int port) {
        executor.submit(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(30000);
                String peerId = host + ":" + port;
                PeerConnection conn = new PeerConnection(peerId, socket);
                peers.put(peerId, conn);
                log.info("Connected to peer {}:{}", host, port);

                // send handshake
                conn.send(toJson(Map.of("type", "HANDSHAKE", "nodeId", this.nodeId)));

                for (NetworkMessageListener listener : listeners) {
                    listener.onPeerConnected(peerId);
                }
                executor.submit(() -> handlePeer(conn));
            } catch (IOException e) {
                log.debug("Cannot connect to {}:{}: {}", host, port, e.getMessage());
            }
        });
    }

    private void broadcast(String message) {
        for (PeerConnection peer : peers.values()) {
            if (peer.isAlive()) {
                peer.send(message);
            }
        }
    }

    private void sendHeartbeats() {
        String hb = toJson(Map.of("type", "HEARTBEAT", "nodeId", nodeId, "timestamp", System.currentTimeMillis()));
        broadcast(hb);
    }

    private void cleanupDeadPeers() {
        peers.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                log.debug("Removing dead peer: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // --- inner class ---

    private static class PeerConnection {
        final String peerId;
        final Socket socket;
        final PrintWriter writer;

        PeerConnection(String peerId, Socket socket) throws IOException {
            this.peerId = peerId;
            this.socket = socket;
            this.writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        }

        void send(String message) {
            writer.println(message);
        }

        boolean isAlive() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        void close() {
            try {
                if (writer != null) writer.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
