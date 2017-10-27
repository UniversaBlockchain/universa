/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PublicKey;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * The complete data about Universa node. This class should provide enough information to connect to a remote node
 * and create local services and should be used everywhere instead of host-port parameters and.
 *
 * The preferred method of idenrifying the node is its integer id, see {@link #getNumber()}.
 */
public class NodeInfo {
    private final PublicKey publicKey;
    private final InetSocketAddress nodeAddress;
    private final InetSocketAddress clientAddress;
    private final InetSocketAddress serverAddress;
    private final int id;
    private final String nodeName;
    private final String publicHost;

    public NodeInfo(@NonNull PublicKey publicKey, int id, @NonNull String nodeName, @NonNull String host,
                    int datagramPort, int clientHttpPort,int serverHttpPort) {
        this(publicKey, id, nodeName, host, host, datagramPort, clientHttpPort, serverHttpPort);
    }

    public String getPublicHost() {
        return publicHost;
    }

    public NodeInfo(@NonNull PublicKey publicKey, int id, @NonNull String nodeName, @NonNull String host,
                    String publicHost, int datagramPort, int clientHttpPort, int serverHttpPort) {
        assert id >= 0;
        assert datagramPort > 0;
        assert clientHttpPort > 0;
        this.publicKey = publicKey;
        this.id = id;
        this.nodeName = nodeName;
        this.publicHost = publicHost;
        nodeAddress = new InetSocketAddress(host, datagramPort);
        clientAddress = new InetSocketAddress(publicHost, clientHttpPort);
        serverAddress = new InetSocketAddress(host, serverHttpPort);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    /**
     * Integer node it is now the preferred way to identify nodes
     * @return
     */
    public int getNumber() {
        return id;
    }

    /**
     * String node name is now a secondary identificator
     * @return
     */
    public String getName() {
        return nodeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeInfo nodeInfo = (NodeInfo) o;

        return id == nodeInfo.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return "NI("+id+")";
    }

    public static NodeInfo loadYaml(Path fileName)  {
        try {
            Yaml yaml = new Yaml();
            Binder b = Binder.from(yaml.load(new FileInputStream(fileName.toString())));
//            System.out.println(fileName);
            int count = fileName.getNameCount();
            String n = fileName.getName(count - 1).toString();
            n = n.substring(0, n.length() - 5) + ".public.unikey";
            String keyPath = fileName.subpath(0, count - 2) + "/keys/" + n;
//            System.out.println(keyPath);
//            System.out.println(b);

            PublicKey key = new PublicKey(Do.read(keyPath));
            return new NodeInfo(
                    key,
                    b.getIntOrThrow("node_number"),
                    b.getStringOrThrow("node_name"),
                    (String)b.getListOrThrow("ip").get(0),
                    b.getStringOrThrow("public_host"),
                    b.getIntOrThrow("udp_server_port"),
                    b.getIntOrThrow("http_client_port"),
                    b.getIntOrThrow("http_server_port")
            );
        }
        catch(Exception e) {
            System.err.println("failed to load node: "+fileName+": "+e);
            e.printStackTrace();
        }
        return null;
    }
}
