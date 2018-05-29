/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Leonid Novikov <flint.emerald@gmail.com>
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.db.Db;
import com.icodici.universa.*;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.*;
import com.icodici.universa.node.models.NameRecordModel;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Network;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


@Ignore("It is base test class for network tests, shouldn't be run")
public class BaseNetworkTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";
    protected static final String CONFIG_2_PATH = "./src/test_config_2/";
    protected static Contract tuContract = null;
    protected Network network = null;
    protected Node node = null;
    protected List<Node> nodes = null;
    protected Map<NodeInfo,Node> nodesMap = null;
    protected Ledger ledger = null;
    protected Config config = null;
    protected Object tuContractLock = new Object();



    public void init(Node node, List<Node> nodes, Map<NodeInfo,Node> nodesMap, Network network, Ledger ledger, Config config) throws Exception {
        this.node = node;
        this.nodes = nodes;
        this.nodesMap = nodesMap;
        this.network = network;
        this.ledger = ledger;
        this.config = config;
    }



    @Test(timeout = 900000)
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
                        ItemResult r = n.waitItem(ok.getId(), 18000);
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

                ItemResult r = node.waitItem(ok.getId(), 15000);
                assertEquals("after: In node "+node+" item "+ok.getId(), ItemState.APPROVED, r.state);

            }
//            });
        }
    }



    @Test(timeout = 900000)
    public void registerGoodParcel() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Thread.sleep(500);
//        LogPrinter.showDebug(true);
        int N = 100;
        for (int k = 0; k < 1; k++) {
            for (int i = 0; i < N; i++) {

                Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
                stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
                stepaCoins.seal();
                stepaCoins.check();
                stepaCoins.traceErrors();

                Parcel parcel = createParcelWithClassTU(stepaCoins, stepaPrivateKeys);

//                System.out.println("-------------- register parcel " + parcel.getId() + " (iteration " + i + ") ------------");
                node.registerParcel(parcel);
                synchronized (tuContractLock) {
                    tuContract = parcel.getPaymentContract();
                }
                for (Node n : nodesMap.values()) {
                    try {
//                        System.out.println("-------------- wait parcel " + parcel.getId() + " on the node " + n + " (iteration " + i + ") ------------");
                        n.waitParcel(parcel.getId(), 25000);
                        ItemResult r = n.waitItem(parcel.getPayloadContract().getId(), 8000);
                        int numIterations = 0;
                        while( !r.state.isConsensusFound()) {
                            System.out.println("wait for consensus receiving on the node " + n + " state is " + r.state);
                            Thread.sleep(500);
                            n.waitParcel(parcel.getId(), 25000);
                            r = n.waitItem(parcel.getPayloadContract().getId(), 8000);
                            numIterations++;
                            if(numIterations > 20)
                                break;
                        }
                        assertEquals("In node " + n + " parcel " + parcel.getId(), ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
//                        System.out.println(n.ping());
////                        System.out.println(n.traceTasksPool());
//                        System.out.println(n.traceParcelProcessors());
//                        System.out.println(n.traceItemProcessors());
                        fail("timeout, node " + n + " parcel " + parcel.getId() + " parcel " + parcel.getId() + " (iteration " + i + ")");
                    }
                }

                node.waitParcel(parcel.getId(), 25000);
                ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 8000);
                assertEquals("after: In node "+node+" item "+parcel.getId(), ItemState.APPROVED, r.state);
                System.out.println("-------------- parcel " + parcel.getId() + " registered (iteration " + i + ")------------");
//                Thread.sleep(5000);
//                System.out.println("-------------- parcel " + parcel.getId() + " wait finished (iteration " + i + ")------------");

            }
        }
    }



    @Test(timeout = 90000)
    public void registerBadItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        ItemResult r = node.waitItem(bad.getId(), 5000);
        assertEquals(ItemState.DECLINED, r.state);
    }



    @Test(timeout = 90000)
    public void checkItem() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 6000);
        node.waitItem(bad.getId(), 6000);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }



    @Test(timeout = 90000)
    public void shouldCreateItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 16000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);
    }



    @Test(timeout = 90000)
    public void shouldDeclineItems() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(false);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.DECLINED, result.state);
    }



    @Test(timeout = 90000)
    public void singleNodeMixApprovedAndDeclined() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);


        // Negative consensus
        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        ItemResult result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);
        result2 = node.waitItem(item2.getId(), 12000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.checkItem(item2.getId());
        assertEquals(ItemState.DECLINED, result2.state);
    }



    @Test(timeout = 90000)
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



    @Test(timeout = 90000)
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

        ItemResult itemResult = node.waitItem(main.getId(), 12500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test(timeout = 90000)
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

        System.out.println("-------------- register item " + main.getId() + " --------");

        node.registerItem(main);
        System.out.println("-------------- wait item " + main.getId() + " --------");
        ItemResult itemResult = node.waitItem(main.getId(), 15000);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test(timeout = 90000)
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

        ItemResult itemResult = node.waitItem(main.getId(), 12000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, itemNew2.state);

        LogPrinter.showDebug(false);
    }



    @Test(timeout = 90000)
    public void acceptWithReferences() throws Exception {
        return;
    }



    @Test(timeout = 35000)
    public void badReferencesDeclineListStates() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            Thread.sleep(300);

            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            TestItem main = new TestItem(true);

            TestItem existingItem1 = new TestItem(true);
            StateRecord existing1 = ledger.findOrCreate(existingItem1.getId());
            existing1.setState(ItemState.APPROVED).save();


            // but second is not good
            TestItem existingItem2 = new TestItem(false);
            StateRecord existing2 = ledger.findOrCreate(existingItem2.getId());
            existing2.setState(badState).save();

            main.addReferencedItems(existingItem1, existingItem2);

            Thread.sleep(300);

            // check that main is fully approved
            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 10000);
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



    @Test(timeout = 90000)
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
        ItemResult ir = node.waitItem(existing1.getId(), 6000);

        System.out.println("--------resister (good) item " + existing2.getId() + " ---------");
        node.registerItem(existing2);
        node.waitItem(existing2.getId(), 6000);

        main.addReferencedItems(existing1, existing2);
        main.addNewItems(new1, new2);

        System.out.println("--------resister (main) item " + main.getId() + " ---------");

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.waitItem(new1.getId(), 3000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(new2.getId(), 3000).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.waitItem(existing1.getId(), 3000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(existing2.getId(), 3000).state);
    }



    @Test(timeout = 90000)
    public void missingReferencesDecline() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }


        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 15000);

        // but second is missing
        TestItem missing = new TestItem(true);

        main.addReferencedItems(existing, missing);

        // check that main is declined
        System.out.println("--------- missind id: " + missing.getId());
        System.out.println("--------- existing id: " + existing.getId());
        node.registerItem(main);
        // need some time to resync missingId
        ItemResult itemResult = node.waitItem(main.getId(), 15000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        System.out.println(node.getItem(missing.getId()));

        assertNull(node.getItem(missing.getId()));
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



    @Test(timeout = 60000)
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

            Thread.sleep(300);
            TestItem main = new TestItem(true);
            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            Thread.sleep(300);

            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 15000);
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

    @Test(timeout = 90000)
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
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(new2_1.getId(), 6000);
        assertEquals(ItemState.APPROVED, result.state);
    }

    @Test(timeout = 90000)
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
        assertEquals(ItemState.APPROVED, node.waitItem(revoke1.getId(), 15000).state);

        node.registerItem(revoke2);
        assertEquals(ItemState.APPROVED, node.waitItem(revoke2.getId(), 15000).state);

        new1_1.addRevokingItems(revoke1);
        new2_1.addRevokingItems(revoke2);
        new1.addNewItems(new1_1);
        new2.addNewItems(new2_1);
        main.addNewItems(new1, new2);

        node.registerItem(main);
        ItemResult result = node.waitItem(main.getId(), 15000);
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

    @Test(timeout = 90000)
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
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(new2_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }

    @Test(timeout = 90000)
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
        ItemResult result = node.waitItem(main.getId(), 6000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(new1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(new1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
        result = node.waitItem(revoke1_1.getId(), 6000);
        assertEquals(ItemState.UNDEFINED, result.state);
    }


    @Test(timeout = 90000)
    public void createRealContract() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

//        LogPrinter.showDebug(true);
        registerAndCheckApproved(c);
    }

    @Test
    public void createNotaryContract() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Contract c = Contract.fromDslFile(ROOT_PATH + "notary_4096.yaml");
        c.addSignerKeyFromFile(ROOT_PATH + "keys/romanuskov_4096.private.unikey");
        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());

        registerAndCheckApproved(c);
    }

    @Test(timeout = 90000)
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

            Parcel parcel = registerWithNewParcel(contract);
            node.waitParcel(parcel.getId(), 8000);
            ItemResult itemResult = node.waitItem(parcel.getPayloadContract().getId(), 3000);
            if (ItemState.APPROVED != itemResult.state)
                fail("Wrong state on repetition " + i + ": " + itemResult + ", " + itemResult.errors +
                        " \r\ncontract_errors: " + contract.getErrors());

            assertEquals(ItemState.APPROVED, itemResult.state);
        }
    }


    // split and join section

    @Test(timeout = 90000)
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
        Contract c1 = ContractsService.createSplit(c, "30", "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
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

    @Test(timeout = 90000)
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
        Contract c1 = ContractsService.createSplit(c, "550", "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);
        assertEquals("-450", c1.getStateData().get("amount").toString());
        assertEquals("550", c2.getStateData().get("amount").toString());

        registerAndCheckDeclined(c1);

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 5000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(c1.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(c2.getId(), 5000).state);
    }

    @Test(timeout = 90000)
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
        Contract c1 = ContractsService.createSplit(c, "30", "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
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

    @Test(timeout = 90000)
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
        Contract c1 = ContractsService.createSplit(c, "30", "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
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

    @Test(timeout = 90000)
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

    @Test(timeout = 90000)
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
        Contract c1 = ContractsService.createSplit(c, "30", "amount", new HashSet<PrivateKey>(Arrays.asList(key)));
        Contract c2 = c1.getNew().get(0);

        assertEquals("70", c1.getStateData().get("amount").toString());
        assertEquals("30", c2.getStateData().get("amount").toString());

        node.registerItem(c1);
        ItemResult itemResult = node.waitItem(c1.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }


    @Test(timeout = 90000)
    public void shouldBreakByQuantizerDeepTree() throws Exception {
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

        Contract swapContract;

        // swap contract has two contracts in new items, and each of them has one in own revoking
        // so we have contracts tree with 3 levels
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());

        // Check 4096 bits signature container contract (8) +
        // register version container contract (20) +

        // Check 2048 bits marty signature swap contract (1) +
        // register version swap contract (20) +

        // Check 2048 bits marty signature new delorean (1) +
        // Check 2048 bits stepa signature new delorean (1) +
        // register version new delorean contract (20) +
        // Check 4096 bits signature revoking old delorean (8) +
        // revoke version old delorean contract (20) +
        // Check reference new delorean (1) +
        // Check owner permission new delorean (1) +
        // Check revoke permission old delorean (1) +

        // Check 2048 bits stepa signature new lamborghini (1) +
        // Check 2048 bits marty signature new lamborghini (1) +
        // register version new lamborghini contract (20) +
        // Check 4096 bits signature revoking old lamborghini (8) +
        // revoke version old lamborghini contract (20) +
        // Check reference new lamborghini (1) +
        // Check owner permission new lamborghini (1) +
        // Check revoke permission old lamborghini (1) +
        Contract.setTestQuantaLimit(154);
        swapContract = imitateSendingTransactionToPartner(swapContract);
        Contract container = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        container.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        container.addNewItems(swapContract);
        container.seal();
        node.registerItem(container);

        ItemResult result = node.waitItem(container.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        result = node.waitItem(swapContract.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, result.state);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);

        Contract.setTestQuantaLimit(-1);
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

    private void checkCreateTwoSignedContractAllGood(boolean newRev) throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        System.out.println("Base contract contract is valid: " + baseContract.isOk());

        if (newRev)
            registerAndCheckApproved(baseContract);

        Contract twoSignContract = ContractsService.createTwoSignedContract(baseContract, martyPrivateKeys, stepaPublicKeys, newRev);

        twoSignContract = imitateSendingTransactionToPartner(twoSignContract);

        twoSignContract.addSignatureToSeal(stepaPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        registerAndCheckDeclined(twoSignContract);

        twoSignContract = imitateSendingTransactionToPartner(twoSignContract);

        twoSignContract.addSignatureToSeal(martyPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        System.out.println("Contract with two signature is valid: " + twoSignContract.isOk());
        registerAndCheckApproved(twoSignContract);
    }

    @Test(timeout = 90000)
    public void createTwoSignedContractAllGood() throws Exception {
        checkCreateTwoSignedContractAllGood(false);
        checkCreateTwoSignedContractAllGood(true);
    }

    @Test(timeout = 90000)
    public void createTokenContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract tokenContract = ContractsService.createTokenContract(martyPrivateKeys,stepaPublicKeys, "100000000000");

        tokenContract.check();
        tokenContract.traceErrors();
        registerAndCheckApproved(tokenContract);
    }

    @Test(timeout = 90000)
    public void createTokenContractWithEmission() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Contract tokenContract = ContractsService.createTokenContractWithEmission(martyPrivateKeys, stepaPublicKeys, "300000000000");

        tokenContract.check();
        tokenContract.traceErrors();
        registerAndCheckApproved(tokenContract);

        Contract emittedContract = ContractsService.createTokenEmission(tokenContract, "100000000000", martyPrivateKeys);

        emittedContract.check();
        emittedContract.traceErrors();
        registerAndCheckApproved(emittedContract);

        assertEquals(emittedContract.getStateData().getString("amount"), "400000000000");
        assertEquals(ItemState.REVOKED, node.waitItem(tokenContract.getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void createTokenContractWithEmissionBadSignature() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Contract tokenContract = ContractsService.createTokenContractWithEmission(martyPrivateKeys, stepaPublicKeys, "300000000000");

        tokenContract.check();
        tokenContract.traceErrors();
        registerAndCheckApproved(tokenContract);

        // with bad signature
        Contract emittedContract = ContractsService.createTokenEmission(tokenContract, "100000000000", stepaPrivateKeys);

        Set<KeyRecord> krs = new HashSet<>();
        for (PublicKey k: stepaPublicKeys)
            krs.add(new KeyRecord(k));
        emittedContract.setCreator(krs);

        emittedContract.check();
        emittedContract.traceErrors();
        registerAndCheckDeclined(emittedContract);

        assertEquals(ItemState.APPROVED, node.waitItem(tokenContract.getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void createShareContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract shareContract = ContractsService.createShareContract(martyPrivateKeys,stepaPublicKeys,"100");

        shareContract.check();
        shareContract.traceErrors();
        registerAndCheckApproved(shareContract);

    }

    @Test(timeout = 90000)
    public void createNotaryContractAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        Contract notaryContract = ContractsService.createNotaryContract(martyPrivateKeys, stepaPublicKeys);

        notaryContract.check();
        notaryContract.traceErrors();
        registerAndCheckApproved(notaryContract);

    }


    @Test(timeout = 90000)
    public void changeOwnerWithAnonId() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        AnonymousId stepaAnonId = AnonymousId.fromBytes(stepaPublicKeys.iterator().next().createAnonymousId());
        Contract anonOwnerContract = c1.createRevisionAnonymously(Arrays.asList(key));
        anonOwnerContract.addSignerKey(key);
        anonOwnerContract.setOwnerKey(stepaAnonId);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getAnonymousIds().iterator().next().equals(stepaAnonId));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevisionAnonymously(stepaPrivateKeys);
        anonSignedContract.addSignerKeys(stepaPrivateKeys);
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getAnonymousIds().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAnonId2() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        AnonymousId stepaAnonId = AnonymousId.fromBytes(stepaPublicKeys.iterator().next().createAnonymousId());
        Contract anonOwnerContract = c1.createRevision(key);
        anonOwnerContract.setOwnerKey(stepaAnonId);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getAnonymousIds().iterator().next().equals(stepaAnonId));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevision();
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.setCreatorKeys(stepaAnonId);
        anonSignedContract.addSignerKey(stepaPrivateKeys.iterator().next());
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getAnonymousIds().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAddress() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        KeyAddress stepaAddress = stepaPublicKeys.iterator().next().getShortAddress();
        Contract anonOwnerContract = c1.createRevisionWithAddress(Arrays.asList(key));
        anonOwnerContract.addSignerKey(key);
        anonOwnerContract.setOwnerKey(stepaAddress);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();

        Contract anonAfterSend = imitateSendingTransactionToPartner(anonOwnerContract);

        registerAndCheckApproved(anonAfterSend);

        assertTrue(anonAfterSend.getOwner().getKeyAddresses().iterator().next().equals(stepaAddress));
        assertEquals(0, anonAfterSend.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonAfterSend.createRevisionWithAddress(stepaPrivateKeys);
        anonSignedContract.addSignerKeys(stepaPrivateKeys);
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getKeyAddresses().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
        assertTrue(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }

    @Test(timeout = 90000)
    public void changeOwnerWithAddress2() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        c1.seal();
        c1.check();
        c1.traceErrors();
        registerAndCheckApproved(c1);

        //

        KeyAddress stepaAddress = stepaPublicKeys.iterator().next().getLongAddress();
        Contract anonOwnerContract = c1.createRevision(key);
        anonOwnerContract.setOwnerKey(stepaAddress);
        anonOwnerContract.seal();
        anonOwnerContract.check();
        anonOwnerContract.traceErrors();
        registerAndCheckApproved(anonOwnerContract);

        assertTrue(anonOwnerContract.getOwner().getKeyAddresses().iterator().next().equals(stepaAddress));
        assertEquals(0, anonOwnerContract.getOwner().getKeys().size());

        //

        Contract anonSignedContract = anonOwnerContract.createRevision();
        anonSignedContract.setOwnerKeys(martyPublicKeys);
        anonSignedContract.setCreatorKeys(stepaAddress);
        anonSignedContract.addSignerKey(stepaPrivateKeys.iterator().next());
        anonSignedContract.seal();
        anonSignedContract.check();
        anonSignedContract.traceErrors();

        Contract afterSend = imitateSendingTransactionToPartner(anonSignedContract);

        registerAndCheckApproved(afterSend);

        assertEquals(0, afterSend.getOwner().getKeyAddresses().size());
        assertTrue(afterSend.getOwner().isAllowedForKeys(martyPublicKeys));

        Contract anonPublishedContract = new Contract(anonSignedContract.getLastSealedBinary());
        ItemResult itemResult = node.waitItem(anonPublishedContract.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertFalse(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
        assertTrue(anonPublishedContract.getSealedByKeys().contains(stepaPublicKeys.iterator().next()));
    }


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
    public void swapManyContractsViaTransactionAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean1 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract delorean2 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract delorean3 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        List<Contract> deloreans = new ArrayList<>();
        deloreans.add(delorean1);
        deloreans.add(delorean2);
        deloreans.add(delorean3);
        Contract lamborghini1 = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        Contract lamborghini2 = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        List<Contract> lamborghinis = new ArrayList<>();
        lamborghinis.add(lamborghini1);
        lamborghinis.add(lamborghini2);

        // ----- prepare contracts -----------

        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        for(Contract d : deloreans) {
            d.addSignerKey(manufacturePrivateKey);
            d.seal();
            registerAndCheckApproved(d);
        }

        for(Contract l : lamborghinis) {
            l.addSignerKey(manufacturePrivateKey);
            l.seal();
            registerAndCheckApproved(l);
        }

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(deloreans, lamborghinis, martyPrivateKeys, stepaPublicKeys);

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
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk() + " num new contracts: " + swapContract.getNewItems().size());
        registerAndCheckApproved(swapContract);

        for(Contract d : deloreans) {
            assertEquals(ItemState.REVOKED, node.waitItem(d.getId(), 5000).state);
            System.out.println("delorean is " + node.waitItem(d.getId(), 5000).state);
        }
        for(Contract l : lamborghinis) {
            assertEquals(ItemState.REVOKED, node.waitItem(l.getId(), 5000).state);
            System.out.println("lamborghini is " + node.waitItem(l.getId(), 5000).state);
        }

        for(Approvable a : swapContract.getNewItems()) {
            assertEquals(ItemState.APPROVED, node.waitItem(a.getId(), 5000).state);
            System.out.println("new is " + node.waitItem(a.getId(), 5000).state);
        }

//        checkSwapResultSuccess(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
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
        newDelorean.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
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
        newDelorean.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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



    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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
        newLamborghini.seal();

        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
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
        newDelorean.seal();
        newLamborghini.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 90000)
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
        newDelorean.seal();
        newLamborghini.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckDeclined(swapContract);

        checkSwapResultDeclined(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }


    @Test(timeout = 60000)
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
        System.out.println(newDelorean.getReferences().values().iterator().next().transactional_id);
        System.out.println(newLamborghini.getReferences().values().iterator().next().transactional_id);

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


    @Test(timeout = 90000)
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


    @Test(timeout = 90000)
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


    @Test(timeout = 60000)
    public void swapSplitJoinAllGood() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract martyCoins = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        martyCoins.addSignerKey(martyPrivateKeys.iterator().next());
        martyCoins.seal();
        martyCoins.check();
        martyCoins.traceErrors();
        registerAndCheckApproved(martyCoins);

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();
        registerAndCheckApproved(stepaCoins);

        System.out.println("--- coins created ---");


        // 100 - 30 = 70
        Contract martyCoinsSplit = ContractsService.createSplit(martyCoins, "30", "amount", martyPrivateKeys);
        Contract martyCoinsSplitToStepa = martyCoinsSplit.getNew().get(0);
        Contract stepaCoinsSplit = ContractsService.createSplit(stepaCoins, "30", "amount", stepaPrivateKeys);
        Contract stepaCoinsSplitToMarty = stepaCoinsSplit.getNew().get(0);

        martyCoinsSplitToStepa.check();
        martyCoinsSplitToStepa.traceErrors();
        stepaCoinsSplitToMarty.check();
        stepaCoinsSplitToMarty.traceErrors();

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;
        swapContract = ContractsService.startSwap(martyCoinsSplitToStepa, stepaCoinsSplitToMarty, martyPrivateKeys, stepaPublicKeys, false);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        martyCoinsSplit.seal();
        stepaCoinsSplit.seal();
        swapContract.getNewItems().clear();
        swapContract.addNewItems(martyCoinsSplit, stepaCoinsSplit);
        swapContract.seal();
        swapContract.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckApproved(swapContract);


        assertEquals(ItemState.APPROVED, node.waitItem(martyCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(martyCoinsSplitToStepa.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoinsSplitToMarty.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(martyCoins.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(stepaCoins.getId(), 5000).state);
    }


    @Test(timeout = 60000)
    public void swapSplitJoinAllGood_api2() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> user1PrivKeySet = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PrivateKey> user2PrivKeySet = new HashSet<>(Arrays.asList(TestKeys.privateKey(2)));
        Set<PublicKey> user1PubKeySet = user1PrivKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());
        Set<PublicKey> user2PubKeySet = user2PrivKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());

        Contract contractTOK92 = ContractsService.createTokenContract(user1PrivKeySet, user1PubKeySet, "100", 0.0001);
        Contract contractTOK93 = ContractsService.createTokenContract(user2PrivKeySet, user2PubKeySet, "100", 0.001);
        contractTOK92.setApiLevel(2);
        contractTOK93.setApiLevel(2);

        contractTOK92.seal();
        contractTOK92.check();
        contractTOK92.traceErrors();
        registerAndCheckApproved(contractTOK92);

        contractTOK93.seal();
        contractTOK93.check();
        contractTOK93.traceErrors();
        registerAndCheckApproved(contractTOK93);

        System.out.println("--- coins created ---");

        // TOK92: 100 - 8.02 = 91.98
        Contract user1CoinsSplit = ContractsService.createSplit(contractTOK92, "8.02", "amount", user1PrivKeySet);
        Contract user1CoinsSplitToUser2 = user1CoinsSplit.getNew().get(0);
        // TOK93: 100 - 10.01 = 89.99
        Contract user2CoinsSplit = ContractsService.createSplit(contractTOK93, "10.01", "amount", user2PrivKeySet);
        Contract user2CoinsSplitToUser1 = user2CoinsSplit.getNew().get(0);

        user1CoinsSplitToUser2.check();
        user1CoinsSplitToUser2.traceErrors();
        user2CoinsSplitToUser1.check();
        user2CoinsSplitToUser1.traceErrors();

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;
        swapContract = ContractsService.startSwap(user1CoinsSplitToUser2, user2CoinsSplitToUser1, user1PrivKeySet, user2PubKeySet, false);
        ContractsService.signPresentedSwap(swapContract, user2PrivKeySet);
        ContractsService.finishSwap(swapContract, user1PrivKeySet);

        user1CoinsSplit.seal();
        user2CoinsSplit.seal();
        swapContract.getNewItems().clear();
        swapContract.addNewItems(user1CoinsSplit, user2CoinsSplit);
        swapContract.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());

        //now emulate sending transaction pack through network
        swapContract = Contract.fromPackedTransaction(swapContract.getPackedTransaction());

        node.registerItem(swapContract);
        assertEquals(ItemState.APPROVED, node.waitItem(swapContract.getId(), 5000).state);

        assertEquals(ItemState.APPROVED, node.waitItem(user1CoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(user2CoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(user1CoinsSplitToUser2.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(user2CoinsSplitToUser1.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(contractTOK92.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(contractTOK93.getId(), 5000).state);
        assertEquals("8.02", user1CoinsSplitToUser2.getStateData().getStringOrThrow("amount"));
        assertEquals("10.01", user2CoinsSplitToUser1.getStateData().getStringOrThrow("amount"));
        assertFalse(user1CoinsSplitToUser2.getOwner().isAllowedForKeys(user1PubKeySet));
        assertTrue(user1CoinsSplitToUser2.getOwner().isAllowedForKeys(user2PubKeySet));
        assertTrue(user2CoinsSplitToUser1.getOwner().isAllowedForKeys(user1PubKeySet));
        assertFalse(user2CoinsSplitToUser1.getOwner().isAllowedForKeys(user2PubKeySet));
    }


    @Test(timeout = 90000)
    public void swapSplitJoinMissingSign() throws Exception {
        if(node == null) {
            System.out.println("network not inited");
            return;
        }

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract martyCoins = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        martyCoins.addSignerKey(martyPrivateKeys.iterator().next());
        martyCoins.seal();
        martyCoins.check();
        martyCoins.traceErrors();
        registerAndCheckApproved(martyCoins);

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();
        registerAndCheckApproved(stepaCoins);

        System.out.println("--- coins created ---");


        // 100 - 30 = 70
        Contract martyCoinsSplit = ContractsService.createSplit(martyCoins, "30", "amount", martyPrivateKeys);
        // remove sign!!
        martyCoinsSplit.getKeysToSignWith().clear();
        martyCoinsSplit.removeAllSignatures();
        martyCoinsSplit.seal();
        Contract martyCoinsSplitToStepa = martyCoinsSplit.getNew().get(0);
        Contract stepaCoinsSplit = ContractsService.createSplit(stepaCoins, "30", "amount", stepaPrivateKeys);
        Contract stepaCoinsSplitToMarty = stepaCoinsSplit.getNew().get(0);

        martyCoinsSplit.check();
        martyCoinsSplit.traceErrors();
        martyCoinsSplitToStepa.check();
        martyCoinsSplitToStepa.traceErrors();
        stepaCoinsSplitToMarty.check();
        stepaCoinsSplitToMarty.traceErrors();

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;
        swapContract = ContractsService.startSwap(martyCoinsSplitToStepa, stepaCoinsSplitToMarty, martyPrivateKeys, stepaPublicKeys, false);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.getNewItems().clear();
        swapContract.addNewItems(martyCoinsSplit, stepaCoinsSplit);
        swapContract.seal();
        swapContract.addSignatureToSeal(martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());
        registerAndCheckDeclined(swapContract);


        assertEquals(ItemState.UNDEFINED, node.waitItem(martyCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(stepaCoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(martyCoinsSplitToStepa.getId(), 5000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(stepaCoinsSplitToMarty.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(martyCoins.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoins.getId(), 5000).state);
    }



    @Test(timeout = 90000)
    public void registerParcel() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);

        parcel.getPayment().getContract().check();
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void registerPayingParcelGood() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys.iterator().next());
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.seal();

        Contract secondPayment = paymentDecreased.createRevision(stepaPrivateKeys.iterator().next());
        secondPayment.getStateData().set("transaction_units", paymentDecreased.getStateData().getIntOrThrow("transaction_units") - 3);
        secondPayment.seal();

        stepaCoins.addNewItems(secondPayment);
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        secondPayment.check();
        secondPayment.traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();
        parcel.getPayment().getContract().check();
        parcel.getPayment().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(secondPayment.isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 1, paymentDecreased.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 4, secondPayment.getStateData().getIntOrThrow("transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(secondPayment.getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void registerPayingParcelBadFirstPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys.iterator().next());
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.getDefinition().getData().set("wrong_value", false);
        paymentDecreased.seal();

        Contract secondPayment = paymentDecreased.createRevision(stepaPrivateKeys.iterator().next());
        secondPayment.getStateData().set("transaction_units", paymentDecreased.getStateData().getIntOrThrow("transaction_units") - 3);
        secondPayment.seal();

        stepaCoins.addNewItems(secondPayment);
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        parcel.getPayloadContract().check();
        parcel.getPayloadContract().traceErrors();
        secondPayment.check();
        secondPayment.traceErrors();
        parcel.getPaymentContract().check();
        parcel.getPaymentContract().traceErrors();

        assertFalse(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(secondPayment.isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 1, paymentDecreased.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 4, secondPayment.getStateData().getIntOrThrow("transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(secondPayment.getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void registerPayingParcelBadSecondPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys.iterator().next());
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.seal();

        Contract secondPayment = paymentDecreased.createRevision(stepaPrivateKeys.iterator().next());
        secondPayment.getStateData().set("transaction_units", paymentDecreased.getStateData().getIntOrThrow("transaction_units") - 3);
        secondPayment.getDefinition().getData().set("wrong_value", false);
        secondPayment.seal();

        stepaCoins.addNewItems(secondPayment);
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        parcel.getPayloadContract().check();
        parcel.getPayloadContract().traceErrors();
//        secondPayment.check();
        secondPayment.traceErrors();
        parcel.getPaymentContract().check();
        parcel.getPaymentContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPayloadContract().isOk());
        assertFalse(secondPayment.isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 1, paymentDecreased.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 4, secondPayment.getStateData().getIntOrThrow("transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(secondPayment.getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void registerPayingParcelBadPayload() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys.iterator().next());
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.seal();

        Contract secondPayment = paymentDecreased.createRevision(stepaPrivateKeys.iterator().next());
        secondPayment.getStateData().set("transaction_units", paymentDecreased.getStateData().getIntOrThrow("transaction_units") - 3);
        secondPayment.seal();

        stepaCoins.addNewItems(secondPayment);
        Contract.Definition cd = stepaCoins.getDefinition();
        cd.setExpiresAt(stepaCoins.getCreatedAt().minusDays(1));
        Contract.State cs = stepaCoins.getState();
        cs.setExpiresAt(stepaCoins.getCreatedAt().minusDays(1));
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        parcel.getPayloadContract().check();
        parcel.getPayloadContract().traceErrors();
        secondPayment.check();
        secondPayment.traceErrors();
        parcel.getPaymentContract().check();
        parcel.getPaymentContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(secondPayment.isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 1, paymentDecreased.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 4, secondPayment.getStateData().getIntOrThrow("transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(secondPayment.getId(), 8000).state);
    }


    @Test(timeout = 90000)
    public void registerPayingParcelGoodWithContractService() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys);
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createPayingParcel(stepaCoins.getTransactionPack(), stepaTU, 3, 7, stepaPrivateKeys, false);

        stepaCoins.getNew().get(0).check();
        stepaCoins.getNew().get(0).traceErrors();
        parcel.getPaymentContract().check();
        parcel.getPaymentContract().traceErrors();
        parcel.getPayloadContract().check();
        parcel.getPayloadContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(stepaCoins.getNew().get(0).isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("transaction_units") - 10, stepaCoins.getNew().get(0).getStateData().getIntOrThrow("transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoins.getNew().get(0).getId(), 8000).state);
    }


    @Test(timeout = 90000)
    public void registerPayingParcelGoodWithContractServiceWithTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(stepaCoins.getCreatedAt().plusMonths(6));
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, stepaPublicKeys, true);
        stepaTU.setExpiresAt(stepaTU.getCreatedAt().plusMonths(6));
        stepaTU.seal();
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createPayingParcel(stepaCoins.getTransactionPack(), stepaTU, 20, 60, stepaPrivateKeys, true);

        stepaCoins.getNew().get(0).check();
        stepaCoins.getNew().get(0).traceErrors();
        parcel.getPaymentContract().check();
        parcel.getPaymentContract().traceErrors();
        parcel.getPayloadContract().check();
        parcel.getPayloadContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(stepaCoins.getNew().get(0).isOk());

        assertEquals(stepaTU.getStateData().getIntOrThrow("test_transaction_units") - 80, stepaCoins.getNew().get(0).getStateData().getIntOrThrow("test_transaction_units"));

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        parcel.getPaymentContract().traceErrors();
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(stepaCoins.getNew().get(0).getId(), 8000).state);
    }


    @Test(timeout = 90000)
    public void imNotGonnaPayForIt() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract paidContract = new Contract(stepaPrivateKeys.iterator().next());
        paidContract.seal();
        paidContract.check();
        paidContract.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(paidContract, stepaTU, 1, stepaPrivateKeys, false);
        Contract payment = parcel.getPaymentContract();
        Contract payload = parcel.getPayloadContract();

        Contract imNotGonnaPayForIt = new Contract(TestKeys.privateKey(5));

        payment.addNewItems(imNotGonnaPayForIt);
        payment.seal();
        parcel = new Parcel(payload.getTransactionPack(),payment.getTransactionPack());

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();



        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        // check payment and payload contracts
        assertNotEquals(ItemState.APPROVED, node.waitItem(imNotGonnaPayForIt.getId(), 8000).state);

    }



    @Test(timeout = 90000)
    public void registerParcelWithRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1 , parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        //int todayPaidAmount = node.nodeStats.todayPaidAmount;//        reuse payment for another contract
        Contract contract = new Contract(TestKeys.privateKey(12));
        contract.seal();
        Parcel parcel2 = new Parcel(contract.getTransactionPack(), parcel.getPayment());
        node.registerParcel(parcel);
        node.registerParcel(parcel2);
        node.waitParcel(parcel.getId(), 8000);

//        node.nodeStats.collect(ledger,config);
//        assertEquals(node.nodeStats.todayPaidAmount-todayPaidAmount,1);


        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        assertTrue(!node.checkItem(parcel.getPayload().getContract().getId()).isTestnet);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?",parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?",parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

            }
        }
    }

    @Test(timeout = 90000)
    public void registerParcelWithTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));


        node.nodeStats.collect(ledger,config);
        //int todayPaidAmount = node.nodeStats.todayPaidAmount;
//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);

        node.nodeStats.collect(ledger,config);
        //assertEquals(node.nodeStats.todayPaidAmount-todayPaidAmount,0);

        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        assertTrue(node.checkItem(parcel.getPayload().getContract().getId()).isTestnet);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?",parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?",parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

            }
        }
    }

    @Test(timeout = 90000)
    public void registerParcelWithTestAndRealRevisionPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Contract subContract = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        subContract.addSignerKey(martyPrivateKeys.iterator().next());
        subContract.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        subContract.seal();
        subContract.check();
        subContract.traceErrors();

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));

        stepaCoins.getTransactionPack().addReferencedItem(subContract);
        stepaCoins.addNewItems(subContract);

        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        Contract subItemPayload = (Contract) parcel.getPayloadContract().getNewItems().iterator().next();
        subItemPayload.check();
        subItemPayload.traceErrors();
        assertTrue(subItemPayload.isOk());
        assertTrue(subItemPayload.isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload subitem contract: " + subItemPayload.getId() + " is TU: " + subItemPayload.isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        ItemResult itemResultPayment = node.waitItem(parcel.getPayment().getContract().getId(), 8000);
        ItemResult itemResultPayload = node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        ItemResult itemResultSubItem = node.waitItem(subItemPayload.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResultPayment.state);
        assertEquals(ItemState.APPROVED, itemResultPayload.state);
        assertEquals(ItemState.APPROVED, itemResultSubItem.state);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", subItemPayload.getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }
            }
        }

        stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        subContract = subContract.createRevision();
        subContract.addSignerKey(martyPrivateKeys.iterator().next());
        subContract.setOwnerKey(stepaPrivateKeys.iterator().next());
        subContract.seal();
        subContract.check();
        subContract.traceErrors();

        stepaCoins = stepaCoins.createRevision();
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setOwnerKey(martyPrivateKeys.iterator().next());

        stepaCoins.getTransactionPack().addReferencedItem(subContract);
        stepaCoins.addNewItems(subContract);

        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1 , parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        subItemPayload = (Contract) parcel.getPayloadContract().getNewItems().iterator().next();
        subItemPayload.check();
        subItemPayload.traceErrors();
        assertTrue(subItemPayload.isOk());
        assertFalse(subItemPayload.isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload subitem contract: " + subItemPayload.getId() + " is TU: " + subItemPayload.isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        itemResultPayment = node.waitItem(parcel.getPayment().getContract().getId(), 8000);
        itemResultPayload = node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        itemResultSubItem = node.waitItem(subItemPayload.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResultPayment.state);
        assertEquals(ItemState.APPROVED, itemResultPayload.state);
        assertEquals(ItemState.APPROVED, itemResultSubItem.state);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", subItemPayload.getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }
            }
        }
    }

    @Test(timeout = 90000)
    public void registerParcelWithRealAndTestRevisionPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        martyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Contract subContract = Contract.fromDslFile(ROOT_PATH + "martyCoins.yml");
        subContract.addSignerKey(martyPrivateKeys.iterator().next());
        subContract.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        subContract.seal();
        subContract.check();
        subContract.traceErrors();

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));

        stepaCoins.getTransactionPack().addReferencedItem(subContract);
        stepaCoins.addNewItems(subContract);

        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        Contract subItemPayload = (Contract) parcel.getPayloadContract().getNewItems().iterator().next();
        subItemPayload.check();
        subItemPayload.traceErrors();
        assertTrue(subItemPayload.isOk());
        assertFalse(subItemPayload.isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload subitem contract: " + subItemPayload.getId() + " is TU: " + subItemPayload.isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        ItemResult itemResultPayment = node.waitItem(parcel.getPayment().getContract().getId(), 8000);
        ItemResult itemResultPayload = node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        ItemResult itemResultSubItem = node.waitItem(subItemPayload.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResultPayment.state);
        assertEquals(ItemState.APPROVED, itemResultPayload.state);
        assertEquals(ItemState.APPROVED, itemResultSubItem.state);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", subItemPayload.getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 0);
                    }
                }
            }
        }

        stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        subContract = subContract.createRevision();
        subContract.addSignerKey(martyPrivateKeys.iterator().next());
        subContract.setOwnerKey(stepaPrivateKeys.iterator().next());
        subContract.seal();
        subContract.check();
        subContract.traceErrors();

        stepaCoins = stepaCoins.createRevision();
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setOwnerKey(martyPrivateKeys.iterator().next());

        stepaCoins.getTransactionPack().addReferencedItem(subContract);
        stepaCoins.addNewItems(subContract);

        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        subItemPayload = (Contract) parcel.getPayloadContract().getNewItems().iterator().next();
        subItemPayload.check();
        subItemPayload.traceErrors();
        assertTrue(subItemPayload.isOk());
        assertTrue(subItemPayload.isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload subitem contract: " + subItemPayload.getId() + " is TU: " + subItemPayload.isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        itemResultPayment = node.waitItem(parcel.getPayment().getContract().getId(), 8000);
        itemResultPayload = node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        itemResultSubItem = node.waitItem(subItemPayload.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResultPayment.state);
        assertEquals(ItemState.APPROVED, itemResultPayload.state);
        assertEquals(ItemState.APPROVED, itemResultSubItem.state);

        if(ledger instanceof PostgresLedger) {
            PostgresLedger pl = (PostgresLedger) ledger;

            try(Db db = pl.getDb()) {
                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPayloadContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", parcel.getPaymentContract().getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }

                try(PreparedStatement ps =  db.statement("select count(*) from ledger_testrecords where hash = ?", subItemPayload.getId().getDigest());) {
                    try(ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rs.getInt(1), 1);
                    }
                }
            }
        }
    }

//    @Test(timeout = 90000)
    public void declineParcelWithTooBigCostTUTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        PrivateKey stepaPrivateKey = stepaPrivateKeys.iterator().next();
        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKey);

        for (int i = 0; i < 100; i++) {
            Contract newItem = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
            newItem.setExpiresAt(ZonedDateTime.now().plusMonths(1));
            newItem.addSignerKey(stepaPrivateKey);
            stepaCoins.addNewItems(newItem);
        }
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        int processedCost = stepaCoins.getProcessedCostTU();
        System.out.println("stepaCoins processed cost in TU: " + processedCost);
        assertTrue(processedCost > Config.maxCostTUInTestMode);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, processedCost, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - processedCost, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 18000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 18000).state);
    }

    @Test(timeout = 90000)
    public void declineParcelWithTestPaymentBut4096Key() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        PrivateKey coinsKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract coins = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        coins.addSignerKey(coinsKey);
        coins.seal();
        coins.check();
        coins.traceErrors();

        assertFalse(coinsKey.getPublicKey().getBitStrength() == 2048);

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(coins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void declineParcelWithTestPaymentButTooFatherExpiration() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusYears(2));
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        assertTrue(stepaCoins.getExpiresAt().isAfter(ZonedDateTime.now().plusYears(1)));

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertFalse(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithTestAndRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Contract stepaTU = InnerContractsService.createFreshTU(100, stepaPublicKeys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract paymentDecreased = stepaTU.createRevision(stepaPrivateKeys);

        paymentDecreased.getStateData().set("test_transaction_units", stepaTU.getStateData().getIntOrThrow("test_transaction_units") - 1);
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 1);
        paymentDecreased.seal();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertFalse(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }


    @Test(timeout = 90000)
    public void registerParcelWithTestTUButRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100, keys, true);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 1, stepaPrivateKeys);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertEquals(100 - 1, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        System.out.println("Parcel: " + parcel.getId());
        System.out.println("Payment contract: " + parcel.getPaymentContract().getId() + " is TU: " + parcel.getPaymentContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));
        System.out.println("Payload contract: " + parcel.getPayloadContract().getId() + " is TU: " + parcel.getPayloadContract().isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName()));

//        LogPrinter.showDebug(true);
        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithBadPayload() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
//        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithNotRegisteredPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract stepaTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        stepaTU.addSignerKey(manufacturePrivateKey);
        stepaTU.seal();
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
//        registerAndCheckApproved(stepaTU);

        Parcel parcel = ContractsService.createParcel(stepaCoins, stepaTU, 50, stepaPrivateKeys);

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }



    @Test(timeout = 90000)
    public void declineParcelWithNotSignedPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }


        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/tu_key.private.unikey"));
        Contract stepaTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        stepaTU.addSignerKey(manufacturePrivateKey);
        stepaTU.seal();
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);
//        registerAndCheckApproved(stepaTU);

        Contract paymentDecreased = stepaTU.createRevision();
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 50);

        //paymentDecreased.setIsTU(true);
        paymentDecreased.seal();
        paymentDecreased.check();
        paymentDecreased.traceErrors();

        Parcel parcel = new Parcel(stepaCoins.getTransactionPack(), paymentDecreased.getTransactionPack());

        assertFalse(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        // check parcel
        node.waitParcel(parcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPaymentContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayloadContract().getId(), 8000).state);
    }



    @Ignore("removed functionality")
    @Test(timeout = 90000)
    public void declineItemFromoutWhiteList() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        node.registerItem(stepaCoins);
        ItemResult itemResult = node.waitItem(stepaCoins.getId(), 18000);
        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test(timeout = 30000)
    public void referenceForChangeOwner() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        TransactionPack tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        tp_before = llcProperty2.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void referenceForRevoke() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        TransactionPack tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = ContractsService.createRevocation(llcProperty, stepaPrivateKeys.iterator().next());
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());


        tp_before = llcProperty2.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void referenceForSplitJoin() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "TokenWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        TransactionPack tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = ContractsService.createSplit(llcProperty, "100",
                "amount", stepaPrivateKeys, true);
//        llcProperty2.createRole("creator", llcProperty2.getRole("owner"));
//        llcProperty2.getNew().get(0).createRole("creator", llcProperty2.getNew().get(0).getRole("owner"));
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        tp_before = llcProperty2.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test//(timeout = 30000)
    public void referenceForChangeNumber() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "AbonementWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        TransactionPack tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.getStateData().set("units",
                llcProperty.getStateData().getIntOrThrow("units") - 1);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        tp_before = llcProperty2.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test
    public void referenceFromStateForChangeOwner() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }


        // contract for reference from definition

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);


        // contract for reference from state

        Contract oldJobCertificate = new Contract(llcPrivateKeys.iterator().next());
        oldJobCertificate.setOwnerKeys(stepaPublicKeys);
        oldJobCertificate.getDefinition().getData().set("type", "old job certificate");
        oldJobCertificate.seal();

        registerAndCheckApproved(oldJobCertificate);


        // contract for reference from state

        Contract newJobCertificate = new Contract(llcPrivateKeys.iterator().next());
        newJobCertificate.setOwnerKeys(stepaPublicKeys);
        newJobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        newJobCertificate.getDefinition().getData().set("type", "new job certificate");
        newJobCertificate.seal();

        registerAndCheckApproved(newJobCertificate);


        // 1 revision: main contract

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithStateReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        TransactionPack tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        // 2 revision: change reference in state
        // it do issuer with reference "certification_contract"

        Contract llcProperty2 = llcProperty.createRevision(llcPrivateKeys.iterator().next());

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.data.type == \"old job certificate\"");

        ContractsService.addReferenceToContract(llcProperty2, oldJobCertificate, "temp_certification_contract", Reference.TYPE_EXISTING_STATE, listConditions, true);

        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        tp_before = llcProperty2.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(jobCertificate);
        tp_before.addReferencedItem(oldJobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        // 3 revision: change existing reference
        // it do issuer with reference "certification_contract"

        List <String> newListConditions = new ArrayList<>();
        newListConditions.add("ref.definition.issuer == \"26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R\"");
        newListConditions.add("ref.definition.data.issuer == \"Roga & Kopita\"");
        newListConditions.add("ref.definition.data.type == \"new job certificate\"");

        Contract llcProperty3 = llcProperty2.createRevision(llcPrivateKeys.iterator().next());

        llcProperty3.getState().getReferences().remove(llcProperty3.getReferences().get("temp_certification_contract"));
        llcProperty3.getReferences().remove("temp_certification_contract");

        ContractsService.addReferenceToContract(llcProperty3, oldJobCertificate, "temp_certification_contract", Reference.TYPE_EXISTING_STATE, newListConditions, true);

        llcProperty3.check();
        llcProperty3.traceErrors();
        assertFalse(llcProperty3.isOk());

        tp_before = llcProperty3.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(jobCertificate);
        tp_before.addReferencedItem(newJobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        // 4 revision: finally change owner
        // it do owner with reference "temp_certification_contract"

        Contract llcProperty4 = llcProperty3.createRevision(stepaPrivateKeys);
        llcProperty4.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty4.seal();
        llcProperty4.check();
        llcProperty4.traceErrors();
        assertFalse(llcProperty4.isOk());

        tp_before = llcProperty4.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(jobCertificate);
        tp_before.addReferencedItem(newJobCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test
    public void inherittedReference() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }


        // contract for reference from state

        Contract oldAccountCertificate = new Contract(llcPrivateKeys.iterator().next());
        oldAccountCertificate.setOwnerKeys(stepaPublicKeys);
        oldAccountCertificate.getDefinition().getData().set("type", "Good Bank");
        oldAccountCertificate.seal();

        registerAndCheckApproved(oldAccountCertificate);


        // contract for reference from state

        Contract newAccountCertificate = new Contract(llcPrivateKeys.iterator().next());
        newAccountCertificate.setOwnerKeys(stepaPublicKeys);
        newAccountCertificate.getDefinition().getData().set("type", "Good Bank");
        newAccountCertificate.seal();

        registerAndCheckApproved(newAccountCertificate);

        // ---------------------------------------------------------------------------

        // 1 revision: main contract with two references:
        // - first in the definition
        // - second in the state
        // - second inherits conditions from the first

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        // update change_owner permission for use required reference

        Collection<Permission> permissions = llcProperty.getPermissions().get("change_owner");
        for (Permission permission : permissions) {
            permission.getRole().addRequiredReference("bank_certificate", Role.RequiredMode.ALL_OF);
            permission.getRole().addRequiredReference("account_in_bank_certificate", Role.RequiredMode.ALL_OF);
        }

        // add modify_data permission

        RoleLink ownerLink = new RoleLink("issuer_link", "issuer");
        llcProperty.registerRole(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("/references", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        llcProperty.addPermission(modifyDataPermission);

        // not alterable reference

        List <String> listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Good Bank\"");
        listConditionsForDefinition.add("this.state.references.account_in_bank_certificate defined");
        listConditionsForDefinition.add("this.state.references.account_in_bank_certificate is_inherit this.definition.references.bank_certificate");

        ContractsService.addReferenceToContract(llcProperty, oldAccountCertificate, "bank_certificate", Reference.TYPE_EXISTING_DEFINITION, listConditionsForDefinition, true);

        // alterable reference

        List <String> listConditionsForState = new ArrayList<>();
        listConditionsForState.add("ref.state.origin == \"" + oldAccountCertificate.getOrigin().toBase64String() + "\"");
        listConditionsForState.add("inherit this.definition.references.bank_certificate");

        ContractsService.addReferenceToContract(llcProperty, oldAccountCertificate, "account_in_bank_certificate", Reference.TYPE_EXISTING_STATE, listConditionsForState, true);

        registerAndCheckApproved(llcProperty);

        //--------------------------------------------------------------------

        // 2 revision: change existing reference (change origin from oldAccountCertificate to newAccountCertificate)

        Contract llcProperty2 = llcProperty.createRevision(llcPrivateKeys.iterator().next());

        List <String> newListConditions = new ArrayList<>();
        newListConditions.add("ref.state.origin == \"" + newAccountCertificate.getOrigin().toBase64String() + "\"");
        newListConditions.add("inherit this.definition.references.bank_certificate");

        llcProperty2.getState().getReferences().remove(llcProperty2.getReferences().get("account_in_bank_certificate"));
        llcProperty2.getReferences().remove("account_in_bank_certificate");

        ContractsService.addReferenceToContract(llcProperty2, newAccountCertificate, "account_in_bank_certificate", Reference.TYPE_EXISTING_STATE, newListConditions, true);

        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        // don't forget add all contracts needed for all references
//        tp_before.addReferencedItem(newAccountCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        // 3 revision: finally change owner
        // it do owner with reference "account_in_bank_certificate"

        Contract llcProperty3 = llcProperty2.createRevision(stepaPrivateKeys);
        llcProperty3.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty3.seal();
        llcProperty3.check();
        llcProperty3.traceErrors();
        assertFalse(llcProperty3.isOk());

        tp_before = llcProperty3.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(newAccountCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test
    public void alphaReference() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }


        // contract for reference from state

        Contract oldAccountCertificate = new Contract(llcPrivateKeys.iterator().next());
        oldAccountCertificate.setOwnerKeys(stepaPublicKeys);
        oldAccountCertificate.getDefinition().getData().set("type", "Good Bank");
        oldAccountCertificate.seal();

        registerAndCheckApproved(oldAccountCertificate);


        // contract for reference from state

        Contract newAccountCertificate = new Contract(llcPrivateKeys.iterator().next());
        newAccountCertificate.setOwnerKeys(stepaPublicKeys);
        newAccountCertificate.getDefinition().getData().set("type", "Good Bank");
        newAccountCertificate.seal();

        registerAndCheckApproved(newAccountCertificate);

        // ---------------------------------------------------------------------------

        // 1 revision: main contract with two references:
        // - first in the definition
        // - second in the state
        // - second inherits conditions from the first

        Contract llcProperty = ContractsService.createTokenContract(llcPrivateKeys, stepaPublicKeys, "100");

        // update change_owner permission for use required reference

        Collection<Permission> permissions = llcProperty.getPermissions().get("split_join");
        for (Permission permission : permissions) {
            permission.getRole().addRequiredReference("bank_certificate", Role.RequiredMode.ALL_OF);
        }

        // add modify_data permission

        RoleLink ownerLink = new RoleLink("issuer_link", "issuer");
        llcProperty.registerRole(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("account_origin", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        llcProperty.addPermission(modifyDataPermission);

        // not alterable reference

        List <String> listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Good Bank\"");
        listConditionsForDefinition.add("ref.state.origin == this.state.data.account_origin");
        listConditionsForDefinition.add("this.state.data.account_origin == ref.state.origin"); // mirroring

        llcProperty.getStateData().set("account_origin", oldAccountCertificate.getOrigin().toBase64String());
        ContractsService.addReferenceToContract(llcProperty, oldAccountCertificate, "bank_certificate", Reference.TYPE_EXISTING_DEFINITION, listConditionsForDefinition, true);

        registerAndCheckApproved(llcProperty);

        //--------------------------------------------------------------------

        // 2 revision: change existing reference (change origin from oldAccountCertificate to newAccountCertificate)

        Contract llcProperty2 = llcProperty.createRevision(llcPrivateKeys.iterator().next());

        llcProperty2.getStateData().set("account_origin", newAccountCertificate.getOrigin().toBase64String());

        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(newAccountCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        // 3 revision: split

        Contract llcProperty3 = ContractsService.createSplit(llcProperty2, "80", "amount", stepaPrivateKeys, true);
        llcProperty3.check();
        llcProperty3.traceErrors();
        assertFalse(llcProperty3.isOk());

        tp_before = llcProperty3.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(newAccountCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        ItemResult itemResult = node.waitItem(llcProperty2.getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);

        // 4 revision: join

        Contract llcProperty4 = ContractsService.createJoin(llcProperty3, llcProperty3.getNew().get(0), "amount", stepaPrivateKeys);
        llcProperty4.check();
        llcProperty4.traceErrors();
        assertFalse(llcProperty4.isOk());

        tp_before = llcProperty4.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(newAccountCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);

        itemResult = node.waitItem(llcProperty3.getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);

        itemResult = node.waitItem(llcProperty3.getNew().get(0).getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);
    }


    @Test(timeout = 30000)
    public void originReference() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }


        // contract for reference

        Contract oldAccountCertificate = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);
        oldAccountCertificate.getDefinition().getData().set("type", "Good Bank");
        oldAccountCertificate.getDefinition().getData().set("is_really_good", true);
        oldAccountCertificate.seal();

        registerAndCheckApproved(oldAccountCertificate);

        // ---------------------------------------------------------------------------

        // 1 revision: main contract with reference in the definition

        Contract llcProperty = ContractsService.createTokenContract(llcPrivateKeys, stepaPublicKeys, "100");

        // update split_join permission for use required reference

        Collection<Permission> permissions = llcProperty.getPermissions().get("split_join");
        for (Permission permission : permissions) {
            permission.getRole().addRequiredReference("bank_certificate", Role.RequiredMode.ALL_OF);
        }

        // not alterable reference

        List <String> listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Good Bank\"");
        listConditionsForDefinition.add("ref.definition.data.is_really_good == true");
        listConditionsForDefinition.add("ref.state.origin == this.state.data.account_origin");

        llcProperty.getStateData().set("account_origin", oldAccountCertificate.getOrigin().toBase64String());

        ContractsService.addReferenceToContract(llcProperty, oldAccountCertificate, "bank_certificate", Reference.TYPE_EXISTING_DEFINITION, listConditionsForDefinition, true);

        registerAndCheckApproved(llcProperty);

        //--------------------------------------------------------------------

        // second revisions for referenced contract and contract with reference

        Contract newAccountCertificate = oldAccountCertificate.createRevision(stepaPrivateKeys.iterator().next());
        newAccountCertificate.setOwnerKeys(thirdPartyPublicKeys);
        newAccountCertificate.seal();
        newAccountCertificate.check();
        newAccountCertificate.traceErrors();

        registerAndCheckApproved(newAccountCertificate);


        // IMPORTANT! need to clear old referenced contract from all references from all subitems
        llcProperty.getReferences().get("bank_certificate").matchingItems.clear();
        llcProperty.getDefinition().getReferences().get(0).matchingItems.clear();

        // wrong (old) referenced contract

        Contract llcProperty2 = ContractsService.createSplit(llcProperty, "80", "amount", stepaPrivateKeys, true);

        TransactionPack tp_before = llcProperty2.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(oldAccountCertificate);
        byte[] data = tp_before.pack();
        // here we "send" data and "got" it
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckDeclined(tp_after);

        // good (new) referenced contract

        System.out.println("------");

        llcProperty2 = ContractsService.createSplit(llcProperty, "80", "amount", stepaPrivateKeys, true);

        // and seal all again
        llcProperty2.getNew().get(0).seal();
        llcProperty2.seal();

        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        tp_before = llcProperty2.getTransactionPack();
        // don't forget add all contracts needed for all references
        tp_before.addReferencedItem(newAccountCertificate);
        data = tp_before.pack();
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void declineReferenceForChangeOwner() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        TransactionPack tp_before;
        TransactionPack tp_after;
        Contract jobCertificate;

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        // bad situations

        // missing data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
//        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
//        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // not registered
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

//        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
//        tp_before.addForeignReference(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong issuer
        jobCertificate = new Contract(stepaPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Not Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "Not chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // revoked reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.addPermission(new RevokePermission(jobCertificate.getOwner()));
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract revokingJobCertificate = ContractsService.createRevocation(jobCertificate, stepaPrivateKeys.iterator().next());
        revokingJobCertificate.check();
        revokingJobCertificate.traceErrors();
        registerAndCheckApproved(revokingJobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);
    }

    @Test(timeout = 30000)
    public void declineReferenceForRevoke() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        TransactionPack tp_before;
        TransactionPack tp_after;
        Contract jobCertificate;

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "NotaryWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = ContractsService.createRevocation(llcProperty, stepaPrivateKeys.iterator().next());
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        // bad situations

        // missing data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
//        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
//        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // not registered
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

//        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
//        tp_before.addForeignReference(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong issuer
        jobCertificate = new Contract(stepaPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Not Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "Not chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // revoked reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.addPermission(new RevokePermission(jobCertificate.getOwner()));
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract revokingJobCertificate = ContractsService.createRevocation(jobCertificate, stepaPrivateKeys.iterator().next());
        revokingJobCertificate.check();
        revokingJobCertificate.traceErrors();
        registerAndCheckApproved(revokingJobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);
    }

    @Test(timeout = 30000)
    public void declineReferenceForSplitJoin() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        TransactionPack tp_before;
        TransactionPack tp_after;
        Contract jobCertificate;

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "TokenWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = ContractsService.createSplit(llcProperty, "100",
                "amount", stepaPrivateKeys, true);
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        // bad situations

        // missing data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
//        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
//        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // not registered
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

//        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
//        tp_before.addForeignReference(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong issuer
        jobCertificate = new Contract(stepaPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Not Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "Not chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // revoked reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.addPermission(new RevokePermission(jobCertificate.getOwner()));
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract revokingJobCertificate = ContractsService.createRevocation(jobCertificate, stepaPrivateKeys.iterator().next());
        revokingJobCertificate.check();
        revokingJobCertificate.traceErrors();
        registerAndCheckApproved(revokingJobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);
    }

    @Test(timeout = 30000)
    public void declineReferenceForChangeNumber() throws Exception {

        // You have a notary dsl with llc's property
        // and only owner of trusted manager's contract can chamge the owner of property

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        TransactionPack tp_before;
        TransactionPack tp_after;
        Contract jobCertificate;

        Contract llcProperty = Contract.fromDslFile(ROOT_PATH + "AbonementWithReferenceDSLTemplate.yml");
        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty.getTransactionPack();
        tp_before.addReferencedItem(jobCertificate);
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckApproved(tp_after);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.getStateData().set("units",
                llcProperty.getStateData().getIntOrThrow("units") - 1);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        // bad situations

        // missing data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
//        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
//        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // not registered
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

//        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // missing reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
//        tp_before.addForeignReference(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong issuer
        jobCertificate = new Contract(stepaPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.issuer
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Not Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // wrong data.type
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "Not chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);

        // revoked reference
        jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.addPermission(new RevokePermission(jobCertificate.getOwner()));
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract revokingJobCertificate = ContractsService.createRevocation(jobCertificate, stepaPrivateKeys.iterator().next());
        revokingJobCertificate.check();
        revokingJobCertificate.traceErrors();
        registerAndCheckApproved(revokingJobCertificate);

        tp_before = llcProperty2.getTransactionPack();
        tp_before.getReferencedItems().clear();
        tp_before.addReferencedItem(jobCertificate);
        // here we "send" data and "got" it
        tp_after = TransactionPack.unpack(tp_before.pack());

        registerAndCheckDeclined(tp_after);
    }

    @Ignore("Stress test")
    @Test(timeout = 900000)
    public void testLedgerLocks() throws Exception {
        ExtendedSignatureTest.parallelize(Executors.newCachedThreadPool(), 4, () -> {
            try {
                Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
                Set<PublicKey> stepaPublicKeys = new HashSet<>();
                stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
                for (PrivateKey pk : stepaPrivateKeys) {
                    stepaPublicKeys.add(pk.getPublicKey());
                }
                PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/tu_key.private.unikey"));
                int N = 100;
                for (int i = 0; i < N; i++) {

                    Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
                    stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
                    stepaCoins.seal();

                    Parcel parcel = createParcelWithClassTU(stepaCoins, stepaPrivateKeys);
                    synchronized (tuContractLock) {
                        tuContract = parcel.getPaymentContract();
                    }

                    System.out.println("-------------- register parcel " + parcel.getId() + " (iteration " + i + ") ------------");
                    node.registerParcel(parcel);

                    for (Node n : nodes) {
                        n.waitParcel(parcel.getId(), 15000);
                        ItemResult itemResult = n.waitItem(stepaCoins.getId(), 15000);
                    }

                    ItemState itemState1 = node.waitItem(parcel.getPaymentContract().getRevoking().get(0).getId(), 15000).state;
                    ItemState itemState2 = node.getLedger().getRecord(parcel.getPaymentContract().getRevoking().get(0).getId()).getState();

                    System.out.println("--- check item " + parcel.getPaymentContract().getRevoking().get(0).getId() + " --- iteration " + i);
                    System.out.println("state from node: " + itemState1);
                    System.out.println("state from ledger: " + itemState2);
                    assertEquals(itemState1, itemState2);
                    assertEquals(ItemState.REVOKED, itemState1);
                    assertEquals(ItemState.REVOKED, itemState2);
                }
            } catch (Exception e) {
                System.out.println("exception: " + e.toString());
            }
        });
    }


    public synchronized Parcel createParcelWithFreshTU(Contract c, Set<PrivateKey> keys) throws Exception {

        Set<PublicKey> ownerKeys = new HashSet();
        keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
        Contract stepaTU = InnerContractsService.createFreshTU(100000000, ownerKeys);
        stepaTU.check();
        //stepaTU.setIsTU(true);
        stepaTU.traceErrors();
        node.registerItem(stepaTU);
        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        return ContractsService.createParcel(c, stepaTU, 150, keys);
    }

    protected Contract getApprovedTUContract() throws Exception {
        synchronized (tuContractLock) {
            if (tuContract == null) {
                PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
                Set<PublicKey> keys = new HashSet();
                keys.add(ownerKey.getPublicKey());
                Contract stepaTU = InnerContractsService.createFreshTU(100000000, keys);
                stepaTU.check();
                stepaTU.traceErrors();
                System.out.println("register new TU ");
                node.registerItem(stepaTU);
                tuContract = stepaTU;
            }
            int needRecreateTuContractNum = 0;
            for (Node n : nodes) {
                try {
                    ItemResult itemResult = n.waitItem(tuContract.getId(), 15000);
                    //assertEquals(ItemState.APPROVED, itemResult.state);
                    if (itemResult.state != ItemState.APPROVED) {
                        System.out.println("TU: node " + n + " result: " + itemResult);
                        needRecreateTuContractNum ++;
                    }
                } catch (TimeoutException e) {
                    System.out.println("ping ");
//                    System.out.println(n.ping());
////                    System.out.println(n.traceTasksPool());
//                    System.out.println(n.traceParcelProcessors());
//                    System.out.println(n.traceItemProcessors());
                    System.out.println("TU: node " + n + " timeout: ");
                    needRecreateTuContractNum ++;
                }
            }
            int recreateBorder = nodes.size() - config.getPositiveConsensus() - 1;
            if(recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateTuContractNum > recreateBorder) {
                tuContract = null;
                Thread.sleep(1000);
                return getApprovedTUContract();
            }
            return tuContract;
        }
    }

    public synchronized Parcel createParcelWithClassTU(Contract c, Set<PrivateKey> keys) throws Exception {
        Contract tu = getApprovedTUContract();
        Parcel parcel =  ContractsService.createParcel(c, tu, 150, keys);
//        System.out.println("create  parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
        return parcel;
    }

    protected synchronized Parcel registerWithNewParcel(Contract c) throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel parcel = createParcelWithClassTU(c, stepaPrivateKeys);
        System.out.println("register  parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        return parcel;
    }

    protected synchronized Parcel registerWithNewParcel(TransactionPack tp) throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }

        return parcel;
    }

    private synchronized void registerAndCheckApproved(Contract c) throws Exception {
        Parcel parcel = registerWithNewParcel(c);
        waitAndCheckApproved(parcel);
    }

    private synchronized void registerAndCheckApproved(TransactionPack tp) throws Exception {
        Parcel parcel = registerWithNewParcel(tp);
        waitAndCheckApproved(parcel);
    }

    private synchronized void waitAndCheckApproved(Parcel parcel) throws Exception {

        waitAndCheckState(parcel, ItemState.APPROVED);
    }

    private synchronized void registerAndCheckDeclined(Contract c) throws Exception {
        Parcel parcel = registerWithNewParcel(c);
        waitAndCheckDeclined(parcel);
    }

    private synchronized void registerAndCheckDeclined(TransactionPack tp) throws Exception {
        Parcel parcel = registerWithNewParcel(tp);
        waitAndCheckDeclined(parcel);
    }

    private synchronized void waitAndCheckDeclined(Parcel parcel) throws Exception {

        waitAndCheckState(parcel, ItemState.DECLINED);
    }

    private synchronized void waitAndCheckState(Parcel parcel, ItemState waitState) throws Exception {
        try {
            System.out.println("wait parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            node.waitParcel(parcel.getId(), 30000);
            System.out.println("wait payment: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            ItemResult itemResult = node.waitItem(parcel.getPaymentContract().getId(), 8000);
            assertEquals(ItemState.APPROVED, itemResult.state);
            System.out.println("wait payload with state: " + waitState + " " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            itemResult = node.waitItem(parcel.getPayloadContract().getId(), 8000);
            parcel.getPayloadContract().traceErrors();
            assertEquals(waitState, itemResult.state);
        } catch (TimeoutException e) {
            if (parcel != null) {
                fail("timeout,  " + node + " parcel " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            } else {
                fail("timeout,  " + node);
            }
        }
    }

    /**
     * Imitate of sending contract from one part of swappers to another.
     *
     * Method packs sending contracts with main swap contract (can be blank - doesn't matter) into TransactionPack.
     * Then restore from packed binary main swap contract, contracts sending with.
     *
     * @param mainContract
     * @return
     * @throws Exception
     */
    public synchronized Contract imitateSendingTransactionToPartner(Contract mainContract) throws Exception {

        TransactionPack tp_before = mainContract.getTransactionPack();
        byte[] data = tp_before.pack();

        // here we "send" data and "got" it

        TransactionPack tp_after = TransactionPack.unpack(data);
        Contract gotMainContract = tp_after.getContract();

        return gotMainContract;
    }



    protected synchronized void addDetailsToAllLedgers(Contract contract) {
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

    protected synchronized void destroyFromAllNodesExistingNew(Contract c50_1) {
        StateRecord orCreate;
        for (Approvable c : c50_1.getNewItems()) {
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    protected synchronized void destroyCurrentFromAllNodesIfExists(Contract finalC) {
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



    public synchronized Contract startSwap_wrongKey(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, PrivateKey wrongKey) {

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

    public synchronized Contract signPresentedSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

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

    public synchronized Contract finishSwap_wrongKey(Contract swapContract, Set<PrivateKey> keys, PrivateKey wrongKey) {

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);
            System.out.println("willBeMine: " + willBeMine + " " + c.getSealedByKeys().size());
            if(willBeMine) {
                c.addSignatureToSeal(wrongKey);
            }
        }

        swapContract.seal();
        swapContract.addSignatureToSeal(keys);

        return swapContract;
    }

    @Ignore("parallel test")
    @Test
    public void parallelTest() throws Exception {
//        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
//        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

//        assertEquals(main.cache, main.node.getCache());
//        ItemCache c1 = main.cache;
//        ItemCache c2 = main.node.getCache();

//        Client client = new Client(myKey, main.myInfo, null);


        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = true;
//        assertTrue(singleContract.isOk());

//        ItemResult r = client.getState(singleContract.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(r);


        contractsForThreads = new ArrayList<>();
        for(int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);

//            ItemResult r = client.getState(contract.getId());
//            assertEquals(ItemState.UNDEFINED, r.state);
//            System.out.println(r);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register


        for(int i = 0; i < N; i++) {

            if(createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for(int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M-1));

            ts1 = new Date().getTime();

            for(Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    long t = System.nanoTime();
                    ItemResult rr = null;
                    rr = node.registerItem(c);
                    try {
                        rr = node.waitItem(c.getId(), 15000);
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("multi thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                ItemResult rr = null;
                rr = node.registerItem(finalSingleContract);
                try {
                    rr = node.waitItem(finalSingleContract.getId(), 15000);
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("single thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);
    }


    @Ignore("parallel test")
    @Test
    public void parallelContractNodeCheck() throws Exception {
//        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
//        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

//        assertEquals(main.cache, main.node.getCache());
//        ItemCache c1 = main.cache;
//        ItemCache c2 = main.node.getCache();

//        Client client = new Client(myKey, main.myInfo, null);


        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = true;
//        assertTrue(singleContract.isOk());

//        ItemResult r = client.getState(singleContract.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(r);


        contractsForThreads = new ArrayList<>();
        for(int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);

//            ItemResult r = client.getState(contract.getId());
//            assertEquals(ItemState.UNDEFINED, r.state);
//            System.out.println(r);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register


        for(int i = 0; i < N; i++) {

            if(createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for(int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M-1));

            ts1 = new Date().getTime();

            for(Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    long t = System.nanoTime();
                    try {
                        List<StateRecord> lockedToCreate = new ArrayList<>();
                        List<StateRecord> lockedToRevoke = new ArrayList<>();
                        StateRecord record = node.getLedger().findOrCreate(c.getId());
                        c.check();
                        isNeedToResync(true, c);
                        checkSubItemsOf(c, record, lockedToCreate, lockedToRevoke);
                    } catch (Quantiser.QuantiserException e) {
                        e.printStackTrace();
                    }

                    System.out.println("multi thread: time: " + ((System.nanoTime() - t) * 1e-9));
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                try {
                    List<StateRecord> lockedToCreate = new ArrayList<>();
                    List<StateRecord> lockedToRevoke = new ArrayList<>();
                    StateRecord record = node.getLedger().findOrCreate(finalSingleContract.getId());
                    finalSingleContract.check();
                    isNeedToResync(true, finalSingleContract);
                    checkSubItemsOf(finalSingleContract, record, lockedToCreate, lockedToRevoke);
                } catch (Quantiser.QuantiserException e) {
                    e.printStackTrace();
                }

                System.out.println("single thread: time: " + ((System.nanoTime() - t) * 1e-9));
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);
    }

    public HashMap<HashId, StateRecord> isNeedToResync(boolean baseCheckPassed, Approvable item) {
        HashMap<HashId, StateRecord> unknownParts = new HashMap<>();
        HashMap<HashId, StateRecord> knownParts = new HashMap<>();
        if (baseCheckPassed) {
            // check the referenced items
            for (Reference refModel : item.getReferences().values()) {
                HashId id = refModel.contract_id;
                if (((refModel.type == Reference.TYPE_EXISTING_DEFINITION) ||
                     (refModel.type == Reference.TYPE_EXISTING_STATE)) && id != null) {
                    StateRecord r = node.getLedger().getRecord(id);

                    if (r == null || !r.getState().isConsensusFound()) {
                        unknownParts.put(id, r);
                    } else {
                        knownParts.put(id, r);
                    }
                }
            }
            // check revoking items
            for (Approvable a : item.getRevokingItems()) {
                StateRecord r = node.getLedger().getRecord(a.getId());

                if (r == null || !r.getState().isConsensusFound()) {
                    unknownParts.put(a.getId(), r);
                } else {
                    knownParts.put(a.getId(), r);
                }
            }
        } else {
        }
        boolean needToResync = false;
        // contract is complex and consist from parts
        if (unknownParts.size() + knownParts.size() > 0) {
            needToResync = baseCheckPassed &&
                    unknownParts.size() > 0 &&
                    knownParts.size() >= config.getKnownSubContractsToResync();
        }

        if (needToResync)
            return unknownParts;
        return new HashMap<>();
    }

    // check subitems of given item recursively (down for newItems line)
    private final void checkSubItemsOf(Approvable checkingItem, StateRecord record, List<StateRecord> lockedToCreate, List<StateRecord> lockedToRevoke) {
        for (Reference refModel : checkingItem.getReferences().values()) {
            HashId id = refModel.contract_id;
            if (refModel.type != Reference.TYPE_TRANSACTIONAL) {
                if (!node.getLedger().isApproved(id)) {
                    checkingItem.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                }
            }
        }
        // check revoking items
        for (Approvable a : checkingItem.getRevokingItems()) {
            StateRecord r = record.lockToRevoke(a.getId());
            if (r == null) {
                checkingItem.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
            } else {
                if (!lockedToRevoke.contains(r))
                    lockedToRevoke.add(r);
            }
        }
        // check new items
        for (Approvable newItem : checkingItem.getNewItems()) {

            checkSubItemsOf(newItem, record, lockedToCreate, lockedToRevoke);

            if (!newItem.getErrors().isEmpty()) {
                checkingItem.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
            } else {
                StateRecord r = record.createOutputLockRecord(newItem.getId());
                if (r == null) {
                    checkingItem.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item exists in ledger");
                } else {
                    if (!lockedToCreate.contains(r))
                        lockedToCreate.add(r);
                }
            }
        }
    }

    @Test
    @Ignore("it is snatch test")
    public void splitSnatch() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        Contract c2 = c1.splitValue("amount", new Decimal(50));
        System.out.println("money after split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
//        c2.addRevokingItems(c1copy);
        c2.getStateData().set("amount", 9000);//150);
        c2.seal();
        System.out.println("money after snatch (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c2): " + c2.check());
        registerAndCheckDeclined(c2);
    }



    @Test
    @Ignore("it is snatch test")
    public void splitSnatch2() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
//        Contract c1copy = new Contract(c1.getLastSealedBinary());
        System.out.println("true money origin: " + c1.getOrigin().toBase64String());
        Contract c1copy = new Contract(c1.getLastSealedBinary());

        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        c1 = c1.createRevision();
        Contract c2 = c1.splitValue("amount", new Decimal(60));
        System.out.println("money after split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after split (c1.origin): " + c1.getOrigin().toBase64String());
        System.out.println("check after split (c2.origin): " + c2.getOrigin().toBase64String());
        ((Contract)c1.getRevokingItems().iterator().next()).getStateData().set("amount", 2000);
        c1.addSignerKey(key);
        c1.seal();
        c2.getStateData().set("amount", 1960);
        c2.addSignerKey(key);
        c2.seal();
        System.out.println("money after snatch (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c2): " + c2.check());
//        registerAndCheckApproved(c1);
//        registerAndCheckApproved(c2);
        System.out.println("check after snatch (c1.origin): " + c1.getOrigin().toBase64String());
        System.out.println("check after snatch (c2.origin): " + c2.getOrigin().toBase64String());
        System.out.println("check after snatch (c1copy.origin): " + c1copy.getOrigin().toBase64String());
        registerAndCheckDeclined(c2);
    }



    @Test
    @Ignore("it is snatch test")
    public void joinSnatch() throws Exception {
        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(key);

        Contract c1 = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c1.addSignerKey(key);
        assertTrue(c1.check());
        c1.seal();
        registerAndCheckApproved(c1);

        System.out.println("money before split (c1): " + c1.getStateData().getIntOrThrow("amount"));
        Contract c2 = ContractsService.createSplit(c1, "99", "amount", keys);
        Contract c3 = c2.getNew().get(0);

        System.out.println("money after split (c2): " + c2.getStateData().getIntOrThrow("amount"));
        System.out.println("money after split (c3): " + c3.getStateData().getIntOrThrow("amount"));

        registerAndCheckApproved(c3);


        Contract c4 = c3.createRevision(keys);
        c4.addRevokingItems(c1);
        c4.getStateData().set("amount", 199);//150);
        c4.seal();
        System.out.println("money after snatch (c4): " + c4.getStateData().getIntOrThrow("amount"));
        System.out.println("check after snatch (c4): " + c4.check());
        c4.traceErrors();
        registerAndCheckDeclined(c4);
    }



    protected synchronized Contract checkPayment_preparePaymentContract(Set<PrivateKey> privateKeys) throws Exception {
        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(privateKeys.iterator().next());
        stepaCoins.seal();
        registerAndCheckApproved(stepaCoins);
        Parcel parcel = createParcelWithFreshTU(stepaCoins, privateKeys);
        return parcel.getPaymentContract();
    }



    protected synchronized Set<PrivateKey> checkPayment_preparePrivateKeys() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        return stepaPrivateKeys;
    }



    @Test(timeout = 90000)
    public void checkPayment_good() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertTrue(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_zeroTU() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transaction_units", 0);
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongTUtype() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transaction_units", "33");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongTUname() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getStateData().set("transacti0n_units", payment.getStateData().get("transaction_units"));
        payment.getStateData().remove("transaction_units");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_missingDecrementPermission() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        payment.getPermissions().remove("decrement_permission");
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_wrongIssuer() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());

        PrivateKey manufactureFakePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        SimpleRole issuerRole = new SimpleRole("issuer");
        KeyRecord kr = new KeyRecord(manufactureFakePrivateKey.getPublicKey());
        issuerRole.addKeyRecord(kr);
        payment.registerRole(issuerRole);

        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 20000)
    public void checkPayment_revision1() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field = payment.getState().getClass().getDeclaredField("revision");
        field.setAccessible(true);
        field.set(payment.getState(), 1);
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_originItself() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field = payment.getState().getClass().getDeclaredField("origin");
        field.setAccessible(true);
        field.set(payment.getState(), payment.getId());
        final Field field2 = payment.getRevoking().get(0).getState().getClass().getDeclaredField("origin");
        field2.setAccessible(true);
        field2.set(payment.getRevoking().get(0).getState(), payment.getId());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void checkPayment_originMismatch() throws Exception {
        Contract payment = checkPayment_preparePaymentContract(checkPayment_preparePrivateKeys());
        final Field field2 = payment.getRevoking().get(0).getState().getClass().getDeclaredField("origin");
        field2.setAccessible(true);
        field2.set(payment.getRevoking().get(0).getState(), payment.getId());
        boolean res = payment.paymentCheck(config.getTransactionUnitsIssuerKeys());
        payment.traceErrors();
        assertFalse(res);
    }



    @Test(timeout = 90000)
    public void declinePaymentAndPaymentWithRemovedPermissions() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract uContract = getApprovedTUContract();
        Contract modifiedU = uContract.createRevision(stepaPrivateKeys);
        modifiedU.getPermissions().remove("revoke");
        modifiedU.seal();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(modifiedU, uContract, 150, stepaPrivateKeys);
        System.out.println("-------------");

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }

        waitAndCheckState(parcel, ItemState.UNDEFINED);
    }

    @Test(timeout = 90000)
    public void declinePaymentAndPaymentWithChangedData() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract uContract = getApprovedTUContract();
        Contract modifiedU = uContract.createRevision(stepaPrivateKeys);
        modifiedU.getStateData().set("transaction_units", modifiedU.getStateData().getIntOrThrow("transaction_units") - 1);
        modifiedU.seal();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(modifiedU, uContract, 150, stepaPrivateKeys);
        System.out.println("-------------");

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }


        waitAndCheckState(parcel, ItemState.UNDEFINED);
    }

    @Test(timeout = 90000)
    public void declinePaymentAndPaymentWithChangedData2() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract uContract = getApprovedTUContract();

        // stepaPrivateKeys - is also U keys
        Contract paymentDecreased = uContract.createRevision(stepaPrivateKeys);
        paymentDecreased.getStateData().set("transaction_units", uContract.getStateData().getIntOrThrow("transaction_units") - 5);
        paymentDecreased.seal();

        Contract modifiedU = paymentDecreased.createRevision(stepaPrivateKeys);
        modifiedU.getStateData().set("transaction_units", modifiedU.getStateData().getIntOrThrow("transaction_units") - 1);
        modifiedU.seal();

        Parcel parcel = new Parcel(modifiedU.getTransactionPack(), paymentDecreased.getTransactionPack());
        System.out.println("-------------");

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }


        waitAndCheckState(parcel, ItemState.UNDEFINED);
    }

    @Test(timeout = 30000)
    public void checkReferencesContracts() throws Exception {
        Contract contract1 = Contract.fromDslFile(ROOT_PATH + "Referenced_contract1.yml");
        Contract contract2 = Contract.fromDslFile(ROOT_PATH + "Referenced_contract2.yml");
        Contract contract3 = Contract.fromDslFile(ROOT_PATH + "Referenced_contract3.yml");
        contract1.seal();
        contract2.seal();
        contract3.seal();

        TransactionPack tp = new TransactionPack();
        tp.setContract(contract1);
        tp.addSubItem(contract1);
        tp.addReferencedItem(contract1);
        tp.addSubItem(contract2);
        tp.addReferencedItem(contract2);
        tp.addSubItem(contract3);
        tp.addReferencedItem(contract3);

        Contract refContract1 = new Contract(contract1.seal(), tp);
        Contract refContract2 = new Contract(contract3.seal(), tp);

        assertTrue(refContract1.getReferences().get("ref_cont").matchingItems.contains(contract1));
        assertTrue(refContract1.getReferences().get("ref_cont").matchingItems.contains(contract2));
        assertFalse(refContract1.getReferences().get("ref_cont").matchingItems.contains(contract3));

        assertFalse(refContract1.getReferences().get("ref_cont2").matchingItems.contains(contract1));
        assertFalse(refContract1.getReferences().get("ref_cont2").matchingItems.contains(contract2));
        assertTrue(refContract1.getReferences().get("ref_cont2").matchingItems.contains(contract3));

        assertTrue(refContract1.getReferences().get("ref_cont_inherit").matchingItems.contains(contract1));
        assertFalse(refContract1.getReferences().get("ref_cont_inherit").matchingItems.contains(contract2));
        assertFalse(refContract1.getReferences().get("ref_cont_inherit").matchingItems.contains(contract3));

        assertTrue(refContract2.getReferences().get("ref_cont3").matchingItems.contains(contract1));
        assertTrue(refContract2.getReferences().get("ref_cont3").matchingItems.contains(contract2));
        assertTrue(refContract2.getReferences().get("ref_cont3").matchingItems.contains(contract3));

        assertTrue(refContract2.getReferences().get("ref_cont4").matchingItems.contains(contract1));
        assertFalse(refContract2.getReferences().get("ref_cont4").matchingItems.contains(contract2));
        assertTrue(refContract2.getReferences().get("ref_cont4").matchingItems.contains(contract3));
    }

    @Test(timeout = 30000)
    public void referenceForChangeOwnerWithCreateContract() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();//manager -
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>(); //issuer
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();
        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.issuer == \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "          a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "          z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "          BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "          vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "          Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "          hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "          LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "          MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"");
        listConditions.add("ref.definition.data.issuer == \"Roga & Kopita\"");
        listConditions.add("ref.definition.data.type == \"chief accountant assignment\"");

        ContractsService.addReferenceToContract(llcProperty, jobCertificate, "certification_contract", Reference.TYPE_EXISTING_DEFINITION, listConditions, true);

        ListRole listRole = new ListRole("list_role");
        SimpleRole ownerRole = new SimpleRole("owner", stepaPrivateKeys);
        listRole.addRole(ownerRole);
        listRole.addRequiredReference("certification_contract", Role.RequiredMode.ALL_OF);

        llcProperty.getPermissions().remove("change_owner");
        llcProperty.getPermissions().remove("revoke");

        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(listRole);
        llcProperty.addPermission(changeOwnerPerm);

        RevokePermission revokePerm = new RevokePermission(listRole);
        llcProperty.addPermission(revokePerm);

        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.setOwnerKeys(thirdPartyPublicKeys);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
//        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void referenceForRevokeWithCreateContract() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.issuer == \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "          a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "          z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "          BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "          vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "          Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "          hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "          LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "          MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"");
        listConditions.add("ref.definition.data.issuer == \"Roga & Kopita\"");
        listConditions.add("ref.definition.data.type == \"chief accountant assignment\"");

        ContractsService.addReferenceToContract(llcProperty, jobCertificate, "certification_contract", Reference.TYPE_EXISTING_DEFINITION, listConditions, true);

        ListRole listRole = new ListRole("list_role");
        SimpleRole ownerRole = new SimpleRole("owner", stepaPrivateKeys);
        listRole.addRole(ownerRole);
        listRole.addRequiredReference("certification_contract", Role.RequiredMode.ALL_OF);

        llcProperty.getPermissions().remove("change_owner");
        llcProperty.getPermissions().remove("revoke");

        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(listRole);
        llcProperty.addPermission(changeOwnerPerm);

        RevokePermission revokePerm = new RevokePermission(listRole);
        llcProperty.addPermission(revokePerm);

        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = ContractsService.createRevocation(llcProperty, stepaPrivateKeys.iterator().next());
        llcProperty2.check();
        llcProperty2.traceErrors();
//        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
//        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void referenceForChangeNumberWithCreateContract() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        llcProperty.getDefinition().getData().remove("name");
        llcProperty.getDefinition().getData().remove("description");

        Binder binderdata = new Binder();
        binderdata.set("name", "Abonement");
        binderdata.set("description", "Abonement.");
        llcProperty.getDefinition().setData(binderdata);

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.issuer == \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "          a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "          z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "          BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "          vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "          Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "          hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "          LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "          MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"");
        listConditions.add("ref.definition.data.issuer == \"Roga & Kopita\"");
        listConditions.add("ref.definition.data.type == \"chief accountant assignment\"");

        ContractsService.addReferenceToContract(llcProperty, jobCertificate, "certification_contract", Reference.TYPE_EXISTING_DEFINITION, listConditions, true);

        ListRole listRole = new ListRole("list_role");
        SimpleRole ownerRole = new SimpleRole("owner", stepaPrivateKeys);
        listRole.addRole(ownerRole);
        listRole.addRequiredReference("certification_contract", Role.RequiredMode.ALL_OF);

        Binder params = new Binder();
        params.set("min_value", 1);
        params.set("max_step", -1);
        params.set("field_name", "units");

        ChangeNumberPermission ChangeNumberPerm = new ChangeNumberPermission(listRole, params);
        llcProperty.addPermission(ChangeNumberPerm);
        llcProperty.getStateData().set("units", 1000000);

        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = llcProperty.createRevision(stepaPrivateKeys);
        llcProperty2.getStateData().set("units",
                llcProperty.getStateData().getIntOrThrow("units") - 1);
        llcProperty2.seal();
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
//        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test(timeout = 30000)
    public void referenceForSplitJoinWithCreateContract() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();
        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        registerAndCheckApproved(jobCertificate);

        Contract llcProperty = ContractsService.createTokenContract(llcPrivateKeys, stepaPublicKeys, "100000000000");

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.issuer == \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "          a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "          z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "          BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "          vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "          Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "          hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "          LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "          MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"");
        listConditions.add("ref.definition.data.issuer == \"Roga & Kopita\"");
        listConditions.add("ref.definition.data.type == \"chief accountant assignment\"");

        ContractsService.addReferenceToContract(llcProperty, jobCertificate, "certification_contract", Reference.TYPE_EXISTING_DEFINITION, listConditions, true);

        ListRole listRole = new ListRole("list_role");
        SimpleRole ownerRole = new SimpleRole("owner", stepaPrivateKeys);
        listRole.setMode(ListRole.Mode.ALL);
        listRole.addRole(ownerRole);
        listRole.addRequiredReference("certification_contract", Role.RequiredMode.ALL_OF);

        llcProperty.getPermissions().remove("split_join");
        llcProperty.getPermissions().remove("change_owner");
        llcProperty.getPermissions().remove("revoke");

        Binder params = new Binder();
        params.set("min_value", 0.01);
        params.set("min_unit", 0.001);
        params.set("field_name", "amount");
        List <String> listFields = new ArrayList<>();
        listFields.add("state.origin");
        params.set("join_match_fields", listFields);

        SplitJoinPermission splitJoinPerm = new SplitJoinPermission(listRole, params);
        llcProperty.addPermission(splitJoinPerm);

        llcProperty.addSignerKey(llcPrivateKeys.iterator().next());
        llcProperty.seal();

        registerAndCheckApproved(llcProperty);

        Contract llcProperty2 = ContractsService.createSplit(llcProperty, "100", "amount", stepaPrivateKeys, true);
        llcProperty2.check();
        llcProperty2.traceErrors();
        assertFalse(llcProperty2.isOk());

        TransactionPack tp_before = llcProperty2.getTransactionPack();
//        tp_before.addReferencedItem(jobCertificate);
        byte[] data = tp_before.pack();
        TransactionPack tp_after = TransactionPack.unpack(data);

        registerAndCheckApproved(tp_after);
    }

    @Test
    public void checkRegisterContractCreatedAtFutureTime() throws Exception{

        Contract futureContract = Contract.fromDslFile(ROOT_PATH + "simple_root_contract_future.yml");
        futureContract.addSignerKeyFromFile(ROOT_PATH+"_xer0yfe2nn1xthc.private.unikey");

        futureContract.seal();
        futureContract.check();
        futureContract.traceErrors();
        System.out.println("Contract is valid: " + futureContract.isOk());
        assertFalse(futureContract.isOk());
    }

    @Test
    public void checkRegisterContractExpiresAtReсentPastTime() throws Exception{

        Contract oldContract = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        oldContract.addSignerKeyFromFile(ROOT_PATH+"_xer0yfe2nn1xthc.private.unikey");
        oldContract.getDefinition().setExpiresAt(oldContract.getCreatedAt().minusMinutes(1));

        oldContract.seal();
        oldContract.check();
        oldContract.traceErrors();
        System.out.println("Contract is valid: " + oldContract.isOk());
        assertFalse(oldContract.isOk());
    }

    @Test
    public void checkRegisterContractExpiresAtDistantPastTime() throws Exception{

        Contract oldContract = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        oldContract.addSignerKeyFromFile(ROOT_PATH+"_xer0yfe2nn1xthc.private.unikey");
        oldContract.getDefinition().setExpiresAt(ZonedDateTime.of(LocalDateTime.MIN.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC));

        oldContract.seal();
        oldContract.check();
        oldContract.traceErrors();
        System.out.println("Contract is valid: " + oldContract.isOk());
        assertFalse(oldContract.isOk());
    }

    @Test
    public void checkRegisterContractExpiresAtReсentFutureTime() throws Exception{

        Contract futureContract = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        futureContract.addSignerKeyFromFile(ROOT_PATH+"_xer0yfe2nn1xthc.private.unikey");
        futureContract.getDefinition().setExpiresAt(futureContract.getCreatedAt().plusMinutes(1));

        futureContract.seal();
        assertTrue(futureContract.check());
        System.out.println("Contract is valid: " + futureContract.isOk());
        registerAndCheckApproved(futureContract);
    }

    @Test
    public void checkContractExpiresAtDistantFutureTime() throws Exception{

        Contract futureContract = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        futureContract.addSignerKeyFromFile(ROOT_PATH+"_xer0yfe2nn1xthc.private.unikey");
        futureContract.getDefinition().setExpiresAt(futureContract.getCreatedAt().plusYears(50));

        futureContract.seal();
        assertTrue(futureContract.check());
        System.out.println("Contract is valid: " + futureContract.isOk());
        registerAndCheckApproved(futureContract);
    }

    @Test
    public void checkRegisterRevisionCreatedAtPastTimeValid() throws Exception{

        Contract contract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        Binder b = contract.serialize(new BiSerializer());

        long seconds = ZonedDateTime.now().minusDays(4).toEpochSecond();
        b.getBinder("definition").getBinder("created_at").set("seconds", seconds);
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract baseContract = new Contract();
        baseContract.deserialize(b, new BiDeserializer());

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revTemp = baseContract.createRevision(ownerPrivateKey);
        b = revTemp.serialize(new BiSerializer());

        seconds = ZonedDateTime.now().minusDays(4).plusMinutes(1).toEpochSecond();
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract revContract = new Contract();
        revContract.deserialize(b, new BiDeserializer());

        revContract.addRevokingItems(baseContract);
        revContract.setOwnerKey(manufacturePrivateKey);
        revContract.addSignerKey(manufacturePrivateKey);
        revContract.addSignerKey(ownerPrivateKey);

        revContract.seal();
        revContract.check();
        revContract.traceErrors();
        System.out.println("Revision contract is valid: " + revContract.isOk());
        registerAndCheckApproved(revContract);
    }

    @Test
    public void checkRegisterRevisionCreatedAtPastTimeInvalid() throws Exception{

        Contract contract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        Binder b = contract.serialize(new BiSerializer());

        long seconds = ZonedDateTime.now().minusDays(4).toEpochSecond();
        b.getBinder("definition").getBinder("created_at").set("seconds", seconds);
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract baseContract = new Contract();
        baseContract.deserialize(b, new BiDeserializer());

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revTemp = baseContract.createRevision(ownerPrivateKey);
        b = revTemp.serialize(new BiSerializer());

        seconds = ZonedDateTime.now().minusDays(4).minusMinutes(1).toEpochSecond();
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract revContract = new Contract();
        revContract.deserialize(b, new BiDeserializer());

        revContract.addRevokingItems(baseContract);
        revContract.setOwnerKey(manufacturePrivateKey);
        revContract.addSignerKey(manufacturePrivateKey);
        revContract.addSignerKey(ownerPrivateKey);

        revContract.seal();
        revContract.check();
        revContract.traceErrors();
        System.out.println("Revision contract is valid: " + revContract.isOk());
        assertFalse(revContract.isOk());
    }

    @Test
    public void checkRegisterRevisionCreatedAtFutureTimeInvalid() throws Exception{

        Contract contract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        Binder b = contract.serialize(new BiSerializer());

        long seconds = ZonedDateTime.now().minusDays(4).toEpochSecond();
        b.getBinder("definition").getBinder("created_at").set("seconds", seconds);
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract baseContract = new Contract();
        baseContract.deserialize(b, new BiDeserializer());

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revTemp = baseContract.createRevision(ownerPrivateKey);
        b = revTemp.serialize(new BiSerializer());

        seconds = ZonedDateTime.now().plusMinutes(1).toEpochSecond();
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract revContract = new Contract();
        revContract.deserialize(b, new BiDeserializer());

        revContract.addRevokingItems(baseContract);
        revContract.setOwnerKey(manufacturePrivateKey);
        revContract.addSignerKey(manufacturePrivateKey);
        revContract.addSignerKey(ownerPrivateKey);

        revContract.seal();
        revContract.check();
        revContract.traceErrors();
        System.out.println("Revision contract is valid: " + revContract.isOk());
        assertFalse(revContract.isOk());
    }

    @Test
    public void checkRevisionsInvalidDefinitionCreatedAt() throws Exception{

        Contract contract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        Binder b = contract.serialize(new BiSerializer());

        long seconds = ZonedDateTime.now().minusDays(5).plusSeconds(3).toEpochSecond();
        b.getBinder("definition").getBinder("created_at").set("seconds", seconds);
        b.getBinder("state").getBinder("created_at").set("seconds", seconds);

        Contract baseContract = new Contract();
        baseContract.deserialize(b, new BiDeserializer());

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Thread.sleep(4000);

        Contract revContract  = baseContract.createRevision(ownerPrivateKey);

        revContract.setOwnerKey(manufacturePrivateKey);
        revContract.addSignerKey(manufacturePrivateKey);

        revContract.seal();
        revContract.check();
        revContract.traceErrors();
        System.out.println("Revision 1 contract is valid: " + revContract.isOk());
        registerAndCheckApproved(revContract);

        Contract rev2Contract  = revContract.createRevision(manufacturePrivateKey);

        rev2Contract.setOwnerKey(ownerPrivateKey);

        rev2Contract.seal();
        rev2Contract.check();
        rev2Contract.traceErrors();
        System.out.println("Revision 2 contract is valid: " + rev2Contract.isOk());
        registerAndCheckApproved(rev2Contract);
    }

    @Test
    public void goodSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        registerAndCheckApproved(smartContract);

        ItemResult itemResult = node.waitItem(smartContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Test
    public void goodSmartContractFromDSL() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(ROOT_PATH + "NotarySmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        registerAndCheckApproved(smartContract);

        ItemResult itemResult = node.waitItem(smartContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Test
    public void goodSmartContractWithSending() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Contract gotContract = imitateSendingTransactionToPartner(smartContract);

        assertTrue(gotContract instanceof NSmartContract);
        assertTrue(gotContract instanceof NContract);

        registerAndCheckApproved(gotContract);

        ItemResult itemResult = node.waitItem(gotContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }


    @Test
    public void goodSmartContractFromDSLWithSending() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(ROOT_PATH + "NotarySmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Contract gotContract = imitateSendingTransactionToPartner(smartContract);

        assertTrue(gotContract instanceof NSmartContract);
        assertTrue(gotContract instanceof NContract);

        registerAndCheckApproved(gotContract);

        ItemResult itemResult = node.waitItem(gotContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Test
    public void goodNSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        registerAndCheckApproved(smartContract);

        ItemResult itemResult = node.waitItem(smartContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Test
    public void goodNSmartContractFromDSL() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(ROOT_PATH + "NotaryNSmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        registerAndCheckApproved(smartContract);

        ItemResult itemResult = node.waitItem(smartContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Test
    public void goodNSmartContractWithSending() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Contract gotContract = imitateSendingTransactionToPartner(smartContract);

        assertTrue(gotContract instanceof NSmartContract);
        assertTrue(gotContract instanceof NContract);

        registerAndCheckApproved(gotContract);

        ItemResult itemResult = node.waitItem(gotContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NSmartContract.NodeInfoProvider() {

        @Override
        public Set<KeyAddress> getTransactionUnitsIssuerKeys() {
            return config.getTransactionUnitsIssuerKeys();
        }

        @Override
        public String getTUIssuerName() {
            return config.getTUIssuerName();
        }

        @Override
        public int getMinPayment(String extendedType) {
            return config.getMinPayment(extendedType);
        }

        @Override
        public double getRate(String extendedType) {
            return config.getRate(extendedType);
        }

        @Override
        public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
            Set<PublicKey> set = new HashSet<>();
            if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
                set.add(config.getAuthorizedNameServiceCenterKey());
            }
            return set;
        }
    };

    @Test
    public void registerSlotContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        Contract simpleContract = new Contract(key);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys,nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), slotContract.getPrepaidKilobytesForDays(), 0.01);

//        for(Node n : nodes) {
//            n.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
//        }
        node.registerParcel(payingParcel);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        slotContract.traceErrors();
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());


        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        ZonedDateTime now;

        double days = (double) 100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length;
        double hours = days * 24;
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg1.plusSeconds(seconds);

        Set<Long> envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment
        assertNotNull(node.getLedger().getEnvironment(slotContract.getId()));


        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(key);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.get("definition.extended_type"));
        assertEquals((100 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), refilledSlotContract.getPrepaidKilobytesForDays(), 0.01);

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        long spentSeconds = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays = (double) spentSeconds / (3600 * 24);
        double spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);

        days = (double) (100 + 300 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length;
        hours = days * 24;
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, node.waitItem(slotContract.getId(), 8000).state);

        assertNull(node.getLedger().getEnvironment(slotContract.getId()));

        assertNotNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));


        // refill slot contract with U again (means add storing days). the oldest revision should removed

        SlotContract refilledSlotContract2 = (SlotContract) refilledSlotContract.createRevision(key);
        refilledSlotContract2.setNodeInfoProvider(nodeInfoProvider);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract2.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract2.check();
        refilledSlotContract2.traceErrors();
        assertTrue(refilledSlotContract2.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.get("definition.extended_type"));
        assertEquals((100 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), refilledSlotContract2.getPrepaidKilobytesForDays(), 0.01);

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg3 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract2.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract2.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        spentSeconds = (timeReg3.toEpochSecond() - timeReg1.toEpochSecond());
        spentDays = (double) spentSeconds / (3600 * 24);
        spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);

        days = (double) (100 + 300 + 300 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length;
        hours = days * 24;
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, node.waitItem(slotContract.getId(), 8000).state);
        assertNull(node.getLedger().getEnvironment(slotContract.getId()));

        assertNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));

        assertNotNull(node.getLedger().getEnvironment(refilledSlotContract2.getId()));


        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract2, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = node.waitItem(refilledSlotContract2.getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        assertEquals(envs.size(),0);
        // check if we remove environment

        assertNull(node.getLedger().getEnvironment(refilledSlotContract2.getId()));
    }


    @Test
    public void registerSlotContractWithStoringRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.setKeepRevisions(2);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), slotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + slotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length) + " days");

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        double spentKDs = 0;
        ZonedDateTime calculateExpires;

        Set<Long> envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    double days = (double) 100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length;
                    double hours = days * 24;
                    long seconds = (long) (days * 24 * 3600);
                    calculateExpires = timeReg1.plusSeconds(seconds);

                    System.out.println("days " + days);
                    System.out.println("hours " + hours);
                    System.out.println("seconds " + seconds);
                    System.out.println("reg time " + timeReg1);
                    System.out.println("expected " + calculateExpires);
                    System.out.println("found " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment

        assertNotNull(node.getLedger().getEnvironment(slotContract.getId()));

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        assertEquals(refilledSlotContract.getKeepRevisions(), 2);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(15000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.get("definition.extended_type"));
        assertEquals((100 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), refilledSlotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)simpleContract2.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> Summ: " + ((double)(simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024) + " Kb");
        System.out.println(">> " +  ((double)(100 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length)) + " days");

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        refilledSlotContract.traceErrors();
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        long spentSeconds = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays = (double) spentSeconds / (3600 * 24);
        spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 * 24 * 3600 - spentBs) /
//                (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));

        int totalLength = simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length;
        double days = (double) (100 + 300 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / totalLength;
        double hours = days * 24;
        long seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);


        System.out.println("spentSeconds " + spentSeconds);
        System.out.println("spentDays " + spentDays);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days);
        System.out.println("hours " + hours);
        System.out.println("seconds " + seconds);
        System.out.println("reg time " + timeReg1);
        System.out.println("totalLength " + totalLength);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println("expected:" + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, node.waitItem(slotContract.getId(), 8000).state);
        assertNull(node.getLedger().getEnvironment(slotContract.getId()));

        assertNotNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));

        // create additional revision of stored contract

        Contract simpleContract3 = restoredContract.createRevision(key);
        simpleContract3.setOwnerKey(key.getPublicKey());
        simpleContract3.seal();
        simpleContract3.check();
        simpleContract3.traceErrors();
        assertTrue(simpleContract3.isOk());

        registerAndCheckApproved(simpleContract3);

        // refill slot contract with U again (means add storing days). the oldest revision should removed

        SlotContract refilledSlotContract2 = (SlotContract) refilledSlotContract.createRevision(key);
        refilledSlotContract2.putTrackingContract(simpleContract3);
        refilledSlotContract2.setNodeInfoProvider(nodeInfoProvider);
        assertEquals(refilledSlotContract2.getKeepRevisions(), 2);
        refilledSlotContract2.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(15000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract2.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract2.check();
        refilledSlotContract2.traceErrors();
        assertTrue(refilledSlotContract2.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.get("definition.extended_type"));
        assertEquals((100 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), refilledSlotContract2.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract2.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract2.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)simpleContract3.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> Summ: " + ((double)(simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024) + " Kb");
        System.out.println(">> " + ((double)(100 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length)) + " days");

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg3 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract2.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract2.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        assertEquals(0, envs.size());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());


//        spentKDs += (timeReg3.toEpochSecond() - timeReg2.toEpochSecond()) * (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 * 24 * 3600 - (long)spentKDs) /
//                (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));
        long spentSeconds2 = (timeReg3.toEpochSecond() - timeReg2.toEpochSecond());
        double spentDays2 = (double) spentSeconds2 / (3600 * 24);
        spentKDs += spentDays2 * ((simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024);

        int totalLength2 = simpleContract2.getPackedTransaction().length + simpleContract3.getPackedTransaction().length;
        double days2 = (double) (100 + 300 + 300 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / totalLength2;
        double hours2 = days2 * 24;
        long seconds2 = (long) (days2 * 24 * 3600);
        calculateExpires = timeReg3.plusSeconds(seconds2);


        System.out.println("spentSeconds " + spentSeconds2);
        System.out.println("spentDays " + spentDays2);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days2);
        System.out.println("hours " + hours2);
        System.out.println("seconds " + seconds2);
        System.out.println("reg time " + timeReg3);
        System.out.println("totalLength " + totalLength2);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check additional revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract3.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract3.getId(), restoredContract.getId());

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract3.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, node.waitItem(slotContract.getId(), 8000).state);
        assertNull(node.getLedger().getEnvironment(slotContract.getId()));

        assertNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));

        assertNotNull(node.getLedger().getEnvironment(refilledSlotContract2.getId()));

        // check reducing number of keep revisions

        SlotContract refilledSlotContract3 = (SlotContract) refilledSlotContract2.createRevision(key);
        refilledSlotContract3.setKeepRevisions(1);
        refilledSlotContract3.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract3.seal();

        // payment contract
        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(15000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract3.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract3.check();
        refilledSlotContract3.traceErrors();
        assertTrue(refilledSlotContract3.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract3.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract3.get("definition.extended_type"));
        assertEquals((100 + 300 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), refilledSlotContract3.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract3.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract3.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)(100 + 300 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract3.getPackedTransaction().length) + " days");

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg4 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract3.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract3.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        assertEquals(0, envs.size());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId());
        assertEquals(0, envs.size());

        // check additional (last) revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract3.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract3.getId(), restoredContract.getId());

//        spentKDs += (timeReg4.toEpochSecond() - timeReg3.toEpochSecond()) * (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg3.plusSeconds(((100 + 300 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 * 24 * 3600 - (long) spentKDs) / simpleContract3.getPackedTransaction().length);

        long spentSeconds3 = (timeReg4.toEpochSecond() - timeReg3.toEpochSecond());
        double spentDays3 = (double) spentSeconds3 / (3600 * 24);
        spentKDs += spentDays3 * ((simpleContract2.getPackedTransaction().length + simpleContract3.getPackedTransaction().length) / 1024);

        int totalLength3 = simpleContract3.getPackedTransaction().length;
        double days3 = (double) (100 + 300 + 300 + 300 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / totalLength3;
        double hours3 = days3 * 24;
        long seconds3 = (long) (days3 * 24 * 3600);
        calculateExpires = timeReg4.plusSeconds(seconds3);


        System.out.println("spentSeconds " + spentSeconds3);
        System.out.println("spentDays " + spentDays3);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days3);
        System.out.println("hours " + hours3);
        System.out.println("seconds " + seconds3);
        System.out.println("reg time " + timeReg3);
        System.out.println("totalLength " + totalLength3);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract3.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, node.waitItem(slotContract.getId(), 8000).state);
        assertNull(node.getLedger().getEnvironment(slotContract.getId()));

        assertNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));

        assertNull(node.getLedger().getEnvironment(refilledSlotContract2.getId()));

        assertNotNull(node.getLedger().getEnvironment(refilledSlotContract3.getId()));

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract3, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = node.waitItem(refilledSlotContract3.getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract3.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        assertEquals(0, envs.size());
        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId());
        assertEquals(0, envs.size());
        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract3.getId());
        assertEquals(0, envs.size());

        // check if we remove environment

        assertNull(node.getLedger().getEnvironment(refilledSlotContract3.getId()));
    }


    @Test
    public void registerSlotContractWithUpdateStoringRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.setKeepRevisions(1);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()), slotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + slotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double) simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double) 100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length) + " days");

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        double spentKDs = 0;
        ZonedDateTime calculateExpires;

        Set<Long> envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        if(envs.size() > 0) {
            for(Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    double days = (double) 100 * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / simpleContract.getPackedTransaction().length;
                    double hours = days * 24;
                    long seconds = (long) (days * 24 * 3600);
                    calculateExpires = timeReg1.plusSeconds(seconds);

                    System.out.println("days " + days);
                    System.out.println("hours " + hours);
                    System.out.println("seconds " + seconds);
                    System.out.println("reg time " + timeReg1);
                    System.out.println("expected " + calculateExpires);
                    System.out.println("found " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment

        assertNotNull(node.getLedger().getEnvironment(slotContract.getId()));

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        registerAndCheckApproved(simpleContract2);

        System.err.println("check " + simpleContract.getId());
        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId());
        assertEquals(0, envs.size());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());


//        spentKDs += (timeReg3.toEpochSecond() - timeReg2.toEpochSecond()) * (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300 + 300) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 * 24 * 3600 - (long)spentKDs) /
//                (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));
        long spentSeconds2 = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays2 = (double) spentSeconds2 / (3600 * 24);
        spentKDs += spentDays2 * ((simpleContract.getPackedTransaction().length) / 1024);

        int totalLength2 = simpleContract2.getPackedTransaction().length;
        double days2 = (double) (100 - spentKDs) * config.getRate(NSmartContract.SmartContractType.SLOT1.name()) * 1024 / totalLength2;
        double hours2 = days2 * 24;
        long seconds2 = (long) (days2 * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds2);


        System.out.println("spentSeconds " + spentSeconds2);
        System.out.println("spentDays " + spentDays2);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days2);
        System.out.println("hours " + hours2);
        System.out.println("seconds " + seconds2);
        System.out.println("reg time " + timeReg2);
        System.out.println("totalLength " + totalLength2);

        envs = node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId());
        if(envs.size() > 0) {
            for (Long envId : envs) {
                NImmutableEnvironment environment = node.getLedger().getEnvironment(envId);
                for (ContractStorageSubscription foundCss : environment.storageSubscriptions()) {
                    System.out.println(foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }
    }

    @Test
    public void registerSlotContractCheckSetKeepRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.setKeepRevisions(5);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        TransactionPack tp_before = slotContract.getTransactionPack();
        TransactionPack tp_after = TransactionPack.unpack(tp_before.pack());

        assertEquals(((SlotContract)tp_after.getContract()).getKeepRevisions(), 5);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract.seal();

        // check saving keepRevisions
        assertEquals(refilledSlotContract.getKeepRevisions(), 5);

        tp_before = refilledSlotContract.getTransactionPack();
        tp_after = TransactionPack.unpack(tp_before.pack());

        assertEquals(((SlotContract)tp_after.getContract()).getKeepRevisions(), 5);
    }

    @Test
    public void registerSlotContractRevision() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // revision should be created without additional payments (only setKeepRevisions and putTrackingContract)
        payingParcel = ContractsService.createParcel(refilledSlotContract, paymentContract, 1, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = paymentContract;
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());
    }

    @Test
    public void registerSlotContractCheckAddNewStoringRevision() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create other stored contract

        Contract otherContract = new Contract(key);
        otherContract.seal();
        otherContract.check();
        otherContract.traceErrors();
        assertTrue(otherContract.isOk());

        registerAndCheckApproved(otherContract);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract newSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        newSlotContract.setKeepRevisions(2);
        newSlotContract.putTrackingContract(otherContract);
        newSlotContract.setNodeInfoProvider(nodeInfoProvider);
        newSlotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(newSlotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        NImmutableEnvironment ime = new NImmutableEnvironment(newSlotContract, node.getLedger());
        ime.setNameCache(new NameCache(Duration.ofMinutes(1)));
        // imitating check process on the node
        newSlotContract.beforeUpdate(ime);
        newSlotContract.check();
        newSlotContract.traceErrors();

        // check error of adding other contract (not revision of old tracking contract)
        assertFalse(newSlotContract.isOk());

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(newSlotContract.getNew().get(0).getId(), 8000).state);
    }

    @Test
    public void registerSlotContractInNewItem() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        ItemResult itemResult = node.waitItem(slotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract.getNew().get(0).getId(), 8000).state);

        itemResult = node.waitItem(refilledSlotContract.getId(), 8000);
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = node.waitItem(refilledSlotContract.getId(), 8000);
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);
        restoredPackedData = node.getLedger().getContractInStorage(slotContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = node.getLedger().getContractInStorage(refilledSlotContract.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        assertEquals(0, node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract.getId()).size());
        assertEquals(0, node.getLedger().getSubscriptionEnviromentIdsForContractId(simpleContract2.getId()).size());
        assertEquals(0, node.getLedger().getSubscriptionEnviromentIdsForContractId(slotContract.getId()).size());
        assertEquals(0, node.getLedger().getSubscriptionEnviromentIdsForContractId(refilledSlotContract.getId()).size());

        // check if we remove environment

        assertNull(node.getLedger().getEnvironment(simpleContract.getId()));
        assertNull(node.getLedger().getEnvironment(simpleContract2.getId()));
        assertNull(node.getLedger().getEnvironment(slotContract.getId()));
        assertNull(node.getLedger().getEnvironment(refilledSlotContract.getId()));
    }

    @Test
    public void registerSlotContractInNewItemBadCreate() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        // provoke error FAILED_CHECK, "Payment for slot contract is below minimum level of " + nodeConfig.getMinSlotPayment() + "U");
        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(slotContract.getExtendedType()) - 1, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        node.registerParcel(payingParcel);
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        payingParcel.getPayload().getContract().traceErrors();

        // check declined payload contract
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test
    public void registerSlotContractInNewItemBadUpdate() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        // provoke error FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(key);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        payingParcel.getPayload().getContract().traceErrors();

        // check declined payload contract
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
    }

    @Ignore
    @Test
    public void registerSlotContractInNewItemBadRevoke() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        // check if we store same contract as want

        byte[] restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeInfoProvider(nodeInfoProvider);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledSlotContract.getNew().get(0).getId(), 8000).state);

        // check root stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = node.getLedger().getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        // provoke error FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
        refilledSlotContract.setCreator(Do.list(slotIssuerPublicKeys));

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract, key);

        registerAndCheckDeclined(revokingSlotContract);
    }


    @Test(timeout = 90000)
    public void registerUnsContract() throws Exception {

        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();

        UnsName unsName = new UnsName("test"+Instant.now().getEpochSecond(), "test"+Instant.now().getEpochSecond(), "test description", "http://test.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(unsName.getUnsNameReduced()).entries.size(),2);
    }

    @Test(timeout = 90000)
    public void checkUnsContractHOLD() throws Exception {

        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        double oldValue = config.getRate(NSmartContract.SmartContractType.UNS1.name());
        config.setRate(NSmartContract.SmartContractType.UNS1.name(),10.0/(24*3600*nodeInfoProvider.getMinPayment(NSmartContract.SmartContractType.UNS1.name())));
        config.setHoldDuration(Duration.ofSeconds(10));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        String name = "test"+Instant.now().getEpochSecond();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns3 = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                name, name, "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns3.addSignatureToSeal(authorizedNameServiceKey);
        uns3.addSignatureToSeal(randomPrivKey);
        uns3.addSignatureToSeal(TestKeys.privateKey(8));
        uns3.check();
        uns3.traceErrors();

        UnsContract uns2 = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                name, name, "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns2.addSignatureToSeal(authorizedNameServiceKey);
        uns2.addSignatureToSeal(randomPrivKey);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        UnsContract uns = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                name, name, "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns.addSignatureToSeal(authorizedNameServiceKey);
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(uns.getExtendedType()), stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(name).entries.size(),1);
        nodes.forEach((n) -> n.getLedger().clearExpiredNameRecords(config.getHoldDuration()));
        Thread.sleep(11000);
        nodes.forEach((n) -> n.getLedger().clearExpiredNameRecords(config.getHoldDuration()));
        NameRecordModel nr = ledger.getNameRecord(name);
        assertEquals(nr.entries.size(),1);
        assertTrue(nr.expires_at.isBefore(ZonedDateTime.now()));

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(uns.getExtendedType()), stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        Thread.sleep(11000);
        nodes.forEach((n) -> n.getLedger().clearExpiredNameRecords(config.getHoldDuration()));
        nr = ledger.getNameRecord(name);
        assertNull(nr);


        paymentContract = getApprovedTUContract();


        payingParcel = ContractsService.createPayingParcel(uns3.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(uns.getExtendedType()), stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        ItemResult ir = node.waitItem(payingParcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns3.getNew().get(0).getId(), 8000).state);


        config.setRate(NSmartContract.SmartContractType.UNS1.name(),oldValue);
    }


    @Test(timeout = 90000)
    public void checkUnsRevocation() throws Exception {

        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        String name = "test"+Instant.now().getEpochSecond();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                name, name, "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns.addSignatureToSeal(authorizedNameServiceKey);
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        UnsContract uns2 = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                name, name, "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns2.addSignatureToSeal(authorizedNameServiceKey);
        uns2.addSignatureToSeal(randomPrivKey);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        //REGISTER UNS1
        Contract paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(uns.getExtendedType()), stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(name).entries.size(),1);


        //REVOKE UNS1
        Contract revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns);
        revokingContract.seal();

        paymentContract = getApprovedTUContract();
        Parcel parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(uns.getId(), 8000).state);

        assertNull(ledger.getNameRecord(name));

        //REGISTER UNS2
        paymentContract = getApprovedTUContract();
        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment(uns.getExtendedType()), stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(name).entries.size(),1);
    }

    @Test(timeout = 90000)
    public void registerUnsContractRevision() throws Exception {

        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);
        PrivateKey randomPrivKey4 = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract1 = new Contract(TestKeys.privateKey(1));
        referencesContract1.seal();

        Contract referencesContract2 = new Contract(TestKeys.privateKey(2));
        referencesContract2.seal();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);

        uns.addSignerKey(TestKeys.privateKey(1));
        uns.addSignerKey(randomPrivKey1);
        uns.addSignerKey(randomPrivKey3);
        uns.addSignerKey(authorizedNameServiceKey);

        uns.seal();

        UnsName unsNameToChange = new UnsName("change"+Instant.now().getEpochSecond(), "change"+Instant.now().getEpochSecond(), "test description", "http://test.com");
        UnsName unsNameToAdd = new UnsName("add"+Instant.now().getEpochSecond(), "add"+Instant.now().getEpochSecond(), "test description", "http://test.com");
        UnsName unsNameToRemove = new UnsName("remove"+Instant.now().getEpochSecond(), "remove"+Instant.now().getEpochSecond(), "test description", "http://test.com");

        UnsRecord unsRecordToChange = new UnsRecord(randomPrivKey1.getPublicKey());
        UnsRecord unsRecordToAdd = new UnsRecord(randomPrivKey2.getPublicKey());
        UnsRecord unsRecordToRemove = new UnsRecord(randomPrivKey3.getPublicKey());

        unsNameToChange.addUnsRecord(unsRecordToChange);
        unsNameToChange.addUnsRecord(unsRecordToRemove);

        unsNameToRemove.addUnsRecord(new UnsRecord(referencesContract1.getId()));
        unsNameToAdd.addUnsRecord(new UnsRecord(referencesContract2.getId()));


        uns.addUnsName(unsNameToChange);
        uns.addUnsName(unsNameToRemove);
        uns.addOriginContract(referencesContract1);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract1.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract1.getId(), 8000).state);

        paymentContract = getApprovedTUContract();

        parcel = ContractsService.createParcel(referencesContract2.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract2.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(unsNameToChange.getUnsNameReduced()).entries.size(),2);
        assertEquals(ledger.getNameRecord(unsNameToRemove.getUnsNameReduced()).entries.size(),1);

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey2);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(randomPrivKey4);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns = (UnsContract) uns.createRevision(keys);
        uns.addUnsName(unsNameToAdd);
        uns.addOriginContract(referencesContract2);

        uns.removeName(unsNameToRemove.getUnsName());
        UnsName unsNameToChangeCopy = uns.getUnsName(unsNameToChange.getUnsName());
        for(int i = 0; i < unsNameToChangeCopy.getUnsRecords().size();i++) {
            UnsRecord unsRecord = unsNameToChangeCopy.getUnsRecord(i);
            if(unsRecord.getAddresses().equals(unsRecordToRemove.getAddresses())) {
                unsNameToChangeCopy.getUnsRecords().remove(i);
                i--;
                continue;
            }

            if(unsRecord.getAddresses().equals(unsRecordToChange.getAddresses())) {
                unsRecord.getAddresses().clear();
                unsRecord.getAddresses().add(randomPrivKey4.getPublicKey().getShortAddress());
                continue;
            }
        }
        unsNameToChangeCopy.addUnsRecord(unsRecordToAdd);
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();


        paymentContract = getApprovedTUContract();


        payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(unsNameToChange.getUnsNameReduced()).entries.size(),2);
        assertEquals(ledger.getNameRecord(unsNameToAdd.getUnsNameReduced()).entries.size(),1);
        assertNull(ledger.getNameRecord(unsNameToRemove.getUnsNameReduced()));
    }


    @Test(timeout = 90000)
    public void registerUnsContractOriginRevision() throws Exception {

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract1 = new Contract(TestKeys.privateKey(1));
        referencesContract1.seal();

        String reducedName = "name"+Instant.now().getEpochSecond();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                reducedName, "change"+Instant.now().getEpochSecond(), "test description", "http://test.com", referencesContract1);

        uns.addSignerKey(TestKeys.privateKey(1));
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract1.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract1.getId(), 8000).state);


        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(1, ledger.getNameRecord(reducedName).entries.size());

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(1));
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);


        Contract referencesContract2 = referencesContract1.createRevision(TestKeys.privateKey(1));
        referencesContract2.setOwnerKeys(TestKeys.privateKey(8));
        referencesContract2.seal();
        paymentContract = getApprovedTUContract();

        parcel = ContractsService.createParcel(referencesContract2.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract2.getId(), 8000).state);

        UnsContract unsOriginal = uns;
        unsOriginal.removeReferencedItem(referencesContract1);

        //Create revision to add payment without any changes. Should be declined
        uns = (UnsContract) unsOriginal.createRevision(keys);
        uns.addOriginContract(referencesContract1);
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(reducedName).entries.size(),1);


        //Create revision to add payment without any changes. Should be declined
        uns = (UnsContract) unsOriginal.createRevision(keys);
        uns.addOriginContract(referencesContract2);
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        ItemResult ir = node.waitItem(payingParcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(reducedName).entries.size(),1);
    }

    @Test(timeout = 90000)
    public void checkUnsContractRefContractNotApproved() throws Exception {

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                "test"+Instant.now().getEpochSecond(), "test"+Instant.now().getEpochSecond(), "test description", "http://test.com", referencesContract);

        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(TestKeys.privateKey(8));
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

    }

    @Test(timeout = 90000)
    public void checkUnsContractRefContractMissing() throws Exception {

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                "test"+Instant.now().getEpochSecond(), "test"+Instant.now().getEpochSecond(), "test description", "http://test.com", referencesContract);

        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(TestKeys.privateKey(8));
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

    }

    @Test(timeout = 90000)
    public void checkUnsContractRefContractSigMissing() throws Exception {

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(9));
        referencesContract.seal();

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                "test"+Instant.now().getEpochSecond(), "test"+Instant.now().getEpochSecond(), "test description", "http://test.com", referencesContract);

        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

    }

    @Test(timeout = 90000)
    public void checkUnsContractAddressSigMissing() throws Exception {
        PrivateKey randomPrivKey = new PrivateKey(2048);
        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider,
                "test"+Instant.now().getEpochSecond(), "test"+Instant.now().getEpochSecond(), "test description", "http://test.com", randomPrivKey.getPublicKey());

        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

    }

    @Ignore
    @Test(timeout = 90000)
    public void registerUnsContractFromDsl() throws Exception {

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        UnsContract uns = UnsContract.fromDslFile(ROOT_PATH + "uns/simple_uns_contract.yml");
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKey(manufacturePrivateKeys.iterator().next());
        uns.seal();
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);
    }

    @Test(timeout =  90000)
    public void checkUnsContractForBusyName() throws Exception{

        PrivateKey randomPrivateKey1 = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract nameContract1 = new Contract(TestKeys.privateKey(8));
        nameContract1.seal();

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(manufacturePrivateKey);
        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());
        UnsContract uns1 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);

        uns1.addSignerKey(authorizedNameServiceKey);
        uns1.seal();

        String name = "testname"+Instant.now().getEpochSecond();

        UnsName unsName = new UnsName(name, name, "testname description", "http://testname.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivateKey1.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(nameContract1.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns1.addUnsName(unsName);
        uns1.addOriginContract(nameContract1);

        uns1.setNodeInfoProvider(nodeInfoProvider);
        uns1.seal();
        uns1.addSignatureToSeal(randomPrivateKey1);
        uns1.addSignatureToSeal(TestKeys.privateKey(8));
        uns1.check();
        uns1.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(nameContract1.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(nameContract1.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns1.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns1.getNew().get(0).getId(), 8000).state);

        //stage 2
        Contract nameContract2 = new Contract(authorizedNameServiceKey);
        nameContract2.seal();

        UnsContract uns2 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns2.addSignerKey(authorizedNameServiceKey);
        uns2.seal();

        PrivateKey randomPrivateKey2 = new PrivateKey(2048);
        unsName = new UnsName(name, name, "testname description", "http://testname.com");
        unsRecord1 = new UnsRecord(randomPrivateKey2.getPublicKey());
        unsRecord2 = new UnsRecord(nameContract2.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns2.addUnsName(unsName);
        uns2.addOriginContract(nameContract2);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivateKey2);
        uns2.addSignatureToSeal(TestKeys.privateKey(9));
        uns2.check();
        uns2.traceErrors();

        paymentContract = getApprovedTUContract();

        parcel = ContractsService.createParcel(nameContract2.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(nameContract2.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

    }

    @Test(timeout =  90000)
    public void checkUnsContractForBusyAddress() throws Exception{

        PrivateKey randomPrivateKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract nameContract1 = new Contract(TestKeys.privateKey(8));
        nameContract1.seal();

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(manufacturePrivateKey);
        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());

        UnsContract uns1 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns1.addSignerKey(authorizedNameServiceKey);

        UnsName unsName = new UnsName("testbusyaddress"+Instant.now().getEpochSecond(), "testbusyaddress"+Instant.now().getEpochSecond(), "testbusyaddress description", "http://testbusyaddress.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivateKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(nameContract1.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns1.addUnsName(unsName);
        uns1.addOriginContract(nameContract1);

        uns1.seal();
        uns1.addSignatureToSeal(randomPrivateKey);
        uns1.addSignatureToSeal(TestKeys.privateKey(8));
        uns1.check();
        uns1.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(nameContract1.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(nameContract1.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns1.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns1.getNew().get(0).getId(), 8000).state);

        //stage 2
        Contract nameContract2 = new Contract(TestKeys.privateKey(9));
        nameContract2.seal();

        UnsContract uns2 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns2.addSignerKey(authorizedNameServiceKey);
        uns2.seal();

        unsName = new UnsName("testbusyaddress"+Instant.now().getEpochSecond(), "testbusyaddress"+Instant.now().getEpochSecond(), "testbusyaddress description", "http://testbusyaddress.com");
        unsRecord1 = new UnsRecord(randomPrivateKey.getPublicKey());
        unsRecord2 = new UnsRecord(nameContract2.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns2.addUnsName(unsName);
        uns2.addOriginContract(nameContract2);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivateKey);
        uns2.addSignatureToSeal(TestKeys.privateKey(9));
        uns2.check();
        uns2.traceErrors();

        paymentContract = getApprovedTUContract();

        parcel = ContractsService.createParcel(nameContract2.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(nameContract2.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

    }

    @Test(timeout =  90000)
    public void checkUnsContractForBusyOrigin() throws Exception{

        PrivateKey randomPrivateKey1 = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract nameContract = new Contract(TestKeys.privateKey(8));
        nameContract.seal();

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(manufacturePrivateKey);
        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());

        UnsContract uns1 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns1.addSignerKey(authorizedNameServiceKey);
        uns1.seal();

        UnsName unsName = new UnsName("testbusyorigin"+Instant.now().getEpochSecond(), "testbusyorigin"+Instant.now().getEpochSecond(), "testbusyorigin description", "http://testbusyorigin.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivateKey1.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(nameContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns1.addUnsName(unsName);
        uns1.addOriginContract(nameContract);

        uns1.seal();
        uns1.addSignatureToSeal(randomPrivateKey1);
        uns1.addSignatureToSeal(TestKeys.privateKey(8));
        uns1.check();
        uns1.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(nameContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(nameContract.getId(), 8000).state);

        paymentContract = getApprovedTUContract();


        Parcel payingParcel = ContractsService.createPayingParcel(uns1.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns1.getNew().get(0).getId(), 8000).state);

        //stage 2
        UnsContract uns2 = ContractsService.createUnsContract(manufacturePrivateKeys, manufacturePublicKeys, nodeInfoProvider);
        uns2.addSignerKey(authorizedNameServiceKey);
        uns2.seal();

        PrivateKey randomPrivateKey2 = new PrivateKey(2048);
        unsName = new UnsName("testbusyorigin"+Instant.now().getEpochSecond(), "testbusyorigin"+Instant.now().getEpochSecond(), "testbusyorigin description", "http://testbusyorigin.com");
        unsRecord1 = new UnsRecord(randomPrivateKey2.getPublicKey());
        unsRecord2 = new UnsRecord(nameContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns2.addUnsName(unsName);
        uns2.addOriginContract(nameContract);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns1.addSignatureToSeal(randomPrivateKey2);
        uns1.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        node.registerParcel(payingParcel);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.DECLINED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(uns2.getNew().get(0).getId(), 8000).state);
    }

    @Test
    public void checkUnsContractForExpiresTime() throws Exception {
        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();

        String reducedName = "testTime" + Instant.now().getEpochSecond();

        UnsName unsName = new UnsName(reducedName, reducedName, "test description", "http://test.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        paymentContract = getApprovedTUContract();

        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);

        // check remaining balance
        assertEquals(1470 * config.getRate(NSmartContract.SmartContractType.UNS1.name()), uns.getPrepaidNamesForDays(), 0.01);

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(unsName.getUnsNameReduced()).entries.size(), 2);

        // check calculation expiration time
        double days = (double) 1470 * config.getRate(NSmartContract.SmartContractType.UNS1.name()) / uns.getUnsName(reducedName).getRecordsCount();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg1.plusSeconds(seconds);

        NameRecordModel nrModel = node.getLedger().getNameRecord(reducedName);
        if(nrModel != null) {
            System.out.println(nrModel.expires_at);
            assertAlmostSame(calculateExpires, nrModel.expires_at, 5);
        } else {
            fail("NameRecordModel was not found");
        }

        // refill uns contract with U (means add storing days).

        UnsContract refilledUnsContract = (UnsContract) uns.createRevision(manufacturePrivateKeys.iterator().next());
        refilledUnsContract.setNodeInfoProvider(nodeInfoProvider);
        refilledUnsContract.seal();

        // pack & unpack for validating reference
        TransactionPack tp_before = refilledUnsContract.getTransactionPack();
        tp_before.addReferencedItem(referencesContract);
        TransactionPack tp_after = TransactionPack.unpack(tp_before.pack());

        refilledUnsContract = (UnsContract) tp_after.getContract();
        refilledUnsContract.setNodeInfoProvider(nodeInfoProvider);
        refilledUnsContract.addSignerKey(manufacturePrivateKeys.iterator().next());
        refilledUnsContract.addSignerKey(randomPrivKey);
        refilledUnsContract.addSignerKey(TestKeys.privateKey(8));
        refilledUnsContract.addSignerKey(authorizedNameServiceKey);
        refilledUnsContract.seal();

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(refilledUnsContract.getTransactionPack(), paymentContract, 1, 1000, stepaPrivateKeys, false);

        refilledUnsContract.check();
        refilledUnsContract.traceErrors();
        assertTrue(refilledUnsContract.isOk());

        // check remaining balance
        assertEquals(2470 * config.getRate(NSmartContract.SmartContractType.UNS1.name()), refilledUnsContract.getPrepaidNamesForDays(), 0.01);

        node.registerParcel(payingParcel);
        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(refilledUnsContract.getNew().get(0).getId(), 8000).state);

        assertEquals(ledger.getNameRecord(reducedName).entries.size(), 2);
        assertEquals(refilledUnsContract.getUnsName(reducedName).getRecordsCount(), 2);

        // check prolongation
        long spentSeconds = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentNDs = (double) spentSeconds / (3600 * 24);

        days = (double) (2470 - spentNDs) * config.getRate(NSmartContract.SmartContractType.UNS1.name()) / refilledUnsContract.getUnsName(reducedName).getRecordsCount();
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);

        nrModel = node.getLedger().getNameRecord(reducedName);
        if(nrModel != null) {
            System.out.println(nrModel.expires_at);
            assertAlmostSame(calculateExpires, nrModel.expires_at, 5);
        } else {
            fail("NameRecordModel was not found");
        }
    }

    @Test
    public void goodNSmartContractFromDSLWithSending() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(ROOT_PATH + "NotaryNSmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Contract gotContract = imitateSendingTransactionToPartner(smartContract);

        assertTrue(gotContract instanceof NSmartContract);
        assertTrue(gotContract instanceof NContract);

        registerAndCheckApproved(gotContract);

        ItemResult itemResult = node.waitItem(gotContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }

    @Ignore
    @Test
    public void checkRevisionExpiresAtReсentPastTime() throws Exception{

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revContract  = baseContract.createRevision(ownerPrivateKey);
        revContract.setExpiresAt(revContract.getCreatedAt().minusMinutes(1));
        revContract.seal();

        revContract.check();
        revContract.traceErrors();
        assertTrue(revContract.isOk());
    }

    @Ignore
    @Test
    public void checkRevisionExpiresAtDistantPastTime() throws Exception{

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revContract  = baseContract.createRevision(ownerPrivateKey);
        revContract.setExpiresAt(ZonedDateTime.of(LocalDateTime.MIN.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC));
        revContract.seal();

        revContract.check();
        revContract.traceErrors();
        assertTrue(revContract.isOk());
    }

    @Ignore
    @Test
    public void checkRevisionExpiresAtReсentFutureTime() throws Exception{

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revContract  = baseContract.createRevision(ownerPrivateKey);
        revContract.setExpiresAt(revContract.getCreatedAt().plusMinutes(1));
        revContract.seal();

        revContract.check();
        revContract.traceErrors();
        assertTrue(revContract.isOk());
    }

    @Ignore
    @Test
    public void checkRevisionExpiresAtDistantFutureTime() throws Exception{

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        System.out.println("Base contract is valid: " + baseContract.isOk());
        registerAndCheckApproved(baseContract);

        Contract revContract  = baseContract.createRevision(ownerPrivateKey);
        revContract.setExpiresAt(ZonedDateTime.of(LocalDateTime.MAX.truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC));
        revContract.seal();

        revContract.check();
        revContract.traceErrors();
        assertTrue(revContract.isOk());
    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithApprovedParcelAndRealPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        contract.addSignerKey(stepaPrivateKeys.iterator().next());
        contract.seal();
        contract.check();
        contract.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract contractTU = InnerContractsService.createFreshTU(100, keys, true);
        contractTU.check();
        //stepaTU.setIsTU(true);
        contractTU.traceErrors();
        node.registerItem(contractTU);
        ItemResult itemResult = node.waitItem(contractTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(contract, contractTU, 1, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        node.nodeStats.collect(ledger, config);

//        int lastMonth = node.nodeStats.lastMonthPaidAmount;
//        int thisMonth = node.nodeStats.thisMonthPaidAmount;
//        int yesterday = node.nodeStats.yesterdayPaidAmount;
//        int today = node.nodeStats.todayPaidAmount;

        System.out.println("Statistic before");
//        System.out.println("last month :  " + lastMonth);
//        System.out.println("this month :  " + thisMonth);
//        System.out.println("yesterday  :  " + yesterday);
//        System.out.println("today      :  " + today);


        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

        System.out.println("Statistic after");
//        System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
//        System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
//        System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
//        System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

//        assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
//        assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 1);
//        assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
//        assertEquals(node.nodeStats.todayPaidAmount - today, 1);

    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithApprovedParcelAndTestPayment() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        contract.addSignerKey(stepaPrivateKeys.iterator().next());
        contract.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        contract.seal();
        contract.check();
        contract.traceErrors();

        PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());

        Contract contractTU = InnerContractsService.createFreshTU(100, keys, true);
        contractTU.check();
        contractTU.traceErrors();
        node.registerItem(contractTU);
        ItemResult itemResult = node.waitItem(contractTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(contract, contractTU, 1, stepaPrivateKeys, true);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertTrue(parcel.getPayloadContract().isLimitedForTestnet());


        node.nodeStats.collect(ledger, config);

//        int lastMonth = node.nodeStats.lastMonthPaidAmount;
//        int thisMonth = node.nodeStats.thisMonthPaidAmount;
//        int yesterday = node.nodeStats.yesterdayPaidAmount;
//        int today = node.nodeStats.todayPaidAmount;

//        System.out.println("Statistic before");
//        System.out.println("last month :  " + lastMonth);
//        System.out.println("this month :  " + thisMonth);
//        System.out.println("yesterday  :  " + yesterday);
//        System.out.println("today      :  " + today);


        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

//        System.out.println("Statistic after");
//        System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
//        System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
//        System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
//        System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

//        assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
//        assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 0);
//        assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
//        assertEquals(node.nodeStats.todayPaidAmount - today, 0);


    }
    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithDeclinedParcel() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        contract.addSignerKey(stepaPrivateKeys.iterator().next());
        contract.seal();
        contract.check();
        contract.traceErrors();


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Contract contractTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        contractTU.addSignerKey(manufacturePrivateKey);
        contractTU.seal();
        contractTU.check();
        contractTU.traceErrors();

        Parcel parcel = ContractsService.createParcel(contract, contractTU, 1, stepaPrivateKeys);

        assertTrue(parcel.getPaymentContract().isOk());
        assertTrue(parcel.getPayloadContract().isOk());

        node.nodeStats.collect(ledger, config);

        //int lastMonth = node.nodeStats.lastMonthPaidAmount;
        //int thisMonth = node.nodeStats.thisMonthPaidAmount;
        //int yesterday = node.nodeStats.yesterdayPaidAmount;
        //int today = node.nodeStats.todayPaidAmount;

        //System.out.println("Statistic before");
        //System.out.println("last month :  " + lastMonth);
        //System.out.println("this month :  " + thisMonth);
        //System.out.println("yesterday  :  " + yesterday);
        //System.out.println("today      :  " + today);

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

        //System.out.println("Statistic after");
        //System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
        //System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
        //System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
        //System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

        //assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
        //assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 0);
        //assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
        //assertEquals(node.nodeStats.todayPaidAmount - today, 0);
    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithApprovedPayingParcelSlot() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing
        Contract simpleContract = new Contract(key);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing
        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing
        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 170, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        node.nodeStats.collect(ledger, config);

        //int lastMonth = node.nodeStats.lastMonthPaidAmount;
        //int thisMonth = node.nodeStats.thisMonthPaidAmount;
        //int yesterday = node.nodeStats.yesterdayPaidAmount;
        //int today = node.nodeStats.todayPaidAmount;

        //System.out.println("Statistic before");
        //System.out.println("last month :  " + lastMonth);
        //System.out.println("this month :  " + thisMonth);
        //System.out.println("yesterday  :  " + yesterday);
        //System.out.println("today      :  " + today);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getNew().get(0).getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

        //System.out.println("Statistic after");
        //System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
        //System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
        //System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
        //System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

        //assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
        //assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 1);
        //assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
        //assertEquals(node.nodeStats.todayPaidAmount - today, 1);
    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithDeclinedPayingParcelSlot() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing
        Contract simpleContract = new Contract(key);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing
        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys, nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);

        // illegal payment contract
        Contract contractTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        contractTU.addSignerKey(key);
        contractTU.seal();
        contractTU.check();
        contractTU.traceErrors();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), contractTU, 1, 170, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        node.nodeStats.collect(ledger, config);

        //int lastMonth = node.nodeStats.lastMonthPaidAmount;
        //int thisMonth = node.nodeStats.thisMonthPaidAmount;
        //int yesterday = node.nodeStats.yesterdayPaidAmount;
        //int today = node.nodeStats.todayPaidAmount;

        //System.out.println("Statistic before");
        //System.out.println("last month :  " + lastMonth);
        //System.out.println("this month :  " + thisMonth);
        //System.out.println("yesterday  :  " + yesterday);
        //System.out.println("today      :  " + today);

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

        //System.out.println("Statistic after");
        //System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
        //System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
        //System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
        //System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

        //assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
        //assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 0);
        //assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
        //assertEquals(node.nodeStats.todayPaidAmount - today, 0);
    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithApprovedPayingParcelUns() throws Exception {

        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();

        UnsName unsName = new UnsName("test_stat" + Instant.now().getEpochSecond(), "test_stat" + Instant.now().getEpochSecond(), "test description", "http://test.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        paymentContract = getApprovedTUContract();

        parcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1800, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        node.nodeStats.collect(ledger, config);

//        int lastMonth = node.nodeStats.lastMonthPaidAmount;
//        int thisMonth = node.nodeStats.thisMonthPaidAmount;
//        int yesterday = node.nodeStats.yesterdayPaidAmount;
//        int today = node.nodeStats.todayPaidAmount;

//        System.out.println("Statistic before");
//        System.out.println("last month :  " + lastMonth);
//        System.out.println("this month :  " + thisMonth);
//        System.out.println("yesterday  :  " + yesterday);
//        System.out.println("today      :  " + today);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = uns.getNew().get(0);
        }
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getNew().get(0).getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

//        System.out.println("Statistic after");
//        System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
//        System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
//        System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
//        System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

//        assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
//        assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 1801);
//        assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
//        assertEquals(node.nodeStats.todayPaidAmount - today, 1801);
//        assertEquals(node.nodeStats.todayPaidAmount - today, 1801);
    }

    @Test(timeout = 90000)
    public void checkPaymentStatisticsWithDeclinedPayingParcelUns() throws Exception {

        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();

        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();

        UnsName unsName = new UnsName("test_stat_declined" + Instant.now().getEpochSecond(), "test_stat_declined" + Instant.now().getEpochSecond(), "test description", "http://test.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();

        Contract paymentContract = getApprovedTUContract();

        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);

        node.registerParcel(parcel);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        // illegal payment contract
        Contract contractTU = Contract.fromDslFile(ROOT_PATH + "StepaTU.yml");
        contractTU.addSignerKey(manufacturePrivateKeys.iterator().next());
        contractTU.seal();
        contractTU.check();
        contractTU.traceErrors();

        parcel = ContractsService.createPayingParcel(uns.getTransactionPack(), contractTU, 1, 1500, stepaPrivateKeys, false);

        parcel.getPayment().getContract().paymentCheck(config.getTransactionUnitsIssuerKeys());
        parcel.getPayment().getContract().traceErrors();
        parcel.getPayload().getContract().check();
        parcel.getPayload().getContract().traceErrors();

        assertTrue(parcel.getPaymentContract().isOk());
        assertFalse(parcel.getPaymentContract().isLimitedForTestnet());
        assertTrue(parcel.getPayloadContract().isOk());
        assertFalse(parcel.getPayloadContract().isLimitedForTestnet());

        node.nodeStats.collect(ledger, config);

        //int lastMonth = node.nodeStats.lastMonthPaidAmount;
        //int thisMonth = node.nodeStats.thisMonthPaidAmount;
        //int yesterday = node.nodeStats.yesterdayPaidAmount;
        //int today = node.nodeStats.todayPaidAmount;

        //System.out.println("Statistic before");
        //System.out.println("last month :  " + lastMonth);
        //System.out.println("this month :  " + thisMonth);
        //System.out.println("yesterday  :  " + yesterday);
        //System.out.println("today      :  " + today);

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);

        node.nodeStats.collect(ledger,config);

        //System.out.println("Statistic after");
        //System.out.println("last month :  " + node.nodeStats.lastMonthPaidAmount );
        //System.out.println("this month :  " + node.nodeStats.thisMonthPaidAmount);
        //System.out.println("yesterday  :  " + node.nodeStats.yesterdayPaidAmount);
        //System.out.println("today      :  " + node.nodeStats.todayPaidAmount);

        //assertEquals(node.nodeStats.lastMonthPaidAmount - lastMonth, 0);
        //assertEquals(node.nodeStats.thisMonthPaidAmount - thisMonth, 0);
        //assertEquals(node.nodeStats.yesterdayPaidAmount - yesterday, 0);
        //assertEquals(node.nodeStats.todayPaidAmount - today, 0);
    }

    @Test(timeout = 90000)
    public void transactionalValidUntil_good() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        if (stepaCoins.getTransactional() == null)
            stepaCoins.createTransactionalSection();
        stepaCoins.getTransactional().setValidUntil(ZonedDateTime.now().plusSeconds(Config.validUntilTailTime.getSeconds()*2).toEpochSecond());
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);
        assertTrue(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void transactionalValidUntil_timeIsOver() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        if (stepaCoins.getTransactional() == null)
            stepaCoins.createTransactionalSection();
        stepaCoins.getTransactional().setValidUntil(ZonedDateTime.now().plusMonths(-1).toEpochSecond());
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);
        assertFalse(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }

    @Test(timeout = 90000)
    public void transactionalValidUntil_timeEnds() throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        if (stepaCoins.getTransactional() == null)
            stepaCoins.createTransactionalSection();
        stepaCoins.getTransactional().setValidUntil(ZonedDateTime.now().plusSeconds(Config.validUntilTailTime.getSeconds()/2).toEpochSecond());
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();
        stepaCoins.check();
        stepaCoins.traceErrors();

        Parcel parcel = createParcelWithFreshTU(stepaCoins, stepaPrivateKeys);
        assertFalse(parcel.getPayloadContract().isOk());

        node.registerParcel(parcel);
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.DECLINED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
    }


    @Test
    public void itemResultWithErrors() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("value", Do.listOf("0", "1"));
        contract.addPermission(modifyDataPermission);
        contract.getStateData().set("value", "1");
        contract.seal();
        node.registerItem(contract);
        node.waitItem(contract.getId(), 5000);

        Contract contract2 = contract.createRevision();
        contract2.addSignerKey(TestKeys.privateKey(0));
        contract2.getStateData().set("value", "2");
        contract2.seal();
        node.registerItem(contract2);

        ItemResult itemResult = node.waitItem(contract2.getId(), 5000);
        System.out.println("itemResult: " + itemResult.state);
        System.out.println("errors: " + itemResult.errors);
        if (itemResult.errors != null) {
            for (ErrorRecord er : itemResult.errors)
                System.out.println("  error: " + er);
        }
        assertNotNull(itemResult.errors);
        assertEquals(true, itemResult.errors.size() > 0);
    }


    @Test
    public void removeEnvironment_uns1() throws Exception {
        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(Arrays.asList(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"))));

        UnsContract uns = ContractsService.createUnsContract(new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), new HashSet<>(Arrays.asList(TestKeys.publicKey(0))), nodeInfoProvider);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.seal();

        Contract paymentContract = getApprovedTUContract();
        Contract referencesContract = new Contract(TestKeys.privateKey(8));
        referencesContract.seal();
        Parcel parcel = ContractsService.createParcel(referencesContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);
        node.registerParcel(parcel);
        synchronized (tuContractLock) {tuContract = parcel.getPaymentContract();}
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(referencesContract.getId(), 8000).state);

        PrivateKey randomPrivKey = new PrivateKey(2048);

        String name = HashId.createRandom().toBase64String();
        UnsName unsName = new UnsName(name, name, "test description", "http://test.com");
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();
        paymentContract = getApprovedTUContract();
        parcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, 1470, stepaPrivateKeys, false);
        node.registerParcel(parcel);
        synchronized (tuContractLock) {tuContract = parcel.getPayloadContract().getNew().get(0);}
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        for (Node n : nodes) {
            // check environments table
            assertNotNull(n.getLedger().getSlotContractBySlotId(uns.getId()));
            // check name_storage table
            assertNotNull(n.getLedger().getNameRecord(name));
            // check name_entry table
            PreparedStatement ps =  ((PostgresLedger)n.getLedger()).getDb().statement(
                    "select count(*) from name_entry where origin=? or short_addr=? or long_addr=?",
                    referencesContract.getId().getDigest(),
                    randomPrivKey.getPublicKey().getShortAddress().toString(),
                    randomPrivKey.getPublicKey().getLongAddress().toString());
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));

            // now remove environment
            n.getLedger().removeEnvironment(uns.getId());

            // check environments table
            assertNull(n.getLedger().getSlotContractBySlotId(uns.getId()));
            // check name_storage table
            assertNull(n.getLedger().getNameRecord(name));
            // check name_entry table
            ps =  ((PostgresLedger)n.getLedger()).getDb().statement(
                    "select count(*) from name_entry where origin=? or short_addr=? or long_addr=?",
                    referencesContract.getId().getDigest(),
                    randomPrivKey.getPublicKey().getShortAddress().toString(),
                    randomPrivKey.getPublicKey().getLongAddress().toString());
            rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

    }


    @Test
    public void removeEnvironment_slot1() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(Arrays.asList(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"))));

        Contract paymentContract = getApprovedTUContract();
        Contract trackingContract = new Contract(TestKeys.privateKey(0));
        trackingContract.seal();
        Parcel parcel = ContractsService.createParcel(trackingContract.getTransactionPack(), paymentContract, 1, stepaPrivateKeys, false);
        node.registerParcel(parcel);
        synchronized (tuContractLock) {tuContract = parcel.getPaymentContract();}
        node.waitParcel(parcel.getId(), 8000);
        assertEquals(ItemState.APPROVED, node.waitItem(trackingContract.getId(), 8000).state);

        SlotContract slotContract = ContractsService.createSlotContract(new HashSet<>(Arrays.asList(TestKeys.privateKey(0))), new HashSet<>(Arrays.asList(TestKeys.publicKey(0))), nodeInfoProvider);
        slotContract.putTrackingContract(trackingContract);
        slotContract.seal();
        paymentContract = getApprovedTUContract();
        parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        node.registerParcel(parcel);
        synchronized (tuContractLock) {tuContract = parcel.getPayloadContract().getNew().get(0);}
        node.waitParcel(parcel.getId(), 8000);

        assertEquals(ItemState.APPROVED, node.waitItem(parcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(slotContract.getNew().get(0).getId(), 8000).state);

        for (Node n : nodes) {
            NImmutableEnvironment env  = n.getLedger().getEnvironment(slotContract.getId());
            // check environments table
            assertNotNull(n.getLedger().getSlotContractBySlotId(slotContract.getId()));
            // check contract_storage table
            assertNotNull(n.getLedger().getContractInStorage(trackingContract.getId()));
            // check contract_subscription table
            PreparedStatement ps =  ((PostgresLedger)n.getLedger()).getDb().statement(
                    "select count(*) from contract_subscription where contract_storage_id=?",
                    ((NContractStorageSubscription)env.storageSubscriptions().iterator().next()).getId());
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));

            // now remove environment
            n.getLedger().removeEnvironment(slotContract.getId());

            // check environments table
            assertNull(n.getLedger().getSlotContractBySlotId(slotContract.getId()));
            // check contract_storage table
            assertNull(n.getLedger().getContractInStorage(trackingContract.getId()));
            // check contract_subscription table
            ps =  ((PostgresLedger)n.getLedger()).getDb().statement(
                    "select count(*) from contract_subscription where contract_storage_id=?",
                    ((NContractStorageSubscription)env.storageSubscriptions().iterator().next()).getId());
            rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

    }

}
