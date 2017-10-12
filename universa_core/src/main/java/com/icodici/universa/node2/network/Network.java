/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.Notification;

import java.util.function.Consumer;

public abstract class Network {

    private NetConfig netConfig;

    public Network(NetConfig netConfig) {

        this.netConfig = netConfig;
    }

    public abstract void deliver(Notification notification);

    public abstract void subscribe(Consumer<Notification> notificationConsumer);

}
