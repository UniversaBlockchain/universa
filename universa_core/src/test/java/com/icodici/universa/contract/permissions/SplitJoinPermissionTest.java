/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.HashSet;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class SplitJoinPermissionTest extends ContractTestBase {

    @Test
    public void splitJoinHasTwoDifferentCoinTypes() throws Exception {
        Contract root = createCoinWithAmount("200", FIELD_NAME);

        Contract c1 = root.splitValue(FIELD_NAME, new Decimal(100));
        sealCheckTrace(c1, true);

        // c1 split 50 50
        c1 = c1.createRevision(ownerKey2);
        c1.seal();
        Contract c50_1 = c1.splitValue(FIELD_NAME, new Decimal(50));
        sealCheckTrace(c50_1, true);

        //set wrong revoking with the same amount
        root = root.createRevision(ownerKey2);
        root.seal();

        Contract cr50 = createCoinWithAmount("50", FIELD_NAME);

        // coin amount: 200 (revoking: 100 and 50 and 50 another different coin)
        root.getStateData().set(FIELD_NAME, new Decimal(200));
        root.addRevokingItems(c50_1);
        root.addRevokingItems(cr50);

        sealCheckTrace(root, false);
    }

    @Test
    public void splitJoinHasNotEnoughSumRevoking() throws Exception {
        Contract root = createCoinWithAmount("200", FIELD_NAME);

        Contract c1 = root.splitValue(FIELD_NAME, new Decimal(100));
        sealCheckTrace(c1, true);

        // c1 split 50 50
        c1 = c1.createRevision(ownerKey2);
        c1.seal();
        Contract c50_1 = c1.splitValue(FIELD_NAME, new Decimal(50));
        sealCheckTrace(c50_1, true);

        //set wrong revoking with the same amount
        root = root.createRevision(ownerKey2);
        root.seal();

        // c1 split 45 5
        c1 = c50_1.createRevision(ownerKey2);
        c1.seal();
        Contract c5 = c1.splitValue(FIELD_NAME, new Decimal(5));
        sealCheckTrace(c5, true);

        // coin amount: 200 (revoking: 100 and 50 and 5)
        root.getStateData().set(FIELD_NAME, new Decimal(200));
        root.addRevokingItems(c50_1);
        root.addRevokingItems(c5);

        sealCheckTrace(root, false);
    }

    @Test
    public void overSpending() throws Exception {
        Contract root = createCoinWithAmount("200", FIELD_NAME);
        root = root.createRevision();
        root.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c1 = root.splitValue(FIELD_NAME, new Decimal(100));
        root.addSignerKey(ownerKey2);
        c1.addSignerKey(ownerKey2);
        sealCheckTrace(c1, true);
        sealCheckTrace(root, true);

        // c1 == 100
        // now reset root to 200:
        root.getStateData().set(FIELD_NAME, new Decimal(200));
        // total now is 300 (200 + 100) - and must be rejected
        sealCheckTrace(root, false);
    }

    @Test
    public void splitLessThanMinValue() throws Exception {
        Contract root = createCoinWithAmount("200", FIELD_NAME);
        root = root.createRevision();
        root.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c1 = root.splitValue(FIELD_NAME, new Decimal("0.00001"));
        root.addSignerKey(ownerKey2);
        c1.addSignerKey(ownerKey2);
        sealCheckTrace(c1, false);
        sealCheckTrace(root, false);
    }

    @Test
    public void shouldSplitJoinHasEnoughSumRevoking() throws Exception {
        // 2 coins: 1st v: 50 (r: 50 and 50), 2nd v: 50 (r: 50 and 50)
        Contract root = createCoinWithAmount("200", FIELD_NAME);

        Contract c1 = root.splitValue(FIELD_NAME, new Decimal(100));
        sealCheckTrace(c1, true);

        // c1 split 50 50
        c1 = c1.createRevision(ownerKey2);
        c1.seal();
        Contract c50_1 = c1.splitValue(FIELD_NAME, new Decimal(50));
        sealCheckTrace(c50_1, true);

        //good join
        Contract finalC = c50_1.createRevision(ownerKey2);
        finalC.seal();

        finalC.getStateData().set(FIELD_NAME, new Decimal(100));
        finalC.addRevokingItems(c50_1);
        finalC.addRevokingItems(c1);

        sealCheckTrace(finalC, true);
    }

    @Test
    public void shouldNotJoinWithWrongParent() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c1 = c.splitValue(FIELD_NAME, new Decimal(1));

        Contract.ContractDev dev = c1.new ContractDev(c1);

        //Check after split.
        sealCheckTrace(c, true);

        //Set wrong parent
        HashId parent = HashId.withDigest(Do.randomNegativeBytes(64));
        HashId origin = HashId.withDigest(Do.randomNegativeBytes(64));
        dev.setParent(parent);
        dev.setOrigin(origin);

        c.getRevokingItems().add(dev.getContract());

        sealCheckTrace(c, false);
    }

    @Test
    public void shouldNotJoinWithWrongAmount() throws Exception {
        int amount = 1000000;
        int v = 1;

        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        // Split with 1
        Contract c2 = c.splitValue(FIELD_NAME, new Decimal(v));
        assertEquals(amount - v, c.getStateData().getIntOrThrow(FIELD_NAME));
        assertEquals(v, c2.getStateData().getIntOrThrow(FIELD_NAME));

        sealCheckTrace(c2, true);

        Contract c3 = c.createRevision(ownerKey2);
        c3.getRevokingItems().add(c2);

        //Trying to hack the join and get bigger amount
        c3.getStateData().set(FIELD_NAME, new Decimal(v + 1));
        assertEquals(amount - v, c.getStateData().getIntOrThrow(FIELD_NAME));
        assertEquals(v + 1, c3.getStateData().getIntOrThrow(FIELD_NAME));

        sealCheckTrace(c3, false);
    }

    @Test
    public void shouldNotSplitWithWrongDataAmountSerialize() throws Exception {
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

        data.remove(FIELD_NAME);

        Contract dc2 = DefaultBiMapper.deserialize(sd2);

        sealCheckTrace(dc2, false);

    }

    @Test
    public void shouldNotSplitWithWrongCreatedBySerialize() throws Exception {
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
    public void shouldNotSplitWithWrongOriginSerialize() throws Exception {
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
    public void shouldNotSplitWithWrongParentSerialize() throws Exception {
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
            assertTrue(e.getMessage().equalsIgnoreCase("split: count should be > 0"));
        }

        try {
            c.split(0);

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("split: count should be > 0"));
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
    public void shouldNotSplitWithAnotherIssuerSerialize() throws Exception {
        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        Contract c2 = c.splitValue(FIELD_NAME, new Decimal(50));
        c2.setIssuerKeys(ownerKey1.getPublicKey());

        sealCheckTrace(c2, false);
    }

    @Test
    public void shouldSplitWithChangedOwnerAndNewValueSerialize() throws Exception {
        int defaultValue = 1000000;
        int valueForSplit = 85;

        Contract c = createCoin();
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();

        sealCheckTrace(c, true);

        Contract c2 = c.splitValue(FIELD_NAME, new Decimal(valueForSplit));
        c2.addSignerKey(ownerKey2);
        Binder d2 = c2.getStateData();

        sealCheckTrace(c2, true);

        assertEquals(defaultValue - valueForSplit, d.getIntOrThrow(FIELD_NAME));
        assertEquals(valueForSplit, d2.getIntOrThrow(FIELD_NAME));
    }


    @Test
    public void testProperSum() throws Exception {

        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow(FIELD_NAME));
        sealCheckTrace(c, true);

        // bad split: no changes
        Contract c1 = c.createRevision(ownerKey2);

        sealCheckTrace(c1, false);

        // Good split
        Contract c2 = c1.splitValue(FIELD_NAME, new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow(FIELD_NAME));
        assertEquals(500, c2.getStateData().getIntOrThrow(FIELD_NAME));

        c1.getErrors().clear();
        sealCheckTrace(c1, true);

        // and it should be the same after seriazling to the transaction pack

        TransactionPack tp = new TransactionPack(c1);
//        tp.trace();

        TransactionPack tp2 = TransactionPack.unpack(new TransactionPack(c1).pack());
//        tp2.trace();
        Contract restored = tp2.getContract();
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
        assertEquals(a, d.getIntOrThrow(FIELD_NAME));
        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.check());


        // bad split: no changes
        Contract c1 = c.createRevision(ownerKey2);

        sealCheckTrace(c1, false);

        // Good split
        Contract c2 = c1.splitValue(FIELD_NAME, new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow(FIELD_NAME));
        assertEquals(500, c2.getStateData().getIntOrThrow(FIELD_NAME));

        c1.getErrors().clear();
        sealCheckTrace(c1, true);

        Contract c3 = c1.createRevision(ownerKey2);
        c3.getRevokingItems().add(c2);
        c3.getStateData().set(FIELD_NAME, new Decimal(a));

        sealCheckTrace(c3, true);

    }

    @Test
    public void cheatCreateValue() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow(FIELD_NAME));
        c.seal();
        assertTrue(c.check());

        Contract c1 = c.createRevision(ownerKey2);


        // Good split but wrong amount
        Contract c2 = c1.splitValue(FIELD_NAME, new Decimal(500));
        assertEquals(a - 500, c1.getStateData().getIntOrThrow(FIELD_NAME));
        assertEquals(500, c2.getStateData().getIntOrThrow(FIELD_NAME));
        c2.getStateData().set(FIELD_NAME, "500.00000001");
        sealCheckTrace(c1, false);

    }

    @Test
    public void cheatCreateValue2() throws Exception {
        Contract c = createCoin();

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        int a = 1000000;
        assertEquals(a, d.getIntOrThrow(FIELD_NAME));
        c.seal();
        assertTrue(c.check());

        Contract c1 = c.createRevision(ownerKey2);
        c1.getStateData().set(FIELD_NAME, "500.00000001");
        sealCheckTrace(c1, false);

    }


    @Test
    public void joinWithoutPermission() throws Exception {

        Contract contract = new Contract(ownerKey1);
        contract.getDefinition().getData().set("currency","UTN");

        Binder params = Binder.of("field_name", "amount", "join_match_fields",asList("definition.data.currency","definition.issuer"));
        Role ownerLink = new RoleLink("@onwer_link","owner");
        contract.registerRole(ownerLink);
        SplitJoinPermission  splitJoinPermission = new SplitJoinPermission(ownerLink,params);
        contract.addPermission(splitJoinPermission);
        contract.getStateData().set("amount","1000.5");
        contract.seal();
        contract.check();
        assertTrue(contract.isOk());

        Contract contractToJoin = new Contract(ownerKey1);
        contractToJoin.getDefinition().getData().set("currency","UTN");
        ownerLink = new RoleLink("@onwer_link","owner");
        contractToJoin.getStateData().set("amount","100.0");
        contractToJoin.registerRole(ownerLink);
        //RevokePermission revokePermission = new RevokePermission(ownerLink);
        //contractToJoin.addPermission(revokePermission);

        contractToJoin.seal();
        contractToJoin.check();
        assertTrue(contractToJoin.isOk());

        HashSet<PrivateKey> keys = new HashSet<>();
        keys.add(ownerKey1);
        Contract joinResult = ContractsService.createJoin(contract, contractToJoin, "amount", keys);
        joinResult.check();
        assertFalse(joinResult.isOk());


        splitJoinPermission = new SplitJoinPermission(ownerLink,params);
        contractToJoin.addPermission(splitJoinPermission);
        contractToJoin.seal();
        contractToJoin.check();
        assertTrue(contractToJoin.isOk());

        joinResult = ContractsService.createJoin(contract, contractToJoin, "amount", keys);
        joinResult.check();
        assertTrue(joinResult.isOk());
    }

}