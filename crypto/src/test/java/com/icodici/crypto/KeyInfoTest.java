/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.Sha256;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 19.12.16.
 */
public class KeyInfoTest {
    @Test
    public void packNoKdf() throws Exception {
        KeyInfo h1 = new KeyInfo(KeyInfo.Algorythm.RSAPublic, new byte[]{1, 2, 3}, 512);
        KeyInfo h2 = new KeyInfo(h1.pack());
        assertEquals(h1.getAlgorythm(), h2.getAlgorythm());
        assertEquals(h1.getPRF(), h2.getPRF());
        assertEquals(h1.getRounds(), h2.getRounds());
        assertEquals(h1.getKeyLength(), h2.getKeyLength());

        assertEquals(KeyInfo.PRF.None, h2.getPRF());
        assertEquals(KeyInfo.Algorythm.RSAPublic, h2.getAlgorythm());

        assertArrayEquals(h1.getTag(), h2.getTag());
        assertArrayEquals(new byte[]{1, 2, 3}, h2.getTag());
        assertEquals(512, h2.getKeyLength());

        h1 = new KeyInfo(KeyInfo.Algorythm.RSAPrivate, new byte[]{4, 2, 3}, 256);
        h2 = new KeyInfo(h1.pack());
        assertEquals(h1.getAlgorythm(), h2.getAlgorythm());
        assertEquals(h1.getPRF(), h2.getPRF());
        assertEquals(h1.getRounds(), h2.getRounds());
        assertEquals(h1.getKeyLength(), h2.getKeyLength());

        assertEquals(KeyInfo.PRF.None, h2.getPRF());
        assertEquals(KeyInfo.Algorythm.RSAPrivate, h2.getAlgorythm());

        assertArrayEquals(h1.getTag(), h2.getTag());
        assertArrayEquals(new byte[]{4, 2, 3}, h2.getTag());
        assertEquals(256, h2.getKeyLength());

        h1 = new KeyInfo(KeyInfo.Algorythm.AES256, new byte[]{4, 2, 3}, 0);
        h2 = new KeyInfo(h1.pack());
        assertEquals(h1.getAlgorythm(), h2.getAlgorythm());
        assertEquals(h1.getPRF(), h2.getPRF());
        assertEquals(h1.getRounds(), h2.getRounds());
        assertEquals(h1.getKeyLength(), h2.getKeyLength());

        assertEquals(KeyInfo.PRF.None, h2.getPRF());
        assertEquals(KeyInfo.Algorythm.AES256, h2.getAlgorythm());

        assertArrayEquals(h1.getTag(), h2.getTag());
        assertArrayEquals(new byte[]{4, 2, 3}, h2.getTag());
        assertEquals(32, h2.getKeyLength());

    }

    @Test
    public void packKdf() throws Exception {
        KeyInfo h1 = new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 4096, null, null);
        KeyInfo h2 = new KeyInfo(h1.pack());
        assertEquals(h1.getAlgorythm(), h2.getAlgorythm());
        assertEquals(h1.getPRF(), h2.getPRF());
        assertEquals(h1.getRounds(), h2.getRounds());
        assertEquals(h1.getKeyLength(), h2.getKeyLength());

        assertEquals(KeyInfo.PRF.HMAC_SHA256, h2.getPRF());
        assertEquals(KeyInfo.Algorythm.AES256, h2.getAlgorythm());

        assertArrayEquals(h1.getTag(), h2.getTag());
    }

    @Test
    public void deriveKey() throws Exception {
        KeyInfo h1 = new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 4096, null, null);
        KeyInfo h2 = new KeyInfo(h1.pack());

        assertArrayEquals(h1.getSalt(), h2.getSalt());
        assertArrayEquals("attesta".getBytes(), h2.getSalt());

        SymmetricKey k = h1.derivePassword("elegance");
        byte[] k1 = PBKDF2.derive(Sha256.class, "elegance", "attesta".getBytes(), 4096, 32);
        assertArrayEquals(k1, k.getKey());
        assertEquals(h1, k.info());
    }

    @Test
    public void symmetricKeyMustHaveRightInfo() {
        SymmetricKey k = new SymmetricKey();
        KeyInfo h = k.info();

        assertEquals(KeyInfo.Algorythm.AES256, h.getAlgorythm());
        assertEquals(32, h.getKeyLength());
        assertEquals(KeyInfo.PRF.None, h.getPRF());
    }

    @Test
    public void publicKeyMustHaveInfo() throws Exception {
        AbstractKey k = TestKeys.privateKey(3).getPublicKey();
        KeyInfo h = k.info();
        assertEquals(KeyInfo.Algorythm.RSAPublic, h.getAlgorythm());
        assertEquals(KeyInfo.PRF.None, h.getPRF());
        assertEquals(5, h.getTag().length);
//        Bytes.dump(h.pack());
    }

    @Test
    public void privateKeyMustHaveInfo() throws Exception {
        AbstractKey prk = TestKeys.privateKey(3);
        AbstractKey puk = prk.getPublicKey();
        KeyInfo h = prk.info();
        assertEquals(KeyInfo.Algorythm.RSAPrivate, h.getAlgorythm());
        assertEquals(KeyInfo.PRF.None, h.getPRF());
        assertEquals(5, h.getTag().length);
        assertArrayEquals(puk.info().getTag(), h.getTag());
//        Bytes.dump(h.pack());
    }

    @Test
    public void unpackKey() throws Exception {
        AbstractKey k1 = TestKeys.privateKey(3).getPublicKey();
        AbstractKey kx = AbstractKey.fromBinder(k1.toBinder());
        assertEquals(k1, kx);

        k1 = SymmetricKey.fromPassword("helluva", 4096);
        kx = AbstractKey.fromBinder(k1.toBinder());
        assertEquals(k1, kx);

        k1 = TestKeys.privateKey(2);
        kx = AbstractKey.fromBinder(k1.toBinder());
        assertEquals(k1, kx);
    }

    @Test
    public void matchTypeAndTag() throws Exception {
        // 2 different private keys
        AbstractKey k1 = TestKeys.privateKey(0);
        AbstractKey k2 = TestKeys.privateKey(1);
        assertTrue(k1.info().matchType(k2.info()));
        assertFalse(k1.info().matchTag(k2.info()));
        // public matches private, not vice versa
        AbstractKey k3 = k1.getPublicKey();
        assertTrue(k1.info().matchType(k3.info()));
        assertFalse(k3.info().matchType(k1.info()));
        assertTrue(k1.info().matchTag(k3.info()));

        // public keys do not match each other!
        assertFalse(k3.info().matchType(k3.info()));
        assertFalse(k3.info().matchType(k2.getPublicKey().info()));

        // Check AES match algorythm and tag
        AbstractKey k4 = new SymmetricKey();
        assertFalse(k2.matchType(k4));
        assertFalse(k3.matchType(k4));
        assertFalse(k4.matchType(k2));
        assertFalse(k4.matchType(k3));
        assertFalse(k4.matchTag(k2));
        assertFalse(k4.matchTag(k3));

        AbstractKey k5 = new SymmetricKey();
        assertTrue(k4.matchType(k5));
        assertTrue(k5.matchType(k4));
        assertFalse(k4.matchTag(k5));
        k4.setTag("Hello");
        k5.setTag("Hello");

    }

}