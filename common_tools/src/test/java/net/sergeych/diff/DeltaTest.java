/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

import net.sergeych.diff.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DeltaTest {
    @Test
    public void compareSimpleTypes() throws Exception {
        Delta d = Delta.between(null, 10, 11);
        assertFalse(d.isEmpty());
        assertEquals(10, d.oldValue());
        assertEquals(11, d.newValue());
        assertNull(Delta.between(101, 101));
        d = Delta.between(null, "foo", "bar");
        assertFalse(d.isEmpty());
        assertEquals("foo", d.oldValue());
        assertEquals("bar", d.newValue());
        assertNull(Delta.between("hello", "hello"));
    }

    @Test
    public void compareArrays() throws Exception {
        Object a[] = new Object[] { 3, 2, 10, 1};
        Object b[] = new Object[] { 3, 2, 10, 1};
        Delta d = Delta.between(a, b);
        assertNull(d);
        b[0] = 0;
        b[3] = 3;
        ArrayDelta d1 = (ArrayDelta) Delta.between(a, b);
        assertNotNull(d1);
        Map<Integer,Delta> changes = d1.getChanges();
        assertEquals(2, changes.size());
        assertEquals(3, changes.get(0).oldValue());
        assertEquals(0, changes.get(0).newValue());
        assertEquals(1, changes.get(3).oldValue());
        assertEquals(3, changes.get(3).newValue());

        // removed array item
        Object c[] = new Object[] { 3, 2, 10};
        d1 = Delta.between(a, c);
        assertFalse(d1.isEmpty());
        RemovedItem rd = (RemovedItem) d1.getChange(3);
        assertTrue(rd instanceof RemovedItem);
        assertEquals(1, rd.oldValue());

        c = new Object[] { 3, 2, 10, 1, 777, 999};
        d1 = Delta.between(a, c);
        assertNotNull(d1);
        assertFalse(d1.isEmpty());
        assertEquals(2, d1.getChanges().size());
        CreatedItem ci = (CreatedItem) d1.getChange(4);
        assertEquals(777, ci.newValue());
        assertEquals(999, d1.getChange(5).newValue());
    }

    @Test
    public void compareLists() throws Exception {
        ArrayList<Integer> a = Do.listOf(3, 2, 10, 1);
        ArrayList<Integer> b = Do.listOf(3, 2, 10, 1);
        Delta d = Delta.between(a, b);
        assertNull(d);
        b.set(0, 0);
        b.set(3, 3);
        ListDelta d1 = Delta.between(a, b);
        assertNotNull(d1);
        Map<Integer,Delta> changes = d1.getChanges();
        assertEquals(2, changes.size());
        assertEquals(3, changes.get(0).oldValue());
        assertEquals(0, changes.get(0).newValue());
        assertEquals(1, changes.get(3).oldValue());
        assertEquals(3, changes.get(3).newValue());

        // removed array item
        ArrayList<Integer> c = Do.listOf(3, 2, 10);
        d1 = Delta.between(a, c);
        assertFalse(d1.isEmpty());
        RemovedItem rd = (RemovedItem) d1.getChange(3);
        assertTrue(rd instanceof RemovedItem);
        assertEquals(1, rd.oldValue());

        c = Do.listOf(3, 2, 10, 1, 778, 997);
        d1 = Delta.between(a, c);
        assertNotNull(d1);
        assertFalse(d1.isEmpty());
        assertEquals(2, d1.getChanges().size());
        CreatedItem ci = (CreatedItem) d1.getChange(4);
        assertEquals(778, ci.newValue());
        assertEquals(997, d1.getChange(5).newValue());
    }

    @Test
    public void compareMaps() throws Exception {
        Binder a = Binder.fromKeysValues("hello", "world", "foo", "bar");
        Binder b = Binder.fromKeysValues("hello", "world", "foo", "bar");
        assertNull(Delta.between(a, b));
        b.put("foo", 117);
        MapDelta md = Delta.between(a, b);
        assertNotNull(md);
        assertFalse(md.isEmpty());
        assertEquals(1, md.getChanges().size());
        ChangedItem ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());

        b.remove("hello");
        md = Delta.between(a, b);
        assertNotNull(md);
        assertFalse(md.isEmpty());
        assertEquals(2, md.getChanges().size());
        ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());
        RemovedItem ri = (RemovedItem) md.getChange("hello");
        assertEquals("world", ri.oldValue());

        b.put("italy", "bel paese");
        md = Delta.between(a, b);
        assertNotNull(md);
        assertFalse(md.isEmpty());
        assertEquals(3, md.getChanges().size());
        ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());
        ri = (RemovedItem) md.getChange("hello");
        assertEquals("world", ri.oldValue());
        CreatedItem ni = (CreatedItem) md.getChange("italy");
        assertEquals("bel paese", ni.newValue());
    }

    @Test
    public void nestedMaps() throws Exception {
        Binder a = Binder.fromKeysValues("hello", "world", "foo", "bar");
        Binder b = Binder.fromKeysValues("hello", "world", "foo", "bar");
        Binder r1 = Binder.fromKeysValues("nice", "day", "root", a);
        Binder r2 = Binder.fromKeysValues("nice", "day", "root", b);

        assertNull(Delta.between(r1, r2));
        b.put("foo", 117);
        MapDelta rd = Delta.between(r1, r2);
        assertNotNull(rd);
        assertFalse(rd.isEmpty());
        assertEquals(1, rd.getChanges().size());
        MapDelta md = (MapDelta) rd.getChange("root");
        assertNotNull(md);
        assertEquals(1,md.getChanges().size());
        ChangedItem ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());

        assertEquals(1,rd.getNestedDelta().size());
    }
    @Test

    public void nestedLists() throws Exception {
        Binder a = Binder.fromKeysValues("hello", "world", "foo", "bar");
        Binder b = Binder.fromKeysValues("hello", "world", "foo", "bar");
        ArrayList<Object> r1 = Do.listOf(a, a, b, a );
        ArrayList<Object> r2 = Do.listOf(a, b, b, b);

        assertNull(Delta.between(r1, r2));
        b.put("foo", 117);
        ListDelta ld = Delta.between(r1, r2);
        assertNotNull(ld);
        assertFalse(ld.isEmpty());
        assertEquals(2, ld.getChanges().size());
        MapDelta md = (MapDelta) ld.getChange(1);
        assertNotNull(md);
        assertEquals(1,md.getChanges().size());
        ChangedItem ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());
        assertEquals(2, ld.getChanges().size());
        md = (MapDelta) ld.getChange(3);
        assertNotNull(md);
        assertEquals(1,md.getChanges().size());
        ci = (ChangedItem) md.getChange("foo");
        assertEquals("bar", ci.oldValue());
        assertEquals(117, ci.newValue());

        List nestedDelta = ld.getNestedDelta();
        assertEquals(2, nestedDelta.size());
    }

}