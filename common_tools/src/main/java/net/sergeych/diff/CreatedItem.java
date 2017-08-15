/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.diff;

/**
 * New item inserted in some collection
 * @param <T>
 */
public class CreatedItem<T> extends Delta {
    public CreatedItem(Delta parent,T newItem) {
        super(parent, null, newItem );
        registerInParent();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

}
