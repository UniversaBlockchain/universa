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

    private final NodeInfo myInfo;
    private final PrivateKey myKey;
    private final UDPAdapter adapter;
//    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(8);
    private Map<NodeInfo, Node> nodes = new HashMap<>();

    private static LogPrinter log = new LogPrinter("TLN");
    private Consumer<Notification> consumer;

    public TestLocalNetwork(NetConfig netConfig, NodeInfo myInfo, PrivateKey myKey) throws IOException {
        super(netConfig);
        this.myInfo = myInfo;
        this.myKey = myKey;

        adapter = new UDPAdapter(myKey, new SymmetricKey(), myInfo);
//        adapter.setVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
        adapter.receive(bytes -> onReceived(bytes));
    }

    private final void onReceived(byte[] packedNotifications) {
        try {
            if( consumer != null ) {
                List<Notification> nn = unpack(packedNotifications);
                for (Notification n : nn) {
                    consumer.accept(n);
                }
            }

        } catch (IOException e) {
            System.err.println("ignoring notification, " + e);
            e.printStackTrace();
        }
    }

    private List<Notification> unpack(byte[] packedNotifications) throws IOException {
        List<Notification> nn = new ArrayList<>();
        Boss.Reader r = new Boss.Reader(packedNotifications);
        if (r.readInt() != 1)
            throw new IOException("invalid packed notification type code");
        int number = r.readInt();
        NodeInfo from = getInfo(number);
        if (from == null)
            throw new IOException("unknown node number: " + number);
        int count = r.readInt();
        if (count < 0 || count > 1000)
            throw new IOException("unvalid packed notifications count: " + count);
        for (int i = 0; i < count; i++) {
            try {
                nn.add(Notification.unpackNotification(from, r));
            } catch (Exception e) {
                throw new IOException("notifiaction unpack failure", e);
            }
        }
        return nn;
    }

    private final byte[] packNotifications(NodeInfo from, Collection<Notification> notifications) {
        Boss.Writer w = new Boss.Writer();
        try {
            w.write(1)
                    .write(from.getNumber())
                    .write(notifications.size());
            notifications.forEach(n -> {
                try {
                    n.writeTo(w);
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
        try {
            log.d("will send "+notification+" to "+toNode);
            adapter.send(toNode, packNotifications(myInfo,Do.listOf(notification)));
            log.d("sent: "+notification+" to "+toNode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void subscribe(NodeInfo info, Consumer<Notification> notificationConsumer) {
        if( info.equals(myInfo))
            consumer = notificationConsumer;
        else
            throw new IllegalArgumentException("this type of Network only allow one consumer and one local node");
    }

    @Override
    public Approvable getItem(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);
        return node.getItem(itemId);
    }
}
