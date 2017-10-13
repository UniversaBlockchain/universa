/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Network;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class Node2SingleTest {

    Network network;
    NetConfig nc;
    Config config;
    Node node;
    NodeInfo myInfo;
    PostgresLedger legder;

    @Before
    public void setUp() throws Exception {
        config = new Config();
        config.setPositiveConsensus(1);
        config.setNegativeConsensus(1);
        myInfo = new NodeInfo(TestKeys.publicKey(0), 1, "node1", "localhost", 7101, 7102);
        nc = new NetConfig(asList(myInfo));
        network = new TestSingleNetwork(nc);
        legder = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING);
        node = new Node(config, myInfo, legder, network);
    }

    @Test
    public void registerGoodItem() throws Exception {
        TestItem ok = new TestItem(true);
        node.registerItem(ok);
        ItemResult r = node.waitItem(ok.getId(), 100);
        assertEquals(ItemState.APPROVED, r.state);
    }

    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
            ItemResult r = node.waitItem(bad.getId(), 100);
            assertEquals(ItemState.DECLINED, r.state);
    }

    @Test
    public void checkItem() throws Exception {
        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 100);
        node.waitItem(bad.getId(), 100);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }

}