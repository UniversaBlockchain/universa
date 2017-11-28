/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 * flint test commit
 *
 */

package com.icodici.universa;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DecimalTest {

    @Test
    public void divideAndRemainder() throws Exception {
        Decimal x = new Decimal("1000000000000");
        Decimal[] dr = x.divideAndRemainder(new Decimal(3));
        assertEquals("333333333333", dr[0].toString());
        assertEquals(1, dr[1].intValue());
    }

    @Test
    public void testPrecision() throws Exception {
        Decimal x = new Decimal("1000000000000");
        Decimal y = x.divide(new Decimal(3));
        assertEquals("0.333333333", y.getFraction().toString());
        assertEquals("333333333333.333333333", y.toString());
    }

    @Test
    public void ulp() throws Exception {
        Decimal x = new Decimal("1000000000000.0000000001");
        assertEquals( 1e-10,x.ulp().doubleValue(), 0);
    }

}
