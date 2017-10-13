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
import java.util.function.Consumer;

public class TestSingleNetwork extends Network {

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
}
