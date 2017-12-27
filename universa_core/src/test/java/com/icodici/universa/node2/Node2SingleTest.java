/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/19/17.
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.*;

import java.io.File;
import java.io.FileReader;
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
        node_s.getLedger().close();
        network_s.shutdown();

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

        NodeInfo myInfo = new NodeInfo(getNodePublicKey(0), 1, "node1", "localhost",
                7101, 7102, 7104);
        NetConfig nc = new NetConfig(asList(myInfo));
        network_s = new TestSingleNetwork(nc);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        ledger_s = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        node_s = new Node(config_s, myInfo, ledger_s, network_s);
        ((TestSingleNetwork)network_s).addNode(myInfo, node_s);

        nodes_s = new ArrayList<>();
        nodes_s.add(node_s);

        nodesMap_s = new HashMap<>();
        nodesMap_s.put(myInfo, node_s);
    }



    @Before
    public void setUp() throws Exception {
        init(node_s, nodes_s, nodesMap_s, network_s, ledger_s, config_s);
    }



    @Test
    public void noQourumError() throws Exception {
        initTestSet(2, 2);
        setUp();

        TestItem item = new TestItem(true);

//        LogPrinter.showDebug(true);
        node.registerItem(item);
        try {
            node.waitItem(item.getId(), 100);
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
            node.waitItem(item2.getId(), 100);
            fail("Expected exception to be thrown.");
        } catch (TimeoutException te) {
            assertNotNull(te);
        }

        checkedItem = node.checkItem(item2.getId());

        initTestSet(1, 1);

        assertEquals(ItemState.PENDING_NEGATIVE, checkedItem.state);
    }


}