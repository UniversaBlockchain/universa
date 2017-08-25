/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.db;

import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import org.junit.Test;
import org.sqlite.SQLiteConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DbTest {

    @Test
    public void instance() throws Exception {
        Db t = new Db("jdbc:sqlite:", null);
        List<Db> all = Collections.synchronizedList(new ArrayList<>());
        DeferredResult dr1 = Do.inParallel(() -> all.add(t.instance()));
        DeferredResult dr2 = Do.inParallel(() -> all.add(t.instance()));
        dr1.join(); dr2.join();
        assertEquals(2, all.size());
        assertEquals(t, all.get(0));
        assertEquals(t, all.get(1));
        assert(t != all.get(0));
        assert(t != all.get(1));
        assert(all.get(1) != all.get(0));
    }

    @Test
    public void testClone() throws Exception {
        SQLiteConfig config = new SQLiteConfig();
        config.setSharedCache(true);
        config.enableRecursiveTriggers(true);
        String connectionString = "jdbc:sqlite:";
        Db a = new Db(connectionString, config.toProperties());
        Db b = a.clone();
        assertEquals(a, b);
        assertEquals(a.getProperties(), b.getProperties());
        assertEquals(connectionString, b.getConnectionString());
    }

    @Test
    public void createWithMigrations() throws Exception {
        Db t = new Db("jdbc:sqlite:", null, "/com/icodici/db/migrate_");
        assertEquals(1, (int) t.getIntParam("version"));
        assertNull(t.getIntParam("test"));
    }

    @Test
    public void params() throws Exception {
        Db t = new Db("jdbc:sqlite:", null, "/com/icodici/db/migrate_");
        assertEquals(-1, t.getIntParam("test", -1));
        t.setIntParam("test", 121);
        assertEquals(121, t.getIntParam("test", -1));
        t.setIntParam("test", 122);
        assertEquals(122, t.getIntParam("test", -1));
        assertNull(t.getStringParam("test2"));
        assertEquals("nope", t.getStringParam("test2", "nope"));
        t.setStringParam("test2", "fubar");
        assertEquals("fubar", t.getStringParam("test2", "nope"));
    }

}