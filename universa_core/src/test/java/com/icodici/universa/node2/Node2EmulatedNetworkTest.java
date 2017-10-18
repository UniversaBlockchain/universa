/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        int N = 10;
        for(int k=0; k<10; k++ ) {
//            StopWatch.measure(true, () -> {
                for (int i = 0; i < N; i++) {
                    TestItem ok = new TestItem(true);
                    node.registerItem(ok);
                    for (Node n : nodes) {
                        try {
                            ItemResult r = n.waitItem(ok.getId(), 2500);
                            assertEquals(ItemState.APPROVED, r.state);
                        } catch (TimeoutException e) {
                            fail("timeout");
                        }
                    }
                }
//            });
            assertThat(node.countElections(), is(lessThan(10)));
        }
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