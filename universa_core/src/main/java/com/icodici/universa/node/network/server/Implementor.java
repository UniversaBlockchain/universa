/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network.server;

import net.sergeych.tools.Binder;

public interface Implementor {
    Binder apply(Session session) throws Exception;
}
