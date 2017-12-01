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
import com.icodici.universa.node2.network.DatagramAdapter;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;

public class Node2LocalNetworkTest extends Node2SingleTest {

    public static int NODES = 3;

    Map<NodeInfo,Node> nodes = new HashMap<>();

    public static String CONFIG_2_PATH = "../../deploy/samplesrv/";

    public void setUp() throws Exception {
        nodes = new HashMap<>();
        networks = new ArrayList<>();

        config = new Config();
        config.setPositiveConsensus(2);
        config.setNegativeConsensus(1);
        config.setResyncBreakConsensus(2);

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

    private interface RunnableWithException<T> {
        void run(T param) throws Exception;
    }

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
                        if( !r.state.isConsensusFound())
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
    public void splitJoinTest() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));


        // 50
        Contract cRev = c.createRevision();
        Contract c2 = cRev.splitValue("amount", new Decimal(50));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c2.check());
        c2.seal();
        assertEquals(new Decimal(50), cRev.getStateData().get("amount"));

        registerAndCheckApproved(c2);
        assertEquals("50", c2.getStateData().get("amount"));


        //send 150 out of 2 contracts (100 + 50)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", (new Decimal((Integer)c.getStateData().get("amount"))).
                add(new Decimal(Integer.valueOf((String)c3.getStateData().get("amount")))));
        c3.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c3.addRevokingItems(c);
        assertTrue(c3.check());
        c3.seal();

        registerAndCheckApproved(c3);
        assertEquals(new Decimal(150), c3.getStateData().get("amount"));

    }

    // This test will no
//    @Test(timeout = 300000)
//    public void resync() throws Exception {
//        Contract c = new Contract(TestKeys.privateKey(0));
//        c.seal();
//        addToAllLedgers(c, ItemState.APPROVED);
//        nodes.values().forEach(n->{
//            System.out.println(node.getLedger().getRecord(c.getId()));
//        });
//        node.getLedger().getRecord(c.getId()).destroy();
//        assertEquals(ItemState.UNDEFINED,node.checkItem(c.getId()).state);
//
//        LogPrinter.showDebug(true);
//        node.resync(c.getId()).await();
//        System.out.println(node.checkItem(c.getId()));
//    }

    private void addToAllLedgers(Contract c, ItemState state) {
        addToAllLedgers(c, state, null);
    }

    private void addToAllLedgers(Contract c, ItemState state, Node exceptNode) {
        for( Node n: nodes.values() ) {
            if(n != exceptNode) {
                n.getLedger().findOrCreate(c.getId()).setState(state).save();
            }
        }
    }

    private void registerAndCheckApproved(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }
//
//    @Test
//    public void resyncContractWithSomeUndefindSubContracts() throws Exception {
//
//        LogPrinter.showDebug(true);
//
//        AsyncEvent ae = new AsyncEvent();
//
//        int numSubContracts = 5;
//        List<Contract> subContracts = new ArrayList<>();
//        for (int i = 0; i < numSubContracts; i++) {
//            Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
//            c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//            assertTrue(c.check());
//            c.seal();
//
//            if(i < config.getKnownSubContractsToResync())
//                addToAllLedgers(c, ItemState.APPROVED);
//            else
//                addToAllLedgers(c, ItemState.APPROVED, node);
//
//            subContracts.add(c);
//        }
//
//        for (int i = 0; i < numSubContracts; i++) {
//            ItemResult r = node.checkItem(subContracts.get(i).getId());
//            System.out.println("Contract: " + i + " state: " + r.state);
//        }
//
//        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
//        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(contract.check());
//
//        for (int i = 0; i < numSubContracts; i++) {
//            contract.addRevokingItems(subContracts.get(i));
//        }
//        contract.seal();
//        contract.check();
//        contract.traceErrors();
//
//        node.registerItem(contract);
//
//        Timer timer = new Timer();
//        timer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//
//                ItemResult r = node.checkItem(contract.getId());
//                System.out.println("Complex contract state: " + r.state);
//
//                if(r.state == ItemState.APPROVED) ae.fire();
//            }
//        }, 0, 500);
//
//        try {
//            ae.await(5000);
//        } catch (TimeoutException e) {
//            System.out.println("time is up");
//        }
//
//        timer.cancel();
//
//        for (TestLocalNetwork ln : networks) {
//            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
//        }
//
//        ItemResult r = node.checkItem(contract.getId());
//        assertEquals(ItemState.APPROVED, r.state);
//    }

    public void shouldNotResyncWithLessKnownContractsEx(ItemState undefinedState, ItemState definedState) throws Exception {

        // Test should broke condition to resync:
        // should be at least one known (APPROVED, DECLINED, LOCKED, REVOKED) subcontract to start resync

//        LogPrinter.showDebug(true);

        AsyncEvent ae = new AsyncEvent();

        List<Contract> subContracts = new ArrayList<>();

        RunnableWithException<ItemState> addContract = (ItemState state) -> {
            Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            assertTrue(c.check());
            c.seal();
            addToAllLedgers(c, state);
            subContracts.add(c);
        };

        int wantedSubContracts = 5;

        int knownSubContractsToResync = config.getKnownSubContractsToResync();
        System.out.println("knownSubContractsToResync: " + knownSubContractsToResync);

        int numDefinedSubContracts = Math.min(wantedSubContracts, knownSubContractsToResync-1);
        System.out.println("add "+numDefinedSubContracts+" defined subcontracts (with state="+definedState+")");
        for (int i = 0; i < numDefinedSubContracts; ++i)
            addContract.run(definedState);

        int numUndefinedSubContracts = Math.max(0, wantedSubContracts - subContracts.size());
        System.out.println("add "+numUndefinedSubContracts+" "+undefinedState+" subcontract");
        for (int i = 0; i < numUndefinedSubContracts; ++i)
            addContract.run(undefinedState);

        for (int i = 0; i < subContracts.size(); i++) {
            ItemResult r = node.checkItem(subContracts.get(i).getId());
            System.out.println("Contract: " + i + " state: " + r.state);
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");

        for (int i = 0; i < subContracts.size(); i++) {
            contract.addRevokingItems(subContracts.get(i));
        }
        contract.seal();
        contract.check();
        contract.traceErrors();

        assertTrue(contract.check());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ItemResult r = node.checkItem(contract.getId());
                System.out.println("Complex contract state: " + r.state);

                if(r.state == ItemState.DECLINED) ae.fire();
            }
        }, 0, 500);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        ItemResult r = node.checkItem(contract.getId());
        assertEquals(ItemState.DECLINED, r.state);
    }

    @Test
    public void shouldNotResyncWithLessKnownContracts_APPROVED() throws Exception {
        shouldNotResyncWithLessKnownContractsEx(ItemState.UNDEFINED, ItemState.APPROVED);
    }

    @Test
    public void shouldNotResyncWithLessKnownContracts_LOCKED() throws Exception {
        shouldNotResyncWithLessKnownContractsEx(ItemState.UNDEFINED, ItemState.LOCKED);
    }

    @Test
    public void shouldNotResyncWithLessKnownContracts_DECLINED() throws Exception {
        shouldNotResyncWithLessKnownContractsEx(ItemState.UNDEFINED, ItemState.DECLINED);
    }

    @Test
    public void shouldNotResyncWithLessKnownContracts_REVOKED() throws Exception {
        shouldNotResyncWithLessKnownContractsEx(ItemState.UNDEFINED, ItemState.REVOKED);
    }

    public void shouldNotResyncWithLessUnknownContractsEx(ItemState definedState, ItemState undefinedState) throws Exception {

        // Test should broke condition to resync:
        // should be at least one unknown subcontract to start resync

//        LogPrinter.showDebug(true);

        AsyncEvent ae = new AsyncEvent();

        List<Contract> subContracts = new ArrayList<>();

        RunnableWithException<ItemState> addContract = (ItemState state) -> {
            Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            assertTrue(c.check());
            c.seal();
            addToAllLedgers(c, state);
            subContracts.add(c);
        };

        int wantedSubContracts = 5;

        System.out.println("add "+wantedSubContracts+" "+definedState+" subcontract");
        for (int i = 0; i < wantedSubContracts; ++i)
            addContract.run(definedState);

        for (int i = 0; i < subContracts.size(); i++) {
            ItemResult r = node.checkItem(subContracts.get(i).getId());
            System.out.println("Contract: " + i + " state: " + r.state);
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");

        for (int i = 0; i < subContracts.size(); i++) {
            contract.addRevokingItems(subContracts.get(i));
        }
        contract.seal();
        contract.check();
        contract.traceErrors();

        assertTrue(contract.check());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ItemResult r = node.checkItem(contract.getId());
                System.out.println("Complex contract state: " + r.state);

                if(r.state == ItemState.APPROVED) ae.fire();
            }
        }, 0, 500);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        ItemResult r = node.checkItem(contract.getId());
        ItemState expectedState = definedState == ItemState.APPROVED ? ItemState.APPROVED : ItemState.DECLINED;
        assertEquals(expectedState, r.state);
    }

    @Test
    public void shouldNotResyncWithLessUnknownContracts_APPROVED() throws Exception {
        shouldNotResyncWithLessUnknownContractsEx(ItemState.APPROVED, ItemState.UNDEFINED);
    }

    @Test
    public void shouldNotResyncWithLessUnknownContracts_LOCKED() throws Exception {
        shouldNotResyncWithLessUnknownContractsEx(ItemState.LOCKED, ItemState.UNDEFINED);
    }

    @Test
    public void shouldNotResyncWithLessUnknownContracts_DECLINED() throws Exception {
        shouldNotResyncWithLessUnknownContractsEx(ItemState.DECLINED, ItemState.UNDEFINED);
    }

    @Test
    public void shouldNotResyncWithLessUnknownContracts_REVOKED() throws Exception {
        shouldNotResyncWithLessUnknownContractsEx(ItemState.REVOKED, ItemState.UNDEFINED);
    }

    @Test
    public void shouldNotResyncWithFalseComplexState() throws Exception {

        // Test should broke condition to resync:
        // complex contract should has no errors itself

//        LogPrinter.showDebug(true);

        ItemState definedState = ItemState.APPROVED;
        ItemState undefinedState = ItemState.UNDEFINED;

        AsyncEvent ae = new AsyncEvent();

        List<Contract> subContracts = new ArrayList<>();

        RunnableWithException<ItemState> addContract = (ItemState state) -> {
            Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            assertTrue(c.check());
            c.seal();
            addToAllLedgers(c, state);
            subContracts.add(c);
        };

        int wantedSubContracts = 5;

        int knownSubContractsToResync = config.getKnownSubContractsToResync();
        System.out.println("knownSubContractsToResync: " + knownSubContractsToResync);

        int numDefinedSubContracts = Math.min(wantedSubContracts, knownSubContractsToResync);
        System.out.println("add "+numDefinedSubContracts+" defined subcontracts (with state="+definedState+")");
        for (int i = 0; i < numDefinedSubContracts; ++i)
            addContract.run(definedState);

        int numUndefinedSubContracts = Math.max(0, wantedSubContracts - subContracts.size());
        System.out.println("add "+numUndefinedSubContracts+" "+undefinedState+" subcontract");
        for (int i = 0; i < numUndefinedSubContracts; ++i)
            addContract.run(undefinedState);

        for (int i = 0; i < subContracts.size(); i++) {
            ItemResult r = node.checkItem(subContracts.get(i).getId());
            System.out.println("Contract: " + i + " state: " + r.state);
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKey(new PrivateKey(2048));

        for (int i = 0; i < subContracts.size(); i++) {
            contract.addRevokingItems(subContracts.get(i));
        }
        contract.seal();
        contract.check();
        contract.traceErrors();

        assertFalse(contract.check());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ItemResult r = node.checkItem(contract.getId());
                System.out.println("Complex contract state: " + r.state);

                if(r.state == ItemState.DECLINED) ae.fire();
            }
        }, 0, 500);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        ItemResult r = node.checkItem(contract.getId());
        assertEquals(ItemState.DECLINED, r.state);
    }

    @Test
    public void resyncContractWithSomeUndefindSubContracts() throws Exception {

        // Test should run resync of each unknown part of a contract

//        LogPrinter.showDebug(true);

        AsyncEvent ae = new AsyncEvent();

        int numSubContracts = 5;
        List<Contract> subContracts = new ArrayList<>();
        for (int i = 0; i < numSubContracts; i++) {
            Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
            c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
            assertTrue(c.check());
            c.seal();

            if(i < config.getKnownSubContractsToResync())
                addToAllLedgers(c, ItemState.APPROVED);
            else
                addToAllLedgers(c, ItemState.APPROVED, node);

            subContracts.add(c);
        }

        for (int i = 0; i < numSubContracts; i++) {
            ItemResult r = node.checkItem(subContracts.get(i).getId());
            System.out.println("Contract: " + i + " state: " + r.state);
        }

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(contract.check());

        for (int i = 0; i < numSubContracts; i++) {
            contract.addRevokingItems(subContracts.get(i));
        }
        contract.seal();
        contract.check();
        contract.traceErrors();

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ItemResult r = node.checkItem(contract.getId());
                System.out.println("Complex contract state: " + r.state);

                if(r.state == ItemState.APPROVED) ae.fire();
            }
        }, 0, 500);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        ItemResult r = node.checkItem(contract.getId());
        assertEquals(ItemState.APPROVED, r.state);
    }

    @Test
    public void checkRegisterContractOnLostPacketsNetwork() throws Exception {

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            ln.setUDPAdapterLostPacketsPercentInTestMode(90);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        }

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.seal();

        addDetailsToAllLedgers(contract);

        contract.check();
        contract.traceErrors();
        assertTrue(contract.isOk());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }

//                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        try {
            ae.await(30000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }
    }

    @Test
    public void checkRegisterContractOnTemporaryOffedNetwork() throws Exception {

        networks.get(2).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
        networks.get(2).setUDPAdapterLostPacketsPercentInTestMode(100);

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.seal();

        addDetailsToAllLedgers(contract);

        contract.check();
        contract.traceErrors();
        assertTrue(contract.isOk());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }
                assertEquals(all_is_approved, false);
            }
        }, 0, 1000);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            timer.cancel();
            System.out.println("switching on node 2");
            networks.get(2).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            networks.get(2).setUDPAdapterLostPacketsPercentInTestMode(50);
        }

        try {
            ae.await(500);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        boolean all_is_approved = true;
        for (Node n : nodes.values()) {
            ItemResult r = n.checkItem(contract.getId());
            System.out.println("Node: " + n.toString() + " state: " + r.state);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        assertEquals(all_is_approved, true);

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }
    }

    @Test
    public void checkRegisterContractOnTemporaryOffedAndHalfOnedNetwork() throws Exception {

        networks.get(2).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
        networks.get(2).setUDPAdapterLostPacketsPercentInTestMode(100);

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.seal();

        addDetailsToAllLedgers(contract);

        contract.check();
        contract.traceErrors();
        assertTrue(contract.isOk());

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }
                assertEquals(all_is_approved, false);
            }
        }, 0, 1000);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            timer.cancel();
            System.out.println("switching on node 2");
            networks.get(2).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks.get(2).setUDPAdapterLostPacketsPercentInTestMode(50);
        }

        try {
            ae.await(2000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        boolean all_is_approved = true;
        for (Node n : nodes.values()) {
            ItemResult r = n.checkItem(contract.getId());
            System.out.println("Node: " + n.toString() + " state: " + r.state);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        assertEquals(all_is_approved, true);

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }
    }

    @Test
    public void resyncApproved() throws Exception {
        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
//                    System.out.println(n.getLedger().getRecord(c.getId()));
                    ItemResult r = n.checkItem(c.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

//        LogPrinter.showDebug(true);
        node.resync(c.getId());

        try {
            ae.await(3000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.APPROVED, node.checkItem(c.getId()).state);
    }

    @Test
    public void resyncRevoked() throws Exception {
        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.REVOKED);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
//                    System.out.println(n.getLedger().getRecord(c.getId()));
                    ItemResult r = n.checkItem(c.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.REVOKED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

//        LogPrinter.showDebug(true);
        node.resync(c.getId());

        try {
            ae.await(3000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.REVOKED, node.checkItem(c.getId()).state);
    }

    @Test
    public void resyncDeclined() throws Exception {
        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.DECLINED);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
//                    System.out.println(n.getLedger().getRecord(c.getId()));
                    ItemResult r = n.checkItem(c.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.DECLINED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

//        LogPrinter.showDebug(true);
        node.resync(c.getId());

        try {
            ae.await(3000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.DECLINED, node.checkItem(c.getId()).state);
    }

    @Test
    public void resyncOther() throws Exception {
        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.PENDING_POSITIVE);

        Duration wasMaxResyncTime = config.getMaxResyncTime();
        config.setMaxResyncTime(Duration.ofMillis(5000));

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodes.values()) {
//                    System.out.println(n.getLedger().getRecord(c.getId()));
                    ItemResult r = n.checkItem(c.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
//                    if(r.state != ItemState.APPROVED) {
//                        all_is_approved = false;
//                    }
                }

//                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

//        LogPrinter.showDebug(true);
        node.resync(c.getId());

        try {
            ae.await(6000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();
        config.setMaxResyncTime(wasMaxResyncTime);

        assertEquals(ItemState.DECLINED, node.checkItem(c.getId()).state);
    }

//    @Test
//    public void resyncFaked() throws Exception {
//        AsyncEvent ae = new AsyncEvent();
//        Contract c = new Contract(TestKeys.privateKey(0));
//        c.seal();
//        addToAllLedgers(c, ItemState.DECLINED);
//
//        node.getLedger().getRecord(c.getId()).setState(ItemState.APPROVED);
//        assertEquals(ItemState.APPROVED, node.checkItem(c.getId()).state);
//
//        // Start checking nodes
//        Timer timer = new Timer();
//        timer.scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//
//                boolean all_is_approved = true;
//                for (Node n : nodes.values()) {
////                    System.out.println(n.getLedger().getRecord(c.getId()));
//                    ItemResult r = n.checkItem(c.getId());
//                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
//                    if(r.state != ItemState.DECLINED) {
//                        all_is_approved = false;
//                    }
//                }
//
//                if(all_is_approved) ae.fire();
//            }
//        }, 0, 1000);
//
//        LogPrinter.showDebug(true);
//        node.resync(c.getId());
//
//        try {
//            ae.await(3000);
//        } catch (TimeoutException e) {
//            System.out.println("time is up");
//        }
//
//        timer.cancel();
//
//        assertEquals(ItemState.DECLINED, node.checkItem(c.getId()).state);
//    }

    private void addDetailsToAllLedgers(Contract contract) {
        HashId id;
        StateRecord orCreate;
        for (Approvable c : contract.getRevokingItems()) {
            id = c.getId();
            for (Node nodeS : nodes.values()) {
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
            for (Node nodeS : nodes.values()) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    private void destroyCurrentFromAllNodesIfExists(Contract finalC) {
        for (Node nodeS : nodes.values()) {
            StateRecord r = nodeS.getLedger().getRecord(finalC.getId());
            if (r != null) {
                r.destroy();
            }
        }
    }


    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        for (Node n : nodes.values()) {
            ItemResult r = node.waitItem(bad.getId(), 500);
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
}