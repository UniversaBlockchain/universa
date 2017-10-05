/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.PermissionsTest;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SplitJoinPermissionTest extends PermissionsTest {

    @Test
    public void checkChanges() throws Exception {
    }

    @Test
    public void testProperSum() throws Exception {

        Contract c = createCoin();

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.check());

        // bad split: no changes
        Contract c1 = c.createRevision(ownerKey2);

        c1.seal();
        c1.check();
//        c1.traceErrors();
        assertFalse(c1.isOk());

        // Good split
        Contract c2 = c1.splitValue("amount", new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow("amount"));
        assertEquals(500, c2.getStateData().getIntOrThrow("amount"));

        c1.seal();
        c1.check();
        c1.traceErrors();
        assertTrue(c1.isOk());

        // TODO: check that value can't be just changed
        // TODO: check that the sum must be equal
        // TODO: check children have different branches
        // TODO: check smae branch spoofing
    }

    @Test
    public void shouldSplitWithChangedOwner() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");

        Contract c2 = c.split(1)[0];
        c2.setOwnerKey(ownerKey1);

        sealCheckTrace(c2, true);
        assertNotEquals(c.getOwner(), c2.getOwner());
    }


    @Test
    public void shouldSplitWithChangedOwnerAndNewValue() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        Binder d = c.getStateData();

        Contract c2 = c.splitValue("amount", new Decimal(85));
        c2.setOwnerKey(ownerKey1);
        Binder d2 = c2.getStateData();

        sealCheckTrace(c2, true);
        assertNotEquals(c.getOwner(), c2.getOwner());

        int a = 1000000;
        assertEquals(1000000, d.getIntOrThrow("amount"));
        assertEquals(a - 85, d2.getIntOrThrow("amount"));
    }

    @Test
    public void cheatCreateValue() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        c.seal();
        assertTrue(c.check());

        Contract c1 = c.createRevision(ownerKey2);


        // Good split but wrong amount
        Contract c2 = c1.splitValue("amount", new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow("amount"));
        assertEquals(500, c2.getStateData().getIntOrThrow("amount"));
        c2.getStateData().set("amount", "500.00000001");
        c1.seal();
        c1.check();
        c1.traceErrors();
        assertFalse(c1.isOk());

    }

    @Test
    public void cheatCreateValue2() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        c.seal();
        assertTrue(c.check());

        Contract c1 = c.createRevision(ownerKey2);
        c1.getStateData().set("amount", "500.00000001");
        c1.seal();
        c1.check();
        c1.traceErrors();
        assertFalse(c1.isOk());

    }

    private Contract createCoin() throws IOException {
        Contract c = Contract.fromYamlFile(rootPath + "coin.yml");
        c.setOwnerKey(ownerKey2);
        return c;
    }
}