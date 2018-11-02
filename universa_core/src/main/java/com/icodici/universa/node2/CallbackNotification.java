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
 * request.
 * For success notification: sending node notifies receiving node that follower callback is success.
 * And send signature of updated item id.
 *
 * Also may contain a notification that callback is not responding to the node request.
 * In this case send a notification without signature. If some nodes (rate defined in config) also sended callback
 * and received packed item (without answer) callback is deemed complete.
 */
public class CallbackNotification extends Notification {

    private static final int CODE_CALLBACK_NOTIFICATION = 4;

    /**
     * Callback notification type
     *
     * COMPLETED - to notify other Universa nodes about the completion of the callback
     * NOT_RESPONDING - to notify other Universa nodes that follower callback server received a callback but did not respond
     * GET_STATE - to query the state of callback
     * RETURN_STATE - to return the state of callback
     */
    public enum CallbackNotificationType {
        COMPLETED,
        NOT_RESPONDING,
        GET_STATE,
        RETURN_STATE
    }

    private HashId id;
    public HashId getId() {
        return id;
    }

    private CallbackNotificationType type;
    public CallbackNotificationType getType() {
        return type;
    }

    private byte[] signature;
    public byte[] getSignature() {
        return signature;
    }

    private Node.FollowerCallbackState state;
    public Node.FollowerCallbackState getState() { return state; }

    /**
     * Create callback notification.
     * For type COMPLETED callback notification should be contain signature.
     * For type RETURN_STATE callback notification should be contain state.
     *
     * @param from is {@link NodeInfo} of node that sent the callback notification
     * @param id is callback identifier
     * @param type of callback notification
     * @param signature is receipt signed by follower callback server (required if type == COMPLETED)
     * @param state is callback state (required if type == RETURN_STATE)
     */
    public CallbackNotification(NodeInfo from, HashId id, CallbackNotificationType type, byte[] signature, Node.FollowerCallbackState state) {
        super(from);
        this.id = id;
        this.signature = signature;
        this.type = type;
        this.state = state;
    }

    public CallbackNotification(NodeInfo from, HashId id, CallbackNotificationType type, byte[] signature) {
        this(from, id, type, signature, Node.FollowerCallbackState.UNDEFINED);
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(id.getDigest());
        bw.writeObject(signature);
        bw.writeObject(type.ordinal());
        bw.writeObject(state.ordinal());
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        id = HashId.withDigest(br.readBinary());
        signature = br.readBinary();
        type = CallbackNotificationType.values()[br.readInt()];
        state = Node.FollowerCallbackState.values()[br.readInt()];
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
        if (!type.equals(that.type)) return false;
        if (!state.equals(that.state)) return false;
        return Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        NodeInfo from = getFrom();
        int result = from.hashCode();
        result = 31 * result + id.hashCode();
        result = 31 * result + Arrays.hashCode(signature);
        result = 31 * result + type.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }

    public String toString() {
        return "[CallbackNotification from " + getFrom() + " with id: " + getId() + "]";
    }

    static public void init() {
        registerClass(CODE_CALLBACK_NOTIFICATION, CallbackNotification.class);
    }
}
