/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import com.icodici.universa.node2.Notification;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * V2 Node network abstraction
 */
public abstract class Network {

    private NetConfig netConfig;

    public Network(NetConfig netConfig) {
        this.netConfig = netConfig;
    }

    public NodeInfo getInfo(int number) {
        return netConfig.getInfo(number);
    }


    /**
     * Put the notification to the delivery queue. Must not block the calling thread.
     *
     * @param notification
     */
    public abstract void deliver(NodeInfo toNode, Notification notification);

    /**
     * Subscribe ot incoming norifications. Old subscriber must be discarded. New consumer should receive notifications
     * received from the moment it is registered. The method must not block.
     *
     * @param forNode              node to which receive notifications
     * @param notificationConsumer the consumer that process incoming notifications in non-blocking manner, e.g.
     *                             it should return without waiting
     */
    public abstract void subscribe(NodeInfo forNode, Consumer<Notification> notificationConsumer);

    /**
     * Block until the item will be available from a specified node non excessing specified timeout.
     *
     * @param itemId item do load
     * @param node   node where the item should be loaded from
     *
     * @return the downloaded item, null if the node can't provide it or network error has occurred
     *
     * @throws InterruptedException
     * @throws TimeoutException     if the item can;t be obtained whithin a given timeout
     */
    public abstract Approvable getItem(HashId itemId, NodeInfo node, Duration maxTimeout)
            throws InterruptedException;

    public abstract Parcel getParcel(HashId itemId, NodeInfo node, Duration maxTimeout)
            throws InterruptedException;

    /**
     * Deliver notification to all nodes except one
     *
     * @param exceptNode   if not null, do not deliver to it.
     * @param notification notification fo deliver
     */
    public void broadcast(NodeInfo exceptNode, Notification notification) {
        netConfig.forEachNode(node -> {
            if (exceptNode != null && !exceptNode.equals(node))
                deliver(node, notification);
        });
    }

    /**
     * Enumerate all nodes passing them to the consumer
     *
     * @param consumer
     */
    public void eachNode(Consumer<NodeInfo> consumer) {
        netConfig.forEachNode(n -> consumer.accept(n));
    }

    public List<NodeInfo> allNodes() {
        return netConfig.toList();
    }

    public void shutdown() {}

    public ItemResult getItemState(NodeInfo nodeInfo, HashId id) throws IOException {
        return null;
    }
}
