package com.icodici.crypto;

import com.icodici.crypto.digest.BouncyCastleDigest;

public class GenericBCDigest extends BouncyCastleDigest {

    final org.bouncycastle.crypto.Digest bd;

    GenericBCDigest(org.bouncycastle.crypto.Digest bcd) {
        this.bd = bcd;
    }

    @Override
    protected org.bouncycastle.crypto.Digest getUnderlyingDigest() {
        return bd;
    }
}
