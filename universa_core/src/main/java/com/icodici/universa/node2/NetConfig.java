/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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

    public void addNode(NodeInfo n) {
        byNumber.put(n.getId(), n);
        byName.put(n.getName(), n);
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

}
