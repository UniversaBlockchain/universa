/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

public class NetConfig {

    private final Map<Integer,NodeInfo> byNumber = new HashMap<>();
    private final Map<String,NodeInfo> byName = new HashMap<>();

    public NetConfig() {}

    public NetConfig(Collection<NodeInfo> nodes) {
        nodes.forEach(n->{
            addNode(n);
        });
    }

    public NetConfig(String v2Path) throws IOException {
        Files.newDirectoryStream(Paths.get(v2Path), path -> path.toString().endsWith(".yaml"))
                .forEach(fileName-> {
                    addNode(NodeInfo.loadYaml(fileName));
                });
    }

    public void addNode(NodeInfo n) {
        if( n != null ) {
            byNumber.put(n.getNumber(), n);
            byName.put(n.getName(), n);
        }
    }

    public NodeInfo getInfo(String nodeName) {
        return byName.get(nodeName);
    }

    public NodeInfo getInfo(int nodeId) {
        return byNumber.get(nodeId);
    }

    public void forEachNode(Consumer<NodeInfo> consumer) {
        byNumber.forEach((i,n)->consumer.accept(n));
    }

    public int size() {
        return byNumber.size();
    }

    public List<NodeInfo> toList() {
        return new ArrayList<>(byName.values());
    }
}
