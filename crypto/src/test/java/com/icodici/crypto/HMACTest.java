/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.crypto;

import net.sergeych.utils.Base64;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class HMACTest {
    @Test
    public void digest() throws Exception {
        String ok = "la3Xl78Z3ktK2JLoDpPKthhqVilUX6+e6a0WultI9f8=";
        byte[] key = "1234567890abcdef1234567890abcdef".getBytes();
        byte[] data = "a quick brown for his done something disgusting".getBytes();
        HMAC hmac = new HMAC(key, Sha256.class);
        byte[] digest = hmac.digest(data);
        assertArrayEquals(Base64.decodeLines(ok), digest);
//        System.out.println(Base64.encodeString(digest));
    }

}