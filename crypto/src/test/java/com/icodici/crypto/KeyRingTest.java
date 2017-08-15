/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by sergeych on 20.12.16.
 */
public class KeyRingTest {
    @Test
    public void saveAndRestore() throws Exception {
        KeyRing kr = new KeyRing();
        SymmetricKey sk1 = new SymmetricKey();
        SymmetricKey sk2 = new SymmetricKey();
        AbstractKey privateKey = TestKeys.privateKey(0);
        AbstractKey publicKey1 = TestKeys.privateKey(1).getPublicKey();
        AbstractKey publicKey2 = privateKey.getPublicKey();

        kr.addKeys( sk1, sk2, privateKey, publicKey1, publicKey2);
        Binder b = kr.toBinder();
        KeyRing kr2 = KeyRing.fromBinder(b);

        for(AbstractKey k: kr2.keySet()) {
            assertTrue(kr2.contains(k));
            assertTrue(kr.contains(k));
        }
        assertEquals(kr, kr2);
    }

    @Test
    public void findKey() throws Exception {
        KeyInfo i1 = new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 1024, null, null);
        AbstractKey pk1 = i1.derivePassword("helluva");
        KeyInfo i2 = new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 1025, null, "the tag".getBytes());
        AbstractKey pk2 = i2.derivePassword("helluva");
        assertEquals(i2.getTag(), pk2.info().getTag());

        KeyRing kr = new KeyRing();
        SymmetricKey sk1 = new SymmetricKey();
        SymmetricKey sk2 = new SymmetricKey();
        AbstractKey privateKey = TestKeys.privateKey(0);
        AbstractKey publicKey1 =TestKeys.privateKey(1).getPublicKey();
        AbstractKey publicKey2 = privateKey.getPublicKey();

        kr.addKeys( sk1, sk2, privateKey, publicKey1, publicKey2, pk1, pk2 );

        kr.addKeys(pk1, pk2);
        Binder b = kr.toBinder();
        KeyRing kr2 = KeyRing.fromBinder(b);

        assertTrue(kr.keySet().contains(pk1));
        assertTrue(kr.keySet().contains(pk2));

        assertEquals(pk2, kr.findKey(i2).toArray()[0]);
        assertEquals(pk2, kr2.findKey(i2).toArray()[0]);

        final Collection<AbstractKey> keys = kr.findKey(i1);
        assertTrue(keys.contains(pk1));
        assertTrue(keys.contains(pk1));
        assertTrue(keys.contains(sk1));
        assertTrue(keys.contains(sk2));
        assertEquals(4, kr2.findKey(i1).size());
    }

}