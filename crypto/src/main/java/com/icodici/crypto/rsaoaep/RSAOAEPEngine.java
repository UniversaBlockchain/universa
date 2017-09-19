/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.rsaoaep.scrsa.NativeRSAEngine;
import org.spongycastle.crypto.engines.RSAEngine;

/**
 * One-stop shop to create an new RSA engine (which implementation may vary).
 */
public class RSAOAEPEngine {
    public static RSAEngine make() {
        return new NativeRSAEngine();
    }
}
