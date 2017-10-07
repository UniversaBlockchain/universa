/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class SplitJoinPermissionTest extends ContractTestBase {

    @Test
    public void checkChanges() throws Exception {
    }

    @Test
    public void shouldNotSplitWithWrongDataAmount() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.split(1)[0];

        sealCheckTrace(c2, true);

        Binder sd2 = DefaultBiMapper.serialize(c2);
        Binder state = (Binder) sd2.get("state");

        assertNotNull(state);
        assertTrue(state.size() > 0);

        Binder data = (Binder) state.get("data");

        assertNotNull(data);
        assertTrue(data.size() > 0);

        data.remove("amount");

        Contract dc2 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc2, false);



    }

    @Test
    public void shouldNotSplitWithWrongCreatedBy() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.split(1)[0];

        sealCheckTrace(c2, true);

        Binder sd2 = DefaultBiMapper.serialize(c2);
        Binder state = (Binder) sd2.get("state");

        assertNotNull(state);
        assertTrue(state.size() > 0);

        state.set("createdBy", "other");

        Contract dc2 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc2, false);


        state.set("createdBy", "owner");

        Contract dc3 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc3, false);


        state.remove("createdBy");

        Contract dc4 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc4, false);
    }

    @Test
    public void shouldNotSplitWithWrongOrigin() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.split(1)[0];

        sealCheckTrace(c2, true);

        Binder sd2 = DefaultBiMapper.serialize(c2);
        Binder state = (Binder) sd2.get("state");

        assertNotNull(state);
        assertTrue(state.size() > 0);

        HashId origin = HashId.withDigest(Do.randomNegativeBytes(64));
        Binder originB = DefaultBiMapper.serialize(origin);

        state.set("origin", originB);

        Contract dc2 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc2, false);


        state.remove("origin");

        Contract dc3 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc3, false);
    }

    @Test
    public void shouldNotSplitWithWrongParent() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.split(1)[0];

        sealCheckTrace(c2, true);


        Binder sd2 = DefaultBiMapper.serialize(c2);
        Binder state = (Binder) sd2.get("state");

        assertNotNull(state);
        assertTrue(state.size() > 0);

        HashId parent = HashId.withDigest(Do.randomNegativeBytes(64));
        Binder parentB = DefaultBiMapper.serialize(parent);

        state.set("parent", parentB);

        Contract dc2 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc2, false);


        state.remove("parent");

        Contract dc3 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc3, false);
    }

    @Test
    public void shouldNotSplitWithNegativeCount() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        try {
            c.split(-1);

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("split: count snould be > 0"));
        }

        try {
            c.split(0);

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("split: count snould be > 0"));
        }
    }

    @Test
    public void shouldNotSplitWithAnotherRevision() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        c.getState().setBranchNumber(1);

        try {
            Contract c2 = c.split(1)[0];

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("this revision is already split"));
        }

    }

    @Test
    public void shouldNotSplitWithAnotherIssuer() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.splitValue("amount", new Decimal(50));
        c2.setIssuerKeys(ownerKey1.getPublicKey());

        sealCheckTrace(c2, false);
    }

    @Test
    public void shouldSplitWithChangedOwnerAndNewValue() throws Exception {
        int defaultValue = 1000000;
        int valueForSplit = 85;

        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();

        sealCheckTrace(c, true);

        Contract c2 = c.splitValue("amount", new Decimal(valueForSplit));
        c2.addSignerKey(ownerKey2);
        Binder d2 = c2.getStateData();

        sealCheckTrace(c2, true);

        assertEquals(defaultValue - valueForSplit, d.getIntOrThrow("amount"));
        assertEquals(valueForSplit, d2.getIntOrThrow("amount"));
    }


    @Test
    public void testProperSum() throws Exception {

        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        sealCheckTrace(c, true);

        // bad split: no changes
        Contract c1 = c.createRevision(ownerKey2);

        sealCheckTrace(c1, false);

        // Good split
        Contract c2 = c1.splitValue("amount", new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow("amount"));
        assertEquals(500, c2.getStateData().getIntOrThrow("amount"));

        sealCheckTrace(c1, true);

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

    @Test
    public void testJoinSum() throws Exception {

        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.check());


        // bad split: no changes
        Contract c1 = c.createRevision(ownerKey2);

        sealCheckTrace(c1, false);

        // Good split
        Contract c2 = c1.splitValue("amount", new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow("amount"));
        assertEquals(500, c2.getStateData().getIntOrThrow("amount"));

        sealCheckTrace(c1, true);

        Contract c3 = c1.createRevision(ownerKey2);
        c3.getRevokingItems().add(c2);
        c3.getStateData().set("amount", new Decimal(a));

        sealCheckTrace(c3, true);

    }

    @Test
    public void cheatCreateValue() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
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
        sealCheckTrace(c1, false);

    }

    @Test
    public void cheatCreateValue2() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow("amount"));
        c.seal();
        assertTrue(c.check());

        Contract c1 = c.createRevision(ownerKey2);
        c1.getStateData().set("amount", "500.00000001");
        sealCheckTrace(c1, false);

    }

    private Contract createCoin() throws IOException {
        Contract c = Contract.fromYamlFile(rootPath + "coin.yml");
        c.setOwnerKey(ownerKey2);
        return c;
    }
}