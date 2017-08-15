/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;

public class FakeItem implements Approvable {
    HashId hashId;

    public FakeItem(StateRecord record) {
        hashId = record.getId();
    }

    @Override
    public boolean check() {
        return true;
    }

    @Override
    public HashId getId() {
        return hashId;
    }
}
