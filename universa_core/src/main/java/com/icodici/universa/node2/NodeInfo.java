/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PublicKey;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/**
 * The complete data about Universa node. This class should provide enough information to connect to a remote node and
 * create local services and should be used everywhere instead of host-port parameters and.
 * <p>
 * The preferred method of idenrifying the node is its integer id, see {@link #getNumber()}.
 */
public class NodeInfo implements BiSerializable {
    private PublicKey publicKey;
    private InetSocketAddress nodeAddress;
    private InetSocketAddress clientAddress;
    private InetSocketAddress serverAddress;
    private int number;
    private String nodeName;
    private String publicHost;

    public NodeInfo(@NonNull PublicKey publicKey, int number, @NonNull String nodeName, @NonNull String host,
                    int datagramPort, int clientHttpPort, int serverHttpPort) {
        this(publicKey, number, nodeName, host, host, datagramPort, clientHttpPort, serverHttpPort);
    }

    public String getPublicHost() {
        return publicHost;
    }

    public NodeInfo(@NonNull PublicKey publicKey, int number, @NonNull String nodeName, @NonNull String host,
                    String publicHost, int datagramPort, int clientHttpPort, int serverHttpPort) {
        assert number >= 0;
        assert datagramPort > 0;
        assert clientHttpPort > 0;
        this.publicKey = publicKey;
        this.number = number;
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
     *
     * @return
     */
    public int getNumber() {
        return number;
    }

    /**
     * String node name is now a secondary identificator
     *
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

        return number == nodeInfo.number;
    }

    @Override
    public int hashCode() {
        return number;
    }

    @Override
    public String toString() {
        return "NI(" + number + ")";
    }

    public static NodeInfo loadYaml(Path fileName) {
        try {
            Yaml yaml = new Yaml();
            Binder b = Binder.from(yaml.load(new FileInputStream(fileName.toString())));
//            System.out.println(fileName);
            int count = fileName.getNameCount();
            String n = fileName.getName(count - 1).toString();
            n = n.substring(0, n.length() - 5) + ".public.unikey";
            String keyPath = "/"+fileName.subpath(0, count - 2) + "/keys/" + n;
            System.out.println("expected key file path: <" + keyPath + ">");
//            System.out.println(keyPath);
//            System.out.println(b);

            PublicKey key = new PublicKey(Do.read(keyPath));
            return new NodeInfo(
                    key,
                    b.getIntOrThrow("node_number"),
                    b.getStringOrThrow("node_name"),
                    (String) b.getListOrThrow("ip").get(0),
                    b.getStringOrThrow("public_host"),
                    b.getIntOrThrow("udp_server_port"),
                    b.getIntOrThrow("http_client_port"),
                    b.getIntOrThrow("http_server_port")
            );
        } catch (Exception e) {
            System.err.println("failed to load node: " + fileName + ": " + e);
            e.printStackTrace();
        }
        return null;
    }

    public String publicUrlString() {
        return publicHost.equals("localhost") ? "http://localhost:"+clientAddress.getPort() : "http://" + publicHost + ":8080";
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        publicKey = deserializer.deserialize(data.getBinderOrThrow("publicKey"));
        publicHost = data.getStringOrThrow("publicHost");
        String host = data.getStringOrThrow("host");
        nodeAddress = new InetSocketAddress(host, data.getIntOrThrow("udpPort"));
        clientAddress = new InetSocketAddress(host, data.getIntOrThrow("clientPort"));
        serverAddress = new InetSocketAddress(host, data.getIntOrThrow("serverPort"));
        nodeName = data.getStringOrThrow("name");
        number = data.getIntOrThrow("number");
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        return Binder.of(
                "publicKey", serializer.serialize(publicKey),
                "publicHost", publicHost,
                "host", clientAddress.getHostName(),
                "clientPort", clientAddress.getPort(),
                "serverPort", serverAddress.getPort(),
                "udpPort", nodeAddress.getPort(),
                "name", nodeName,
                "number", number
        );
    }

    static {
        DefaultBiMapper.registerClass(NodeInfo.class);
    }

    public String internalUrlString() {
        return "http://" + clientAddress.getHostName() + ":" + clientAddress.getPort();
    }
}
