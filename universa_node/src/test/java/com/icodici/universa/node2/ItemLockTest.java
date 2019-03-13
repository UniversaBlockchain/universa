/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

@Ignore("in the sequence  causes SIGSEGV in java machine in sqlite module")
public class ItemLockTest {

    private int count = 0;

    /*
    Until the error in sqlite finalizers is fixed, call this test separately.
     */
    @Test
    @Ignore("in the sequence  causes SIGSEGV in java machine in sqlite module")
    public void lock() throws Exception {
        for( int z=0; z<10; z++ ) {
            HashId id = HashId.createRandom();

            count = 0;

            ItemLock il = new ItemLock();

            il.synchronize(id, (__) -> count++);
            il.synchronize(id, (__) -> count++);
            il.synchronize(id, (__) -> count++);

            assertEquals(3, count);
            assertEquals(1, il.size());
            id = null;
            for (int i = 0; i < 10; i++) {
                System.gc();
                System.runFinalization();
                if (il.size() == 0)
                    break;
                Thread.sleep(100);
            }
            assertEquals(0, il.size());

        }
    }

}