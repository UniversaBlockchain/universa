/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/19/17.
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.*;

import java.io.File;
import java.io.FileReader;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.anyOf;
import static org.junit.Assert.*;

public class Node2SingleTest extends BaseNetworkTest {

    private static Network network_s = null;
    private static Node node_s = null;
    private static List<Node> nodes_s = null;
    private static Map<NodeInfo,Node> nodesMap_s = null;
    private static Ledger ledger_s = null;
    private static Config config_s = null;



    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        node_s.shutdown();
        node_s.getLedger().close();

        network_s = null;
        node_s = null;
        nodes_s = null;
        nodesMap_s = null;
        ledger_s = null;
        config_s = null;
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        config_s = new Config();

        // The quorum bigger than the network capacity: we model the situation
        // when the system will not get the answer
        config_s.setPositiveConsensus(posCons);
        config_s.setNegativeConsensus(negCons);
        config_s.setResyncBreakConsensus(1);
        config_s.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
        config_s.setPermanetMode(false);
        //config_s.getKeysWhiteList().add(config_s.getUIssuerKey());

        NodeInfo myInfo = new NodeInfo(getNodePublicKey(0), 1, "node1", "localhost",
                7101, 7102, 7104);
        NetConfig nc = new NetConfig(asList(myInfo));
        network_s = new TestSingleNetwork(nc);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        ledger_s = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        node_s = new Node(config_s, myInfo, ledger_s, network_s, getNodeKey(0));
        ((TestSingleNetwork)network_s).addNode(myInfo, node_s);

        nodes_s = new ArrayList<>();
        nodes_s.add(node_s);

        nodesMap_s = new HashMap<>();
        nodesMap_s.put(myInfo, node_s);

//        System.out.println("waiting for sanitation... ");
//        for(Node n : nodes_s) {
//            if(n.isSanitating())
//                n.sanitationFinished.await();
//        }
//        System.out.println("sanitation finished!");
    }



    @Before
    public void setUp() throws Exception {
        init(node_s, nodes_s, nodesMap_s, network_s, ledger_s, config_s);
    }


//    @Test
    public void sanitationTest() throws Exception {
        afterClass();

        Thread.sleep(2000);

        List<Contract> origins = new ArrayList<>();
        List<Contract> newRevisions = new ArrayList<>();
        List<Contract> newContracts = new ArrayList<>();
        PrivateKey myKey = TestKeys.privateKey(4);

        final int N = 100;
        for(int i = 0; i < N; i++) {
            Contract origin = new Contract(myKey);
            origin.seal();
            origins.add(origin);

            Contract newRevision = origin.createRevision(myKey);

            if(i < N/2) {
                //ACCEPTED
                newRevision.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
            } else {
                //DECLINED
                //State is equal
            }

            Contract newContract = new Contract(myKey);
            newRevision.addNewItems(newContract);
            newRevision.seal();

            newContracts.add(newContract);
            newRevisions.add(newRevision);

            System.out.println("item# "+ newRevision.getId().toBase64String().substring(0,6));
            int finalI = i;

            StateRecord originRecord = ledger.findOrCreate(origin.getId());
            originRecord.setExpiresAt(origin.getExpiresAt());
            originRecord.setCreatedAt(origin.getCreatedAt());

            StateRecord newRevisionRecord = ledger.findOrCreate(newRevision.getId());
            newRevisionRecord.setExpiresAt(newRevision.getExpiresAt());
            newRevisionRecord.setCreatedAt(newRevision.getCreatedAt());

            StateRecord newContractRecord = ledger.findOrCreate(newContract.getId());
            newContractRecord.setExpiresAt(newContract.getExpiresAt());
            newContractRecord.setCreatedAt(newContract.getCreatedAt());

            if(new Random().nextBoolean()) {
                if(finalI < N/2) {
                    originRecord.setState(ItemState.REVOKED);
                    newContractRecord.setState(ItemState.APPROVED);
                    newRevisionRecord.setState(ItemState.APPROVED);
                } else {
                    originRecord.setState(ItemState.APPROVED);
                    newContractRecord.setState(ItemState.UNDEFINED);
                    newRevisionRecord.setState(ItemState.DECLINED);
                }
            } else {
                originRecord.setState(ItemState.LOCKED);
                originRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newContractRecord.setState(ItemState.LOCKED_FOR_CREATION);
                newContractRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newRevisionRecord.setState(finalI < N/2 ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
            }

            originRecord.save();
            ledger.putItem(originRecord,origin, Instant.now().plusSeconds(3600*24));
            newRevisionRecord.save();
            ledger.putItem(newRevisionRecord,newRevision, Instant.now().plusSeconds(3600*24));
            if(newContractRecord.getState() == ItemState.UNDEFINED) {
                newContractRecord.destroy();
            } else {
                newContractRecord.save();
            }
        }

        Thread.sleep(2000);

        initTestSet();
        setUp();

        while (node.isSanitating()) {
            System.out.println("Node sanitating " + node.getRecordsToSanitate().size());
            Thread.sleep(2000);
        }
    }

    @Test
    public void noQuorumError() throws Exception {
        afterClass();
        initTestSet(2, 2);
        setUp();

        TestItem item = new TestItem(true);

        System.out.println("noQuorumError " + item.getId());
//        node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        node.registerItem(item);
        try {
            System.out.println("noQuorumError wait " + item.getId());
            node.waitItem(item.getId(), 5000);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        @NonNull ItemResult checkedItem = node.checkItem(item.getId());

        assertEquals(ItemState.PENDING_POSITIVE, checkedItem.state);
        assertTrue(checkedItem.expiresAt.isBefore(ZonedDateTime.now().plusHours(5)));

        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        try {
            node.waitItem(item2.getId(), 5000);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        checkedItem = node.checkItem(item2.getId());

        afterClass();
        initTestSet(1, 1);

        assertEquals(ItemState.PENDING_NEGATIVE, checkedItem.state);
    }


}