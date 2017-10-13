/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.*;
import net.sergeych.utils.LogPrinter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Node2EmulatedNetworkTest extends Node2SingleTest {

    public static int NODES = 3;

    public static List<Node> nodes = new ArrayList<>();

    public void setUp() throws Exception {
        config = new Config();
        config.setPositiveConsensus(2);
        config.setNegativeConsensus(1);

        nc = new NetConfig();
        TestEmulatedNetwork en = new TestEmulatedNetwork(nc);
        for(int i=0; i<NODES; i++) {
            legder = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING+"_t"+i);
            int offset = 7100 + 10*i;
            NodeInfo info =
                    new NodeInfo(
                            getNodeKey(i).getPublicKey(),
                            i,
                            "testnode_"+i,
                            "localhost",
                            offset+3,
                            offset,
                            offset+2
                    );
            nc.addNode(info);
            Node n = new Node(config, info, legder, en);
            nodes.add(n);
            en.addNode(info, n);
        }
        network = en;
        node = nodes.get(0);
    }

    @Test
    public void registerGoodItem() throws Exception {
        TestItem ok = new TestItem(true);
        LogPrinter.showDebug(true);
        node.registerItem(ok);
        for(Node n: nodes) {
            System.out.println("--- checking node "+n);
            ItemResult r = n.waitItem(ok.getId(), 1500);
            assertEquals(ItemState.APPROVED, r.state);
            System.out.println("--- processed "+n+" / "+System.identityHashCode(n));
        }
    }

//    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
            ItemResult r = node.waitItem(bad.getId(), 100);
            assertEquals(ItemState.DECLINED, r.state);
    }

//    @Test
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