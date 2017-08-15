/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListDelta<T, U> extends Delta {

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

    ListDelta(Delta parent, List<T> tt, List<U> uu) {
        super(parent, tt, uu);
        for (int i = 0; i < tt.size(); i++) {
            if (i < uu.size()) {
                Delta d = Delta.between(this, tt.get(i), uu.get(i));
                if (d != null)
                    changes.put(i, d);
            } else {
                changes.put(i, new RemovedItem(this, tt.get(i)));
            }
        }
        for (int i = tt.size(); i < uu.size(); i++) {
            changes.put(i, new CreatedItem(parent, uu.get(i)));
        }
        registerInParent();
    }

    /**
     * Compare two {@link List} instances. Note that current implementation assumes position-dependent array, not set,
     * does not look for minimal delta, e.g. if the first element is popped (all attay shifted) it will provide attay of
     * {@link ChangedItem} instances and {@link RemovedItem} for the last element, what is not exactly right for arrays
     * used as sets. We strongly recommend using hash-based sets.
     *
     * @param parent
     * @param tt
     * @param uu
     * @param <T>
     * @param <U>
     *
     * @return the {@link ListDelta} instance or null if lists appears to be equal
     */
    public static <T, U> Delta compare(Delta parent, List<T> tt, List<U> uu) {
        ListDelta d = new ListDelta(parent, tt, uu);
        return d.isEmpty() ? null : d;
    }

    public void addChange(int atIndex, Delta change) {
        changes.put(atIndex, change);
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
