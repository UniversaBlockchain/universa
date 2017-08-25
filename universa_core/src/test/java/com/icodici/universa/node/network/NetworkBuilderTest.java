/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.node.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;

public class NetworkBuilderTest {

    public static final int TEST_NODES = 10;

//    @Test
    public void dummy() throws Exception {

    }

    @Test
    public void createNode() throws Exception {
        NodeStarter.main(new String[]{"-c", "src/test_config2", "-i", "node3", "-p", "15510", "--test"});
        HttpClient client = new HttpClient("test", "http://127.0.0.1:15510");
        HttpClient.Answer answer = client.request("network");
        assertTrue(answer.isOk());
        Binder n4 = answer.data.getBinderOrThrow("node4");
        assertEquals(2086, n4.getIntOrThrow("port"));
        assertEquals("127.0.0.1", n4.getStringOrThrow("ip"));
        NodeStarter.shutdown();
        Thread.currentThread().sleep(200);
    }


//    @Test
    public void buildNetwork() throws Exception {

        try (NetworkBuilder e = NetworkBuilder.from("src/test_config2")) {
            List<NetworkBuilder.NodeInfo> nodeInfo = new ArrayList<>(e.nodeInfo());
            Network n1 = nodeInfo.get(0).buildNetowrk(18370);
            Network n2 = nodeInfo.get(1).buildNetowrk(18371);
            for (int i = 2; i < TEST_NODES; i++) {
                nodeInfo.get(i).buildNetowrk(18370 + i);
            }

            assertEquals(TEST_NODES, n1.getAllNodes().size());
            assertEquals(TEST_NODES, n2.getAllNodes().size());

            n1.setRequeryPause(Duration.ofMillis(100));
            ArrayList<Long> times = new ArrayList<>();
            for (int n = 0; n < 10; n++) {
                TestItem item1 = new TestItem(true);
                long t = StopWatch.measure(() -> {
                    ItemResult itemResult = n1.getLocalNode().registerItemAndWait(item1);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                });
                times.add(t);
                System.out.println(t);
            }
            long t1 = times.get(3);
            long t2 = times.get(times.size() - 1);
            System.out.println(t1 + " / " + t2);
            long mean = (t1 + t2) / 2;
            assertThat((double) Math.abs(t2 - t1) / ((double) mean), CoreMatchers.is(lessThan(0.20)));
        }
    }

}