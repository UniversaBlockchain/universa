/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.node.Network;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TestV1Network implements AutoCloseable {

    private final ArrayList<NetworkV1> networks = new ArrayList<>();
    private final NetConfig netConfig;

    public TestV1Network(String rootPath, int clientPortBase) throws IOException, SQLException {
        this(NetConfig.from(rootPath),clientPortBase);
    }
    public TestV1Network(NetConfig netConfig, int clientPortBase) throws IOException, SQLException {
        this.netConfig = netConfig;
        for(NetConfig.NodeInfo i: netConfig.getRoster().values()){
            build(i, clientPortBase++);
        }
    }

    public @NonNull NetworkV1 get(int i) {
        return networks.get(i);
    }

    Network build(NetConfig.NodeInfo i, int clientPort) throws IOException, SQLException {
        NetworkV1 n = new NetworkV1(i,clientPort);
        networks.add(n);
        return n;
    }

    @Override
    public void close() throws Exception {
        networks.forEach(n -> {
            try {
                n.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public List<NetworkV1> getNetworks() {
        return networks;
    }

}
