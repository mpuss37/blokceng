package org.example.domain.network;

import org.example.domain.model.Block;
import org.example.domain.model.Transaction;

import java.util.List;

public interface P2pNetwork {

    void start(int port);

    void stop();

    void broadcastTransaction(Transaction transaction);

    void broadcastBlock(Block block);

    void sendToPeer(String peerId, String message);

    List<String> getConnectedPeerIds();

    void addMessageListener(NetworkMessageListener listener);

    interface NetworkMessageListener {
        void onTransactionReceived(Transaction transaction);
        void onBlockReceived(Block block);
        void onPeerConnected(String peerId);
        void onPeerDisconnected(String peerId);
    }
}
