/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemState;

public abstract class Node  {

    /**
     * Send notification to a single node about sending
     * @param itemId
     * @param s
     */
    public abstract void notify(HashId itemId, ItemState s);


    /**
     * download an item with a given id
     *
     * @param itemId
     * @return
     */

    public abstract Approvable getItem(HashId itemId);
}
