package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.StateRecord;
import net.sergeych.boss.Boss;

import java.io.IOException;

public class ResyncNotification extends ItemNotification {

    private static final int CODE_RESYNC_NOTIFICATION = 3;

    private ItemState itemState = null;

    public ResyncNotification(NodeInfo from, HashId itemId, boolean answerIsRequested)  throws IOException {
        super(from, itemId, new ItemResult(new StateRecord(itemId)), answerIsRequested);
    }

    public ResyncNotification(NodeInfo from, HashId itemId, ItemState itemState, boolean answerIsRequested)  throws IOException {
        super(from, itemId, new ItemResult(new StateRecord(itemId)), answerIsRequested);
        this.itemState = itemState;
    }

    public ResyncNotification() {
        super();
    }

    public ItemState getItemState() {
        return itemState;
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        super.writeTo(bw);
        if (!answerIsRequested())
            bw.write(itemState.ordinal());
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        super.readFrom(br);
        if (!answerIsRequested())
            itemState = ItemState.values()[br.readInt()];
    }

    @Override
    protected int getTypeCode() {
        return CODE_RESYNC_NOTIFICATION;
    }

    @Override
    public String toString() {
        return "[ResyncNotification from: " + getFrom()
                + " for item: " + getItemId()
                + ", is answer requested: " + answerIsRequested()
                + "]";
    }

    static public void init() {
        registerClass(CODE_RESYNC_NOTIFICATION, ResyncNotification.class);
    }

}
