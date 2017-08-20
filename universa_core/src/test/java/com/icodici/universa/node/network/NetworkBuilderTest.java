/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.node.*;
import net.sergeych.tools.StopWatch;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class NetworkBuilderTest {

    public static final int TEST_NODES = 10;

    @Test
    public void buildNetwork() throws Exception {
        NetworkBuilder e = NetworkBuilder.from("src/test_config2");
        List<NetworkBuilder.NodeInfo> nodeInfo = new ArrayList<>(e.nodeInfo());
        Network n1 = nodeInfo.get(0).buildNetowrk(18170);
        Network n2 = nodeInfo.get(1).buildNetowrk(18171);
        for (int i = 2; i < TEST_NODES; i++) {
            nodeInfo.get(i).buildNetowrk(18170+i);
        }
//        Network n3 = nodeInfo.get(2).buildNetowrk();
//        Network n4 = nodeInfo.get(3).buildNetowrk();
//        Network n5 = nodeInfo.get(4).buildNetowrk();
//        Network n6 = nodeInfo.get(5).buildNetowrk();
//        Network n7 = nodeInfo.get(6).buildNetowrk();
//        Network n8 = nodeInfo.get(7).buildNetowrk();
//        Network n9 = nodeInfo.get(8).buildNetowrk();
//        Network n10 = nodeInfo.get(9).buildNetowrk();

        assertEquals(TEST_NODES, n1.getAllNodes().size());
        assertEquals(TEST_NODES, n2.getAllNodes().size());
//        assertEquals(4, n1.getNegativeConsensus());
//        assertEquals(7, n1.getPositiveConsensus());

//        System.out.println("filling ledger");
//        SqlLedger ledger = (SqlLedger) n1.getLocalNode().getLedger();
//        StopWatch.measure(true, () -> {
//            for (int i = 0; i < 1500; i++)
//                ledger.findOrCreate(HashId.createRandom());
//        });

//        System.out.println("filling ledger2");
//        StopWatch.measure(true, () -> {
//            for (int i = 0; i < 1500; i++)
//                ledger.findOrCreate(HashId.createRandom());
//        });
//
//
        n1.setRequeryPause(Duration.ofMillis(100));
        ArrayList<Long> times = new ArrayList<>();
        for (int n = 0; n < 10; n++) {
            TestItem item1 = new TestItem(true);
            long t = StopWatch.measure(() -> {
                ItemResult itemResult = n1.getLocalNode().registerItemAndWait(item1);
                assertEquals(ItemState.APPROVED, itemResult.state);
            });
            times.add(t);
//            System.out.println(t);
        }
        long t1 = times.get(3);
        long t2 = times.get(times.size()-1);
//        System.out.println(t1 + " / "+ t2);
        long mean = (t1 + t2)/2;
        assertThat((double) Math.abs(t2-t1) / ((double) mean), CoreMatchers.is(lessThan(0.20)) );
    }

}