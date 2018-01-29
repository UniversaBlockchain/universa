/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ParcelNotification extends ItemNotification {


    private static final int CODE_PARCEL_NOTIFICATION = 2;


    private ParcelNotificationType type = ParcelNotificationType.PAYLOAD;
    public ParcelNotificationType getType() {
        return type;
    }

    public ParcelNotification(NodeInfo from, HashId itemId, ItemResult itemResult, boolean requestResult, ParcelNotificationType type) {
        super(from, itemId, itemResult, requestResult);
        this.type = type;
    }

    protected ParcelNotification() {
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        super.writeTo(bw);
        bw.writeObject(type.ordinal());
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        super.readFrom(br);
        type = ParcelNotificationType.values()[br.readInt()];
    }

    @Override
    protected int getTypeCode() {
        return CODE_PARCEL_NOTIFICATION;
    }

    @Override
    public String toString() {
        return "[ParcelNotification from: " + getFrom()
                + " for parcel: " + getItemId()
                + ", type is: " + type
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    static {
        registerClass(CODE_PARCEL_NOTIFICATION, ParcelNotification.class);
    }

    public enum ParcelNotificationType {
        PAYMENT,
        PAYLOAD;

        public boolean isTU() {
            return this == PAYMENT;
        }
    }
}
