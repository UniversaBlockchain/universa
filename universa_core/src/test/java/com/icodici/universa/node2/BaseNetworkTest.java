/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Leonid Novikov <nil182@mail.ru>
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class BaseNetworkTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";
    protected static final String CONFIG_2_PATH = "./src/test_config_2/";

    protected Network network = null;
    protected Node node = null;
    protected List<Node> nodes = null;
    protected Map<NodeInfo,Node> nodesMap = null;
    protected Ledger ledger = null;
    protected Config config = null;



    public void init(Node node, List<Node> nodes, Map<NodeInfo,Node> nodesMap, Network network, Ledger ledger, Config config) throws Exception {
        this.node = node;
        this.nodes = nodes;
        this.nodesMap = nodesMap;
        this.network = network;
        this.ledger = ledger;
        this.config = config;
    }



    @Test(timeout = 120000)
    public void registerGoodItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Thread.sleep(500);
        int N = 100;
        for (int k = 0; k < 1; k++) {
//            StopWatch.measure(true, () -> {
                for (int i = 0; i < N; i++) {
                    TestItem ok = new TestItem(true);
                    System.out.println("--------------register item " + ok.getId() + " ------------");
                    node.registerItem(ok);
                    for (Node n : nodesMap.values()) {
                        try {
                            ItemResult r = n.waitItem(ok.getId(), 8000);
                            int numIterations = 0;
                            while( !r.state.isConsensusFound()) {
                                System.out.println("wait for consensus receiving on the node " + n + " state is " + r.state);
                                Thread.sleep(500);
                                r = n.waitItem(ok.getId(), 8000);
                                numIterations++;
                                if(numIterations > 20)
                                    break;
                            }
                            assertEquals("In node " + n + " item " + ok.getId(), ItemState.APPROVED, r.state);
                        } catch (TimeoutException e) {
                            fail("timeout");
                        }
                    }

                    ItemResult r = node.waitItem(ok.getId(), 5500);
                    assertEquals("after: In node "+node+" item "+ok.getId(), ItemState.APPROVED, r.state);

                }
//            });
        }
    }



    @Test
    public void registerBadItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        ItemResult r = node.waitItem(bad.getId(), 3000);
        assertEquals(ItemState.DECLINED, r.state);
    }



    @Test
    public void checkItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 2000);
        node.waitItem(bad.getId(), 2000);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }



    @Test
    public void shouldCreateItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

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
    }



    @Test
    public void shouldDeclineItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

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
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

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
    public void timeoutError() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Duration savedMaxElectionsTime = config.getMaxElectionsTime();

        config.setMaxElectionsTime(Duration.ofMillis(200));

        TestItem item = new TestItem(true);

        // We start elections but no node in the network know the source, so it
        // will short-circuit to self and then stop by the timeout:

        ItemResult itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        assertFalse(itemResult.haveCopy);
        assertNull(itemResult.createdAt);
        assertNull(itemResult.expiresAt);

        itemResult = node.waitItem(item.getId(), 100);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        config.setMaxElectionsTime(savedMaxElectionsTime);
    }



    @Test
    public void testNotCreatingOnReject() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 2500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test
    public void rejectBadNewItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(false);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);
        ItemResult itemResult = node.waitItem(main.getId(), 2000);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test
    public void badNewDocumentsPreventAccepting() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we run the day for teh output document:
        node.registerItem(new2);
        node.waitItem(new2.getId(), 3000);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        @NonNull ItemResult item = node.checkItem(main.getId());
        assertEquals(ItemState.UNDEFINED, item.state);

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 2000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, itemNew2.state);

        LogPrinter.showDebug(false);
    }



    @Test
    public void acceptWithReferences() throws Exception {
        return;
    }



    @Test(timeout = 30000)
    public void badReferencesDeclineListStates() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            Thread.sleep(100);

            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();


            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addReferencedItems(existing1.getId(), existing2.getId());

            // check that main is fully approved
            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 5000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            while(ItemState.APPROVED != existing1.getState()) {
                Thread.sleep(500);
                System.out.println(existing1.reload().getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.getState()) {
                Thread.sleep(500);
                System.out.println(existing2.reload().getState());
            }
            assertEquals(badState, existing2.getState());
        }
    }



    @Test
    public void badReferencesDecline() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem existing1 = new TestItem(false);
        TestItem existing2 = new TestItem(true);

        System.out.println("--------resister (bad) item " + existing1.getId() + " ---------");
        node.registerItem(existing1);
        node.waitItem(existing1.getId(), 2000);

        System.out.println("--------resister (good) item " + existing2.getId() + " ---------");
        node.registerItem(existing2);
        node.waitItem(existing2.getId(), 2000);

        main.addReferencedItems(existing1.getId(), existing2.getId());
        main.addNewItems(new1, new2);

        System.out.println("--------resister (main) item " + main.getId() + " ---------");

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.waitItem(new1.getId(), 3000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(new2.getId(), 3000).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.waitItem(existing1.getId(), 3000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(existing2.getId(), 3000).state);
    }



    @Test
    public void missingReferencesDecline() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 2000);

        // but second is missing
        HashId missingId = HashId.createRandom();

        main.addReferencedItems(existing.getId(), missingId);

        // check that main is declined
        System.out.println("--------- missind id: " + missingId);
        System.out.println("--------- existing id: " + existing.getId());
        node.registerItem(main);
        // need some time to resync missingId
        ItemResult itemResult = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        System.out.println(node.getItem(missingId));

        assertNull(node.getItem(missingId));
    }



    // this test can't be executed in a network as it needs setup in all 3 ledgers
//    @Test
//    public void approveAndRevoke() throws Exception {
//        TestItem main = new TestItem(true);
//
//        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//        existing1.setState(ItemState.APPROVED).save();
//        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//        existing2.setState(ItemState.APPROVED).save();
//
//        main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));
//
//        // check that main is fully approved
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        // and the references are intact
//        assertEquals(ItemState.REVOKED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.REVOKED, node.checkItem(existing2.getId()).state);
//    }
//



    @Test(timeout = 25000)
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {


            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            Thread.sleep(200);
            TestItem main = new TestItem(true);
            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            Thread.sleep(200);

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 5000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            while (ItemState.APPROVED != existing1.getState()) {
                Thread.sleep(500);
                System.out.println(existing1.reload().getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.getState()) {
                Thread.sleep(500);
                System.out.println(existing2.reload().getState());
            }
            assertEquals(badState, existing2.getState());
        }
    }

    @Test
    public void registerDeepTree() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(true);

        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
    }

    @Test
    public void registerDeepTreeWithRevoke() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(true);
        TestItem revoke1 = new TestItem(true);
        TestItem revoke2 = new TestItem(true);

        node.registerItem(revoke1);
        assertEquals(ItemState.APPROVED, node.waitItem(revoke1.getId(), 5000).state);

        node.registerItem(revoke2);
        assertEquals(ItemState.APPROVED, node.waitItem(revoke2.getId(), 5000).state);

        new1_1.addRevokingItems(revoke1);
        new2_1.addRevokingItems(revoke2);
        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2_1.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(revoke1.getId(), 2000);
        assertEquals(ItemState.REVOKED, result.state);
        result = node.waitItem(revoke2.getId(), 2000);
        assertEquals(ItemState.REVOKED, result.state);
    }

    @Test
    public void declineDeepTreeBadNew() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem new2_1 = new TestItem(false);

        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 2000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2_1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }

    @Test
    public void declineDeepTreeBadRevoke() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem revoke1 = new TestItem(true);


        TestItem new1_1 = new TestItem(true);
        TestItem revoke1_1 = new TestItem(true);

        new1_1.addRevokingItems(revoke1_1);
        new1.addNewItems(new1_1);
        new1.addRevokingItems(revoke1);
        main.addNewItems(new1);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 2000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1_1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }


    @Test
    public void createRealContract() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
    }

    @Test
    public void checkSimpleCase() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

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
            ItemResult itemResult = node.waitItem(contract.getId(), 3000);
            if (ItemState.APPROVED != itemResult.state)
                fail("Wrong state on repetition " + i + ": " + itemResult + ", " + itemResult.errors +
                        " \r\ncontract_errors: " + contract.getErrors());

            assertEquals(ItemState.APPROVED, itemResult.state);
        }
    }


    // split and join section

    @Test
    public void shouldApproveSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", key);
        Contract c2 = c1.getNew().get(0);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c2.getId(), 5000).state);
    }

    @Test
    public void shouldDeclineSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 550
        Contract c1 = ContractsService.createSplit(c, 550, "amount", key);
        Contract c2 = c1.getNew().get(0);
        assertEquals("-450", c1.getStateData().get("amount").toString());
        assertEquals("550", c2.getStateData().get("amount").toString());

        registerAndCheckDeclined(c1);

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(c2.getId(), 5000).state);
    }

    @Test
    public void shouldApproveSplitAndJoinWithNewSend() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(key);
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));

        // split 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", key);
        Contract c2 = c1.getNew().get(0);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c2.getId(), 5000).state);


        // join 70 + 30 = 100
        Contract c3 = ContractsService.createJoin(c1, c2, "amount", keys);
        c3.check();
        c3.traceErrors();
        assertTrue(c3.isOk());

        registerAndCheckApproved(c3);
        assertEquals(new Decimal(100), c3.getStateData().get("amount"));

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(c2.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(c3.getId(), 5000).state);
    }

    @Test
    public void shouldDeclineSplitAndJoinWithWrongAmount() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKey(key);
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));

        // split 100 - 30 = 70
        Contract c1 = ContractsService.createSplit(c, 30, "amount", key);
        Contract c2 = c1.getNew().get(0);
        registerAndCheckApproved(c1);
        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());


        //wrong. send 500 out of 2 contracts (70 + 30)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", new Decimal(500));
        c3.addSignerKey(key);
        c3.addRevokingItems(c1);
        assertFalse(c3.check());
        c3.seal();

        registerAndCheckDeclined(c3);
    }

//    @Test
//    public void checkSergeychCase() throws Exception {
//    String transactionName = "./src/test_contracts/transaction/e00b7488-9a8f-461f-96f6-177c6272efa0.transaction";
//
//        for( int i=0; i < 5; i++) {
//            Contract contract = readContract(transactionName, true);
//
//            HashId id;
//            StateRecord record;
//
//            for (Approvable c : contract.getRevokingItems()) {
//                id = c.getId();
//                record = ledger.findOrCreate(id);
//                record.setState(ItemState.APPROVED).save();
//            }
//
//            for( Approvable c: contract.getNewItems()) {
//                record = ledger.getRecord(c.getId());
//                if( record != null )
//                    record.destroy();
//            }
//
//            StateRecord r = ledger.getRecord(contract.getId());
//            if( r !=  null ) {
//                r.destroy();
//            }
//
//            contract.check();
//            contract.traceErrors();
//            assertTrue(contract.isOk());
//
//            @NonNull ItemResult ir = node.registerItem(contract);
////            System.out.println("-- "+ir);
//            ItemResult itemResult = node.waitItem(contract.getId(), 15000);
//            if( ItemState.APPROVED != itemResult.state)
//                fail("Wrong state on repetition "+i+": "+itemResult+", "+itemResult.errors);
//            assertEquals(ItemState.APPROVED, itemResult.state);
//        }
//    }



    // quantizer section

    @Test
    public void shouldBreakByQuantizer() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        // 100
        Contract.setTestQuantaLimit(10);
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c.seal();

        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test
    public void shouldBreakByQuantizerSplit() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c.seal();

        registerAndCheckApproved(c);


        Contract.setTestQuantaLimit(60);
        // 30
        Contract c1 = ContractsService.createSplit(c, 30, "amount", key);
        Contract c2 = c1.getNew().get(0);

        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        node.registerItem(c1);
        ItemResult itemResult = node.waitItem(c1.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }


    // swap section

    public void prepareContractsForSwap(
            Set<PrivateKey> martyPrivateKeys,
            Set<PublicKey> martyPublicKeys,
            Set<PrivateKey> stepaPrivateKeys,
            Set<PublicKey> stepaPublicKeys,
            Contract delorean,
            Contract lamborghini) throws Exception {

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));

        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
    }


    public void checkSwapResultSuccess(Contract swapContract,
                                       Contract delorean,
                                       Contract lamborghini,
                                       Set<PublicKey> martyPublicKeys,
                                       Set<PublicKey> stepaPublicKeys ) throws TimeoutException, InterruptedException {
        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.APPROVED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));

        lamborghiniResult = node.waitItem(newLamborghini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
        assertTrue(newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
    }


    public void checkSwapResultDeclined(Contract swapContract,
                                        Contract delorean,
                                        Contract lamborghini,
                                        Set<PublicKey> martyPublicKeys,
                                        Set<PublicKey> stepaPublicKeys ) throws TimeoutException, InterruptedException {
        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership still belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership still belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership should belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));

        lamborghiniResult = node.waitItem(newLamborghini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership should belongs to Marty: " + newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
        assertTrue(newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
    }


    @Test
    public void swapContractsViaTransactionAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckApproved(swapContract);

        checkSwapResultSuccess(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneNotSign1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign!
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getSealedByKeys().removeAll(martyPublicKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneNotSign2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getSealedByKeys().removeAll(stepaPublicKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneNotSign3() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.removeAllSignatures();
        newLamborghini.addSignatureToSeal(stepaPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneNotSign4() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // erase one sign
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.removeAllSignatures();
        newLamborghini.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneWrongSign1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
//        swapContract = imitateSendingTransactionToPartner(swapContract);
//        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        finishSwap_wrongKey(swapContract, martyPrivateKeys, wrongSign);
        //

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneWrongSign2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
//        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        signPresentedSwap_wrongKey(swapContract, stepaPrivateKeys, wrongSign);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionOneWrongSign3() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        // WRONG SIGN!
        PrivateKey wrongSign = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        swapContract = startSwap_wrongKey(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys, wrongSign);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }



    @Test
    public void swapContractsViaTransactionWrongTID1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        Contract newDelorean = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
        }
        newDelorean.getTransactional().setId(HashId.createRandom().toBase64String());
        newDelorean.seal();
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);



        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionWrongTID2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);

        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        newLamborghini.getTransactional().setId(HashId.createRandom().toBase64String());

        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionMissingTransactional1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getTransactional().getReferences().get(0).transactional_id);
        System.out.println(newLamborghini.getTransactional().getReferences().get(0).transactional_id);

        // erase both of transactional_id
        newDelorean.getTransactional().setId("");
        newLamborghini.getTransactional().setId("");
        newDelorean.getTransactional().getReferences().get(0).transactional_id = "";
        newLamborghini.getTransactional().getReferences().get(0).transactional_id = "";

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionMissingTransactional2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getTransactional().getReferences().get(0).transactional_id);
        System.out.println(newLamborghini.getTransactional().getReferences().get(0).transactional_id);

        // set both of transactional_id to null
        newDelorean.getTransactional().setId(null);
        newLamborghini.getTransactional().setId(null);
        newDelorean.getTransactional().getReferences().get(0).transactional_id = null;
        newLamborghini.getTransactional().getReferences().get(0).transactional_id = null;

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionWrongCID() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // set wrong reference.contract_id for second contract
        int iHack = 1;
        if(swapContract.getNew().get(0).getParent().equals(lamborghini.getId()))
            iHack = 0;
        swapContract.getNew().get(iHack).getTransactional().getReferences().get(0).contract_id = HashId.createRandom();
        swapContract.getNew().get(iHack).seal();
        swapContract.getNew().get(iHack).addSignatureToSeal(stepaPrivateKeys);
        swapContract.seal();

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }
        System.out.println(newDelorean.getTransactional().getId());
        System.out.println(newLamborghini.getTransactional().getId());
        System.out.println(newDelorean.getReferencedItems().iterator().next().transactional_id);
        System.out.println(newLamborghini.getReferencedItems().iterator().next().transactional_id);

        System.out.println(newDelorean.getTransactional().getReferences().get(0));
        System.out.println(newLamborghini.getTransactional().getReferences().get(0));
        System.out.println(swapContract.getNew().get(0).getTransactional().getReferences().get(0));
        System.out.println(swapContract.getNew().get(1).getTransactional().getReferences().get(0));

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test
    public void swapContractsViaTransactionSnatch1() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        // stepa stole new revisions of contarcts and try to register it as single
        newDelorean.check();
        newDelorean.traceErrors();
        registerAndCheckDeclined(newDelorean);

        newLamborghini.check();
        newLamborghini.traceErrors();
        registerAndCheckDeclined(newLamborghini);

    }


    @Test
    public void swapContractsViaTransactionSnatch2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        // marty stole new revisions of contracts and try to register it as single
        newDelorean.check();
        newDelorean.traceErrors();
        registerAndCheckDeclined(newDelorean);

        newLamborghini.check();
        newLamborghini.traceErrors();
        registerAndCheckDeclined(newLamborghini);

    }



    private void registerAndCheckApproved(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    private void registerAndCheckDeclined(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 8000);
        assertEquals(ItemState.DECLINED, itemResult.state);
    }

    /**
     * Imitate of sending contract from one part of swappers to another.
     *
     * Method packs sending contracts with main swap contract (can be blank - doesn't matter) into TransactionPack.
     * Then restore from packed binary main swap contract, contracts sending with.
     * And fill sent contract with oldContracts.
     * It is hook because current implementation of uTransactionPack,unpack() missing them.
     * Second hook is Contarct.clearContext() - if do not call, checking will fail. 
     *
     * @param mainContract
     * @return
     * @throws Exception
     */
    public Contract imitateSendingTransactionToPartner(Contract mainContract) throws Exception {

        TransactionPack tp_before = mainContract.getTransactionPack();
        byte[] data = tp_before.pack();

        // here we "send" data and "got" it

        TransactionPack tp_after = TransactionPack.unpack(data);
        Contract gotMainContract = tp_after.getContract();

        return gotMainContract;
    }



    protected void addDetailsToAllLedgers(Contract contract) {
        HashId id;
        StateRecord orCreate;
        for (Approvable c : contract.getRevokingItems()) {
            id = c.getId();
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().findOrCreate(id);
                orCreate.setState(ItemState.APPROVED).save();
            }
        }

        destroyFromAllNodesExistingNew(contract);

        destroyCurrentFromAllNodesIfExists(contract);
    }

    protected void destroyFromAllNodesExistingNew(Contract c50_1) {
        StateRecord orCreate;
        for (Approvable c : c50_1.getNewItems()) {
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    protected void destroyCurrentFromAllNodesIfExists(Contract finalC) {
        for (Node nodeS : nodesMap.values()) {
            StateRecord r = nodeS.getLedger().getRecord(finalC.getId());
            if (r != null) {
                r.destroy();
            }
        }
    }



    @Test
    public void itemsCachedThenPurged() throws Exception {

        // todo: rewrite
//        config.setMaxElectionsTime(Duration.ofMillis(100));
//
//        TestItem main = new TestItem(true);
//        main.setExpiresAtPlusFive(false);
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 1500);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
//
//        assertEquals(main, node.getItem(main.getId()));
//        Thread.sleep(500);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
    }



    public Contract startSwap_wrongKey(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, PrivateKey wrongKey) {

        Set<PublicKey> fromPublicKeys = new HashSet<>();
        for (PrivateKey pk : fromKeys) {
            fromPublicKeys.add(pk.getPublicKey());
        }

        // first of all we creating main swap contract which will include new revisions of contract for swap
        // you can think about this contract as about transaction
        Contract swapContract = new Contract();

        Contract.Definition cd = swapContract.getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(swapContract.getCreatedAt().plusDays(30));

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
        }

        swapContract.registerRole(issuerRole);
        swapContract.registerRole((issuerRole).linkAs("owner"));
        swapContract.registerRole((issuerRole).linkAs("creator"));

        // now we will prepare new revisions of contracts

        // create new revisions of contracts and create transactional sections in it

        Contract newContract1 = contract1.createRevision(wrongKey);
        Contract.Transactional transactional1 = newContract1.createTransactionalSection();
        transactional1.setId(HashId.createRandom().toBase64String());

        Contract newContract2 = contract2.createRevision();
        Contract.Transactional transactional2 = newContract2.createTransactionalSection();
        transactional2.setId(HashId.createRandom().toBase64String());


        // prepare roles for references
        // it should new owners and old creators in new revisions of contracts

        SimpleRole ownerFrom = new SimpleRole("owner");
        SimpleRole creatorFrom = new SimpleRole("creator");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            ownerFrom.addKeyRecord(kr);
            creatorFrom.addKeyRecord(kr);
        }

        SimpleRole ownerTo = new SimpleRole("owner");
        SimpleRole creatorTo = new SimpleRole("creator");
        for (PublicKey k : toKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerTo.addKeyRecord(kr);
            creatorTo.addKeyRecord(kr);
        }


        // create references for contracts that point to each other and asks correct signs

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(ownerFrom);
        reference1.signed_by.add(creatorTo);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(ownerTo);
        reference2.signed_by.add(creatorFrom);

        // and add this references to existing transactional section
        transactional1.addReference(reference1);
        transactional2.addReference(reference2);


        // swap owners in this contracts
        newContract1.setOwnerKeys(toKeys);
        newContract2.setOwnerKeys(fromPublicKeys);

        newContract1.seal();
        newContract2.seal();

        // finally on this step add created new revisions to main swap contract
        swapContract.addNewItems(newContract1);
        swapContract.addNewItems(newContract2);
        swapContract.seal();

        return swapContract;
    }

    public Contract signPresentedSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : keys) {
            publicKeys.add(pk.getPublicKey());
        }

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own and sign it
        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(publicKeys);

            if(willBeMine) {
                c.addSignatureToSeal(wrongKey);
                contractHashId = c.getId();
            }
        }

        // looking for contract that was own, add to reference hash of above contract and sign it
        for (Contract c : swappingContracts) {
            boolean willBeNotMine = (!c.getOwner().isAllowedForKeys(publicKeys));

            if(willBeNotMine) {

                Set<KeyRecord> krs = new HashSet<>();
                for (PublicKey k: publicKeys) {
                    krs.add(new KeyRecord(k));
                }
                c.setCreator(krs);

                if(c.getTransactional() != null && c.getTransactional().getReferences() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swapContract;
                }

                c.seal();
                c.addSignatureToSeal(wrongKey);
            }
        }

        swapContract.seal();
        return swapContract;
    }

    public Contract finishSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);

            if(willBeMine) {
                c.addSignatureToSeal(wrongKey);
            }
        }

        swapContract.seal();
        swapContract.addSignatureToSeal(keys);

        return swapContract;
    }

}
