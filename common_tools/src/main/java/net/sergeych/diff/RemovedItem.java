/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

/**
 * Item is removed from some collection
 * @param <T>
 * @param <U>
 */
public class RemovedItem<T, U> extends Delta<T, U> {

    public T getRemovedItem() {
        return (T) oldValue();
    }

    public RemovedItem(Delta parent, T removedItem) {
        super(parent, removedItem, null);
        registerInParent();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
