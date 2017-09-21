/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.node.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkV1 extends Network implements AutoCloseable {

    private ClientEndpoint clientEndpoint;
    private AtomicBoolean closed = new AtomicBoolean(false);
    private PrivateKey privateKey;
    private Ledger ledger;
    private BitrustedLocalAdapter localAdapter;

    @Override
    public void close() throws Exception {
        super.close();
        if (closed.compareAndSet(false, true)) {
            localAdapter.shutdown();
            clientEndpoint.shutdown();
        }
    }

    public NetworkV1(NetConfig.NodeInfo localNodeInfo, int overrideClientPort) throws IOException, SQLException {
        String nodeId = localNodeInfo.getNodeId();
        Map<String, NetConfig.NodeInfo> roster = localNodeInfo.getRoster();
        System.out.println("------------- network for "+nodeId+":"+privateKey+" ------------ "+overrideClientPort);

        // First, we need our private key
        privateKey = localNodeInfo.getPrivateKey();

        // now we should create remote adapters
        for (NetConfig.NodeInfo nodeInfo : roster.values()) {
            if (!nodeInfo.equals(localNodeInfo))
                createRemoteNode(nodeInfo);
        }

        // and finally create local node and enpoint:
        createLocalNode(localNodeInfo,overrideClientPort);

        deriveConsensus(0.9);
    }


    private void createLocalNode(NetConfig.NodeInfo ni, int overrideClientPort) throws SQLException, IOException {
        String rootPath = ni.getRootPath();

        // Important: we can have only one local node
        LocalNode localNode;
        synchronized (this) {
            if( getLocalNode() != null )
                throw new IllegalStateException("local node is already created");

            // create ledger and local node
            // TODO: read config.yaml and get the valid ledger type and connection string
            ledger = new SqliteLedger("jdbc:sqlite:" + rootPath + "/system/" + ni.getNodeId() + ".sqlite.db");
            localNode = new LocalNode(ni.getNodeId(), this, ledger);
            registerLocalNode(localNode);
        }

        // create local adapter for universa networkm version 1.*, first we need to create key mappings:
        Map<HashId, Node> keysNodes = new HashMap<>();
        for (NetConfig.NodeInfo i : ni.getRoster().values()) {
            // We don't authorize connecting to self
            if (i != ni) {
                keysNodes.put(i.getPublicKeyId(), getNode(i.getNodeId()));
            }
        }

        privateKey = ni.getPrivateKey();

        // now we can create local adapter
        localAdapter = new BitrustedLocalAdapter(localNode, privateKey, keysNodes, ni.getPort());
        clientEndpoint = new ClientEndpoint(
                privateKey,
                overrideClientPort > 0 ? overrideClientPort : ni.getClientPort(),
                localNode,
                ni.getRoster()
        );
    }

    private void createRemoteNode(NetConfig.NodeInfo ni) {
        synchronized (this) {
            if( getNode(ni.getNodeId()) != null )
                throw new IllegalStateException("remote node is already created");
            if (privateKey == null)
                throw new IllegalStateException("private key must be set before creating remote nodes");
            BitrustedRemoteAdapter remoteNode = null;
            remoteNode = new BitrustedRemoteAdapter(
                    ni.getNodeId(),
                    privateKey,
                    ni.getPublicKey(),
                    ni.getHost(),
                    ni.getPort()
            );
            registerNode(remoteNode);
        }
    }
}
