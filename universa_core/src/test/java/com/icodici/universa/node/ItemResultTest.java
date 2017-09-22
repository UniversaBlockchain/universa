/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import net.sergeych.boss.Boss;
import org.junit.Test;

import java.time.ZonedDateTime;

import static com.icodici.universa.node.ItemState.APPROVED;
import static com.icodici.universa.node.ItemState.REVOKED;
import static org.junit.Assert.assertEquals;

public class ItemResultTest extends TestCase {
    @Test
    public void toBinder() throws Exception {
        ItemResult r1 = new ItemResult(REVOKED, false, ZonedDateTime.now(), inFuture(300));
        ItemResult r2 = new ItemResult(r1.toBinder());
        assertEquals(r1.state, r2.state);
        assertEquals(r1.haveCopy, r2.haveCopy);
        assertEquals(r1.createdAt, r2.createdAt);
        assertEquals(r1.expiresAt, r2.expiresAt);
        assertEquals(r1, r2);
    }

    ZonedDateTime inFuture(int seconds) {
        return ZonedDateTime.now().plusSeconds(seconds);
    }

    @Test
    public void bossSerialization() throws Exception {
        ItemResult r1 = new ItemResult(APPROVED, false, ZonedDateTime.now(), inFuture(300));
        ItemResult r2 = new ItemResult(Boss.unpack(Boss.pack(r1.toBinder())));
        assertEquals(r1.state, r2.state);
        assertEquals(r1.haveCopy, r2.haveCopy);
        assertAlmostSame(r1.createdAt, r2.createdAt);
        assertAlmostSame(r1.expiresAt, r2.expiresAt);
        assertEquals(r1, r2);
    }

    @Test
    public void bossFormatter() throws Exception {
        ItemResult r1 = new ItemResult(APPROVED, false, ZonedDateTime.now(), inFuture(300));
        ItemResult r2 = Boss.load(Boss.pack(r1));
        assertEquals(r1, r2);
    }
}