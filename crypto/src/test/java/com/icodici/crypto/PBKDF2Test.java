/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Do;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

import static org.junit.Assert.assertArrayEquals;

/**
 * Created by sergeych on 19.12.16.
 */
public class PBKDF2Test {

    @Test
    public void computeSha512() {
        String src = "test $pbkdf$pbkdf2-sha512$5000$26$KFuMDXmo$yPsu5qmQto99vDqAMWnldNuagfVl5OhPr6g=";
        String[] parts = src.split("\\$");
        String password = parts[0].trim();
        int c = Integer.valueOf(parts[3]);
        int dkLen = Integer.valueOf(parts[4]);
        byte[] salt = Do.decodeBase64(parts[5]); //.getBytes();
        byte[] DK = Do.decodeBase64(parts[6]);

        assertArrayEquals(DK, PBKDF2.derive(Sha512.class, password, salt, c, dkLen));
    }

    @Test
    public void computeSha1() throws Exception {
        String P = "password";
        byte[] S = "salt".getBytes();
        int c = 1;
        int dkLen = 20;
        byte[] DK = Hex.decode("0c 60 c8 0f 96 1f 0e 71\n" +
                                       "f3 a9 b5 24 af 60 12 06\n" +
                                       "2f e0 37 a6");

        final byte[] key = PBKDF2.derive(Sha1.class, P, S, c, dkLen);
        assertArrayEquals(DK, key);
    }

    @Test
    public void computeSha256() throws Exception {
        String P = "password";
        byte[] S = "salt".getBytes();
        int c = 1;
        int dkLen = 32;

        byte [] DK = Hex.decode("12 0f b6 cf fc f8 b3 2c\n" +
                                        "43 e7 22 52 56 c4 f8 37\n" +
                                        "a8 65 48 c9 2c cc 35 48\n" +
                                        "08 05 98 7c b7 0b e1 7b");
        byte[] key = PBKDF2.derive(Sha256.class, P, S, c, dkLen);
        assertArrayEquals(DK, key);

        c = 2;
        DK = Hex.decode("ae 4d 0c 95 af 6b 46 d3\n" +
                                "       2d 0a df f9 28 f0 6d d0\n" +
                                "       2a 30 3f 8e f3 c2 51 df\n" +
                                "       d6 e2 d8 5a 95 47 4c 43");

        c = 4096;
        DK = Hex.decode("c5 e4 78 d5 92 88 c8 41\n" +
                                "       aa 53 0d b6 84 5c 4c 8d\n" +
                                "       96 28 93 a0 01 ce 4e 11\n" +
                                "       a4 96 38 73 aa 98 13 4a");
        key = PBKDF2.derive(Sha256.class, P, S, c, dkLen);
        assertArrayEquals(DK, key);
    }

//    @Test
//    public void timing() {
//        for( int i=0; i<5; i++) {
//            long t1 = System.currentTimeMillis();
//            PBKDF2.derive(Sha256.class, "testing", "test".getBytes(), 100000, 32 );
//            System.out.println(" -- " + (System.currentTimeMillis() - t1)+"ms");
//        }
//    }
}