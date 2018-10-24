package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.Ledger;

import java.time.ZonedDateTime;

public class CallbackRecord {
    private HashId id;
    private long environmentId;
    private Node.FollowerCallbackState state;
    private ZonedDateTime expiresAt;
    private ZonedDateTime storedUntil;

    public CallbackRecord(HashId id, long environmentId, Node.FollowerCallbackState state, ZonedDateTime expiresAt, ZonedDateTime storedUntil) {
        this.id = id;
        this.environmentId = environmentId;
        this.state = state;
        this.expiresAt = expiresAt;
        this.storedUntil = storedUntil;
    }

    public static void addCallbackRecordToLedger(HashId id, long environmentId, Config config, int networkNodesCount, Ledger ledger) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime expiresAt = now.plus(config.getFollowerCallbackExpiration()).plusSeconds(config.getFollowerCallbackDelay().getSeconds() * (networkNodesCount + 3));
        ZonedDateTime storedUntil = now.plus(config.getFollowerCallbackStateStoreTime());

        ledger.addFollowerCallback(id, environmentId, expiresAt, storedUntil);
    }

    public void resyncState() {

    }
}
