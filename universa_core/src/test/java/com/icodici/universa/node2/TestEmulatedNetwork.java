/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.network.Network;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;

public class TestEmulatedNetwork extends Network {

    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(8);
    private Map<NodeInfo, Node> nodes = new HashMap<>();
    private HashMap<NodeInfo, Consumer<Notification>> consumers = new HashMap<>();

    private HashMap<Node, Boolean> test_nodeWorkStates = new HashMap<>();

    public int getTest_nodeBeingOffedChance() {
        return test_nodeBeingOffedChance;
    }

    public void setTest_nodeBeingOffedChance(int test_nodeBeingOffedChance) {
        this.test_nodeBeingOffedChance = test_nodeBeingOffedChance;
    }

    private int test_nodeBeingOffedChance = 0;

    private static LogPrinter log = new LogPrinter("TEMN");

    public TestEmulatedNetwork(NetConfig netConfig) {
        super(netConfig);
    }

    public void addNode(NodeInfo ni, Node node) {
        nodes.put(ni, node);
        test_nodeWorkStates.put(node, true);
    }

    @Override
    public void deliver(NodeInfo toNode, Notification notification) {
        if(new Random().nextInt(100) > test_nodeBeingOffedChance) {
            if (test_nodeWorkStates.get(getNode(toNode))) {
                executorService.submit(() -> {
                    Consumer<Notification> consumer = consumers.get(toNode);
                    assert consumer != null;
                    consumer.accept(notification);
                });
            }
        }
    }

    @Override
    public void subscribe(NodeInfo info, Consumer<Notification> notificationConsumer) {
        consumers.put(info, notificationConsumer);
    }

    @Override
    public Approvable getItem(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);

        Approvable item = node.getItem(itemId);

//        if(item instanceof Contract) {
//            TransactionPack tp_before = ((Contract) item).getTransactionPack();
//            byte[] data = tp_before.pack();
//
//            // here we "send" data and "got" it
//
//            TransactionPack tp_after = null;
//            try {
//                tp_after = TransactionPack.unpack(data);
//                Contract gotMainContract = tp_after.getContract();
//
//                return gotMainContract;
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        return item;
    }

    @Override
    public Parcel getParcel(HashId itemId, NodeInfo nodeInfo, Duration maxTimeout) throws InterruptedException {
        Node node = nodes.get(nodeInfo);
        return node.getParcel(itemId);
    }

    @Override
    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
        return nodes.get(nodeInfo).checkItem(id);
    }

    public Node getNode(NodeInfo info) {
        return nodes.get(info);
    }

    public void switchOnAllNodesTestMode() {

        for (Node n : test_nodeWorkStates.keySet()) {
            test_nodeWorkStates.put(n, true);
        }
    }

    public void switchOffNodeTestMode(Node node) {

        if(test_nodeWorkStates.containsKey(node))
            test_nodeWorkStates.put(node, false);
    }
}
