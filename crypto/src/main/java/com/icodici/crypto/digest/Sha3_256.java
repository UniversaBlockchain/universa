/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.digest;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA3Digest;

/**
 * SHA3-256 (SHA-3 family) digest implementation.
 */
public class Sha3_256 extends SpongyCastleDigest {

    final Digest md = new SHA3Digest(256);

    public Sha3_256() {
    }

    @Override
    protected int getChunkSize() {
        return 136;
    }

    @Override
    protected Digest getUnderlyingDigest() {
        return md;
    }
}
