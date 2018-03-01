/*
 * Copyright (c) 2018, iCodici S.n.C, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;

import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * The smart lock, allow global synchronize on per-hashId operation. Just call {@link #synchronize(HashId, Function)}
 * and execute your code in a callable argument.
 */
public final class ParcelLock {

    public ParcelLock() {
    }

    /**
     * Execute a callable acquiring a unique lock (mutex) for a given {@link HashId}. Locks is released upon callable
     * return.
     *
     * @param id       ot get a lock to
     * @param callable lamda to execute exclusively for the id
     * @param <T> type
     *
     * @return whatever the callable returns
     *
     * @throws Exception whatever callable throws
     */
    public <T> T synchronize(HashId id, Function<Object, T> callable) throws Exception {
        ParcelLock lock = null;

        // short exclusive mutex: obtaning a lock
        synchronized (monitors) {
            lock = monitors.get(id);
            if (lock == null) {
                lock = new ParcelLock();
                monitors.put(id, lock);
            }
        }
        // now we only lock the item:
        synchronized (lock) {
            return (T) callable.apply(lock);
        }
    }

    private WeakHashMap<HashId, ParcelLock> monitors = new WeakHashMap<>();

    /**
     * Niber of cached locks. Not all of them are acquired. Locks are cached as long as corresponding {@link HashId} is
     * alive, so do not clone() and copy them, use shared instance (it is generally immutable). When the last instance
     * of this itemId will be garbage collected, the lock will be removed from the cache.
     *
     * @return number of cached locks
     */
    public int size() {
        return monitors.size();
    }
}
