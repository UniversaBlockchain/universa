/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.test;

import java.security.SecureRandom;

/**
 * Created by amyodov on 21.04.16.
 */
public class VecRand extends SecureRandom {
    byte[] seed;

    public VecRand(byte[] seed) {
        this.seed = seed;
    }

    public void nextBytes(byte[] bytes) {
        System.arraycopy(seed, 0, bytes, 0, bytes.length);
    }
}
