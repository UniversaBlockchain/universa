/*
 * Copyright (c) 2018, iCodici S.n.C, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import net.sergeych.boss.Boss;

import java.io.IOException;

public class ParcelNotification extends ItemNotification {


    private static final int CODE_PARCEL_NOTIFICATION = 2;


    private HashId parcelId;
    public HashId getParcelId() {
        return parcelId;
    }

    private ParcelNotificationType type = ParcelNotificationType.PAYLOAD;
    public ParcelNotificationType getType() {
        return type;
    }

    private ParcelNotificationClass notificationClass = ParcelNotificationClass.PARCEL;
    public ParcelNotificationClass getNotificationClass() {
        return notificationClass;
    }

    public ParcelNotification(NodeInfo from, HashId itemId, HashId parcelId, ItemResult itemResult,
                              boolean requestResult, ParcelNotificationType type, ParcelNotificationClass notificationClass) {
        super(from, itemId, itemResult, requestResult);
        this.parcelId = parcelId;
        this.type = type;
        this.notificationClass = notificationClass;
    }

    protected ParcelNotification() {
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        super.writeTo(bw);
        bw.writeObject(type.ordinal());
        bw.writeObject(notificationClass.ordinal());
        if(parcelId != null)
            bw.writeObject(parcelId.getDigest());
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        super.readFrom(br);
        type = ParcelNotificationType.values()[br.readInt()];
        notificationClass = ParcelNotificationClass.values()[br.readInt()];
        try {
            byte[] parcelBytes = br.readBinary();
            if (parcelBytes != null)
                parcelId = HashId.withDigest(parcelBytes);
        } catch (IOException e) {
            parcelId = null;
        }
    }

    @Override
    protected int getTypeCode() {
        return CODE_PARCEL_NOTIFICATION;
    }

    @Override
    public String toString() {
        return "[ParcelNotification from: " + getFrom()
                + " for parcel: " + parcelId
                + " and item: " + getItemId()
                + ", type is: " + type
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    public static void init() {
        registerClass(CODE_PARCEL_NOTIFICATION, ParcelNotification.class);
    }

    public enum ParcelNotificationType {
        PAYMENT,
        PAYLOAD;

        public boolean isU() {
            return this == PAYMENT;
        }

        /**
         * @deprecated use {@link #isU()} instead.
         */
        @Deprecated
        public boolean isTU() {
            return this == PAYMENT;
        }
    }

    public enum ParcelNotificationClass {
        PARCEL,
        PAID_OPERATION,
        UBOT_REGISTRATION;
    }
}
