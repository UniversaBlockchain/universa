/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.digest;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

/**
 * SHA-256 (SHA-2 family) digest implementation.
 */
public class Sha256 extends SpongyCastleDigest {

    final org.bouncycastle.crypto.Digest md = new SHA256Digest();

    public Sha256() {
    }

    @Override
    protected Digest getUnderlyingDigest() {
        return md;
    }
}
