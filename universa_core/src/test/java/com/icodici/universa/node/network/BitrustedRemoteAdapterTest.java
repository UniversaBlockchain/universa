/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.HashId;
import com.icodici.universa.node.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

@Ignore("outdated vy v2")
public class BitrustedRemoteAdapterTest extends NodeTestCase {

    protected List<LocalNode> allNodes = new ArrayList<>();
    private LocalNode localNode;

    protected void createConsensus() throws IOException, SQLException, TimeoutException, InterruptedException {
        network = new Network();
        LocalNode remoteNode = createTempNode(network);
        localNode = createTempNode(network);

        Map<HashId, Node> knownNodes = new HashMap<>();
        knownNodes.put(HashId.of(TestKeys.publicKey(0).pack()), remoteNode);
        knownNodes.put(HashId.of(TestKeys.publicKey(1).pack()), localNode);


        BitrustedLocalAdapter localAdapter =
                new BitrustedLocalAdapter(remoteNode, TestKeys.privateKey(0), knownNodes, 17722);

        Node remoteNodeInterface = new BitrustedRemoteAdapter(remoteNode.getId(),
                                                        TestKeys.privateKey(1),
                                                        TestKeys.publicKey(0),
                                                        "localhost",
                                                        17722);
        network.registerNode(localNode);
        network.registerNode(remoteNodeInterface);

        network.setPositiveConsensus(2);
        network.setNegativeConsensus(1);

        network.setRequeryPause(Duration.ofMillis(500));
        network.setMaxElectionsTime(Duration.ofSeconds(2));

    }


    @Test
    public void checkItem() throws Exception {
        createConsensus();
        TestItem item1 = new TestItem(true);
        ItemResult itemResult = localNode.registerItemAndWait(item1);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }

    @Test
    public void getItem() throws Exception {
    }

    @Test
    public void bossEncodintgOgItemState() throws Exception {
        // better to put into itemstate!
    }
}