/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.services.NImmutableEnvironment;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import com.icodici.universa.node2.network.UDPAdapter;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class TestLocalNetwork extends Network {

    private NodeInfo myInfo;
    private PrivateKey myKey;
    private UDPAdapter adapter;
    //    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(8);
    private Map<NodeInfo, Node> nodes = new HashMap<>();

    private static LogPrinter log = new LogPrinter("TLN");
    private Consumer<Notification> consumer;
    private Boolean shutdown = false;
    public static final Object mutex = new Object();

    public TestLocalNetwork(NetConfig netConfig, NodeInfo myInfo, PrivateKey myKey) throws IOException {
        super(netConfig);
        this.myInfo = myInfo;
        this.myKey = myKey;

        adapter = new UDPAdapter(myKey, new SymmetricKey(), myInfo, netConfig);
//        adapter.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        adapter.receive(this::onReceived);
        adapter.addErrorsCallback(this::exceptionCallback);
    }

    public void setUDPAdapterTestMode(int testMode) {
        adapter.setTestMode(testMode);
    }

    public void setUDPAdapterVerboseLevel(int level) {
        adapter.setVerboseLevel(level);
    }

    public void setUDPAdapterLostPacketsPercentInTestMode(int percent) {
        adapter.setLostPacketsPercentInTestMode(percent);
    }

    private final void onReceived(byte[] packedNotifications) {
        try {
            synchronized (this) {
                List<Notification> nn = unpack(packedNotifications);
                for (Notification n : nn) {
                    if (consumer != null) {
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
            throw new IOException("failed to unpack notifications");
        }
    }

    private final byte[] packNotifications(NodeInfo from, Collection<Notification> notifications) {
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

    private Map<NodeInfo, DatagramAdapter> adapters = new HashMap<>();

    public void addNode(NodeInfo ni, Node node) {
        nodes.put(ni, node);
    }

    @Override
    public void deliver(NodeInfo toNode, Notification notification) {
        if(!shutdown) {
            try {
                byte[] data = packNotifications(myInfo, Do.listOf(notification));
                try {
                    unpack(data);
                } catch (Exception e) {
                    System.err.println("-- pack test failed -- " + e);
                    e.printStackTrace();
                    System.exit(75);
                }
                adapter.send(toNode, data);
            } catch (InterruptedException e) {
                System.err.println("expected interrupt exception " + e.toString());
            } catch (Exception e) {
                System.out.println("--------------> " + shutdown + " " + myInfo + " " + adapter + " " + consumer);
                e.printStackTrace();
            }
        }
    }

    @Override
    public void subscribe(NodeInfo _info, Consumer<Notification> notificationConsumer) {
        consumer = notificationConsumer;
    }

    public void removeAllSubscribes() {
        consumer = null;
    }

    @Override
    synchronized public Approvable getItem(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);

        Approvable item = node.getItem(itemId);

        if(item instanceof Contract) {
            TransactionPack tp_before = ((Contract) item).getTransactionPack();
            byte[] data = tp_before.pack();

            // here we "send" data and "got" it

            TransactionPack tp_after = null;
            try {
                tp_after = TransactionPack.unpack(data);
                Contract gotMainContract = tp_after.getContract();

                return gotMainContract;

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return item;
    }

    @Override
    public NImmutableEnvironment getEnvironment(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);
        NImmutableEnvironment env = node.getEnvCache().get(itemId);
        if(env != null)
            return Boss.load(Boss.pack(env));
        return null;
    }

    @Override
    synchronized public Parcel getParcel(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        synchronized (mutex) {
            Node node = nodes.get(nodeInfo);

            Parcel parcel = node.getParcel(itemId);
            byte[] array = parcel.pack();

            //unpack
            Parcel des_parcel = null;
            try {
                des_parcel = Parcel.unpack(array);
            } catch (Exception e) {
                System.out.println("error parcel ");
                e.printStackTrace();
            }

            return des_parcel;
//        return parcel;
        }
    }

    private String exceptionCallback(String message) {
        System.out.println(message);
        return message;
    }

    public void setNodes(Map<NodeInfo, Node> nodes) {
        this.nodes = nodes;
    }

    public void shutDown() {
        adapter.shutdown();
        myInfo = null;
        myKey = null;
        adapter = null;
        nodes = null;
        consumer = null;
        shutdown = true;
    }

    @Override
    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
        return nodes.get(nodeInfo).checkItem(id);
    }

    @Override
    public int pingNodeUDP(int number, int timeoutMillis) {
        return 0;
    }

    @Override
    public int pingNodeTCP(int nodeNumber, int timeoutMillis) {
        return 0;
    }

    // redo it to work right in the local network
//    @Override
//    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
//        Client client;
//        synchronized (cachedClients) {
//            client = cachedClients.get(nodeInfo);
//            if( client == null ) {
//                client = new Client(myKey, nodeInfo);
//                cachedClients.put(nodeInfo, client);
//            }
//        }
//        return client.getState(id);
//    }
//    private final Map<NodeInfo,Client> cachedClients = new HashMap<>();


}
