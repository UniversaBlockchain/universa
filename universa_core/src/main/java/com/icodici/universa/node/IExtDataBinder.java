/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Leonid Novikov <flint.emerald@gmail.com>
 */

package com.icodici.universa.node;

import net.sergeych.tools.Binder;

/**
 * Interface to extra data field for {@link ItemResult}
 */
public interface IExtDataBinder {

    /**
     * Returns extra {@link Binder}. Creates new Binder if its null.
     */
    Binder getExtraBinder();

}
