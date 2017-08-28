/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.db;

import org.junit.Test;
import org.sqlite.SQLiteConfig;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DbTest {

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