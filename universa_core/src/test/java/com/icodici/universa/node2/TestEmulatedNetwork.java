/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node2.network.Network;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

public class TestEmulatedNetwork extends Network{

    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(8);
    private Map<NodeInfo,Node> nodes = new HashMap<>();
    private HashMap<NodeInfo,Consumer<Notification>> consumers = new HashMap<>();

    public TestEmulatedNetwork(NetConfig netConfig) {
        super(netConfig);
    }

    public void addNode(NodeInfo ni,Node node) {
        nodes.put(ni, node);
    }

    @Override
    public void deliver(NodeInfo toNode, Notification notification) {
        executorService.submit( ()-> {
            Consumer<Notification> consumer = consumers.get(toNode);
            assert consumer != null;
            consumer.accept(notification);
        });
    }

    @Override
    public void subscribe(NodeInfo info,Consumer<Notification> notificationConsumer) {
        consumers.put(info,notificationConsumer);
    }

    @Override
    public Approvable getItem(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);
        return node.getItem(itemId);
    }
}
