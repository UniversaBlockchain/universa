/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PublicKey;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.net.InetSocketAddress;

/**
 * The complete data about Universa node. This class should provide enough information to connect to a remote node
 * and create local services and should be used everywhere instead of host-port parameters and.
 *
 * The preferred method of idenrifying the node is its integer id, see {@link #getId()}.
 */
public class NodeInfo {
    private final PublicKey publicKey;
    private final InetSocketAddress nodeAddress;
    private final InetSocketAddress clientAddress;
    private final InetSocketAddress serverAddress;
    private final int id;
    private final String nodeName;

    public NodeInfo(@NonNull PublicKey publicKey, int id, @NonNull String nodeName, @NonNull String host,
                    int datagramPort, int clientHttpPort,int serverHttpPort) {
        assert id >= 0;
        assert datagramPort > 0;
        assert clientHttpPort > 0;
        this.publicKey = publicKey;
        this.id = id;
        this.nodeName = nodeName;
        nodeAddress = new InetSocketAddress(host, datagramPort);
        clientAddress = new InetSocketAddress(host, clientHttpPort);
        serverAddress = new InetSocketAddress(host, serverHttpPort);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    /**
     * Integer node it is now the preferred way to identify nodes
     * @return
     */
    public int getId() {
        return id;
    }

    /**
     * String node name is now a secondary identificator
     * @return
     */
    public String getName() {
        return nodeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeInfo nodeInfo = (NodeInfo) o;

        return id == nodeInfo.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "NI("+id+")";
    }
}
