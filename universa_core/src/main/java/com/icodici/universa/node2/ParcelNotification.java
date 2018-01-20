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

public class ParcelNotification extends ItemNotification {


    private static final int CODE_PARCEL_NOTIFICATION = 2;

    public ParcelNotification(NodeInfo from, HashId itemId, ItemResult itemResult, boolean requestResult) {
        super(from, itemId, itemResult, requestResult);
    }

    protected ParcelNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected ParcelNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_PARCEL_NOTIFICATION;
    }

    @Override
    public String toString() {
        return "[ParcelNotification from: " + getFrom()
                + " for item: " + getItemId()
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    static {
        registerClass(CODE_PARCEL_NOTIFICATION, ParcelNotification.class);
    }
}
