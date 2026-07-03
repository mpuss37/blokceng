package org.example.infrastructure.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.example.domain.model.Block;
import org.example.domain.model.Transaction;
import org.example.domain.network.P2pNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpP2pNetwork implements P2pNetwork {

    private static final Logger log = LoggerFactory.getLogger(TcpP2pNetwork.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String PEERS_FILE = "data/peers.json";
    private static final Set<String> VALID_TYPES = Set.of("HANDSHAKE", "HEARTBEAT", "TX", "BLOCK", "ADDR");
    private static final int MAX_SEEN_MESSAGES = 10000;

    private final String nodeId;
    private final List<String> bootstrapPeers;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();
    private final Set<String> seenMessages = ConcurrentHashMap.newKeySet();
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
        executor = Executors.newVirtualThreadPerTaskExecutor();
        scheduler = Executors.newScheduledThreadPool(3);

        loadPeers();

        try {
            serverSocket = new ServerSocket(port);
            log.info("Node [{}] listening on port {}", nodeId, port);

            executor.submit(this::acceptLoop);

            connectToSavedPeers();
            connectToBootstrapPeers();

            scheduler.scheduleAtFixedRate(this::sendHeartbeats, 5, 30, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::cleanupDeadPeers, 10, 60, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::savePeers, 60, 120, TimeUnit.SECONDS);

        } catch (IOException e) {
            log.error("Failed to start P2P on port {}: {}", port, e.getMessage());
        }
    }

    @Override
    public void stop() {
        running.set(false);
        savePeers();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
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
        String msgId = UUID.randomUUID().toString();
        seenMessages.add(msgId);
        String json = toJson(Map.of("type", "TX", "messageId", msgId, "payload", transaction));
        broadcast(json);
    }

    @Override
    public void broadcastBlock(Block block) {
        String msgId = UUID.randomUUID().toString();
        seenMessages.add(msgId);
        String json = toJson(Map.of("type", "BLOCK", "messageId", msgId, "payload", block));
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

    // --- message handling ---

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                String remoteId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                PeerConnection conn = new PeerConnection(remoteId, socket);
                peers.put(remoteId, conn);
                executor.submit(() -> handlePeer(conn));
            } catch (IOException e) {
                if (running.get()) log.debug("Accept error: {}", e.getMessage());
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
            log.debug("Peer {} disconnected", conn.peerId);
        } finally {
            conn.close();
            peers.remove(conn.peerId);
            for (NetworkMessageListener l : listeners) l.onPeerDisconnected(conn.peerId);
        }
    }

    private void handleMessage(String fromPeer, String message) {
        if (message.isEmpty()) return;
        try {
            Map<String, Object> msg = MAPPER.readValue(message, Map.class);
            String type = (String) msg.get("type");
            String msgId = (String) msg.get("messageId");

            if (type == null || !VALID_TYPES.contains(type)) {
                log.debug("Invalid message type: {}", type);
                return;
            }

            // dedup check
            if (msgId != null && !seenMessages.add(msgId)) {
                log.debug("Duplicate message {} from {}, skipping", msgId.substring(0, 8), fromPeer);
                return;
            }

            switch (type) {
                case "HANDSHAKE" -> {
                    String remoteNodeId = (String) msg.get("nodeId");
                    log.info("Handshake from {} (nodeId={})", fromPeer, remoteNodeId);
                    // respond with handshake
                    PeerConnection conn = peers.get(fromPeer);
                    if (conn != null) {
                        conn.send(toJson(Map.of("type", "HANDSHAKE", "nodeId", this.nodeId)));
                    }
                    // send our known peers
                    broadcastAddr(fromPeer);
                    for (NetworkMessageListener l : listeners) l.onPeerConnected(fromPeer);
                }
                case "HEARTBEAT" -> {
                    log.debug("Heartbeat from {}", fromPeer);
                }
                case "ADDR" -> {
                    List<String> addresses = MAPPER.convertValue(msg.get("addresses"), new TypeReference<>() {});
                    log.debug("Received {} peer addresses from {}", addresses.size(), fromPeer);
                    for (String addr : addresses) {
                        if (!peers.containsKey(addr) && !addr.equals(this.nodeId + ":" + this.port)) {
                            String[] parts = addr.split(":");
                            if (parts.length == 2) {
                                try {
                                    connectToPeer(parts[0], Integer.parseInt(parts[1]));
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
                case "TX" -> {
                    Transaction tx = MAPPER.convertValue(msg.get("payload"), Transaction.class);
                    log.info("Received TX {} from {}", tx.transactionId().substring(0, 16), fromPeer);
                    for (NetworkMessageListener l : listeners) l.onTransactionReceived(tx);
                    // gossip: forward to all peers except sender
                    gossip(message, fromPeer);
                }
                case "BLOCK" -> {
                    Block block = MAPPER.convertValue(msg.get("payload"), Block.class);
                    log.info("Received block #{} from {}", block.index(), fromPeer);
                    for (NetworkMessageListener l : listeners) l.onBlockReceived(block);
                    gossip(message, fromPeer);
                }
            }

            // trim seen messages if too large
            if (seenMessages.size() > MAX_SEEN_MESSAGES) {
                List<String> trimmed = new ArrayList<>(seenMessages);
                seenMessages.clear();
                seenMessages.addAll(trimmed.subList(trimmed.size() - MAX_SEEN_MESSAGES / 2, trimmed.size()));
            }

        } catch (Exception e) {
            log.debug("Error parsing message from {}: {}", fromPeer, e.getMessage());
        }
    }

    private void gossip(String message, String excludePeer) {
        for (PeerConnection peer : peers.values()) {
            if (!peer.peerId.equals(excludePeer) && peer.isAlive()) {
                peer.send(message);
            }
        }
    }

    private void broadcastAddr(String toPeer) {
        List<String> addresses = new ArrayList<>();
        for (String peerId : peers.keySet()) {
            addresses.add(peerId);
        }
        addresses.add(this.nodeId + ":" + this.port);
        String msgId = UUID.randomUUID().toString();
        String json = toJson(Map.of("type", "ADDR", "messageId", msgId, "addresses", addresses));
        sendToPeer(toPeer, json);
    }

    // --- peer management ---

    private void connectToBootstrapPeers() {
        for (String peer : bootstrapPeers) {
            String[] parts = peer.split(":");
            if (parts.length != 2) continue;
            try {
                connectToPeer(parts[0], Integer.parseInt(parts[1]));
            } catch (NumberFormatException ignored) {}
        }
    }

    private void connectToSavedPeers() {
        for (String peerId : new ArrayList<>(loadPeerList())) {
            if (peerId.equals(this.nodeId + ":" + this.port)) continue;
            String[] parts = peerId.split(":");
            if (parts.length == 2) {
                try {
                    connectToPeer(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void connectToPeer(String host, int peerPort) {
        String peerId = host + ":" + peerPort;
        if (peers.containsKey(peerId)) return;

        executor.submit(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, peerPort), 5000);
                socket.setSoTimeout(30000);
                PeerConnection conn = new PeerConnection(peerId, socket);
                peers.put(peerId, conn);
                log.info("Connected to peer {}", peerId);

                conn.send(toJson(Map.of("type", "HANDSHAKE", "nodeId", this.nodeId)));
                executor.submit(() -> handlePeer(conn));
            } catch (IOException e) {
                log.debug("Cannot connect to {}: {}", peerId, e.getMessage());
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
        peers.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }

    // --- peer persistence ---

    private void savePeers() {
        try {
            Files.createDirectories(Path.of("data"));
            List<String> peerList = new ArrayList<>(peers.keySet());
            peerList.add(this.nodeId + ":" + this.port);
            MAPPER.writeValue(Path.of(PEERS_FILE).toFile(), peerList);
            log.debug("Saved {} peers", peerList.size());
        } catch (IOException e) {
            log.debug("Error saving peers: {}", e.getMessage());
        }
    }

    private void loadPeers() {
        List<String> saved = loadPeerList();
        log.debug("Loaded {} saved peers", saved.size());
    }

    private List<String> loadPeerList() {
        Path path = Path.of(PEERS_FILE);
        if (!Files.exists(path)) return List.of();
        try {
            return MAPPER.readValue(path.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            return List.of();
        }
    }

    // --- utility ---

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
            if (isAlive()) writer.println(message);
        }

        boolean isAlive() {
            return socket != null && socket.isConnected() && !socket.isClosed();
        }

        void close() {
            try {
                if (writer != null) writer.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }
}
