/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.node2.NodeInfo;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Adapter to the Universa Node UDP protocol v2.
 *
 * Protocol description: https://docs.google.com/document/d/1vlRQu5pcqWlOUT3yzBEKTq95jM8XlRutP-tR-aoUUiI/edit?usp=sharing
 *
 *
 */

public abstract class DatagramAdapter {

    /**
     * the queue where to put incoming data
     */
    BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>();

    /**
     * Maximum packet size in bytes. Adapter should try to send several blocks together as long as the overall encoded
     * packet sie is no more than MAX_PACKET_SIZE with all extra data attached.
     */
    static public final int MAX_PACKET_SIZE = 512;
    private NodeInfo myNodeInfo;
    private Consumer<byte[]> receiver = null;
    private final SymmetricKey sessionKey;

    /**
     * Create an instance that listens for the incoming datagrams using the specified configurations. The adapter should
     * start serving incoming datagrams immediately upon creation.
     *
     * @param myNodeInfo
     */
    public DatagramAdapter(SymmetricKey sessionKey,NodeInfo myNodeInfo) {
        this.myNodeInfo = myNodeInfo;
        this.sessionKey = sessionKey;
    }

    public abstract void send(NodeInfo destination, byte[] payload);

    public void receive(Consumer<byte[]> receiver) {
        byte[] payload;
        // first set the receiver so the queue won't be grow
        // the order does not matter anyway
        this.receiver = receiver;
        // now let's drain the buffer
        while((payload = inputQueue.poll()) != null ) {
            receiver.accept(payload);
        }
    }
}
