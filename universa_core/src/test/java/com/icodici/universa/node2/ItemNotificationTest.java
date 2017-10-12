/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.boss.Boss;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

public class ItemNotificationTest {
    @Test
    public void packTo() throws Exception {
        ItemNotification n;
        NodeInfo ni = new NodeInfo(TestKeys.publicKey(0),1, "test1", "localhost", 17101, 17102);
        HashId id1 = HashId.createRandom();
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime expiresAt = now.plusDays(30);
        ItemResult ir = new ItemResult(ItemState.PENDING, false, now, expiresAt);
        n = new ItemNotification(ni, id1, ir, true);

        Boss.Writer w = new Boss.Writer();
        n.writeTo(w);
        ItemNotification n1 = new ItemNotification(ni, new Boss.Reader(w.toByteArray()));
        assertEquals(n, n1);
    }

}