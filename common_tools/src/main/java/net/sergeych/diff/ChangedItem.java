/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

/**
 * Represent an element that is changed without any further information, e.g. deep inspection is not available. For
 * example, the types are too different, or different primitive value types.
 * @param <O>
 * @param <N>
 */
public class ChangedItem<O,N> extends Delta<O,N> {

    public ChangedItem(Delta parent, O oldValue, N newValue)
    {
        super(parent, oldValue, newValue);
        registerInParent();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

}
