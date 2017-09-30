/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import net.sergeych.tools.Binder;
import org.junit.Test;

public class SplitJoinPermissionTest {
    @Test
    public void checkChanges() throws Exception {
    }

    @Test
    public void testProperDecimals() throws Exception {
        // 100 nUTC for example
        Binder x = Binder.of(
                "saldo", "0.000000100"
        );

    }
}