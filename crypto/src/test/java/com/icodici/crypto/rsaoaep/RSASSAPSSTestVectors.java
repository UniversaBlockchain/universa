/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.AbstractPrivateKey;
import com.icodici.crypto.AbstractPublicKey;
import com.icodici.crypto.HashType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongycastle.util.encoders.Hex;

import java.security.SecureRandom;

/**
 * The container of test vectors from RSASSA-PSS specification
 * (PKCS #1 v2.1).
 * <p>
 * Created by amyodov on 19.04.16.
 */
public class RSASSAPSSTestVectors {
    /* RSA key information. */

    /** Modulus */
    public static final byte[] n = Hex.decode("a2 ba 40 ee 07 e3 b2 bd 2f 02 ce 22 7f 36 a1 95 02 44 86 e4 9c 19 cb 41 bb bd fb ba 98 b2 2b 0e 57 7c 2e ea ff a2 0d 88 3a 76 e6 5e 39 4c 69 d4 b3 c0 5a 1e 8f ad da 27 ed b2 a4 2b c0 00 fe 88 8b 9b 32 c2 2d 15 ad d0 cd 76 b3 e7 93 6e 19 95 5b 22 0d d1 7d 4e a9 04 b1 ec 10 2b 2e 4d e7 75 12 22 aa 99 15 10 24 c7 cb 41 cc 5e a2 1d 00 ee b4 1f 7c 80 08 34 d2 c6 e0 6b ce 3b ce 7e a9 a5");
    /** Public exponent */
    public static final byte[] e = Hex.decode("01 00 01");
    /* Prime factors of n */
    public static final byte[] p = Hex.decode("d1 7f 65 5b f2 7c 8b 16 d3 54 62 c9 05 cc 04 a2 6f 37 e2 a6 7f a9 c0 ce 0d ce d4 72 39 4a 0d f7 43 fe 7f 92 9e 37 8e fd b3 68 ed df f4 53 cf 00 7a f6 d9 48 e0 ad e7 57 37 1f 8a 71 1e 27 8f 6b");
    public static final byte[] q = Hex.decode("c6 d9 2b 6f ee 74 14 d1 35 8c e1 54 6f b6 29 87 53 0b 90 bd 15 e0 f1 49 63 a5 e2 63 5a db 69 34 7e c0 c0 1b 2a b1 76 3f d8 ac 1a 59 2f b2 27 57 46 3a 98 24 25 bb 97 a3 a4 37 c5 bf 86 d0 3f 2f");

    /** Pseudo-random salt. */
    public static final byte[] salt = Hex.decode("e3 b5 d5 d0 02 c1 bc e5 0c 2b 65 ef 88 a1 88 d8 3b ce 7e 61");
    /** The message to be signed. */
    public static final byte[] M = Hex.decode("85 9e ef 2f d7 8a ca 00 30 8b dc 47 11 93 bf 55 bf 9d 78 db 8f 8a 67 2b 48 46 34 f3 c9 c2 6e 64 78 ae 10 26 0f e0 dd 8c 08 2e 53 a5 29 3a f2 17 3c d5 0c 6d 5d 35 4f eb f7 8b 26 02 1c 25 c0 27 12 e7 8c d4 69 4c 9f 46 97 77 e4 51 e7 f8 e9 e0 4c d3 73 9c 6b bf ed ae 48 7f b5 56 44 e9 ca 74 ff 77 a5 3c b7 29 80 2f 6e d4 a5 ff a8 ba 15 98 90 fc");
    /** Message signature. */
    public static final byte[] S = Hex.decode("8d aa 62 7d 3d e7 59 5d 63 05 6c 7e c6 59 e5 44 06 f1 06 10 12 8b aa e8 21 c8 b2 a0 f3 93 6d 54 dc 3b dc e4 66 89 f6 b7 95 1b b1 8e 84 05 42 76 97 18 d5 71 5d 21 0d 85 ef bb 59 61 92 03 2c 42 be 4c 29 97 2c 85 62 75 eb 6d 5a 45 f0 5f 51 87 6f c6 74 3d ed dd 28 ca ec 9b b3 0e a9 9e 02 c3 48 82 69 60 4f e4 97 f7 4c cd 7c 7f ca 16 71 89 71 23 cb d3 0d ef 5d 54 a2 b5 53 6a d9 0a 74 7e");


    public @NonNull SecureRandom getRandSalt() {
        return new VecRand(salt);
    }

    public @NonNull AbstractPublicKey getPublicKey() {
        return new RSAOAEPPublicKey(n, e, HashType.SHA1, HashType.SHA1, getRandSalt());
    }

    public @NonNull AbstractPrivateKey getPrivateKey() {
        return new RSAOAEPPrivateKey(e, p, q, HashType.SHA1, HashType.SHA1, getRandSalt());
    }
}
