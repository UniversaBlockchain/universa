/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by sergeych on 07/01/16.
 */
public class Sha256 extends Digest {

    MessageDigest md;

    public Sha256() {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is not implemented");
        }
    }

    @Override
    protected void _update(byte[] data, int offset, int size) {
        md.update(data,offset,size);
    }

    @Override
    protected byte[] _digest() {
        return md.digest();
    }

    @Override
    protected int getLength() {
        return 32;
    }
}
