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
 * UDP-optimized notification for v2 consensus
 */
public class ItemNotification {

    transient public final NodeInfo from;
    public final HashId itemId;
    public final ItemResult itemResult;
    public final boolean requestResult;

    public ItemNotification(NodeInfo from, HashId itemId, ItemResult itemResult, boolean requestResult) {
        this.from = from;
        this.itemId = itemId;
        this.itemResult = itemResult;
        this.requestResult = requestResult;
    }

    public void packTo(Boss.Writer bw) throws IOException {
        bw.writeObject(itemId.getDigest());
        itemResult.writeTo(bw);
        bw.writeObject(requestResult);
    }

    public ItemNotification(NodeInfo from,Boss.Reader br) throws IOException {
        this.from = from;
        itemId = HashId.withDigest(br.readBinary());
        itemResult = new ItemResult(br);
        requestResult = br.read();
    }
}
