/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import java.time.ZonedDateTime;
import java.util.concurrent.Callable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TestCase {
    public void assertAlmostSame(ZonedDateTime t1, ZonedDateTime t2) {
        if( t1 == null && t2 == null )
            return ;
        long delta = Math.abs(t1.toEpochSecond() - t2.toEpochSecond());
        assertThat(delta, is(lessThan(2L)));
    }

    protected void assertSameRecords(StateRecord r, StateRecord r1) {
        assertEquals(r.getId(), r1.getId());
        assertEquals(r.getState(), r1.getState());
        assertAlmostSame(r.getCreatedAt(), r1.getCreatedAt());
        assertEquals(r.getRecordId(), r1.getRecordId());
        assertEquals(r.getLockedByRecordId(), r1.getLockedByRecordId());
    }

    protected void assertThrows(Class<? extends Exception> exClass, Callable<?> block) {
        try {
            block.call();
            fail("Exception of class " + exClass.getName() + " was expected, but nothing was thrown");
        } catch (Throwable t) {
            if (exClass.isInstance(t))
                return;
            t.printStackTrace();
            fail("Expected exception of class " + exClass.getName() + "instead " + t.getClass().getName() + " was thrown");
        }
    }

    protected void assertThrows(Callable<?> callable) {
        assertThrows(Exception.class, callable);
    }
}
