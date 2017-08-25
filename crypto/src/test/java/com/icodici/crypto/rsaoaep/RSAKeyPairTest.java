/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.test.RSAOAEPTestVectors;

import static org.junit.Assert.assertArrayEquals;

/**
 * Created by amyodov on 21.04.16.
 */
public class RSAKeyPairTest {
    /** Test vectors from RSA OAEP specification. */
    private static final RSAOAEPTestVectors rsaSpec = new RSAOAEPTestVectors();

    /**
     * Test {@link RSAKeyPair#fromExponents}.
     */
    @org.junit.Test
    public void fromExponents() throws Exception {
        RSAKeyPair rsaPair = RSAKeyPair.fromExponents(rsaSpec.e, rsaSpec.p, rsaSpec.q);
        assertArrayEquals(rsaPair.n, rsaSpec.n);
        assertArrayEquals(rsaPair.e, rsaSpec.e);
        assertArrayEquals(rsaPair.d, rsaSpec.d);
        assertArrayEquals(rsaPair.p, rsaSpec.p);
        assertArrayEquals(rsaPair.q, rsaSpec.q);
        assertArrayEquals(rsaPair.dP, rsaSpec.dP);
        assertArrayEquals(rsaPair.dQ, rsaSpec.dQ);
        assertArrayEquals(rsaPair.qInv, rsaSpec.qInv);
    }
}
