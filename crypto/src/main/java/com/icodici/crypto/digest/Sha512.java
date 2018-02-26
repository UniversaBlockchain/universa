/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.digest;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA512Digest;

/**
 * SHA-512 (SHA-2 family) digest implementation.
 */
public class Sha512 extends SpongyCastleDigest {

    final org.spongycastle.crypto.Digest md = new SHA512Digest();

    public Sha512() {
    }

    @Override
    protected int getChunkSize() {
        return 128;
    }

    @Override
    protected Digest getUnderlyingDigest() {
        return md;
    }
}
