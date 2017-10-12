/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;

import java.util.WeakHashMap;
import java.util.concurrent.Callable;

/**
 * The smart lock, allow global synchronize on per-hashId operation. Just call {@link #synchronize(HashId, Callable)}
 * and execute your code in a callable argument.
 */
public final class ItemLock {

    private ItemLock() {
    }

    /**
     * Execute a callable acquiring a unique lock (mutex) for a given {@link HashId}. Locks is released upon callable
     * return.
     *
     * @param id       ot get a lock to
     * @param callable lamda to execute exclusively for the id
     * @param <T>
     *
     * @return whatever the callable returns
     *
     * @throws Exception whatever callable throws
     */
    static public <T> T synchronize(HashId id, Callable<T> callable) throws Exception {
        ItemLock lock = null;

        // short exclusive mutex: obtaning a lock
        synchronized (monitors) {
            lock = monitors.get(id);
            if (lock == null) {
                lock = new ItemLock();
                monitors.put(id, lock);
            }
        }
        // now we only lock the item:
        synchronized (lock) {
            return (T) callable.call();
        }
    }

    static private WeakHashMap<HashId, ItemLock> monitors = new WeakHashMap<>();

    /**
     * Niber of cached locks. Not all of them are acquired. Locks are cached as long as corresponding {@link HashId} is
     * alive, so do not clone() and copy them, use shared instance (it is generally immutable). When the last instance
     * of this itemId will be garbage collected, the lock will be removed from the cache.
     *
     * @return number of cached locks
     */
    public static int size() {
        return monitors.size();
    }
}
