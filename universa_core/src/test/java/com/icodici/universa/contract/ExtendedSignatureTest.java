/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExtendedSignatureTest extends TestCase{

    @Test
    public void sign() throws Exception {
        byte[] data = "Hello world".getBytes();
        PrivateKey k = TestKeys.privateKey(3);
        byte [] signature = ExtendedSignature.sign(k, data);
        PublicKey pubKey = k.getPublicKey();
        ExtendedSignature es = ExtendedSignature.verify(pubKey, signature, data);
        assertNotNull(es);
        assertAlmostSame(es.getCreatedAt(), LocalDateTime.now());
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.keyId(pubKey));
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.extractKeyId(signature));
    }
}