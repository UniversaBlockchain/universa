/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ItemLockTest {

    private int count = 0;

    @Test
    public void lock() throws Exception {
        HashId id = HashId.createRandom();

        ItemLock.synchronize(id, (__) -> count++);
        ItemLock.synchronize(id, (__) -> count++);
        ItemLock.synchronize(id, (__) -> count++);

        assertEquals(3, count);
        assertEquals(1, ItemLock.size());
        id = null;
        for (int i = 0; i < 10; i++) {
            System.gc();
            System.runFinalization();
            if( ItemLock.size() == 0 )
            break;
            Thread.sleep(100);
        }
        assertEquals(0, ItemLock.size());


    }

}