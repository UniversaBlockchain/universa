/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PublicKey;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The complete data about Universa node. This class should provide enough information to connect to a remote node and
 * create local services and should be used everywhere instead of host-port parameters and.
 * <p>
 * The preferred method of identifying the node is its integer id, see {@link #getNumber()}.
 */
public class NodeInfo implements BiSerializable {
    private String hostV6;
    private PublicKey publicKey;
    private InetSocketAddress nodeAddress;
    private InetSocketAddress nodeAddressV6;
    private InetSocketAddress clientAddress;
    private InetSocketAddress serverAddress;
    private int number;
    private String nodeName;
    private String publicHost;
    private String host;

    public NodeInfo() {

    }


    public NodeInfo(@NonNull PublicKey publicKey, int number, @NonNull String nodeName, @NonNull String host,
                    int datagramPort, int clientHttpPort, int serverHttpPort) {
        this(publicKey, number, nodeName, host, null, host , datagramPort, clientHttpPort, serverHttpPort);
    }

    public NodeInfo(@NonNull PublicKey publicKey, int number, @NonNull String nodeName, @NonNull String host,
                    String publicHost, int datagramPort, int clientHttpPort, int serverHttpPort) {
        this(publicKey, number, nodeName, host, null, publicHost , datagramPort, clientHttpPort, serverHttpPort);
    }
        @Deprecated
    public String getPublicHost() {
        return publicHost;
    }

    public String getServerHost() { return host; }

    public NodeInfo(@NonNull PublicKey publicKey, int number, @NonNull String nodeName, @NonNull String host, @Nullable String hostV6,
                    String publicHost, int datagramPort, int clientHttpPort, int serverHttpPort) {
        assert number >= 0;
        assert datagramPort > 0;
        assert clientHttpPort > 0;
        this.publicKey = publicKey;
        this.number = number;
        this.nodeName = nodeName;
        this.publicHost = publicHost;
        this.host = host;
        this.hostV6 = hostV6;
        nodeAddress = new InetSocketAddress(host, datagramPort);
        if(hostV6 != null) {
            nodeAddressV6 = new InetSocketAddress(hostV6, datagramPort);
        }
        clientAddress = new InetSocketAddress(publicHost, clientHttpPort);
        serverAddress = new InetSocketAddress(host, serverHttpPort);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public InetSocketAddress getNodeAddress() {
        return nodeAddress;
    }
    public InetSocketAddress getNodeAddressV6() {
        return nodeAddressV6 != null ? nodeAddressV6 : nodeAddress;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public InetSocketAddress getServerAddress() {
        return serverAddress;
    }

    /**
     * Integer node it is now the preferred way to identify nodes
     *
     * @return node number
     */
    public int getNumber() {
        return number;
    }

    /**
     * String node name is now a secondary identificator
     *
     * @return name
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
                    b.containsKey("ipv6") ? (String) b.getListOrThrow("ipv6").get(0) : null,
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

    public String serverUrlString() {
        return publicHost.equals("localhost") ? "http://localhost:"+clientAddress.getPort() : "http://" + (host) + ":8080";
    }

    public String serverUrlStringV6() {
        return publicHost.equals("localhost") ? "http://localhost:"+clientAddress.getPort() : "http://" + (hostV6 != null ? "["+hostV6+"]" : host) + ":8080";
    }


    public String domainUrlStringV4() {
        return publicHost.equals("localhost") ? "https://localhost:"+clientAddress.getPort() : "https://" + publicHost + ":8080";
    }

    public String directUrlStringV4() {
        return publicHost.equals("localhost") ? "http://localhost:"+clientAddress.getPort() : "http://" + host + ":8080";
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
    public static NodeInfo initFrom(ResultSet rs) throws SQLException, EncryptionError {
        return new NodeInfo(new PublicKey(rs.getBytes("public_key")), rs.getInt("node_number"), rs.getString("node_name"), rs.getString("host"),rs.getString("public_host"),
                rs.getInt("udp_server_port"), rs.getInt("http_client_port"), rs.getInt("http_server_port"));

    }

    public boolean hasV6() {
        return hostV6 != null;
    }
}
