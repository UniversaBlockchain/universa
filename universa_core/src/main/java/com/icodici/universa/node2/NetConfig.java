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

public class NetConfig {

    private final Map<Integer,NodeInfo> byNumber = new HashMap<>();

    public NetConfig(Collection<NodeInfo> nodes) {
        nodes.forEach(n->byNumber.put(n.getId(), n));
    }

}
