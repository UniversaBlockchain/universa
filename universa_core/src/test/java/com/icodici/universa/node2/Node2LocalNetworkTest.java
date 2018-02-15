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
import com.icodici.universa.contract.Parcel;
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

public class Node2LocalNetworkTest extends BaseNetworkTest {

    private static TestLocalNetwork network_s = null;
    private static List<TestLocalNetwork> networks_s = new ArrayList<>();
    private static Node node_s = null;
    private static List<Node> nodes_s = null;
    private static Map<NodeInfo,Node> nodesMap_s = null;
    private static Ledger ledger_s = null;
    private static NetConfig nc_s = null;
    private static Config config_s = null;


    private static final int NODES = 3;


    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        networks_s.forEach(n->n.shutDown());
        nodesMap_s.forEach((i,n)-> {
            n.getLedger().close();
            n.shutdown();
        });

        network_s = null;
        networks_s = null;
        node_s = null;
        nodes_s = null;
        nodesMap_s = null;
        ledger_s = null;
        nc_s = null;
        config_s = null;

//        Thread.sleep(5000);
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        nodesMap_s = new HashMap<>();
        networks_s = new ArrayList<>();

        config_s = new Config();
        config_s.setPositiveConsensus(2);
        config_s.setNegativeConsensus(2);
        config_s.setResyncBreakConsensus(1);
//        config_s.setPollTime(Duration.ofMillis(2500));
//        config_s.setConsensusReceivedCheckTime(Duration.ofMillis(2500));
//        config_s.setResyncTime(Duration.ofMillis(2500));

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

        nc_s = new NetConfig();

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
            nc_s.addNode(info);
        }

        for (int i = 0; i < NODES; i++) {

            NodeInfo info = nc_s.getInfo(i);

            TestLocalNetwork ln = new TestLocalNetwork(nc_s, info, getNodeKey(i));
            ln.setNodes(nodesMap_s);
//            ledger = new SqliteLedger("jdbc:sqlite:testledger" + "_t" + i);
            Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);
            Node n = new Node(config_s, info, ledger, ln);
            nodesMap_s.put(info, n);
            networks_s.add(ln);

            if (i == 0) {
                ledger_s = ledger;
                network_s = ln;
            }
        }
        node_s = nodesMap_s.values().iterator().next();

        nodes_s = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            nodes_s.add(nodesMap_s.get(nc_s.getInfo(i)));
        }
    }



    @Before
    public void setUp() throws Exception {
        System.out.println("setup test");
        System.out.println("Switch on UDP network full mode");
        for (int i = 0; i < NODES; i++) {
            networks_s.get(i).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            networks_s.get(i).setUDPAdapterLostPacketsPercentInTestMode(0);
        }
        for (TestLocalNetwork ln : networks_s) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterLostPacketsPercentInTestMode(0);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        }
        init(node_s, nodes_s, nodesMap_s, network_s, ledger_s, config_s);

        System.out.println(node.traceTasksPool());
        for (Node n : nodes) {
            System.out.println(n + " " + n.traceParcelProcessors());
            System.out.println(n + " " + n.traceItemProcessors());
        }

        while(node.freeThreadsNum() < 20) {
            System.out.println("num free pools: " + node.freeThreadsNum() + " wait");
            Thread.sleep(1000);
        }
    }


    @After
    public void coolDown() throws Exception {
//        Thread.sleep(5000);
    }


    private interface RunnableWithException<T> {
        void run(T param) throws Exception;
    }



    @Test
    public void networkPassesData() throws Exception {
        AsyncEvent<Void> ae = new AsyncEvent<>();
        TestLocalNetwork n0 = networks_s.get(0);
        TestLocalNetwork n1 = networks_s.get(1);
        NodeInfo i1 = n0.getInfo(1);
        NodeInfo i0 = n0.getInfo(0);

        n1.subscribe(null, n -> {
            System.out.println("received n: " + n);
            ae.fire();
            System.out.println("fired");
        });
        n0.deliver(i1, new ItemNotification(i0,
                HashId.createRandom(),
                new ItemResult(ItemState.PENDING,
                        false,
                        ZonedDateTime.now(),
                        ZonedDateTime.now()),
                false)
        );
        boolean endWithFail = false;
        try {
            ae.await(1000);
        } catch (TimeoutException e) {
            endWithFail = true;
        } finally {

            n1.removeAllSubscribes();

            // fully recreate network - we broke subscribers
            afterClass();
            beforeClass();
            setUp();
            if(endWithFail) {
                fail("timeout");
            }
        }
    }


    // This test will no
//    @Test(timeout = 300000)
//    public void resync() throws Exception {
//        Contract c = new Contract(TestKeys.privateKey(0));
//        c.seal();
//        addToAllLedgers(c, ItemState.APPROVED);
//        nodesMap_s.values().forEach(n->{
//            System.out.println(node.getLedger().getRecord(c.getId()));
//        });
//        node.getLedger().getRecord(c.getId()).destroy();
//        assertEquals(ItemState.UNDEFINED,node.checkItem(c.getId()).state);
//
//        LogPrinter.showDebug(true);
//        node.resync(c.getId()).await();
//        System.out.println(node.checkItem(c.getId()));
//    }



    private synchronized void addToAllLedgers(Contract c, ItemState state) {
        addToAllLedgers(c, state, null);
    }

    private synchronized void addToAllLedgers(Contract c, ItemState state, Node exceptNode) {
        for( Node n: nodesMap_s.values() ) {
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



    public void shouldNotResyncWithLessKnownContractsEx(ItemState undefinedState, ItemState definedState) throws Exception {

        // Test should broke condition to resync:
        // should be at least one known (APPROVED, DECLINED, LOCKED, REVOKED) subcontract to start resync

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

        Parcel parcel = registerWithNewParcel(contract);

        node.waitParcel(parcel.getId(), 15000);
        ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 5000);
        System.out.println("Complex contract state: " + r.state);
        assertEquals(ItemState.DECLINED, r.state);

        for (TestLocalNetwork ln : networks_s) {
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

        Parcel parcel = registerWithNewParcel(contract);

        node.waitParcel(parcel.getId(), 30000);
        ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 8000);
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

//        LogPrinter.showDebug(true);

        // Test should broke condition to resync:
        // complex contract should has no errors itself

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

//        LogPrinter.showDebug(true);
        Parcel parcel = registerWithNewParcel(contract);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                ItemResult r = node.checkItem(parcel.getPayloadContract().getId());
                System.out.println("Complex contract state: " + r.state);

                if(r.state == ItemState.DECLINED) ae.fire();
            }
        }, 0, 500);

        try {
            ae.await(25000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks_s) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        node.waitParcel(parcel.getId(), 18000);
        ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 3000);
        assertEquals(ItemState.DECLINED, r.state);
    }



    @Test
    public void resyncContractWithSomeUndefindSubContracts() throws Exception {

        // Test should run resync of each unknown part of a contract

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

        Parcel parcel = registerWithNewParcel(contract);

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

        for (TestLocalNetwork ln : networks_s) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        node.waitParcel(parcel.getId(), 30000);
        ItemResult r = node.waitItem(parcel.getPayloadContract().getId(), 3000);
        assertEquals(ItemState.APPROVED, r.state);
    }



    @Test
    public void resyncContractWithSomeUndefindSubContractsWithTimeout() throws Exception {

        // Test should run resync of each unknown part of a contract
        // But resync should failed by timeout. And complex contract should be declined.

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
            System.out.println("Contract: " + i + " > " + subContracts.get(i).getId() + " state: " + r.state);
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
            networks_s.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks_s.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
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
            ae.await(15000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

//        ItemResult r = node.waitItem(contract.getId(), 5000);
        ItemResult r = node.checkItem(contract.getId());
        // If resync broken but need more then oned nodes to decline, state should be PENDING_NEGATIVE
        Assert.assertThat(r.state, anyOf(equalTo(ItemState.PENDING_NEGATIVE), equalTo(ItemState.DECLINED)));

        for (TestLocalNetwork ln : networks_s) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        config.setMaxResyncTime(wasDuration);
    }



    @Test
    public void checkRegisterContractOnLostPacketsNetwork() throws Exception {

        for (TestLocalNetwork ln : networks_s) {
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
                for (Node n : nodesMap_s.values()) {
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
            ae.await(30000);
        } catch (TimeoutException e) {
            time_is_up = true;
            System.out.println("time is up");
        }

        timer.cancel();

        for (TestLocalNetwork ln : networks_s) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        }

        assertFalse(time_is_up);
    }



    @Test
    public void checkRegisterContractOnTemporaryOffedNetwork() throws Exception {

        // switch off half network
        for (int i = 0; i < NODES/2; i++) {
            networks_s.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks_s.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
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
                for (Node n : nodesMap_s.values()) {
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
            for (TestLocalNetwork ln : networks_s) {
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
                    for (Node n : nodesMap_s.values()) {
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
        for (Node n : nodesMap_s.values()) {
            ItemResult r = n.waitItem(contract.getId(), 13000);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        assertEquals(all_is_approved, true);


    }



    @Test
    public void checkRegisterContractOnTemporaryOffedAndHalfOnedNetwork() throws Exception {


        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.seal();

        addDetailsToAllLedgers(contract);

        contract.check();
        contract.traceErrors();
        assertTrue(contract.isOk());

        Parcel parcel = registerWithNewParcel(contract);


        // switch off half network
        for (int i = 0; i < NODES/2; i++) {
            networks_s.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks_s.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
        }

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodesMap_s.values()) {
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
                networks_s.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
                networks_s.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(50);
            }
        }

        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                for (Node n : nodesMap_s.values()) {
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
        for (Node n : nodesMap_s.values()) {
            n.waitParcel(parcel.getId(), 13000);
            ItemResult r = n.waitItem(parcel.getPayloadContract().getId(), 3000);
            System.out.println("Node: " + n.toString() + " state: " + r.state);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        assertEquals(all_is_approved, true);

        for (TestLocalNetwork ln : networks_s) {
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
                for (Node n : nodesMap_s.values()) {
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

        node.resync(c.getId());

        try {
            ae.await(3000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 13000).state);
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
                for (Node n : nodesMap_s.values()) {
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
        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 3000).state);

        node.resync(c.getId());

        try {
            ae.await(3000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 8000).state);
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
                for (Node n : nodesMap_s.values()) {
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

        assertEquals(ItemState.DECLINED, node.waitItem(c.getId(), 13000).state);
    }



    @Test
    public void resyncOther() throws Exception {

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
                for (Node n : nodesMap_s.values()) {
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

        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 13000).state);
    }



    @Test
    public void resyncWithTimeout() throws Exception {

        AsyncEvent ae = new AsyncEvent();
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);

        Duration wasDuration = config.getMaxResyncTime();
        config.setMaxResyncTime(Duration.ofMillis(2000));

        for (int i = 0; i < NODES/2; i++) {
            networks_s.get(NODES-i-1).setUDPAdapterTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
            networks_s.get(NODES-i-1).setUDPAdapterLostPacketsPercentInTestMode(100);
        }

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);
        node.resync(c.getId());
        assertEquals(ItemState.PENDING, node.checkItem(c.getId()).state);

        // Start checking nodes
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if( ItemState.UNDEFINED == node.checkItem(c.getId()).state)
                    ae.fire();
            }
        }, 100, 500);

        try {
            ae.await(6000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        timer.cancel();
        config.setMaxResyncTime(wasDuration);

        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 3000).state);

        for (int i = 0; i < NODES; i++) {
            networks_s.get(i).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
        }
    }



    @Test(timeout = 30000)
    public void resyncComplex() throws Exception {

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
                for (Node n : nodesMap_s.values()) {
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

        assertEquals(ItemState.UNDEFINED, node.waitItem(contract.getId(), 13000).state);
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
//        for (TestLocalNetwork ln : networks_s) {
//            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
//        }
//
//        ItemResult r = node.checkItem(contract.getId());
//        assertEquals(ItemState.APPROVED, r.state);
//    }

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
//                for (Node n : nodesMap_s.values()) {
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

}