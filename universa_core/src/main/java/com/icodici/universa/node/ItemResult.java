/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * The exported state of the item. This object is used to return data for the external (e.g. network) queries. We do not
 * expose local data in direct mode. It is a "structure" of final members, to simplify access and avoid getters.
 */
public class ItemResult {
    /**
     * The current state of the item in question
     */
    public final ItemState state;
    /**
     * true if the node has the copy of approvable item at the moment. Note that the node will discard its copy as soon
     * as the consensus of any sort will be found, or the election will fail (timeout, no quorum, etc)
     */
    public final boolean haveCopy;
    /**
     * Time when the item was created on the node. It will be slightly different accross the network
     */
    public final LocalDateTime createdAt;
    /**
     * Current expiration time. It could be changed if the state is not final. Expired items are discarded by the
     * network.
     */
    public final LocalDateTime expiresAt;

    /**
     * Initialize from a record and posession flag
     *
     * @param record   record to get data from
     * @param haveCopy true if the node has a copy of the {@link Approvable} item (e.g. one can try go call {@link
     *                 Node#getItem(HashId)}  on it
     */
    public ItemResult(StateRecord record, boolean haveCopy) {
        state = record.getState();
        this.haveCopy = haveCopy;
        createdAt = record.getCreatedAt();
        expiresAt = record.getExpiresAt();
    }

    /**
     * Initialize from a record
     *
     * @param record to get data from
     */
    public ItemResult(StateRecord record) {
        this(record, false);
    }

    /**
     * Construct from serialized parameters, presented in the binder instance
     *
     * @param fields binder with named parameters (case sensitive field names, like haveCopy or createdAt)
     */
    public ItemResult(Binder fields) {
        state = ItemState.valueOf(fields.getStringOrThrow("state"));
        haveCopy = fields.getBoolean("haveCopy");
        createdAt = fields.getLocalDateTimeOrThrow("createdAt");
        expiresAt = fields.getLocalDateTime("expiresAt", null);
    }

    public ItemResult(ItemState state, boolean haveCopy, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.state = state;
        this.haveCopy = haveCopy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Binder toBinder() {
        return Binder.fromKeysValues(
                "state", state.name(),
                "haveCopy", haveCopy,
                "createdAt", createdAt,
                "expiresAt", expiresAt
        );
    }

    @Override
    public String toString() {
        return "ItemResult<" + state + " " + createdAt + " (" + (haveCopy ? "copy" : "") + ")>";
    }

    /**
     * The equivalence is not absolutely exact. As serializing and deserializing often looses seconds fration, it
     * compares {@link #expiresAt} and {@link #createdAt} only truncated to seconds. So, if comarison with maximum
     * precision is of essence, compare these fields separately.
     *
     * @param obj presumably another {@link ItemResult} instance
     *
     * @return true if instances represent the same state with datetimes fields equal to seconds.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ItemResult) {
            ItemResult result2 = (ItemResult) obj;
            if (result2.state == state && result2.haveCopy == haveCopy &&
                    createdAt.truncatedTo(ChronoUnit.SECONDS).equals(result2.createdAt.truncatedTo(ChronoUnit.SECONDS))) {
                return (expiresAt == null && result2.expiresAt == null) ||
                        expiresAt.truncatedTo(ChronoUnit.SECONDS).equals(result2.expiresAt.truncatedTo(ChronoUnit.SECONDS));
            }
        }
        return false;
    }

    static {
        Boss.registerAdapter(ItemResult.class, new Boss.Adapter() {
            @Override
            public Binder serialize(Object object) {
                return ((ItemResult)object).toBinder();
            }

            @Override
            public ItemResult deserialize(Binder binder) {
                return new ItemResult(binder);
            }

            @Override
            public String typeName() {
                return "ItemResult";
            }
        });
    }
}
