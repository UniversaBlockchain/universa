package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.StateRecord;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ItemResyncNotification extends ItemNotification {

    private static final int CODE_ITEM_RESYNC_NOTIFICATION = 1;

    private HashMap<HashId, ItemState> itemsToResync;

    public ItemResyncNotification(NodeInfo from, HashId itemId, HashMap<HashId, ItemState> itemsToResync, boolean requestResult) {
        // itemResult not use.
        super(from, itemId, new ItemResult(new StateRecord(itemId)), requestResult);
        this.itemsToResync = itemsToResync;
    }

    protected ItemResyncNotification() {
        super();
        itemsToResync = new HashMap<>();
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        super.writeTo(bw);
        Map<String, Object> packingMap = new HashMap<>();
        for (HashId hid : itemsToResync.keySet()) {
            packingMap.put(hid.toBase64String(), itemsToResync.get(hid).ordinal());
        }
        bw.writeObject(packingMap);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        super.readFrom(br);
        Map<String, Object> packingMap = br.readMap();
        for (String s : packingMap.keySet()) {
            HashId hid = HashId.withDigest(s);
            ItemState state = ItemState.values()[(int)packingMap.get(s)];
            itemsToResync.put(hid, state);
        }
    }

    @Override
    protected int getTypeCode() {
        return CODE_ITEM_RESYNC_NOTIFICATION;
    }

    @Override
    public String toString() {
        return "[ItemResyncNotification from: " + getFrom()
                + " for item: " + getItemId()
                + ", items to resync: " + itemsToResync
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    public HashMap<HashId, ItemState> getItemsToResync() {
        return itemsToResync;
    }

    static {
        registerClass(CODE_ITEM_RESYNC_NOTIFICATION, ItemResyncNotification.class);
    }
}
