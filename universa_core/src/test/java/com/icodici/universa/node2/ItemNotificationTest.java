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
import org.junit.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class ItemNotificationTest {
    @Test
    public void packUnpack() throws Exception {
        NodeInfo ni = new NodeInfo(TestKeys.publicKey(0),1, "test1", "localhost", 17101, 17102);
        HashId id1 = HashId.createRandom();
        ZonedDateTime now = ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        ZonedDateTime expiresAt = now.plusDays(30);
        ItemResult ir1 = new ItemResult(ItemState.PENDING, false, now, expiresAt);
        ItemResult ir2 = new ItemResult(ItemState.REVOKED, true, now, expiresAt);

        ItemNotification n1 = new ItemNotification(ni, id1, ir1, true);
        ItemNotification n2 = new ItemNotification(ni, id1, ir1, false);
        ItemNotification n3 = new ItemNotification(ni, id1, ir1, true);


        byte[] packed = Notification.pack(asList(n1, n2, n3));
        List<Notification> l = Notification.unpack(ni, packed);
        assertEquals(3, l.size());
        ItemNotification n = (ItemNotification) l.get(0);
        assertEquals(n, n1);
        n = (ItemNotification) l.get(1);
        assertEquals(n, n2);
        n = (ItemNotification) l.get(2);
        assertEquals(n, n3);
    }

}