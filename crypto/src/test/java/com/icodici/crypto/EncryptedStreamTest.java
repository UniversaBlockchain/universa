/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.*;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Created by sergeych on 15.12.16.
 */
public class EncryptedStreamTest {
    @Test
    public void bytesRange() throws Exception {
        SymmetricKey k = new SymmetricKey();
        byte[] src = new byte[4];
        src[0] = (byte) 255;
        src[1] = (byte) 254;
        src[2] = (byte) 253;
        src[3] = (byte) 252;
        assertThat(k.decrypt(k.encrypt(src)), equalTo(src));
    }

    @Test
    public void bigVolume() throws Exception {
        SymmetricKey k = new SymmetricKey();
        byte[] src = CTRTransformer.randomBytes(0x23456);
        assertThat(k.decrypt(k.encrypt(src)), equalTo(src));
    }

    @Test
    public void decrypt() throws Exception {
        byte[] src = "FU beyond all r!".getBytes();
        assertEquals(16, src.length);

        byte [] key = CTRTransformer.randomBytes(32);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EncryptingStream es = new EncryptingStream(AES256.class, key, bos);

        es.write(src);
        es.write(src);

        byte[] encrypted = bos.toByteArray();
        assertEquals(48, encrypted.length);

        byte []p1 = Arrays.copyOfRange(encrypted, 0x10, 0x16);
        byte []p2 = Arrays.copyOfRange(encrypted, 0x20, 0x26);
        byte []cp = Arrays.copyOfRange(src, 0, 6);

        assertThat(p1, not(equalTo(p2)));
        assertThat(cp, not(equalTo(p2)));
        assertThat(cp, not(equalTo(p1)));

        byte[] decrypted = DecryptingStream.decrypt(AES256.class, key, encrypted);
        p1 = Arrays.copyOfRange(decrypted, 0, 16);
        p2 = Arrays.copyOfRange(decrypted, 16, 32);
        assertThat(src, equalTo(p1));
        assertThat(src, equalTo(p2));

        src = "Only5".getBytes();
        encrypted = EncryptingStream.encrypt(AES256.class, key, src);
        assertThat(encrypted.length, equalTo(21));
        assertThat(DecryptingStream.decrypt(AES256.class, key, encrypted), equalTo(src));
    }

}