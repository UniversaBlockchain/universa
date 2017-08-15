/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// todo: implement Set comparing

/**
 * Deep compare of simple types, maps and lists. Found differences are provided as a hierarchy of Delta subclasses:
 * simple and unknown types produce {@link ChangedItem}, Maps and lists procude {@link MapDelta} and {@link ListDelta}
 * and plain arrays produce {@link ArrayDelta}.
 * <p>
 * Note that maps could be effectively compared only if use same key types. For maps, lists and arrays removed, added
 * and changed keys are detected separately, using {@link CreatedItem}, {@link RemovedItem}. Changed items could be any
 * {@link Delta} inherited instances.
 * <p>
 * Comaring is made in terms of the value being changed, from old value to new value.
 * <p>
 * To perform deep compare use {@link Delta#between(Object, Object)} method.
 * <p>
 * This is an abstract base class which is a root for implementations describing different types of differences ;)
 *
 * @param <T>
 * @param <U>
 */
abstract public class Delta<T, U> {
    private final Delta parent;
    private final T oldValue;
    private final U newValue;
    private final List<Delta> children = new ArrayList<>();

    public Delta(Delta parent, T oldValue, U newValue) {
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.parent = parent;
    }

    /**
     * Subclasses must call it after the difference item is completely constructed and {@link #isEmpty()} return valid
     * result. This method builds parent-child tree of Delta instances, excluding empty ones.
     */
    protected void registerInParent() {
        if (parent != null && !isEmpty())
            parent.children.add(this);

    }

    /**
     * Same as {@link #between(Object, Object)} but specifying parent object.
     *
     * @param parent
     * @param oldValue
     * @param newValue
     * @param <T>
     * @param <U>
     * @param <D>
     *
     * @return
     */
    static <T, U, D extends Delta> D between(Delta parent, T oldValue, U newValue) {
        if (oldValue == null && newValue == null)
            return null;

        if (oldValue == null || newValue == null)
            return (D) new ChangedItem(parent, oldValue, newValue);

        if (oldValue instanceof Map && newValue instanceof Map)
            return (D) MapDelta.compare(parent, (Map) oldValue, (Map) newValue);

        if (oldValue instanceof List && newValue instanceof List)
            return (D) ListDelta.compare(parent, (List<T>) oldValue, (List<U>) newValue);

//        if( oldValue instanceof Collection && newValue instanceof Collection )
//            return (D) compareCollections(parent, (Collection)oldValue, (Collection)newValue);

        if (oldValue.getClass().isArray() && newValue.getClass().isArray()) {
            return (D) ArrayDelta.compare(parent, (T[]) oldValue, (U[]) newValue);
        }
        return oldValue.equals(newValue) ? null : (D) new ChangedItem(parent, oldValue, newValue);
    }

    /**
     * Deply compare two objects and calculate the difference. Depending on its type, the {@link MapDelta}, {@link
     * ListDelta}, {@link ArrayDelta} ioe {@link ChangedItem} will be returned. Deep diff traverse structures of {@link
     * Map}, {@link List} and primitive java arrays. Difference in collection is showb with {@link ChangedItem}, {@link
     * CreatedItem} and {@link RemovedItem}. Note that Map instances should use the same key type (for example, String)
     * to properly calculate the delta.
     *
     * We use "old" and "new" item terminology for clarity, actually, if can compare any 2 objects.
     *
     * @param oldValue first value to compare
     * @param newValue second value to compare
     * @param <T>
     * @param <U>
     * @param <D>
     *
     * @return Delta or null if oldValue is deeply equal to the new value.
     */
    public static <T, U, D extends Delta> D between(T oldValue, U newValue) {
        return between(null, oldValue, newValue);
    }


    public T oldValue() {
        return oldValue;
    }

    public U newValue() {
        return newValue;
    }


    /**
     * @return true if the difference is not found. This is internal function to be implemented by subclasses.
     */
    abstract public boolean isEmpty();

    public List<Delta> getNestedDelta() {
        return children;
    }

}
