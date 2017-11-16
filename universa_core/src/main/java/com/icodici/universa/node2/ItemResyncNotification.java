package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;

import java.io.IOException;

public class ItemResyncNotification extends ItemNotification {

    private static final int CODE_ITEM_RESYNC_NOTIFICATION = 1;

    public ItemResyncNotification(NodeInfo from, HashId itemId, ItemResult itemResult, boolean requestResult) {
        super(from, itemId, itemResult, requestResult);
    }

    protected ItemResyncNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected ItemResyncNotification() {
        super();
    }

    @Override
    protected int getTypeCode() {
        return CODE_ITEM_RESYNC_NOTIFICATION;
    }

    static {
        registerClass(CODE_ITEM_RESYNC_NOTIFICATION, ItemResyncNotification.class);
    }
}
