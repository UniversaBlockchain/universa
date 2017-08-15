/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import org.spongycastle.util.BigIntegers;

import java.math.BigInteger;

/**
 * The inner parameters of RSA key pair.
 * Can be used to create a full RSA key pair from partially available data.
 * Tuple of (n, e) form the public key;
 * Tuple of (e, p, q) form the private key.
 * <p>
 * Created by amyodov on 21.04.16.
 */
public class RSAKeyPair {
    /** Modulus. */
    public final byte[] n;
    /** Public exponent. */
    public final byte[] e;
    /** Private exponent. */
    public final byte[] d;
    /** First prime factor of n. */
    public final byte[] p;
    /** Second prime factor of n. */
    public final byte[] q;
    public final byte[] dP;
    public final byte[] dQ;
    public final byte[] qInv;

    /** Full constructor. */
    public RSAKeyPair(byte[] n, byte[] e, byte[] d, byte[] p, byte[] q, byte[] dP, byte[] dQ, byte[] qInv) {
        this.n = n;
        this.e = e;
        this.d = d;
        this.p = p;
        this.q = q;
        this.dP = dP;
        this.dQ = dQ;
        this.qInv = qInv;
    }

    /**
     * Generate from the exponents.
     */
    public static RSAKeyPair fromExponents(byte[] e, byte[] p, byte[] q) {
        BigInteger
                eInt = BigIntegers.fromUnsignedByteArray(e),
                pInt = BigIntegers.fromUnsignedByteArray(p),
                qInt = BigIntegers.fromUnsignedByteArray(q),
                nInt = pInt.multiply(qInt),
                mInt = pInt.subtract(BigInteger.ONE).multiply(qInt.subtract(BigInteger.ONE)),
                dInt = eInt.modInverse(mInt),
                dPInt = dInt.remainder(pInt.subtract(BigInteger.ONE)),
                dQInt = dInt.remainder(qInt.subtract(BigInteger.ONE)),
                qInvInt = qInt.modInverse(pInt);
        byte[]
                n = BigIntegers.asUnsignedByteArray(nInt),
                d = BigIntegers.asUnsignedByteArray(dInt),
                dP = BigIntegers.asUnsignedByteArray(dPInt),
                dQ = BigIntegers.asUnsignedByteArray(dQInt),
                qInv = BigIntegers.asUnsignedByteArray(qInvInt);

        return new RSAKeyPair(n, e, d, p, q, dP, dQ, qInv);
    }
}
