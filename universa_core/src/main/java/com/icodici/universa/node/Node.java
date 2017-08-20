/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;

import java.io.IOException;

/**
 * Abstract node, remote or local.
 *
 * Nod has string id, {@link #getId()}. It is used to compare and generate hashcode, so Node instances could be
 * used as {@link java.util.Map} keys, and could be compared and searched.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public abstract class Node {

    private final String nodeId;

    protected Node(String id) {
        nodeId = id;
    }

    public String getId() {return nodeId; }

    /**
     * Request opition on the item
     *
     * @param caller   calling Node instance
     * @param itemId   item hash
     * @param state    my state (could be ignored if the caller identity is not trusted)
     * @param haveCopy true if the caller has a copy of the item and can provide it with {@link #getItem(HashId)} call.
     * @return current operation state
     */
    public abstract ItemResult checkItem(Node caller, HashId itemId, ItemState state, boolean haveCopy) throws IOException, InterruptedException;

    /**
     * Try to obtain the copy of the {@link Approvable} item.
     *
     * @param itemId item to get
     * @return instance or null if the node can not provide it
     * @throws IOException on any error except that missing item
     */
    public abstract Approvable getItem(HashId itemId) throws IOException, InterruptedException;

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }

    public abstract void shutdown();

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof Node ) {
            return ((Node) obj).nodeId.equals(nodeId);
        }
        return false;
    }
}
