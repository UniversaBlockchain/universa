/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.digest;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA1Digest;

/**
 * SHA-1 digest implementation.
 */
public class Sha1 extends SpongyCastleDigest {

    final org.spongycastle.crypto.Digest md = new SHA1Digest();

    public Sha1() {
    }

    @Override
    protected Digest getUnderlyingDigest() {
        return md;
    }
}
