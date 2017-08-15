/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.test;

import com.icodici.crypto.AbstractPrivateKey;
import com.icodici.crypto.AbstractPublicKey;
import com.icodici.crypto.HashType;
import com.icodici.crypto.rsaoaep.RSAOAEPPrivateKey;
import com.icodici.crypto.rsaoaep.RSAOAEPPublicKey;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * The container of test vectors from RSA OAEP specification.
 * <p>
 * Created by amyodov on 19.04.16.
 */
public class RSAOAEPTestVectors {
    /* RSA key information. */

    /** Modulus */
    public static final byte[] n = Hex.decode("bb f8 2f 09 06 82 ce 9c 23 38 ac 2b 9d a8 71 f7 36 8d 07 ee d4 10 43 a4 40 d6 b6 f0 74 54 f5 1f b8 df ba af 03 5c 02 ab 61 ea 48 ce eb 6f cd 48 76 ed 52 0d 60 e1 ec 46 19 71 9d 8a 5b 8b 80 7f af b8 e0 a3 df c7 37 72 3e e6 b4 b7 d9 3a 25 84 ee 6a 64 9d 06 09 53 74 88 34 b2 45 45 98 39 4e e0 aa b1 2d 7b 61 a5 1f 52 7a 9a 41 f6 c1 68 7f e2 53 72 98 ca 2a 8f 59 46 f8 e5 fd 09 1d bd cb");
    public static final BigInteger nInt = BigIntegers.fromUnsignedByteArray(n);
    /** Public exponent */
    public static final byte[] e = Hex.decode("11");
    public static final BigInteger eInt = BigIntegers.fromUnsignedByteArray(e);
    /* Prime factors of n */
    public static final byte[] p = Hex.decode("ee cf ae 81 b1 b9 b3 c9 08 81 0b 10 a1 b5 60 01 99 eb 9f 44 ae f4 fd a4 93 b8 1a 9e 3d 84 f6 32 12 4e f0 23 6e 5d 1e 3b 7e 28 fa e7 aa 04 0a 2d 5b 25 21 76 45 9d 1f 39 75 41 ba 2a 58 fb 65 99");
    public static final byte[] q = Hex.decode("c9 7f b1 f0 27 f4 53 f6 34 12 33 ea aa d1 d9 35 3f 6c 42 d0 88 66 b1 d0 5a 0f 20 35 02 8b 9d 86 98 40 b4 16 66 b4 2e 92 ea 0d a3 b4 32 04 b5 cf ce 33 52 52 4d 04 16 a5 a4 41 e7 00 af 46 15 03");
    public static final BigInteger pInt = BigIntegers.fromUnsignedByteArray(p);
    public static final BigInteger qInt = BigIntegers.fromUnsignedByteArray(q);
    /* Exponents of prime factors */
    public static final byte[] dP = Hex.decode("54 49 4c a6 3e ba 03 37 e4 e2 40 23 fc d6 9a 5a eb 07 dd dc 01 83 a4 d0 ac 9b 54 b0 51 f2 b1 3e d9 49 09 75 ea b7 74 14 ff 59 c1 f7 69 2e 9a 2e 20 2b 38 fc 91 0a 47 41 74 ad c9 3c 1f 67 c9 81");
    public static final byte[] dQ = Hex.decode("47 1e 02 90 ff 0a f0 75 03 51 b7 f8 78 86 4c a9 61 ad bd 3a 8a 7e 99 1c 5c 05 56 a9 4c 31 46 a7 f9 80 3f 8f 6f 8a e3 42 e9 31 fd 8a e4 7a 22 0d 1b 99 a4 95 84 98 07 fe 39 f9 24 5a 98 36 da 3d");
    public static final BigInteger dPInt = BigIntegers.fromUnsignedByteArray(dP);
    public static final BigInteger dQInt = BigIntegers.fromUnsignedByteArray(dQ);
    /** The CRT coefficient. */
    public static final byte[] qInv = Hex.decode("b0 6c 4f da bb 63 01 19 8d 26 5b db ae 94 23 b3 80 f2 71 f7 34 53 88 50 93 07 7f cd 39 e2 11 9f c9 86 32 15 4f 58 83 b1 67 a9 67 bf 40 2b 4e 9e 2e 0f 96 56 e6 98 ea 36 66 ed fb 25 79 80 39 f7");
    public static final BigInteger qInvInt = BigIntegers.fromUnsignedByteArray(qInv);
    /* Private exponent */
    /* m = (p - 1) * (q - 1);  d ≡ e⁻¹ (mod m) */
    public static final BigInteger mInt = pInt.subtract(BigInteger.ONE).multiply(qInt.subtract(BigInteger.ONE));
    public static final byte[] m = BigIntegers.asUnsignedByteArray(mInt);
    public static final BigInteger dInt = eInt.modInverse(mInt);
    public static final byte[] d = BigIntegers.asUnsignedByteArray(dInt);

    /* Reference keys */
    public static final RSAKeyParameters
            pubParameters = new RSAKeyParameters(false, nInt, eInt),
            privParameters = new RSAPrivateCrtKeyParameters(
                    nInt, eInt, dInt, pInt, qInt,
                    BigIntegers.fromUnsignedByteArray(dP), BigIntegers.fromUnsignedByteArray(dQ),
                    BigIntegers.fromUnsignedByteArray(qInv));

    /* Encryption. */

    /** The message to be encrypted. */
    public static final byte[] M = Hex.decode("d4 36 e9 95 69 fd 32 a7 c8 a0 5b bc 90 d3 2c 49");
    /** C, the RSA encryption of EM. */
    public static final byte[] C = Hex.decode("12 53 e0 4d c0 a5 39 7b b4 4a 7a b8 7e 9b f2 a0 39 a3 3d 1e 99 6f c8 2a 94 cc d3 00 74 c9 5d f7 63 72 20 17 06 9e 52 68 da 5d 1c 0b 4f 87 2c f6 53 c1 1d f8 23 14 a6 79 68 df ea e2 8d ef 04 bb 6d 84 b1 c3 1d 65 4a 19 70 e5 78 3b d6 eb 96 a0 24 c2 ca 2f 4a 90 fe 9f 2e f5 c9 c1 40 e5 bb 48 da 95 36 ad 87 00 c8 4f c9 13 0a de a7 4e 55 8d 51 a7 4d df 85 d8 b5 0d e9 68 38 d6 06 3e 09 55");
    /** The fake random data, used as a seed. */
    public static final byte[] seed = Hex.decode("aa fd 12 f6 59 ca e6 34 89 b4 79 e5 07 6d de c2 f0 6c b5 8f");

    /* Intermediate constants, tested during testing the MGF */
    public static final byte[] DB = Hex.decode("da 39 a3 ee 5e 6b 4b 0d 32 55 bf ef 95 60 18 90 af d8 07 09 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 d4 36 e9 95 69 fd 32 a7 c8 a0 5b bc 90 d3 2c 49");
    /* dbMask = MGF (seed, 107); should be tested */
    public static final byte[] dbMask = Hex.decode("06 e1 de b2 36 9a a5 a5 c7 07 d8 2c 8e 4e 93 24 8a c7 83 de e0 b2 c0 46 26 f5 af f9 3e dc fb 25 c9 c2 b3 ff 8a e1 0e 83 9a 2d db 4c dc fe 4f f4 77 28 b4 a1 b7 c1 36 2b aa d2 9a b4 8d 28 69 d5 02 41 21 43 58 11 59 1b e3 92 f9 82 fb 3e 87 d0 95 ae b4 04 48 db 97 2f 3a c1 4e af f4 9c 8c 3b 7c fc 95 1a 51 ec d1 dd e6 12 64");
    /* maskedDB = Db ⊕ dbMask; should be tested */
    public static final byte[] maskedDB = Hex.decode("dc d8 7d 5c 68 f1 ee a8 f5 52 67 c3 1b 2e 8b b4 25 1f 84 d7 e0 b2 c0 46 26 f5 af f9 3e dc fb 25 c9 c2 b3 ff 8a e1 0e 83 9a 2d db 4c dc fe 4f f4 77 28 b4 a1 b7 c1 36 2b aa d2 9a b4 8d 28 69 d5 02 41 21 43 58 11 59 1b e3 92 f9 82 fb 3e 87 d0 95 ae b4 04 48 db 97 2f 3a c1 4f 7b c2 75 19 52 81 ce 32 d2 f1 b7 6d 4d 35 3e 2d");
    /* seedMask = MGF (maskedDB, 20); should be tested */
    public static final byte[] seedMask = Hex.decode("41 87 0b 5a b0 29 e6 57 d9 57 50 b5 4c 28 3c 08 72 5d be a9");
    /* maskedSeed = seed ⊕ seedMask; should be tested */
    public static final byte[] maskedSeed = Hex.decode("eb 7a 19 ac e9 e3 00 63 50 e3 29 50 4b 45 e2 ca 82 31 0b 26");
    /* EM = maskedSeed∥maskedDB; should be tested */
    public static final byte[] EM = Hex.decode("eb 7a 19 ac e9 e3 00 63 50 e3 29 50 4b 45 e2 ca 82 31 0b 26 dc d8 7d 5c 68 f1 ee a8 f5 52 67 c3 1b 2e 8b b4 25 1f 84 d7 e0 b2 c0 46 26 f5 af f9 3e dc fb 25 c9 c2 b3 ff 8a e1 0e 83 9a 2d db 4c dc fe 4f f4 77 28 b4 a1 b7 c1 36 2b aa d2 9a b4 8d 28 69 d5 02 41 21 43 58 11 59 1b e3 92 f9 82 fb 3e 87 d0 95 ae b4 04 48 db 97 2f 3a c1 4f 7b c2 75 19 52 81 ce 32 d2 f1 b7 6d 4d 35 3e 2d");


    @NonNull
    public SecureRandom getRandSeed() {
        return new VecRand(seed);
    }

    public @NonNull AbstractPublicKey getPublicKey() {
        return new RSAOAEPPublicKey(n, e, HashType.SHA1, HashType.SHA1, getRandSeed());
    }

    public @NonNull AbstractPrivateKey getPrivateKey() {
        return new RSAOAEPPrivateKey(e, p, q, HashType.SHA1, HashType.SHA1, getRandSeed());
    }
}
