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

/**
 * The status notification for consensus creation procedure, carries information about some node item status and update
 * request
 */
public class ItemNotification extends Notification {


    /**
     * If true, sending node asks receiving node to sent its status of this item back to sender. This overrides default
     * logic of sending only one broadcast about item status.
     *
     * @return
     */

    private static final int CODE_ITEM_NOTIFICATION = 0;

    private HashId itemId;
    public HashId getItemId() {
        return itemId;
    }

    private ItemResult itemResult;
    public ItemResult getItemResult() {
        return itemResult;
    }

    private boolean requestResult;
    public boolean answerIsRequested() {
        return requestResult;
    }

    public ItemNotification(NodeInfo from, HashId itemId, ItemResult itemResult, boolean requestResult) {
        super(from);
        this.itemId = itemId;
        this.itemResult = itemResult;
        this.requestResult = requestResult;
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(itemId.getDigest());
        itemResult.writeTo(bw);
        bw.writeObject(requestResult);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        itemId = HashId.withDigest(br.readBinary());
        itemResult = new ItemResult(br);
        requestResult = br.read();
    }

    protected ItemNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected ItemNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_ITEM_NOTIFICATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ItemNotification that = (ItemNotification) o;

        NodeInfo from = getFrom();
        if (requestResult != that.requestResult) return false;
        if (!from.equals(that.getFrom())) return false;
        if (!itemId.equals(that.itemId)) return false;
        return itemResult.equals(that.itemResult);
    }

    @Override
    public int hashCode() {
        NodeInfo from = getFrom();
        int result = from.hashCode();
        result = 31 * result + itemId.hashCode();
        result = 31 * result + itemResult.hashCode();
        result = 31 * result + (requestResult ? 1 : 0);
        return result;
    }

    public String toString() {
        return "[ItemNotification from " + getFrom()
                + " for item: " + getItemId()
                + ", item result: " + itemResult
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    static public void init() {
        registerClass(CODE_ITEM_NOTIFICATION, ItemNotification.class);
    }
}
