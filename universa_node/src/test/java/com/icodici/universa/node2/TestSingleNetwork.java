/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.services.NImmutableEnvironment;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.network.Network;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TestSingleNetwork extends Network {

    private Map<NodeInfo, Node> nodes = new HashMap<>();

    public TestSingleNetwork(NetConfig nc) {
        super(nc);
    }

    @Override
    public void deliver(NodeInfo toNode, Notification notification) {

    }

    @Override
    public void subscribe(NodeInfo info,Consumer<Notification> notificationConsumer) {

    }

    @Override
    public Approvable getItem(HashId itemId, NodeInfo node, Duration maxTimeout) throws InterruptedException {
        return null;
    }

    @Override
    public NImmutableEnvironment getEnvironment(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        return null;
    }

    @Override
    public Parcel getParcel(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        return null;
    }

    @Override
    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
        return nodes.get(nodeInfo).checkItem(id);
    }

    public void addNode(NodeInfo ni, Node node) {
        nodes.put(ni, node);
    }
}
