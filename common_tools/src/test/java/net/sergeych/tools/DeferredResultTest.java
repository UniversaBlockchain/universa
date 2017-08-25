/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, July 2017.
 *
 */

package net.sergeych.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DeferredResultTest {
    @Test
    public void success() throws Exception {
        DeferredResult dr = new DeferredResult();
        dr.success( (text-> assertEquals("hello", text)));
        dr.sendSuccess("hello");
    }

}