/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.digest;

import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.digest.Sha256;

import java.util.Arrays;

import static com.icodici.crypto.SymmetricKey.xor;

/**
 * keyed-hash message authentication code (HMAC) implementation, according to RFC2104.
 * <p>
 * Please do not use it with MD5 and SHA1. Good ones are SHA256+. With SHA3 it also could be used
 * though SHA3 does not require this type of algorythm as is considered to be prone to the length
 * extension attack.
 * <p>
 * @see <a href='https://tools.ietf.org/html/rfc2104'>RFC2104: HMAC</a>
 * <p>
 * Created by sergeych on 19.12.16.
 */
public class HMAC extends Digest {

    private final Digest hash;
    private final int blockSize;
    private final byte[] oKeyPad;
    private final Class<? extends Digest> hashClass;

    /**
     * Default implementation uses SHA256 and it's blick size.
     *
     * @param key
     *         secret key
     */
    public HMAC(byte[] key) {
        this(key, Sha256.class);
    }

    /**
     * Create new HMAC hash.
     *
     * @param key
     *         secret key
     * @param hashClass
     *         class of the hash to use to sign the message. SHA256+ is recommended.
     */
    public HMAC(byte[] key, Class<? extends Digest> hashClass) {
        this.hashClass = hashClass;
        this.hash = hashInstance();
        this.blockSize = hash.getChunkSize();

        byte[] keyBlock;
        if (key.length > blockSize) {
            byte[] kd = new Sha256().digest(key);
            keyBlock = Arrays.copyOf(kd, blockSize);
        } else
            keyBlock = Arrays.copyOf(key, blockSize);

        oKeyPad = xor(keyBlock, 0x5c);
        byte[] iKeyPad = xor(keyBlock, 0x36);
        hash.update(iKeyPad);
    }

    @Override
    protected void _update(byte[] data, int offset, int size) {
        hash._update(data, offset, size);
    }

    @Override
    protected byte[] _digest() {
        Digest d = hashInstance();
        d.update(oKeyPad);
        d.update(hash.digest());
        return d.digest();
    }

    protected final Digest hashInstance() {
        try {
            return hashClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("can't create hash instance");
        }
    }

    @Override
    public int getLength() {
        return hash.getLength();
    }
}
