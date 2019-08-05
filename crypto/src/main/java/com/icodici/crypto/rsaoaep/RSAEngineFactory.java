/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.rsaoaep.scrsa.NativeRSAEngine;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.ParametersWithRandom;

/**
 * One-stop shop to create an new RSA engine (which implementation may vary).
 */
public class RSAEngineFactory {

    protected final static boolean shouldUseNative = whetherShouldUseNative();

    /**
     * Create the {@link RSAEngine} implementation using the best possible optimization.
     */
    public static RSAEngine make() {
        return (shouldUseNative) ? new NativeRSAEngine() : new RSAEngine();
    }

    /**
     * Perform a test to check whether we should use an optimized native implementation or default Java one.
     */
    static boolean whetherShouldUseNative() {
        // Sometimes NativeRSAEngine does not work; e.g. when GMP native binary is not available.
        // Test once if it will work; if it doesn't, fallback to use default RSAEngine.
        final RSAOAEPTestVectors oaepSpec = new RSAOAEPTestVectors();
        final ParametersWithRandom param = new ParametersWithRandom(oaepSpec.pubParameters, oaepSpec.getRandSeed());

        boolean errorOccured;
        try {
            final NativeRSAEngine nativeRSAEngine = new NativeRSAEngine();
            nativeRSAEngine.init(true, param);
            errorOccured = false;
        } catch (Throwable e) {
            errorOccured = true;
        }
        return !errorOccured;
    }
}
