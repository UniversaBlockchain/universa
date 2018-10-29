package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.Ledger;
import net.sergeych.tools.Binder;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

// *

public class CallbackRecord {
    private HashId id;
    private long environmentId;
    private long subscriptionId;
    private Node.FollowerCallbackState state;
    private ZonedDateTime expiresAt;

    // synchronization counters
    private AtomicInteger completedNodes = new AtomicInteger(0);
    private AtomicInteger failedNodes = new AtomicInteger(0);
    private AtomicInteger allNodes = new AtomicInteger(0);

    // consensus for synchronization state and limit for end synchronization
    private int consensus = 1;
    private int limit = 1;

    public CallbackRecord(HashId id, long environmentId, long subscriptionId, Node.FollowerCallbackState state) {
        this.id = id;
        this.environmentId = environmentId;
        this.subscriptionId = subscriptionId;
        this.state = state;
    }

    // *

    public static void addCallbackRecordToLedger(HashId id, long environmentId, long subscriptionId, Config config, int networkNodesCount, Ledger ledger) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plus(config.getFollowerCallbackExpiration()).plusSeconds(config.getFollowerCallbackDelay().getSeconds() * (networkNodesCount + 3));
        ZonedDateTime storedUntil = now.plus(config.getFollowerCallbackStateStoreTime());

        ledger.addFollowerCallback(id, environmentId, subscriptionId, expiresAt, storedUntil);
    }

    public HashId getId() { return id; }

    public ZonedDateTime getExpiresAt() { return expiresAt; }

    public void setExpiresAt(ZonedDateTime expiresAt) { this.expiresAt = expiresAt; }

    private int incrementCompletedNodes() {
        allNodes.incrementAndGet();
        return completedNodes.incrementAndGet();
    }

    private int incrementFailedNodes() {
        allNodes.incrementAndGet();
        return failedNodes.incrementAndGet();
    }

    private void incrementOtherNodes() {
        allNodes.incrementAndGet();
    }

    private void complete(Node node) {

        // full environment
        Binder fullEnvironment = node.getFullEnvironment(environmentId, subscriptionId);
        FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
        NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");
        NContractFollowerSubscription subscription = (NContractFollowerSubscription) fullEnvironment.get("subscription");

        // complete event
        follower.onContractSubscriptionEvent(new ContractSubscription.CompletedEvent() {
            @Override
            public MutableEnvironment getEnvironment() {
                return environment;
            }

            @Override
            public ContractSubscription getSubscription() {
                return subscription;
            }
        });
        environment.save();
    }

    private void fail(Node node) {

        // full environment
        Binder fullEnvironment = node.getFullEnvironment(environmentId, subscriptionId);
        FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
        NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");
        NContractFollowerSubscription subscription = (NContractFollowerSubscription) fullEnvironment.get("subscription");

        // fail event
        follower.onContractSubscriptionEvent(new ContractSubscription.FailedEvent() {
            @Override
            public MutableEnvironment getEnvironment() {
                return environment;
            }

            @Override
            public ContractSubscription getSubscription() {
                return subscription;
            }
        });
        environment.save();
    }

    private void spent(Node node) {

        // full environment
        Binder fullEnvironment = node.getFullEnvironment(environmentId, subscriptionId);
        FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
        NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");
        NContractFollowerSubscription subscription = (NContractFollowerSubscription) fullEnvironment.get("subscription");

        // fail event
        follower.onContractSubscriptionEvent(new ContractSubscription.SpentEvent() {
            @Override
            public MutableEnvironment getEnvironment() {
                return environment;
            }

            @Override
            public ContractSubscription getSubscription() {
                return subscription;
            }
        });
        environment.save();
    }

    // *

    public void setConsensusAndLimit(int nodesCount) {
        consensus = (int) Math.ceil((nodesCount - 1) * 0.51);
        limit = (int) Math.floor(nodesCount * 0.8);
    }

    // *

    public boolean synchronizeState(Node.FollowerCallbackState newState, Ledger ledger, Node node) {
        if (newState == Node.FollowerCallbackState.COMPLETED) {
            if (incrementCompletedNodes() >= consensus) {
                if (state == Node.FollowerCallbackState.STARTED)
                    complete(node);
                else if (state == Node.FollowerCallbackState.EXPIRED)
                    spent(node);

                ledger.updateFollowerCallbackState(id, Node.FollowerCallbackState.COMPLETED);
                return true;
            }
        } else if ((newState == Node.FollowerCallbackState.FAILED) || (newState == Node.FollowerCallbackState.EXPIRED)) {
            if (incrementFailedNodes() >= consensus) {
                if (state == Node.FollowerCallbackState.STARTED)
                    fail(node);

                ledger.updateFollowerCallbackState(id, Node.FollowerCallbackState.FAILED);
                return true;
            }
        } else
            incrementOtherNodes();

        return false;
    }

    // *

    public boolean endSynchronize(Ledger ledger, Node node) {
        if (ZonedDateTime.now().isBefore(expiresAt))
            return false;

        // final (additional) check for consensus of callback state
        if (completedNodes.get() >= consensus) {
            if (state == Node.FollowerCallbackState.STARTED)
                complete(node);
            else if (state == Node.FollowerCallbackState.EXPIRED)
                spent(node);

            ledger.updateFollowerCallbackState(id, Node.FollowerCallbackState.COMPLETED);
        } else if (failedNodes.get() >= consensus) {
            if (state == Node.FollowerCallbackState.STARTED)
                fail(node);

            ledger.updateFollowerCallbackState(id, Node.FollowerCallbackState.FAILED);
        } else if (allNodes.get() >= limit)
            // remove callback if synchronization is impossible
            ledger.removeFollowerCallback(id);

        return true;
    }
}
