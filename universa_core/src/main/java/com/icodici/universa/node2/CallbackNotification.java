/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.Arrays;

/**
 * The success notification for follower callback, carries callback identifier and signature of updated item id
 * request
 */
public class CallbackNotification extends Notification {


    /**
     * Sending node notifies receiving node that follower callback is success.
     * And send signature of updated item id.
     */

    private static final int CODE_CALLBACK_NOTIFICATION = 4;

    private HashId id;
    public HashId getId() {
        return id;
    }

    private byte[] signature;
    public byte[] getSignature() {
        return signature;
    }

    public CallbackNotification(NodeInfo from, HashId id, byte[] signature) {
        super(from);
        this.id = id;
        this.signature = signature;
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(id.getDigest());
        bw.writeObject(signature);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        id = HashId.withDigest(br.readBinary());
        signature = br.readBinary();
    }

    protected CallbackNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected CallbackNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_CALLBACK_NOTIFICATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CallbackNotification that = (CallbackNotification) o;

        NodeInfo from = getFrom();
        if (!from.equals(that.getFrom())) return false;
        if (!id.equals(that.id)) return false;
        return Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        NodeInfo from = getFrom();
        int result = from.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }

    public String toString() {
        return "[CallbackNotification from " + getFrom() + " with id: " + getId() + "]";
    }

    static public void init() {
        registerClass(CODE_CALLBACK_NOTIFICATION, CallbackNotification.class);
    }
}
