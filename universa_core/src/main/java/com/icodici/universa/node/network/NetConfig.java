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
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Universa network configuratior & enviromnent.
 */
public class NetConfig {

    public class NodeInfo {
        private String nodeId;
        private String host;
        private int port;
        private int clientPort;
        private PublicKey publicKey;
        private byte[] packedPublicKey;
        private HashId publicKeyId;
        private PrivateKey privateKey;

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

        NodeInfo(String nodeId, byte[] packedPublicKey, String host, int port,int clientPort) throws EncryptionError {
            this.nodeId = nodeId;
            this.packedPublicKey = packedPublicKey;
            this.host = host;
            this.port = port;
            this.clientPort = clientPort;
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

        public int getClientPort() {
            return clientPort;
        }

        public String getRootPath() {
            return rootPath;
        }

        public Map<String,NodeInfo> getRoster() {
            return roster;
        }

        public synchronized PrivateKey getPrivateKey() throws IOException {
            if( privateKey == null ) {
                String privateKeyFileName = rootPath + "/tmp/" + nodeId + ".private.unikey";
                if (!new File(privateKeyFileName).exists())
                    privateKeyFileName = rootPath + "/tmp/pkey";
                if (!new File(privateKeyFileName).exists())
                    throw new FileNotFoundException("no private key file found for "+nodeId);
                privateKey = new PrivateKey(Do.read(new FileInputStream(privateKeyFileName)));
            }
            return privateKey;
        }

        @Override
        public boolean equals(Object obj) {
            if( obj instanceof NodeInfo)
                return ((NodeInfo) obj).nodeId.equals(nodeId);
            return super.equals(obj);
        }
    }

    LogPrinter log = new LogPrinter("ENV");
    private String rootPath;


    private Map<String, NodeInfo> roster = new ConcurrentHashMap<>();

    public Map<String,NodeInfo> getRoster() {
        return roster;
    }

    /**
     * Load networking configuration from a given root path
     *
     * @param rootPath
     *
     * @throws IOException
     */
    private void loadConfig(String rootPath) throws IOException {
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

    static public NetConfig from(String rootPath) throws IOException {
        NetConfig nb = new NetConfig();
        nb.loadConfig(rootPath);
        return nb;
    }

    public NetworkV1 buildNetworkV1(String localNodeId, int overrideClientPort) throws InterruptedException, SQLException, TimeoutException, IOException {
        NodeInfo nodeInfo = roster.get(localNodeId);
        if (nodeInfo == null)
            throw new IllegalArgumentException("node information not found: " + localNodeId);
        if (rootPath == null)
            throw new IllegalArgumentException("root path not set");
        return new NetworkV1(nodeInfo, overrideClientPort);
    }
}
