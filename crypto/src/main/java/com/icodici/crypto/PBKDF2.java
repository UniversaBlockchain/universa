/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.BouncyCastleDigest;
import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.digest.HMAC;
import org.bouncycastle.crypto.digests.SHA256Digest;

import java.nio.charset.Charset;

import static java.util.Arrays.copyOf;

/**
 * Password-based Key Derivation Function, as defined in <a href='https://tools.ietf.org/html/rfc2898'>
 * RFC2898</a>, that uses HMAC as PRF with a given hash type.
 *
 * Created by sergeych on 19.12.16.
 */
public class PBKDF2 {

    static private Charset utf8 = Charset.forName("utf-8");
    private final Class<? extends Digest> hashClass;
    private final byte[] salt;
    private final int c;
    private final int dkLen;
    private final int hLen;
    private byte[] computed;
    private byte[] passwordBytes;

    private PBKDF2(Class<? extends Digest> hashClass,
                  String password,
                  byte[] salt,
                  int c,
                  int dkLen) {
        this.hashClass = hashClass;
        passwordBytes = password.getBytes(utf8);
        this.salt = salt;
        this.c = c;
        this.dkLen = dkLen;

        Digest d = hashInstance();
        hLen = d.getLength();
    }

    private byte[] compute() {
        if (computed == null) {

            int nBlocks = (dkLen + hLen - 1) / hLen;
            byte[] result = new byte[nBlocks * hLen];

            for (int i = 0; i < nBlocks; i++) {
                System.arraycopy(F(i + 1), 0, result, i * hLen, hLen);
            }
            computed = copyOf(result, dkLen);
        }
        return computed;
    }

    private byte[] F(int i) {
        Digest d = hashInstance();

        d.update(salt);
        d.update((i>>24) & 0xFF);
        d.update((i>>16) & 0xFF);
        d.update((i>>8) & 0xFF);
        d.update((i) & 0xFF);

        byte[]  block = d.digest();
        byte [] u1 = block;

        for( int k=1; k< c; k++) {
            d = hashInstance();
            d.update(u1);
            byte [] u2 = d.digest();
            for( int j=0; j<hLen; j++) {
                block[j] ^= u2[j];
            }
            u1 = u2;
        }
        return block;
    }

    private Digest hashInstance() {
        return new HMAC(passwordBytes, hashClass);
    }

    public static byte[] derive(Class<? extends Digest> hash, String password, byte[] salt, int c, int dkLen) {
        return new PBKDF2(hash, password, salt, c, dkLen).compute();
    }

    public static byte[] derive(HashType hashType, String password, byte[] salt, int c, int dkLen) {
        return new PBKDF2(hashType.findDigestClass(), password, salt, c, dkLen).compute();
    }
}