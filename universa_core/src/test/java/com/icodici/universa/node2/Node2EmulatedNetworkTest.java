/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;

public class Node2EmulatedNetworkTest extends Node2SingleTest {

    public static int NODES = 3;

    public static List<Node> nodes = new ArrayList<>();

    public void setUp() throws Exception {
        nodes = new ArrayList<>();
        config = new Config();
        config.setPositiveConsensus(2);
        config.setNegativeConsensus(1);
        config.setResyncBreakConsensus(2);

        Properties properties = new Properties();
        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        nc = new NetConfig();
        TestEmulatedNetwork en = new TestEmulatedNetwork(nc);

        for (int i = 0; i < NODES; i++) {

            ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);

            int offset = 7100 + 10 * i;
            NodeInfo info =
                    new NodeInfo(
                            getNodeKey(i).getPublicKey(),
                            i,
                            "testnode_" + i,
                            "localhost",
                            offset + 3,
                            offset,
                            offset + 2
                    );
            nc.addNode(info);
            Node n = new Node(config, info, ledger, en);
            nodes.add(n);
            en.addNode(info, n);
        }
        network = en;
        node = nodes.get(0);
        Thread.sleep(100);
    }

    @Test
    public void registerGoodItem() throws Exception {
        int N = 100;
        for (int k = 0; k < 1; k++) {
//            StopWatch.measure(true, () -> {
            for (int i = 0; i < N; i++) {
                TestItem ok = new TestItem(true);
                node.registerItem(ok);
                for (Node n : nodes) {
                    try {
                        ItemResult r = n.waitItem(ok.getId(), 2500);
                        assertEquals(ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
                        fail("timeout");
                    }
                }
            }
//            });
            assertThat(node.countElections(), is(lessThan(10)));
        }
    }

    //    @Test
    public void unexpectedStrangeCaseWithConcurrent() throws Exception {
        String FIELD_NAME = "amount";
        PrivateKey ownerKey2 = TestKeys.privateKey(1);
        String PRIVATE_KEY = "_xer0yfe2nn1xthc.private.unikey";

        Contract root = Contract.fromDslFile("./src/test_contracts/coin.yml");
        root.getStateData().set(FIELD_NAME, new Decimal(200));
        root.addSignerKeyFromFile("./src/test_contracts/" + PRIVATE_KEY);
        root.setOwnerKey(ownerKey2);
        root.seal();
        assertTrue(root.check());


        Contract c1 = root.splitValue(FIELD_NAME, new Decimal(100));
        c1.seal();
        assertTrue(root.check());
        assertTrue(c1.isOk());

        // c1 split 50 50
        c1 = c1.createRevision(ownerKey2);
        c1.seal();
        Contract c50_1 = c1.splitValue(FIELD_NAME, new Decimal(50));
        c50_1.seal();
        assertTrue(c50_1.isOk());

        //good join
        Contract finalC = c50_1.createRevision(ownerKey2);
        finalC.seal();

        finalC.getStateData().set(FIELD_NAME, new Decimal(100));
        finalC.addRevokingItems(c50_1);
        finalC.addRevokingItems(c1);

        for (int j = 0; j < 500; j++) {

            HashId id;
            StateRecord orCreate;

            int p = 0;
            for (Approvable c : finalC.getRevokingItems()) {
                id = c.getId();
                for (int i = 0; i < nodes.size(); i++) {
                    if (i == nodes.size() - 1 && p == 1) break;

                    Node nodeS = nodes.get(i);
                    orCreate = nodeS.getLedger().findOrCreate(id);
                    orCreate.setState(ItemState.APPROVED).save();
                }
                ++p;
            }

            destroyFromAllNodesExistingNew(finalC);

            destroyCurrentFromAllNodesIfExists(finalC);

            node.registerItem(finalC);
            ItemResult itemResult = node.waitItem(finalC.getId(), 1500);
            System.out.println(itemResult.state);
//            if (ItemState.APPROVED != itemResult.state)
//                System.out.println("\r\nWrong state on repetition " + j + ": " + itemResult + ", " + itemResult.errors +
//                        " \r\ncontract_errors: " + finalC.getErrors());
//            else
//                System.out.println("\r\nGood. repetition: " + j + " ledger:" + node.getLedger().toString());
//                fail("Wrong state on repetition " + j + ": " + itemResult + ", " + itemResult.errors +
//                        " \r\ncontract_errors: " + finalC.getErrors());

//            assertEquals(ItemState.APPROVED, itemResult.state);
        }

    }

    private void destroyCurrentFromAllNodesIfExists(Contract finalC) {
        for (Node nodeS : nodes) {
            StateRecord r = nodeS.getLedger().getRecord(finalC.getId());
            if (r != null) {
                r.destroy();
            }
        }
    }

    @Test
    public void checkSimpleCase() throws Exception {
//        String transactionName = "./src/test_contracts/transaction/93441e20-242a-4e91-b283-8d0fd5f624dd.transaction";

        for (int i = 0; i < 5; i++) {
            Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            contract.seal();

            addDetailsToAllLedgers(contract);

            contract.check();
            contract.traceErrors();
            assertTrue(contract.isOk());

            node.registerItem(contract);
            ItemResult itemResult = node.waitItem(contract.getId(), 1500);
            if (ItemState.APPROVED != itemResult.state)
                fail("Wrong state on repetition " + i + ": " + itemResult + ", " + itemResult.errors +
                        " \r\ncontract_errors: " + contract.getErrors());

            assertEquals(ItemState.APPROVED, itemResult.state);
        }
    }

    @Test(timeout = 3000)
    public void resync() throws Exception {
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);
        nodes.forEach(n -> {
            System.out.println(n.getLedger().getRecord(c.getId()));
        });
        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

//        LogPrinter.showDebug(true);
        node.resync(c.getId());
        System.out.println(node.checkItem(c.getId()));
    }

    private void addToAllLedgers(Contract c, ItemState state) {
        for (Node n : nodes) {
            n.getLedger().findOrCreate(c.getId()).setState(state).save();
        }
    }

    //    @Test
//    public void checkSergeychCase() throws Exception {
//        String transactionName = "./src/test_contracts/transaction/b8f8a512-8c45-4744-be4e-d6788729b2a7.transaction";
//
//        for (int i = 0; i < 5; i++) {
//            Contract contract = readContract(transactionName, true);
//
//            addDetailsToAllLedgers(contract);
//
//            contract.check();
//            contract.traceErrors();
//            assertTrue(contract.isOk());
//
//            node.registerItem(contract);
//            ItemResult itemResult = node.waitItem(contract.getId(), 15000);
//
//            if (ItemState.APPROVED != itemResult.state)
//                fail("Wrong state on repetition " + i + ": " + itemResult + ", " + itemResult.errors +
//                        " \r\ncontract_errors: " + contract.getErrors());
//
//            assertEquals(ItemState.APPROVED, itemResult.state);
//        }
//    }

    private void addDetailsToAllLedgers(Contract contract) {
        HashId id;
        StateRecord orCreate;
        for (Approvable c : contract.getRevokingItems()) {
            id = c.getId();
            for (Node nodeS : nodes) {
                orCreate = nodeS.getLedger().findOrCreate(id);
                orCreate.setState(ItemState.APPROVED).save();
            }
        }

        destroyFromAllNodesExistingNew(contract);

        destroyCurrentFromAllNodesIfExists(contract);
    }

    private void destroyFromAllNodesExistingNew(Contract c50_1) {
        StateRecord orCreate;
        for (Approvable c : c50_1.getNewItems()) {
            for (Node nodeS : nodes) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        ItemResult r = node.waitItem(bad.getId(), 500);
        assertEquals(ItemState.DECLINED, r.state);
    }

    @Test
    public void checkItem() throws Exception {
        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 500);
        node.waitItem(bad.getId(), 500);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }


    @Test
    public void shouldCreateItems() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);
    }

    @Test
    public void shouldDeclineItems() throws Exception {
        TestItem item = new TestItem(false);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);
        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.DECLINED, result.state);
    }

    @Test
    public void singleNodeMixApprovedAndDeclined() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);


        // Negative consensus
        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        ItemResult result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);
        result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.checkItem(item2.getId());
        assertEquals(ItemState.DECLINED, result2.state);
    }


    @Test
    public void testNotCreatingOnReject() throws Exception {
        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }

    @Test
    public void rejectBadNewItem() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(false);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);
        ItemResult itemResult = node.waitItem(main.getId(), 500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }

    @Test
    public void badNewDocumentsPreventAccepting() throws Exception {
        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we run the day for teh output document:
        node.registerItem(new2);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        @NonNull ItemResult item = node.checkItem(main.getId());
        assertEquals(ItemState.UNDEFINED, item.state);

        node.registerItem(main);

        ItemResult itemResult = node.checkItem(main.getId());
        assertThat(itemResult.state, anyOf(equalTo(ItemState.PENDING), equalTo(ItemState.PENDING_NEGATIVE), equalTo(ItemState.DECLINED)));

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        Assert.assertThat(itemNew2.state, anyOf(equalTo(ItemState.APPROVED), equalTo(ItemState.PENDING_POSITIVE)));
    }

//    @Test
//    public void acceptWithReferences() throws Exception {
//        TestItem main = new TestItem(true);
//        TestItem new1 = new TestItem(true);
//        TestItem new2 = new TestItem(true);
//
//        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//        existing1.setState(ItemState.APPROVED).save();
//        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//        existing2.setState(ItemState.LOCKED).save();
//
//        main.addReferencedItems(existing1.getId(), existing2.getId());
//        main.addNewItems(new1, new2);
//
//        main.addReferencedItems(existing1.getId(), existing2.getId());
//        main.addNewItems(new1, new2);
//
//        // check that main is fully approved
//        node.registerItem(main);
//
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        assertEquals(ItemState.APPROVED, node.checkItem(new1.getId()).state);
//        assertEquals(ItemState.APPROVED, node.checkItem(new2.getId()).state);
//
//        // and the references are intact
//        assertEquals(ItemState.APPROVED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.LOCKED, node.checkItem(existing2.getId()).state);
//    }

    @Test
    public void badReferencesDeclineListStates() throws Exception {

        LogPrinter.showDebug(true);

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {


            System.out.println("--------state " + badState + " ---------");

            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();

            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addReferencedItems(existing1.getId(), existing2.getId());

            // check that main is fully approved
            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 500);
            assertEquals(ItemState.DECLINED, itemResult.state);

            Thread.sleep(500);

            // and the references are intact
            assertEquals(ItemState.APPROVED, existing1.reload().getState());
            assertEquals(badState, existing2.reload().getState());
        }
    }

    @Test
    public void badReferencesDecline() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem existing1 = new TestItem(false);
        TestItem existing2 = new TestItem(true);

        System.out.println("--------resister (bad) item " + existing1.getId() + " ---------");
        node.registerItem(existing1);

        System.out.println("--------resister (good) item " + existing2.getId() + " ---------");
        node.registerItem(existing2);

        main.addReferencedItems(existing1.getId(), existing2.getId());
        main.addNewItems(new1, new2);

        System.out.println("--------resister (main) item " + main.getId() + " ---------");

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.checkItem(new1.getId()).state);
        assertEquals(ItemState.UNDEFINED, node.checkItem(new2.getId()).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.checkItem(existing1.getId()).state);
        if (node.checkItem(existing2.getId()).state.isPending())
            Thread.sleep(500);
        assertEquals(ItemState.APPROVED, node.checkItem(existing2.getId()).state);
    }

    @Test
    public void missingReferencesDecline() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 1500);

        // but second is missing
        HashId missingId = HashId.createRandom();

        main.addReferencedItems(existing.getId(), missingId);

        // check that main is fully approved
        node.registerItem(main);
        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        System.out.println(node.getItem(missingId));

        assertNull(node.getItem(missingId));
    }

    @Test
    public void approveAndRevoke() throws Exception {
        return;
//        TestItem main = new TestItem(true);
//
//        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//        existing1.setState(ItemState.APPROVED).save();
//        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//        existing2.setState(ItemState.APPROVED).save();
//
//        main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));
//
//         check that main is fully approved
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//         and the references are intact
//        assertEquals(ItemState.REVOKED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.REVOKED, node.checkItem(existing2.getId()).state);
    }

    @Test
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {

//        LogPrinter.showDebug(true);

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 1500);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            assertEquals(ItemState.APPROVED, existing1.reload().getState());
            assertEquals(badState, existing2.reload().getState());

        }
    }

//    @Test
//    public void itemsCachedThenPurged() throws Exception {
//        config.setMaxElectionsTime(Duration.ofMillis(100));
//
//        TestItem main = new TestItem(true);
//        main.setExpiresAtPlusFive(false);
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 3000);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        assertEquals(main, node.getItem(main.getId()));
//        Thread.sleep(1200);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
//    }

    @Test
    public void createRealContract() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }


}