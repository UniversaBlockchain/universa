/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

import java.util.HashMap;
import java.util.Map;

/**
 * Map difference calculation.
 * @param <T> key type
 * @param <V> first map value type
 * @param <U> second map value type
 */
public class MapDelta<T, V, U> extends Delta {
    private final Map<T, Delta> changes = new HashMap<>();

    MapDelta(Delta parent, Map<T, U> oldMap, Map<T, U> newMap) {
        super(parent, oldMap, newMap);
        oldMap.forEach((key, oldValue) -> {
            // We can't rely on null values, as Java Map can store nulls as values...
            if (newMap.containsKey(key)) {
                U newValue = newMap.get(key);
                Delta d = Delta.between(this, oldValue, newValue);
                if (d != null)
                    changes.put(key, d);
            } else {
                changes.put(key, new RemovedItem(this, oldValue));
            }
        });
        // detecting new items
        newMap.forEach((key, value) -> {
            if (!oldMap.containsKey(key)) {
                changes.put(key, new CreatedItem(this, value));
            }
        });
        registerInParent();
    }

    /**
     * Get all changes as a map where key is a key of the different items and a value is are {@link Delta} instances
     * describing the changes.
     *
     * @return map of changes.
     */

    public Map<T, Delta> getChanges() {
        return this.changes;
    }

    static public MapDelta compare(Delta parent, Map oldMap, Map newMap) {
        MapDelta delta = new MapDelta(parent, oldMap, newMap);
        return delta.isEmpty() ? null : delta;
    }

    /**
     * Compare two maps with same key types. Detected are: new items, deleted and changed items. See {@link Delta} for
     * more.
     *
     * @param oldMap
     * @param newMap
     * @return Delta showin the difference between maps or null if they are equal.
     */
    static public MapDelta compare(Map oldMap, Map newMap) {
        return new MapDelta(null, oldMap, newMap);
    }

    @Override
    public boolean isEmpty() {
        for( Delta d: changes.values() ) {
            if( !d.isEmpty() )
                return false;
        }
        return true;
    }

    /**
     * Get the difference for a given key
     * @param key
     * @return the difference or null if the items apper to be equal
     */
    public Delta getChange(T key) {
        return changes.get(key);
    }

    public void remove(T field) {
        changes.remove(field);
    }

}
