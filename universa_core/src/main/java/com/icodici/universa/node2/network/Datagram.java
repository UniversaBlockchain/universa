/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.universa.node2.NodeInfo;

public class Datagram {
    public NodeInfo getDestination() {
        return destination;
    }

    public byte[] getPayload() {
        return payload;
    }

    private final NodeInfo destination;
    private final byte[] payload;

    public Datagram(NodeInfo destination, byte[] payload) {
        this.destination = destination;
        this.payload = payload;
    }
}
