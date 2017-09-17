/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.node.*;
import net.sergeych.tools.Average;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NetworkBuilderTest {

    public static final int TEST_NODES = 10;

    //    @Test
    public void dummy() throws Exception {

    }

    @Test
    public void createNode() throws Exception {
        NodeStarter.main(new String[]{"-c", "src/test_config_2", "-i", "node3", "-p", "15510", "--test"});
        HttpClient client = new HttpClient("test", "http://127.0.0.1:15510");
        HttpClient.Answer answer = client.request("network");
        assertTrue(answer.isOk());
        Binder n4 = answer.data.getBinderOrThrow("node4");
        assertEquals(2086, n4.getIntOrThrow("port"));
        assertEquals("127.0.0.1", n4.getStringOrThrow("ip"));
        NodeStarter.shutdown();
        Thread.currentThread().sleep(200);
    }


    @Test
    public void buildNetwork() throws Exception {

        Average a = new Average();
        try (NetworkBuilder e = NetworkBuilder.from("src/test_config_2")) {
            List<NetworkBuilder.NodeInfo> nodeInfo = new ArrayList<>(e.nodeInfo());
            Network n1 = nodeInfo.get(0).buildNetwork(18370);
            Network n2 = nodeInfo.get(1).buildNetwork(18371);
            for (int i = 2; i < TEST_NODES; i++) {
                nodeInfo.get(i).buildNetwork(18370 + i);
            }

            assertEquals(TEST_NODES, n1.getAllNodes().size());
            assertEquals(TEST_NODES, n2.getAllNodes().size());

            n1.setRequeryPause(Duration.ofMillis(1000));
            ArrayList<Long> times = new ArrayList<>();
            for (int n = 0; n < 5; n++) {
                TestItem item1 = new TestItem(true);
                long t = StopWatch.measure(() -> {
                    ItemResult itemResult = n1.getLocalNode().registerItemAndWait(item1);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                });
                // The first cycle is before JIT pass, ignoring it
                System.out.println(t);
                if (n > 1)
                    a.update(t);
            }
            System.out.println("Average transaction time: " + a);
            System.out.println("variation:                " + a.variation());
            assertThat(a.variation(), is(lessThan(0.3)));
        }
    }
}