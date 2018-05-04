/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.TestItem;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class ItemCacheTest {
    @Test
    public void cleanUp() throws Exception {
        ItemCache c = new ItemCache(Duration.ofMillis(10));
        TestItem i1 = new TestItem(true);
        c.put(i1, ItemResult.UNDEFINED);
        assertEquals(i1, c.get(i1.getId()));
        Thread.sleep(11);
        c.cleanUp();
        assertEquals(null, c.get(i1.getId()));
    }
}