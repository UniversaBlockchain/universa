/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import com.icodici.universa.node2.Notification;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class NetworkV2 extends Network {

    private final NodeInfo myInfo;
    private final PrivateKey myKey;
    private final UDPAdapter adapter;

//    private Map<NodeInfo, Node> nodes = new HashMap<>();

    private static LogPrinter log = new LogPrinter("TLN");
    private Consumer<Notification> consumer;

    public NetworkV2(NetConfig netConfig, NodeInfo myInfo, PrivateKey myKey) throws IOException {
        super(netConfig);
        this.myInfo = myInfo;
        this.myKey = myKey;

        adapter = new UDPAdapter(myKey, new SymmetricKey(), myInfo);
//        adapter.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        adapter.receive(this::onReceived);
        adapter.addErrorsCallback(this::exceptionCallback);
    }

    private final void onReceived(byte[] packedNotifications) {
        try {
            if (consumer != null) {
                List<Notification> nn = unpack(packedNotifications);
                for (Notification n : nn) {
                    if( n == null )
                        System.out.println("bad notification skipped");
                    else {
                        consumer.accept(n);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("ignoring notification, " + e);
            e.printStackTrace();
        }
    }

    private List<Notification> unpack(byte[] packedNotifications) throws IOException {
        List<Notification> nn = new ArrayList<>();

        try {
            // packet type code
            Boss.Reader r = new Boss.Reader(packedNotifications);
            if (r.readInt() != 1)
                throw new IOException("invalid packed notification type code");

            // from node number
            int number = r.readInt();
            NodeInfo from = getInfo(number);
            if (from == null)
                throw new IOException("unknown node number: " + number);

            // number of notifications in the packet
            int count = r.readInt();
            if (count < 0 || count > 1000)
                throw new IOException("unvalid packed notifications count: " + count);

            for (int i = 0; i < count; i++) {
                nn.add(Notification.read(from, r));
            }
            return nn;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("failed to unpack notification: " + e);
            throw new IOException("failed to unpack notifications", e);
        }
    }

    private synchronized final byte[] packNotifications(NodeInfo from, Collection<Notification> notifications) {
        Boss.Writer w = new Boss.Writer();
        try {
            w.write(1)                                      // packet type code
                    .write(from.getNumber())                // from number
                    .write(notifications.size());           // count notifications
            notifications.forEach(n -> {
                try {
                    Notification.write(w, n);
                } catch (IOException e) {
                    throw new RuntimeException("notificaiton pack failure", e);
                }
            });
            return w.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("notificaiton pack failure", e);
        }
    }

    @Override
    public void deliver(NodeInfo toNode, Notification notification) {
        try {
            byte[] data = packNotifications(myInfo, Do.listOf(notification));
            adapter.send(toNode, data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void subscribe(NodeInfo _info, Consumer<Notification> notificationConsumer) {
        consumer = notificationConsumer;
    }

//    private final Map<NodeInfo,BasicHttpClient> httpClients = new HashMap<>();

    @Override
    public Approvable getItem(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        try {
//            URL url = new URL("http://localhost:8080/contracts/" + itemId.toBase64String());
            URL url = new URL(nodeInfo.publicUrlString() + "/contracts/" + itemId.toBase64String());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
            connection.setRequestMethod("GET");
            if (200 != connection.getResponseCode())
                return null;
            byte[] data = Do.read(connection.getInputStream());
            TransactionPack tp = TransactionPack.unpack(data, true);
//            tp.trace();
//            Contract c = Contract.fromPackedTransaction(data);
            return tp.getContract();
        } catch (Exception e) {
            System.out.println("download failure: "+e);
            e.printStackTrace();
            return null;
        }
    }

    private final Map<NodeInfo,Client> cachedClients = new HashMap<>();

    @Override
    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
        Client client;
        synchronized (cachedClients) {
            client = cachedClients.get(nodeInfo);
            if( client == null ) {
                client = new Client(myKey, nodeInfo, null);
                cachedClients.put(nodeInfo, client);
            }
        }
        return client.getState(id);
    }

    private String exceptionCallback(String message) {
        System.err.println("UDP adapter error: " + message);
        return message;
    }

    public void shutdown() {
        adapter.shutdown();
    }
}
