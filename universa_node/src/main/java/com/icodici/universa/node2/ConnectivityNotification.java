/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The status notification for consensus creation procedure, carries information about some node item status and update
 * request
 */
public class ConnectivityNotification extends Notification {


    /**
     * If true, sending node asks receiving node to sent its status of this item back to sender. This overrides default
     * logic of sending only one broadcast about item status.
     *
     * @return
     */

    private static final int CODE_CONNECTIVITY_NOTIFICATION = 7;
    private Set<Integer> unreachableNodes;


    public ConnectivityNotification(NodeInfo from, Set<NodeInfo> unreachableNodes) {
        super(from);
        this.unreachableNodes = unreachableNodes.stream().map(ni->ni.getNumber()).collect(Collectors.toSet());
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(unreachableNodes);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        unreachableNodes = new HashSet((Collection<NodeInfo>)br.read());
    }

    protected ConnectivityNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected ConnectivityNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_CONNECTIVITY_NOTIFICATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConnectivityNotification that = (ConnectivityNotification) o;

        NodeInfo from = getFrom();
        if(!that.getFrom().equals(from))
            return false;

        if(!that.unreachableNodes.equals(unreachableNodes))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(unreachableNodes);
    }

    public String toString() {
        return "[ConnectivityNotification from " + getFrom()
                + " with unreachable nodes: " + unreachableNodes
                + "]";
    }

    public Set<Integer> getUnreachableNodes() {
        return unreachableNodes;
    }

    static public void init() {
        registerClass(CODE_CONNECTIVITY_NOTIFICATION, ConnectivityNotification.class);
    }
}
