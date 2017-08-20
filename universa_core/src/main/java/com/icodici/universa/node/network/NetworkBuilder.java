/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;


import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.node.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Universa network configuratior & enviromnent.
 */
public class NetworkBuilder {

    LogPrinter log = new LogPrinter("ENV");
    private String rootPath;

    public class NodeInfo {
        private String nodeId;
        private String host;
        private int port;
        private int clientPort;
        private PublicKey publicKey;
        private byte[] packedPublicKey;
        private HashId publicKeyId;

        public String getNodeId() {
            return nodeId;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        public byte[] getPackedPublicKey() {
            return packedPublicKey;
        }

        public HashId getPublicKeyId() {
            return publicKeyId;
        }

        NodeInfo(String nodeId, byte[] packedPublicKey, String host, int port) throws EncryptionError {
            this.nodeId = nodeId;
            this.packedPublicKey = packedPublicKey;
            this.host = host;
            this.port = port;
            setupKey();
        }

        public NodeInfo(String nodeId, Binder fields, byte[] packedPublicKey) throws EncryptionError {
            this.nodeId = nodeId;
            host = fields.getStringOrThrow("ip");
            port = fields.getIntOrThrow("port");
            clientPort = fields.getInt("client_port", -1);
            this.packedPublicKey = packedPublicKey;
            setupKey();
        }

        private final void setupKey() throws EncryptionError {
            publicKeyId = HashId.of(packedPublicKey);
            publicKey = new PublicKey(packedPublicKey);
        }

        /**
         * Setup a network using this instance as a local node and others as remote ones. rootPath must be set before
         * calling this method.
         *
         * @param overrideClietnPort if set to non-zero, {@link ClientEndpoint} will use this port instead of one
         *                           specified in node configuration file.
         *
         * @return
         */
        Network buildNetowrk(int overrideClietnPort) throws SQLException, IOException, TimeoutException, InterruptedException {
            String privateKeyFileName = rootPath + "/tmp/" + nodeId + ".private.unikey";
            if (!new File(privateKeyFileName).exists())
                privateKeyFileName = rootPath + "/tmp/pkey";
            if (!new File(privateKeyFileName).exists())
                throw new FileNotFoundException("no private key file found for "+nodeId);
            PrivateKey privateKey = new PrivateKey(Do.read(new FileInputStream(privateKeyFileName)));
            Network network = new Network();
            for (NodeInfo nodeInfo : roster.values()) {// let's be paranoid
                if (!nodeInfo.getNodeId().equals(nodeId))
                    nodeInfo.createRemoteNode(network, privateKey);
            }
            // very important to create local node when all remote nodes are ready
            createLocalNode(network, privateKey, overrideClietnPort);
            network.deriveConsensus(0.7);
            return network;
        }

        private void createRemoteNode(Network network, PrivateKey privateKey) throws InterruptedException, TimeoutException, IOException {
            BitrustedRemoteAdapter remoteNode = new BitrustedRemoteAdapter(
                    nodeId, privateKey, publicKey, host, port
            );
            network.registerNode(remoteNode);
        }

        /**
         * This method MUST BE CALLED AFTER ALL NETWORK INITIALIZATION IS DONE. Failure to do it will cause
         * nonfunctional node rejecting incoming connections.
         *
         * @param network    to which to connect. Must be fully formed except of this instance
         * @param privateKey
         *
         * @throws SQLException
         */
        private void createLocalNode(Network network, PrivateKey privateKey, int overrideClientPort) throws SQLException, IOException {
            SqlLedger ledger = new SqlLedger("jdbc:sqlite:" + rootPath + "/system/" + nodeId + ".sqlite.db");
            LocalNode localNode = new LocalNode(nodeId, network, ledger);
            network.registerLocalNode(localNode);
            Map<HashId, Node> keysNodes = new HashMap<>();
            for (NodeInfo ni : roster.values()) {
                if (ni != this) {
                    keysNodes.put(ni.publicKeyId, network.getNode(ni.nodeId));
                }
            }
            new BitrustedLocalAdapter(localNode, privateKey, keysNodes, port);
            new ClientEndpoint(overrideClientPort == 0 ? clientPort : overrideClientPort, localNode, NetworkBuilder.this);
        }
    }

    Map<String, NodeInfo> roster = new ConcurrentHashMap<>();

    public void registerNode(String nodeId, byte[] packedPublicKey, String host, int port) throws EncryptionError {
        roster.put(nodeId, new NodeInfo(nodeId, packedPublicKey, host, port));
    }

    public void unregisterNode(String nodeId) {
        roster.remove(nodeId);
    }

    public Collection<NodeInfo> nodeInfo() {
        return roster.values();
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Load networking configuration from a given root path
     *
     * @param rootPath
     *
     * @throws IOException
     */
    public void loadConfig(String rootPath) throws IOException {
        this.rootPath = rootPath;
        String nodesPath = rootPath + "/config/nodes";
        String keysPath = rootPath + "/config/keys";
        File nodesFile = new File(nodesPath);
        if (!nodesFile.exists())
            throw new IllegalArgumentException("nodes path does not eixist: " + nodesFile.getCanonicalPath());
        File keysFile = new File(keysPath);
        if (!keysFile.exists())
            throw new IllegalArgumentException("keys path does not eixist: " + keysFile.getCanonicalPath());
        Yaml yaml = new Yaml();
        int count = 0;
        for (String name : keysFile.list()) {
            if (name.endsWith(".unikey")) {
                if (name.indexOf(".private.unikey") >= 0)
                    throw new IllegalStateException("private key found in shared folder. please remove.");
                String nodeId = name.substring(0, name.length() - 14);
                File yamlFile = new File(nodesPath + "/" + nodeId + ".yaml");
                if (!yamlFile.exists())
                    yamlFile = new File(nodesPath + "/" + nodeId + ".yml");
                if (!yamlFile.exists())
                    throw new IOException("Not found .yml confoguration for " + nodeId);
                try (FileInputStream in = new FileInputStream(yamlFile)) {
                    NodeInfo ni = new NodeInfo(
                            nodeId,
                            Binder.from(yaml.load(in)),
                            Do.read(new FileInputStream(keysPath + "/" + nodeId + ".public.unikey"))
                    );
                    roster.put(nodeId, ni);
                }
            }
        }
    }

    static public NetworkBuilder from(String rootPath) throws IOException {
        NetworkBuilder e = new NetworkBuilder();
        e.loadConfig(rootPath);
        return e;
    }

    public Network buildNetwork(String localNodeId, int ovverideClientPort) throws InterruptedException, SQLException, TimeoutException, IOException {
        NodeInfo nodeInfo = roster.get(localNodeId);
        if (nodeInfo == null)
            throw new IllegalArgumentException("node information not found: " + localNodeId);
        if (rootPath == null)
            throw new IllegalArgumentException("root path not set");
        return nodeInfo.buildNetowrk(ovverideClientPort);
    }
}
