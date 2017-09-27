/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.diff;

import java.util.HashMap;
import java.util.Map;

/**
 * Compares delta of the primitive byte arrays. See {@link Delta} for details.
 */
public class ByteArrayDelta extends Delta {
    /**
     * Get all changes as a map where key is an index of changed item and a value is a {@link Delta} instance
     * describing the change.
     *
     * @return map of changes.
     */
    public Map<Integer, Delta> getChanges() {
        return changes;
    }

    private final Map<Integer, Delta> changes = new HashMap<>();

    ByteArrayDelta(Delta parent, byte[] tt, byte[] uu) {
        super(parent, tt, uu);
        for (int i = 0; i < tt.length; i++) {
            if (i < uu.length) {
                Delta d = Delta.between(this, (int)tt[i], (int)uu[i]);
                if (d != null)
                    changes.put(i, d);
            } else {
                changes.put(i, new RemovedItem(this, (int)tt[i]));
            }
        }
        for (int i = tt.length; i < uu.length; i++) {
            changes.put(i, new CreatedItem(parent, (int)uu[i]));
        }
        registerInParent();
    }

    /**
     * Compare two arrays. Note that current implementation assumes position-dependent array, not set, does not look for
     * minimal delta, e.g. if the first element is popped (all attay shifted) it will provide attay of {@link
     * ChangedItem} instances and {@link RemovedItem} for the last element, what is not exactly right for arrays
     * used as sets. We strongly recommend using hash-based sets.
     *
     * @param parent
     * @param tt
     * @param uu
     *
     * @return
     */
    public static Delta compare(Delta parent, byte[] tt, byte[] uu) {
        ByteArrayDelta d = new ByteArrayDelta(parent, tt, uu);
        return d.isEmpty() ? null : d;
    }

    @Override
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Get a change for a given index.
     *
     * @param index integer index value
     * @return Delta instance of null if the values at the specified index were not changed
     */
    public Delta getChange(int index) {
        return changes.get(index);
    }

}
