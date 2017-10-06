/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SplitJoinPermissionTest extends ContractTestBase {
    private PrivateKey ownerKey1;
    private PrivateKey ownerKey2;
    private PrivateKey ownerKey3;

    @Before
    public void setUp() throws Exception {
        ownerKey1 = TestKeys.privateKey(3);
        ownerKey2 = TestKeys.privateKey(1);
        ownerKey3 = TestKeys.privateKey(2);
    }

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

        // and it should be the same after seriazling:
        Contract restored = new Contract(c1.getLastSealedBinary());
        restored.check();
        restored.traceErrors();
        assertTrue(restored.isOk());

        // TODO: check that value can't be just changed
        // TODO: check that the sum must be equal
        // TODO: check children have different branches
        // TODO: check smae branch spoofing
    }

    private Contract createCoin() throws IOException {
        Contract c = Contract.fromYamlFile(rootPath + "coin.yml");
        c.setOwnerKey(ownerKey2);
        return c;
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
}