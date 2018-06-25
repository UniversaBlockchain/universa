/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
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
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.*;

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

@Ignore
public class Node2EmulatedNetworkTest extends BaseNetworkTest {

    private static TestEmulatedNetwork network_s = null;
    private static Node node_s = null;
    private static List<Node> nodes_s = null;
    private static Map<NodeInfo,Node> nodesMap_s = new HashMap<>();
    private static Ledger ledger_s = null;
    private static NetConfig nc_s = null;
    private static Config config_s = null;


    private static final int NODES = 10;


    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        network_s.shutdown();
        nodes_s.forEach((n)-> {
            n.getLedger().close();
            n.shutdown();
        });

        network_s = null;
        node_s = null;
        nodes_s = null;
        nodesMap_s = null;
        ledger_s = null;
        nc_s = null;
        config_s = null;
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        System.out.println("Emulated network setup");
        nodes_s = new ArrayList<>();
        config_s = new Config();
        config_s.setPositiveConsensus(7);
        config_s.setNegativeConsensus(4);
        config_s.setResyncBreakConsensus(2);
        config_s.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
        //config_s.getKeysWhiteList().add(config_s.getUIssuerKey());

        Properties properties = new Properties();
        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        nc_s = new NetConfig();
        TestEmulatedNetwork en = new TestEmulatedNetwork(nc_s);

        for (int i = 0; i < NODES; i++) {

            Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);

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
            Node n = new Node(config_s, info, ledger, en);
            nodes_s.add(n);
            en.addNode(info, n);

            if (i == 0)
                ledger_s = ledger;
        }
        network_s = en;
        node_s = nodes_s.get(0);

        nodesMap_s = new HashMap<>();
        for (int i = 0; i < NODES; i++) {
            nodesMap_s.put(nc_s.getInfo(i), nodes_s.get(i));
        }

        System.out.println("Emulated network created on the nodes: " + nodes_s);
        System.out.println("Emulated network base node is: " + node_s);
        Thread.sleep(100);
    }



    @Before
    public void setUp() throws Exception {
        System.out.println("Switch on network full mode");
        network_s.switchOnAllNodesTestMode();
        network_s.setTest_nodeBeingOffedChance(0);
        init(node_s, nodes_s, nodesMap_s, network_s, ledger_s, config_s);
    }





    @Test(timeout = 15000)
    public void resyncApproved() throws Exception {
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        node.resync(c.getId());

        assertEquals(ItemState.APPROVED, node.waitItem(c.getId(), 15000).state);
    }

    @Test(timeout = 15000)
    public void resyncRevoked() throws Exception {
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.REVOKED);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        node.resync(c.getId());

        assertEquals(ItemState.REVOKED, node.waitItem(c.getId(), 13000).state);
    }

    @Test(timeout = 15000)
    public void resyncDeclined() throws Exception {
        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.DECLINED);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        node.resync(c.getId());

        assertEquals(ItemState.DECLINED, node.waitItem(c.getId(), 12000).state);
    }

    @Test(timeout = 15000)
    public void resyncOther() throws Exception {

        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.PENDING_POSITIVE);

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);

        node.resync(c.getId());
        assertEquals(ItemState.PENDING, node.checkItem(c.getId()).state);

        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 12000).state);
    }

    @Test(timeout = 15000)
    public void resyncWithTimeout() throws Exception {

        Contract c = new Contract(TestKeys.privateKey(0));
        c.seal();
        addToAllLedgers(c, ItemState.APPROVED);

        Duration wasDuration = config.getMaxResyncTime();
        config.setMaxResyncTime(Duration.ofMillis(2000));

        for (int i = 0; i < NODES/2; i++) {
            ((TestEmulatedNetwork)network).switchOffNodeTestMode(nodes.get(NODES-i-1));
        }

        node.getLedger().getRecord(c.getId()).destroy();
        assertEquals(ItemState.UNDEFINED, node.checkItem(c.getId()).state);


        node.resync(c.getId());
        assertEquals(ItemState.PENDING, node.checkItem(c.getId()).state);

        assertEquals(ItemState.UNDEFINED, node.waitItem(c.getId(), 15000).state);

        config.setMaxResyncTime(wasDuration);

        ((TestEmulatedNetwork)network).switchOnAllNodesTestMode();
    }

    @Test(timeout = 15000)
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

        assertEquals(ItemState.UNDEFINED, node.waitItem(contract.getId(), 13000).state);
    }


    @Test
    public void checkRegisterContractOnLostPacketsNetwork() throws Exception {

        ((TestEmulatedNetwork)network).setTest_nodeBeingOffedChance(75);

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.addSignerKeyFromFile(Config.uKeyPath);
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
                for (Node n : nodes) {
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
            ae.await(60000);
        } catch (TimeoutException e) {
            time_is_up = true;
            System.out.println("time is up");
        }

        timer.cancel();

        ((TestEmulatedNetwork)network).setTest_nodeBeingOffedChance(0);

        assertFalse(time_is_up);
    }

    @Test
    public void checkRegisterContractOnTemporaryOffedNetwork() throws Exception {

        // switch off half network
        for (int i = 0; i < NODES/2; i++) {
            ((TestEmulatedNetwork)network).switchOffNodeTestMode(nodes.get(NODES-i-1));
        }

        AsyncEvent ae = new AsyncEvent();

        Contract contract = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        contract.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        contract.addSignerKeyFromFile(Config.uKeyPath);
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
                for (Node n : nodes) {
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
            ((TestEmulatedNetwork)network).switchOnAllNodesTestMode();
        }

        Timer timer2 = new Timer();
        timer2.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {

                System.out.println("-----------nodes state--------------");

                boolean all_is_approved = true;
                for (Node n : nodes) {
                    ItemResult r = n.checkItem(contract.getId());
                    System.out.println("Node: " + n.toString() + " state: " + r.state);

                    if(r.state != ItemState.APPROVED) {
                        all_is_approved = false;
                    }

                    if(all_is_approved) {
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
        for (Node n : nodes) {
            ItemResult r = n.waitItem(contract.getId(), 2000);
            if(r.state != ItemState.APPROVED) {
                all_is_approved = false;
            }
        }

        assertEquals(all_is_approved, true);


    }

    private void addToAllLedgers(Contract c, ItemState state) {
        addToAllLedgers(c, state, null);
    }

    private void addToAllLedgers(Contract c, ItemState state, Node exceptNode) {
        for( Node n: nodes ) {
            if(n != exceptNode) {
                n.getLedger().findOrCreate(c.getId()).setState(state).save();
            }
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
        finalC.addSignerKeyFromFile(Config.uKeyPath);
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



}