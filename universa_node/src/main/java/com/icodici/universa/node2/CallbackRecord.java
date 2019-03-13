package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.Ledger;
import net.sergeych.tools.Binder;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Callback record for synchronize state of expired callbacks.
 * Callback records are subject to synchronization if callback state == STARTED or EXPIRED and time to send callback expired.
 * To synchronize the callback, it is necessary that more than half of the Universa network nodes confirm the status
 * of the callback (COMPLETED or FAILED). To synchronize the callback was considered impossible it is necessary that 80%
 * network nodes (excluding the node performing synchronization) respond, but the state of the callback cannot be synchronized.
 */
public class CallbackRecord {
    private HashId id;
    private long environmentId;
    private CallbackService.FollowerCallbackState state;
    private ZonedDateTime expiresAt;

    // synchronization counters
    private AtomicInteger completedNodes = new AtomicInteger(0);
    private AtomicInteger failedNodes = new AtomicInteger(0);
    private AtomicInteger allNodes = new AtomicInteger(0);

    // consensus for synchronization state and limit for end synchronization
    private int consensus = 1;
    private int limit = 1;

    /**
     * Create callback record.
     *
     * @param id is callback identifier
     * @param environmentId is environment subscription
     * @param state is callback state
     */
    public CallbackRecord(HashId id, long environmentId, CallbackService.FollowerCallbackState state) {
        this.id = id;
        this.environmentId = environmentId;
        this.state = state;
    }

    /**
     * Save callback record to ledger for possible synchronization.
     *
     * @param id is callback identifier
     * @param environmentId is environment identifier
     * @param config is node configuration
     * @param networkNodesCount is count of nodes in Universa network
     * @param ledger is node ledger
     */
    public static void addCallbackRecordToLedger(HashId id, long environmentId, Config config, int networkNodesCount, Ledger ledger) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plus(config.getFollowerCallbackExpiration()).plusSeconds(config.getFollowerCallbackDelay().getSeconds() * (networkNodesCount + 3));
        ZonedDateTime storedUntil = now.plus(config.getFollowerCallbackStateStoreTime());

        ledger.addFollowerCallback(id, environmentId, expiresAt, storedUntil);
    }

    public HashId getId() { return id; }

    public CallbackService.FollowerCallbackState getState() { return state; }

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
        synchronized (node.getCallbackService()) {
            // full environment
            Binder fullEnvironment = node.getFullEnvironment(environmentId);
            FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
            NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");

            // complete event
            follower.onContractSubscriptionEvent(new ContractSubscription.CompletedEvent() {
                @Override
                public MutableEnvironment getEnvironment() {
                    return environment;
                }
            });
            environment.save();
        }
    }

    private void fail(Node node) {
        synchronized (node.getCallbackService()) {
            // full environment
            Binder fullEnvironment = node.getFullEnvironment(environmentId);
            FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
            NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");

            // fail event
            follower.onContractSubscriptionEvent(new ContractSubscription.FailedEvent() {
                @Override
                public MutableEnvironment getEnvironment() {
                    return environment;
                }
            });
            environment.save();
        }
    }

    private void spent(Node node) {
        synchronized (node.getCallbackService()) {
            // full environment
            Binder fullEnvironment = node.getFullEnvironment(environmentId);
            FollowerContract follower = (FollowerContract) fullEnvironment.get("follower");
            NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");

            // fail event
            follower.onContractSubscriptionEvent(new ContractSubscription.SpentEvent() {
                @Override
                public MutableEnvironment getEnvironment() {
                    return environment;
                }
            });
            environment.save();
        }
    }

    /**
     * Set network consensus needed for synchronize callback state. And set limit of network nodes count needed for
     * delete callback record (if 80% network nodes respond, but the state of the callback cannot be synchronized).
     *
     * @param nodesCount is count of nodes in Universa network
     */
    public void setConsensusAndLimit(int nodesCount) {
        consensus = (int) Math.ceil((nodesCount - 1) * 0.51);
        limit = (int) Math.floor(nodesCount * 0.8);
    }

    /**
     * Increases callback state counters according new state received from notification. Callback state will be
     * synchronized if the number of notifications from Universa nodes with states COMPLETED or FAILED reached
     * a given consensus.
     *
     * @param newState is callback state received from notification
     * @param ledger is node ledger
     * @param node is Universa node
     *
     * @return true if callback state is synchronized
     */
    public boolean synchronizeState(CallbackService.FollowerCallbackState newState, Ledger ledger, Node node) {
        if (newState == CallbackService.FollowerCallbackState.COMPLETED) {
            if (incrementCompletedNodes() >= consensus) {
                if (state == CallbackService.FollowerCallbackState.STARTED)
                    complete(node);
                else if (state == CallbackService.FollowerCallbackState.EXPIRED)
                    spent(node);

                ledger.updateFollowerCallbackState(id, CallbackService.FollowerCallbackState.COMPLETED);
                return true;
            }
        } else if ((newState == CallbackService.FollowerCallbackState.FAILED) || (newState == CallbackService.FollowerCallbackState.EXPIRED)) {
            if (incrementFailedNodes() >= consensus) {
                if (state == CallbackService.FollowerCallbackState.STARTED)
                    fail(node);

                ledger.updateFollowerCallbackState(id, CallbackService.FollowerCallbackState.FAILED);
                return true;
            }
        } else
            incrementOtherNodes();

        return false;
    }

    /**
     * Final checkout of the callback state counters if the time to synchronize callback expired. Callback state
     * will be synchronized if the number of notifications from Universa nodes with states COMPLETED or FAILED reached
     * a given consensus.
     *
     * If reached the callback synchronization consensus, updates state of the callback in ledger.
     * If reached the nodes limit for ending synchronization (but the state of the callback cannot be synchronized),
     * callback record removes from ledger.
     *
     * @param ledger is node ledger
     * @param node is Universa node
     *
     * @return true if callback synchronization is ended
     */
    public boolean endSynchronize(Ledger ledger, Node node) {
        if (ZonedDateTime.now().isBefore(expiresAt))
            return false;

        // final (additional) check for consensus of callback state
        if (completedNodes.get() >= consensus) {
            if (state == CallbackService.FollowerCallbackState.STARTED)
                complete(node);
            else if (state == CallbackService.FollowerCallbackState.EXPIRED)
                spent(node);

            ledger.updateFollowerCallbackState(id, CallbackService.FollowerCallbackState.COMPLETED);
        } else if (failedNodes.get() >= consensus) {
            if (state == CallbackService.FollowerCallbackState.STARTED)
                fail(node);

            ledger.updateFollowerCallbackState(id, CallbackService.FollowerCallbackState.FAILED);
        } else if (allNodes.get() >= limit)
            // remove callback if synchronization is impossible
            ledger.removeFollowerCallback(id);

        return true;
    }
}
