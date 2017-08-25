/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA512Digest;
import org.spongycastle.util.encoders.Hex;

import static org.junit.Assert.*;


/**
 * Created by amyodov on 18.04.16.
 */
public class RSAOAEPDigestFactoryTest {

    /**
     * Reset the `digest` and run it over the data.
    */
    private static byte[] resetAndDigest(Digest digest, byte[] data) {
        byte[] result = new byte[digest.getDigestSize()];
        digest.update(data, 0, data.length);
        digest.doFinal(result, 0);
        return result;
    }

    /**
     * Test {@link RSAOAEPDigestFactory#getDigestByName}.
     * */
    @org.junit.Test
    public void getDigestByName() throws Exception {
        Digest dNoDigest1 = RSAOAEPDigestFactory.getDigestByName("");
        assertNull(dNoDigest1);
        Digest dNoDigest2 = RSAOAEPDigestFactory.getDigestByName("Missing hash");
        assertNull(dNoDigest2);

        Digest dSHA1 = RSAOAEPDigestFactory.getDigestByName("SHA-1");
        assertArrayEquals(
                resetAndDigest(dSHA1, "".getBytes()),
                Hex.decode("da39a3ee5e6b4b0d3255bfef95601890afd80709"));
        assertArrayEquals(
                resetAndDigest(dSHA1, "The quick brown fox jumps over the lazy dog".getBytes()),
                Hex.decode("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12"));

        Digest dSHA224 = RSAOAEPDigestFactory.getDigestByName("SHA-224");
        assertArrayEquals(
                resetAndDigest(dSHA224, "".getBytes()),
                Hex.decode("d14a028c2a3a2bc9476102bb288234c415a2b01f828ea62ac5b3e42f"));
        assertArrayEquals(
                resetAndDigest(dSHA224, "The quick brown fox jumps over the lazy dog".getBytes()),
                Hex.decode("730e109bd7a8a32b1cb9d9a09aa2325d2430587ddbc0c38bad911525"));

        Digest dSHA256 = RSAOAEPDigestFactory.getDigestByName("SHA-256");
        assertArrayEquals(
                resetAndDigest(dSHA256, "".getBytes()),
                Hex.decode("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"));
        assertArrayEquals(
                resetAndDigest(dSHA256, "The quick brown fox jumps over the lazy dog".getBytes()),
                Hex.decode("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592"));

        Digest dSHA384 = RSAOAEPDigestFactory.getDigestByName("SHA-384");
        assertArrayEquals(
                resetAndDigest(dSHA384, "".getBytes()),
                Hex.decode("38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b"));
        assertArrayEquals(
                resetAndDigest(dSHA384, "The quick brown fox jumps over the lazy dog".getBytes()),
                Hex.decode("ca737f1014a48f4c0b6dd43cb177b0afd9e5169367544c494011e3317dbf9a509cb1e5dc1e85a941bbee3d7f2afbc9b1"));

        Digest dSHA512 = RSAOAEPDigestFactory.getDigestByName("SHA-512");
        assertArrayEquals(
                resetAndDigest(dSHA512, "".getBytes()),
                Hex.decode("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"));
        assertArrayEquals(
                resetAndDigest(dSHA512, "The quick brown fox jumps over the lazy dog".getBytes()),
                Hex.decode("07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6"));
    }

    /**
     * Test {@link RSAOAEPDigestFactory#cloneDigest}.
     * */
    @org.junit.Test
    public void cloneDigest() throws Exception {
        Digest d1Orig = new SHA512Digest();
        Digest d1Clone = RSAOAEPDigestFactory.cloneDigest(d1Orig);
        assertTrue(d1Clone instanceof SHA512Digest);
        assertNotEquals(d1Orig, d1Clone);
    }
}
