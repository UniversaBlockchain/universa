/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import org.junit.After;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class Node2LocalNetworkTest extends Node2SingleTest {

    public static int NODES = 3;

    Map<NodeInfo,Node> nodes = new HashMap<>();

    public static String CONFIG_2_PATH = "../../deploy/samplesrv/";

    public void setUp() throws Exception {
        config = new Config();
        config.setPositiveConsensus(2);
        config.setNegativeConsensus(1);

        Properties properties = new Properties();
        File file = new File(CONFIG_2_PATH + "config/config.yaml");

        Yaml yaml = new Yaml();
        Binder settings = new Binder();
        if (file.exists())
            settings = Binder.from(yaml.load(new FileReader(file)));

//        properties.setProperty("database", settings.getStringOrThrow("database"));

        /* test loading onfig should be in other place
        NetConfig ncNet = new NetConfig(CONFIG_2_PATH+"config/nodes");
        List<NodeConsumer> netNodes = ncNet.toList();
        */

        nc = new NetConfig();

        for (int i = 0; i < NODES; i++) {
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
        }

        for (int i = 0; i < NODES; i++) {

            NodeInfo info = nc.getInfo(i);

            TestLocalNetwork ln = new TestLocalNetwork(nc, info, getNodeKey(i));
            ln.setNodes(nodes);
//            ledger = new SqliteLedger("jdbc:sqlite:testledger" + "_t" + i);
            ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);
            Node n = new Node(config, info, ledger, ln);
            nodes.put(info, n);
            networks.add(ln);
        }
        node = nodes.values().iterator().next();
    }

    @After
    public void tearDown() throws Exception {
        networks.forEach(n->n.shutDown());
        nodes.forEach((i,n)->n.getLedger().close());
    }

    private List<TestLocalNetwork> networks = new ArrayList<>();

    @Test
    public void networkPassesData() throws Exception {
        AsyncEvent<Void> ae = new AsyncEvent<>();
        TestLocalNetwork n0 = networks.get(0);
        TestLocalNetwork n1 = networks.get(1);
        NodeInfo i1 = n0.getInfo(1);
        NodeInfo i0 = n0.getInfo(0);

        n1.subscribe(null, n -> {
            System.out.println("received n: " + n);
            ae.fire();
        });
        n0.deliver(i1, new ItemNotification(i0,
                                            HashId.createRandom(),
                                            new ItemResult(ItemState.PENDING,
                                                           false,
                                                           ZonedDateTime.now(),
                                                           ZonedDateTime.now()),
                                            false)
        );
        ae.await(500);
    }

    @Test
    public void registerGoodItem() throws Exception {
        int N = 100;
//        LogPrinter.showDebug(true);
        for (int k = 0; k < 1; k++) {
            StopWatch.measure(true, () -> {
            for (int i = 0; i < N; i++) {
                TestItem ok = new TestItem(true);
                node.registerItem(ok);
                for (Node n : nodes.values()) {
                    try {
                        ItemResult r = n.waitItem(ok.getId(), 5500);
                        if( !r.state.consensusFound())
                            Thread.sleep(30);
                        assertEquals("In node "+n+" item "+ok.getId(), ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
                        fail("timeout");
                    }
                }
//                System.out.println("\n\n--------------------------\n\n");
                assertThat(node.countElections(), is(lessThan(10)));

                ItemResult r = node.waitItem(ok.getId(), 5500);
                assertEquals("after: In node "+node+" item "+ok.getId(), ItemState.APPROVED, r.state);

            }
            });
        }
    }

    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        for (Node n : nodes.values()) {
            ItemResult r = node.waitItem(bad.getId(), 100);
            assertEquals(ItemState.DECLINED, r.state);
        }
    }

    @Test
    public void checkItem() throws Exception {
        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 1500);
        node.waitItem(bad.getId(), 1500);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }

    @Test
    public void badmodify2ContractCase() throws Exception {
        byte[] packed = Do.read("src/test_contracts/badmodify2.unicon");
        Contract c = new Contract(packed);
        node.registerItem(c);
        System.out.println(c.getId().toBase64String());
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }


//
//
//    @Test
//    public void shouldCreateItems() throws Exception {
//        TestItem item = new TestItem(true);
//
//        node.registerItem(item);
//        ItemResult result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//
//        result = node.checkItem(item.getId());
//        assertEquals(ItemState.APPROVED, result.state);
//    }
//
//    @Test
//    public void shouldDeclineItems() throws Exception {
//        TestItem item = new TestItem(false);
//
//        node.registerItem(item);
//        ItemResult result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result.state);
//
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result.state);
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result.state);
//
//        result = node.checkItem(item.getId());
//        assertEquals(ItemState.DECLINED, result.state);
//    }
//
//    @Test
//    public void singleNodeMixApprovedAndDeclined() throws Exception {
//        TestItem item = new TestItem(true);
//
//        node.registerItem(item);
//        ItemResult result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//        result = node.waitItem(item.getId(), 1000);
//        assertEquals(ItemState.APPROVED, result.state);
//
//        result = node.checkItem(item.getId());
//        assertEquals(ItemState.APPROVED, result.state);
//
//
//        // Negative consensus
//        TestItem item2 = new TestItem(false);
//
//        node.registerItem(item2);
//        ItemResult result2 = node.waitItem(item2.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result2.state);
//
//        result2 = node.waitItem(item2.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result2.state);
//        result2 = node.waitItem(item2.getId(), 1000);
//        assertEquals(ItemState.DECLINED, result2.state);
//
//        result2 = node.checkItem(item2.getId());
//        assertEquals(ItemState.DECLINED, result2.state);
//    }
//
//
//    @Test
//    public void testNotCreatingOnReject() throws Exception {
//        TestItem main = new TestItem(false);
//        TestItem new1 = new TestItem(true);
//        TestItem new2 = new TestItem(true);
//
//        main.addNewItems(new1, new2);
//
//        assertEquals(2, main.getNewItems().size());
//
//        node.registerItem(main);
//
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//
//        assertEquals(ItemState.DECLINED, itemResult.state);
//
//        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
//        assertEquals(ItemState.UNDEFINED, itemNew1.state);
//
//        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
//        assertEquals(ItemState.UNDEFINED, itemNew2.state);
//    }
//
//    @Test
//    public void rejectBadNewItem() throws Exception {
//        TestItem main = new TestItem(true);
//        TestItem new1 = new TestItem(true);
//        TestItem new2 = new TestItem(false);
//
//        main.addNewItems(new1, new2);
//
//        assertEquals(2, main.getNewItems().size());
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//
//        assertEquals(ItemState.DECLINED, itemResult.state);
//
//        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
//        assertEquals(ItemState.UNDEFINED, itemNew1.state);
//
//        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
//        assertEquals(ItemState.UNDEFINED, itemNew2.state);
//    }
//
//    @Test
//    public void badNewDocumentsPreventAccepting() throws Exception {
//        TestItem main = new TestItem(true);
//        TestItem new1 = new TestItem(true);
//        TestItem new2 = new TestItem(true);
//
//        // and now we run the day for teh output document:
//        node.registerItem(new2);
//
//        main.addNewItems(new1, new2);
//
//        assertEquals(2, main.getNewItems().size());
//
//        @NonNull ItemResult item = node.checkItem(main.getId());
//        assertEquals(ItemState.UNDEFINED, item.state);
//
//        node.registerItem(main);
//
//        ItemResult itemResult = node.checkItem(main.getId());
//        assertEquals(ItemState.PENDING, itemResult.state);
//
//        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
//        assertEquals(ItemState.UNDEFINED, itemNew1.state);
//
//        // and this one was created before
//        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
//        Assert.assertThat(itemNew2.state, anyOf(equalTo(ItemState.APPROVED), equalTo(ItemState.PENDING_POSITIVE)));
//    }
//
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
//
//    @Test
//    public void badReferencesDeclineListStates() throws Exception {
//        for (ItemState badState : Arrays.asList(
//                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
//                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
//                ) {
//
//            TestItem main = new TestItem(true);
//
//            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//            existing1.setState(ItemState.APPROVED).save();
//
//            // but second is not good
//            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//            existing2.setState(badState).save();
//
//            main.addReferencedItems(existing1.getId(), existing2.getId());
//
//            // check that main is fully approved
//            node.registerItem(main);
//            ItemResult itemResult = node.waitItem(main.getId(), 100);
//            assertEquals(ItemState.DECLINED, itemResult.state);
//
//            // and the references are intact
//            assertEquals(ItemState.APPROVED, existing1.reload().getState());
//            assertEquals(badState, existing2.reload().getState());
//        }
//    }
//
//    @Test
//    public void badReferencesDecline() throws Exception {
//        TestItem main = new TestItem(true);
//        TestItem new1 = new TestItem(true);
//        TestItem new2 = new TestItem(true);
//
//
//        TestItem existing1 = new TestItem(false);
//        TestItem existing2 = new TestItem(true);
//        node.registerItem(existing1);
//        node.registerItem(existing2);
//
//        main.addReferencedItems(existing1.getId(), existing2.getId());
//        main.addNewItems(new1, new2);
//
//        // check that main is fully approved
//        node.registerItem(main);
//
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.DECLINED, itemResult.state);
//
//        assertEquals(ItemState.UNDEFINED, node.checkItem(new1.getId()).state);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(new2.getId()).state);
//
//        // and the references are intact
//        assertEquals(ItemState.DECLINED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.APPROVED, node.checkItem(existing2.getId()).state);
//    }
//
//    @Test
//    public void missingReferencesDecline() throws Exception {
//        TestItem main = new TestItem(true);
//
//        TestItem existing = new TestItem(true);
//        node.registerItem(existing);
//        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 100);
//
//        // but second is missing
//        HashId missingId = HashId.createRandom();
//
//        main.addReferencedItems(existing.getId(), missingId);
//
//        // check that main is fully approved
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.DECLINED, itemResult.state);
//
//        // and the references are intact
//        assertEquals(ItemState.APPROVED, existingItem.state);
//
//        assertNull(node.getItem(missingId));
//    }
//
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
//    @Test
//    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {
//        for (ItemState badState : Arrays.asList(
//                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
//                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
//                ) {
//
//            TestItem main = new TestItem(true);
//
//            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//            existing1.setState(ItemState.APPROVED).save();
//            // but second is not good
//            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//            existing2.setState(badState).save();
//
//            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));
//
//            node.registerItem(main);
//            ItemResult itemResult = node.waitItem(main.getId(), 100);
//            assertEquals(ItemState.DECLINED, itemResult.state);
//
//            // and the references are intact
//            assertEquals(ItemState.APPROVED, existing1.reload().getState());
//            assertEquals(badState, existing2.reload().getState());
//
//        }
//    }
//
//    @Test
//    public void itemsCachedThenPurged() throws Exception {
//        config.setMaxElectionsTime(Duration.ofMillis(100));
//
//        TestItem main = new TestItem(true);
//        main.setExpiresAtPlusFive(false);
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        assertEquals(main, node.getItem(main.getId()));
//        Thread.sleep(110);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
//    }
//
//    @Test
//    public void createRealContract() throws Exception {
//        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
//        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c.check());
//        c.seal();
//
//        node.registerItem(c);
//        ItemResult itemResult = node.waitItem(c.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//    }
//

}