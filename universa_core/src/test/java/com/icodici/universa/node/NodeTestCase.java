/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.HashId;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Ignore("outdated vy v2")
public class NodeTestCase extends TestCase {
    protected Network network;
    protected Ledger ledger;
    int nodesCount = 1;

    protected LocalNode createTempNode(Network network) throws IOException, SQLException {
        String nodeId = "temp_node_" + nodesCount++;
//        File tempFile = new File("nodedb_"+nodeId+ ".db");
        File tempFile = File.createTempFile("nodedb", "db");
//        LogPrinter.showDebug(true);
        SqliteLedger ledger = new SqliteLedger("jdbc:sqlite:" + tempFile);
        assertNotNull(ledger.findOrCreate(HashId.createRandom()));
        assertNull(ledger.getRecord(HashId.createRandom()));
        LocalNode n = new LocalNode(nodeId, network, ledger);
        network.registerNode(n);
        return n;
    }

    protected LocalNode createLocalConsensus() throws IOException, SQLException {
        network = new Network();
        LocalNode node = createTempNode(network);
        network.setNegativeConsensus(1);
        network.setPositiveConsensus(1);
        ledger = node.getLedger();
        return node;
    }
}
