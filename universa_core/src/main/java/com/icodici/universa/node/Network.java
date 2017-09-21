/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The abstraction of a Universa network: provides list of known nodes, consensus limits and mechanism to register and
 * unregister nodes.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class Network implements AutoCloseable {

    private Duration votingExpiration = Duration.ofSeconds(10);
    private int negativeConsensus;
    private int positiveConsensus;
    private ConcurrentHashMap<String, Node> allNodes = new ConcurrentHashMap<>();
    private TemporalAmount archiveExpiration = Duration.ofDays(30);
    private TemporalAmount approvedExpiration = Duration.ofDays((int) (365.26 * 10)); // 10 years
    private Duration maxElectionsTime = Duration.ofSeconds(5);
    private Duration declinedExpiration = Duration.ofDays(30);
    private Duration requeryPause = Duration.ofMillis(20);
    private LocalNode localNode;

    public Duration getVotingExpiration() {
        return votingExpiration;
    }

    public int getNegativeConsensus() {
        return negativeConsensus;
    }

    public void setNegativeConsensus(int negativeConsensus) {
        this.negativeConsensus = negativeConsensus;
    }

    public int getPositiveConsensus() {
        return positiveConsensus;
    }

    /**
     * True if number of attached nodes forms the quorum. It is required state to process clieant API calls, until hten,
     * an error of type temporarily out of service should be reported.
     *
     * @return true if the quorum is found.
     */
    public boolean hasQuorum() {
        return positiveConsensus < allNodes.size();
    }

    public void setPositiveConsensus(int positiveConsensus) {
        this.positiveConsensus = positiveConsensus;
    }

    public Node getNode(String nodeId) {
        return allNodes.get(nodeId);
    }

    public List<Node> getAllNodes() {
        return new ArrayList<Node>(allNodes.values());
    }

    public void registerNode(Node node) {
        allNodes.put(node.getId(), node);
    }

    public void unregisterNode(Node node) {
        allNodes.remove(node.getId());
    }

    /**
     * calculate consensus values from the current number of registered nodes and a given quorum ratio for positive
     * solution. Negative solution is derived from it.
     *
     * @param positiveRatio value ]0.5, 1] range (it can not be < 50% by design) for example, 0.75 means 3/4 of the
     *                      registered nodes should vote to get a positive consensus. The calculated value is rounded.
     */
    public void deriveConsensus(double positiveRatio) {
        if (positiveRatio <= 0.5 || positiveRatio > 1)
            throw new IllegalArgumentException("parameter should be positive 0.5 < value <= 1");
        int count = allNodes.size();
        if( count == 1) {
            // 1-node test network
            negativeConsensus = positiveConsensus = 1;
            return;
        }
        if( count < 6) {
            positiveConsensus = count / 2 + 1;
            negativeConsensus = count / 2 - 1;
            if( negativeConsensus < 1 )
                negativeConsensus = 1;
        }
        else {
            positiveConsensus = (int) Math.round(count * positiveRatio);
            negativeConsensus = count - positiveConsensus + 1;
            if (negativeConsensus >= positiveConsensus || negativeConsensus < 1)
                throw new IllegalArgumentException("not enaougn nodes (" + count
                        + ") to form consensus rules for a given ratio: "
                        + positiveRatio);
        }
    }

    public TemporalAmount getArchiveExpiration() {
        return archiveExpiration;
    }

    public TemporalAmount getApprovedExpiration() {
        return approvedExpiration;
    }

    public void setApprovedExpiration(TemporalAmount approvedExpiration) {
        this.approvedExpiration = approvedExpiration;
    }

    public void setMaxElectionsTime(Duration maxElectionsTime) {
        this.maxElectionsTime = maxElectionsTime;
    }

    public Duration getMaxElectionsTime() {
        return maxElectionsTime;
    }

    public Duration getDeclinedExpiration() {
        return declinedExpiration;
    }

    public void setDeclinedExpiration(Duration declinedExpiration) {
        this.declinedExpiration = declinedExpiration;
    }

    public Duration getRequeryPause() {
        return requeryPause;
    }

    public void setRequeryPause(Duration requeryPause) {
        this.requeryPause = requeryPause;
    }

    public void registerLocalNode(LocalNode localNode) {
        if( this.localNode != null )
            throw new IllegalStateException("local node is already set");
        this.localNode = localNode;
        allNodes.put(localNode.getId(), localNode);
    }

    public LocalNode getLocalNode() {
        return localNode;
    }

    @Override
    public void close() throws Exception {
    }
}