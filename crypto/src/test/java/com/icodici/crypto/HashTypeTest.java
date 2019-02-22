/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.digest.Sha256;
import com.icodici.crypto.digest.Sha512;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by sergeych on 18.12.16.
 */
public class HashTypeTest {


    @Test
    public void checkAlgorithmNames() throws Exception {
        assertEquals(HashType.SHA1.makeDigest().getAlgorithmName(),"SHA-1");
        assertEquals(HashType.SHA256.makeDigest().getAlgorithmName(),"SHA-256");
        assertEquals(HashType.SHA512.makeDigest().getAlgorithmName(),"SHA-512");
        assertEquals(HashType.SHA3_256.makeDigest().getAlgorithmName(),"SHA3-256");
        assertEquals(HashType.SHA3_384.makeDigest().getAlgorithmName(),"SHA3-384");
        assertEquals(HashType.SHA3_512.makeDigest().getAlgorithmName(),"SHA3-512");
    }


}