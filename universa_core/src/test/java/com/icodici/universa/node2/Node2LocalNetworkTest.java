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
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;

public class Node2LocalNetworkTest extends TestCase {

    public static int NODES = 10;

    static Map<NodeInfo,Node> nodesMap = new HashMap<>();

    protected static final String ROOT_PATH = "./src/test_contracts/";
    public static String CONFIG_2_PATH = "../../deploy/samplesrv/";

    static Network network;
    static NetConfig nc;
    static Config config;
    static Node node;
    static NodeInfo myInfo;
    static Ledger ledger;

    @BeforeClass
    public static void setUp() throws Exception {
        nodesMap = new HashMap<>();
        networks = new ArrayList<>();

        config = new Config();
        config.setPositiveConsensus(7);
        config.setNegativeConsensus(4);
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
            ln.setNodes(nodesMap);
//            ledger = new SqliteLedger("jdbc:sqlite:testledger" + "_t" + i);
            ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);
            Node n = new Node(config, info, ledger, ln);
            nodesMap.put(info, n);
            networks.add(ln);
        }
        node = nodesMap.values().iterator().next();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        networks.forEach(n->n.shutDown());
        nodesMap.forEach((i,n)->n.getLedger().close());
    }

    @Before
    public void setUpTest() throws Exception {
        System.out.println("setup test");
        System.out.println("Switch on UDP network full mode");
        for (int i = 0; i < NODES; i++) {
            networks.get(i).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            networks.get(i).setUDPAdapterLostPacketsPercentInTestMode(0);
        }
        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterLostPacketsPercentInTestMode(0);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        }

    }

    @After
    public void tearDownTest() throws Exception {
        System.out.println("tear down test");
    }

    private static List<TestLocalNetwork> networks = new ArrayList<>();

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
        ae.await(1000);
        n1.removeAllSubscribes();

        // fully recreate network - we broke subscribers
        tearDown();
        setUp();
    }

    @Test(timeout = 90000)
    public void registerGoodItem() throws Exception {

        int N = 100;
//        LogPrinter.showDebug(true);
        for (int k = 0; k < 1; k++) {
            StopWatch.measure(true, () -> {
            for (int i = 0; i < N; i++) {
                TestItem ok = new TestItem(true);
                System.out.println("\n--------------register item " + ok.getId() + " ------------\n");
                node.registerItem(ok);
                for (Node n : nodesMap.values()) {
                    try {
                        ItemResult r = n.waitItem(ok.getId(), 2000);
                        int numIterations = 0;
                        while( !r.state.isConsensusFound()) {
                            System.out.println("wait for consensus receiving on the node " + n);
                            Thread.sleep(500);
                            r = n.waitItem(ok.getId(), 2000);
                            numIterations++;
                            if(numIterations > 20)
                                break;
                        }
                        System.out.println("In node " + n + " item " + ok.getId() + " has state " +  r.state);
                        assertEquals("In node " + n + " item " + ok.getId(), ItemState.APPROVED, r.state);
                    } catch (TimeoutException e) {
                        fail("timeout");
                    }
                }
//                assertThat(node.countElections(), is(lessThan(10)));

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
//        nodesMap.values().forEach(n->{
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
        for( Node n: nodesMap.values() ) {
            if(n != exceptNode) {
                n.getLedger().findOrCreate(c.getId()).setState(state).save();
            }
        }
    }

    private void registerAndCheckApproved(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 8000);
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

        ItemResult r = node.waitItem(contract.getId(), 5000);
        System.out.println("Complex contract state: " + r.state);
        assertEquals(ItemState.DECLINED, r.state);

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }
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

        ItemResult r = node.waitItem(contract.getId(), 5000);
        System.out.println("Complex contract state: " + r.state);
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

        ItemResult r = node.waitItem(contract.getId(), 2000);
        assertEquals(ItemState.APPROVED, r.state);
    }

    @Test
    public void resyncContractWithSomeUndefindSubContractsWithTimeout() throws Exception {

        // Test should run resync of each unknown part of a contract
        // But resync should failed by timeout. And complex contract should be declined.

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

        Duration wasDuration = config.getMaxResyncTime();
        config.setMaxResyncTime(Duration.ofMillis(2000));

        for (int i = 0; i < NODES/2; i++) {
            networks.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
        }

        // preparing is finished

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

//        ItemResult r = node.waitItem(contract.getId(), 5000);
        ItemResult r = node.checkItem(contract.getId());
        // If resync broken but need more then oned nodes to decline, state should be PENDING_NEGATIVE
        Assert.assertThat(r.state, anyOf(equalTo(ItemState.PENDING_NEGATIVE), equalTo(ItemState.DECLINED)));

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        config.setMaxResyncTime(wasDuration);
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
                for (Node n : nodesMap.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        boolean time_is_up = false;
        try {
            ae.await(45000);
        } catch (TimeoutException e) {
            time_is_up = true;
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        assertFalse(time_is_up);
    }

    @Test
    public void checkRegisterContractOnTemporaryOffedNetwork() throws Exception {

        // switch off half network
        for (int i = 0; i < NODES/2; i++) {
            networks.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
        }

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.seal();

        addDetailsToAllLedgers(contract);

        contract.check();
        contract.traceErrors();
        assertTrue(contract.isOk());

//        LogPrinter.showDebug(true);

        node.registerItem(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodesMap.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }
                }
                assertEquals(all_is_approved, false);
            }
        }, 0, 1000);

        // wait and now switch on full network
        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            timer.cancel();
            System.out.println("switching on network");
            for (TestLocalNetwork ln : networks) {
                ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            }
        }

        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                Object lock = new Object();
                synchronized (lock) {
                    int num_approved = 0;
                    for (Node n : nodesMap.values()) {
                        ItemResult r = n.checkItem(contract.getId());

                        if (r.state == ItemState.APPROVED) {
                            num_approved++;
                        }
                        System.out.println("Node: " + n.toString() + " state: " + r.state);
                    }

                    if (num_approved == NODES) {
                        System.out.println("All approved: " + num_approved);
                        ae.fire();
                    }
                }
            }
        }, 0, 1000);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer2.cancel();

        boolean all_is_approved = true;
        for (Node n : nodesMap.values()) {
            ItemResult r = n.waitItem(contract.getId(), 2000);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        LogPrinter.showDebug(false);

        assertEquals(all_is_approved, true);


    }

    @Test
    public void checkRegisterContractOnTemporaryOffedAndHalfOnedNetwork() throws Exception {

        // switch off half network
        for (int i = 0; i < NODES/2; i++) {
            networks.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
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
                for (Node n : nodesMap.values()) {
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

            for (int i = 0; i < NODES/2; i++) {
                networks.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
                networks.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(50);
            }
        }

        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                for (Node n : nodesMap.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);
                }
            }
        }, 0, 1000);

        try {
            ae.await(5000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer2.cancel();

        boolean all_is_approved = true;
        for (Node n : nodesMap.values()) {
            ItemResult r = n.waitItem(contract.getId(), 2000);
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

//        LogPrinter.showDebug(true);

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
                for (Node n : nodesMap.values()) {
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

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 2000).state);
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
                for (Node n : nodesMap.values()) {
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

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 2000).state);
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
                for (Node n : nodesMap.values()) {
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

        assertEquals(ItemState.DECLINED, node.waitItem(c.getId(), 2000).state);
    }

    @Test
    public void resyncOther() throws Exception {

//        LogPrinter.showDebug(true);

        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.PENDING_POSITIVE);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);
        node.resync(c.getId());
        assertEquals(ItemState.PENDING, node.checkItem(c.getId()).state);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodesMap.values()) {
                    ItemResult r = n.checkItem(c.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.UNDEFINED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        try {
            ae.await(6000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 2000).state);
    }

    @Test(timeout = 5000)
    public void resyncWithTimeout() throws Exception {

//        LogPrinter.showDebug(true);

        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);

        Duration wasDuration = config.getMaxResyncTime();
        config.setMaxResyncTime(Duration.ofMillis(2000));

        for (int i = 0; i < NODES/2; i++) {
            networks.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
        }

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);
        node.resync(c.getId());
        assertEquals(ItemState.PENDING, node.checkItem(c.getId()).state);


        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 5000).state);
        config.setMaxResyncTime(wasDuration);

        for (int i = 0; i < NODES; i++) {
            networks.get(i).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
        }
    }

    @Test
    public void resyncComplex() throws Exception {

//        LogPrinter.showDebug(true);

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

        AsyncEvent ae = new AsyncEvent();
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();

        for (int i = 0; i < numSubContracts; i++) {
            contract.addRevokingItems(subContracts.get(i));
        }
        addToAllLedgers(contract, ItemState.PENDING_POSITIVE);

        node.getLedger().getRecord(contract.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(contract.getId()).state);
        node.resync(contract.getId());
        assertEquals(ItemState.PENDING, node.checkItem(contract.getId()).state);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                boolean all_is_approved = true;
                for (Node n : nodesMap.values()) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println(">>>Node: " + n.toString() + " state: " + r.state);
                    if(r.state != ItemState.UNDEFINED) {
                        all_is_approved = false;
                    }
                }

                if(all_is_approved) ae.fire();
            }
        }, 0, 1000);

        try {
            ae.await(6000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.UNDEFINED, node.checkItem(contract.getId()).state);
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
//                for (Node n : nodesMap.values()) {
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
            for (Node nodeS : nodesMap.values()) {
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
            for (Node nodeS : nodesMap.values()) {
                orCreate = nodeS.getLedger().getRecord(c.getId());
                if (orCreate != null)
                    orCreate.destroy();
            }
        }
    }

    private void destroyCurrentFromAllNodesIfExists(Contract finalC) {
        for (Node nodeS : nodesMap.values()) {
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
        for (Node n : nodesMap.values()) {
            ItemResult r = node.waitItem(bad.getId(), 2000);
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













    /////////////////////////////// from Node2Single

    @Test
    public void shouldCreateItems() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 3000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 3000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 3000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);
    }

    @Test
    public void shouldDeclineItems() throws Exception {
        TestItem item = new TestItem(false);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.DECLINED, result.state);
        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.DECLINED, result.state);
    }

    @Test
    public void singleNodeMixApprovedAndDeclined() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 3000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 3000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 3000);
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

        Duration maxElectionsTime = config.getMaxElectionsTime();
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

        config.setMaxElectionsTime(maxElectionsTime);
    }

    @Test
    public void testNotCreatingOnReject() throws Exception {
        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

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
    public void rejectBadNewItem() throws Exception {
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
        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we run the day for teh output document:
        node.registerItem(new2);

        // and this one was created before
        @NonNull ItemResult itemNew2before = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, itemNew2before.state);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        @NonNull ItemResult item = node.checkItem(main.getId());
        assertEquals(ItemState.UNDEFINED, item.state);

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 2000);
        assertEquals(ItemState.DECLINED, itemResult.state);
//        Assert.assertThat(itemResult.state, anyOf(equalTo(ItemState.PENDING), equalTo(ItemState.PENDING_NEGATIVE), equalTo(ItemState.DECLINED)));

        @NonNull ItemResult itemNew1 = node.waitItem(new1.getId(), 2000);
        assertEquals(ItemState.UNDEFINED, itemNew1.state);
//        Assert.assertThat(itemNew1.state, anyOf(equalTo(ItemState.UNDEFINED), equalTo(ItemState.LOCKED_FOR_CREATION)));

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.waitItem(new2.getId(), 2000);
        assertEquals(ItemState.APPROVED, itemNew2.state);
    }

    @Test
    public void acceptWithReferences() throws Exception {
        return;
    }

    @Test(timeout = 30000)
    public void badReferencesDeclineListStates() throws Exception {

//        LogPrinter.showDebug(true);

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

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
        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem existing1 = new TestItem(false);
        TestItem existing2 = new TestItem(true);
        node.registerItem(existing1);
        node.registerItem(existing2);


        // we need them to be settled first
        node.waitItem(existing1.getId(), 2000);
        node.waitItem(existing2.getId(), 2000);

        main.addReferencedItems(existing1.getId(), existing2.getId());
        main.addNewItems(new1, new2);

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 1500);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.checkItem(new1.getId()).state);
        assertEquals(ItemState.UNDEFINED, node.checkItem(new2.getId()).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.checkItem(existing1.getId()).state);
        assertEquals(ItemState.APPROVED, node.checkItem(existing2.getId()).state);
    }

    @Test
    public void missingReferencesDecline() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 3000);

        // but second is missing
        HashId missingId = HashId.createRandom();

        main.addReferencedItems(existing.getId(), missingId);

        // check that main is fully approved
        node.registerItem(main);
        ItemResult itemResult = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        assertNull(node.getItem(missingId));
    }

    @Test(timeout = 15000)
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {

//        LogPrinter.showDebug(true);

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
    public void shouldDeclineSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 50
        c = c.createRevision();
        Contract c2 = c.splitValue("amount", new Decimal(550));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertFalse(c2.check());
        c2.seal();
        assertEquals(new Decimal(-450), c.getStateData().get("amount"));

        registerAndCheckDeclined(c2);
    }

    @Test
    public void shouldApproveSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 50
        c = c.createRevision();
        Contract c2 = c.splitValue("amount", new Decimal(50));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c2.check());
        c2.seal();
        assertEquals(new Decimal(50), c.getStateData().get("amount"));

        registerAndCheckApproved(c2);
        assertEquals("50", c2.getStateData().get("amount"));
    }

    @Test
    public void shouldBreakByQuantizer() throws Exception {
        // 100
        Contract.setTestQuantaLimit(10);
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c.check());
        c.seal();

        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test
    public void shouldBreakByQuantizerSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);


        Contract.setTestQuantaLimit(60);
        // 50
        Contract forSplit = c.createRevision();
        Contract c2 = forSplit.splitValue("amount", new Decimal(30));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c2.check());
        c2.seal();
        forSplit.seal();
        assertEquals(new Decimal(30), new Decimal(Long.valueOf(c2.getStateData().get("amount").toString())));
        assertEquals(new Decimal(70), forSplit.getStateData().get("amount"));

        node.registerItem(forSplit);
        ItemResult itemResult = node.waitItem(forSplit.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }

    @Test
    public void shouldApproveSplitAndJoinWithNewSend() throws Exception {
        // 100
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

    @Test
    public void shouldDeclineSplitAndJoinWithWrongAmount() throws Exception {
        // 100
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


        //wrong. send 500 out of 2 contracts (100 + 50)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", new Decimal(500));
        c3.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c3.addRevokingItems(c);
        assertFalse(c3.check());
        c3.seal();

        registerAndCheckDeclined(c3);
    }

    private void registerAndCheckDeclined(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);
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

    @Test
    public void createRealContract() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
    }


}