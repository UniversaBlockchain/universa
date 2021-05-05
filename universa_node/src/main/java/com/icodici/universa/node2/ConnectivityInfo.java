package com.icodici.universa.node2;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

public class ConnectivityInfo {
    private final NodeInfo node;
    private final Set<NodeInfo> unreachableNodes;
    private final Instant expiresAt;

    public ConnectivityInfo(NodeInfo node, Duration validityPeriod, Set<NodeInfo> unreachableNodes) {
        this.node = node;
        this.unreachableNodes = unreachableNodes;
        this.expiresAt = Instant.now().plus(validityPeriod);
    }

    public Set<NodeInfo> getUnreachableNodes() {
        return unreachableNodes;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
