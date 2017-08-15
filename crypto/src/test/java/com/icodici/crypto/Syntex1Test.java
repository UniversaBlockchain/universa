/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.Syntex1;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

/**
 * Created by sergeych on 14/02/16.
 */
public class Syntex1Test {

    @Test
    public void testBasics() throws Exception {
        Syntex1 s = new Syntex1();
        s.update("Hello world");
        byte[] d1 = s.digest();
        assertEquals(36, s.getLength());
        byte[] d2 = new Syntex1().digest("Fello world");
        assertEquals(36, d2.length);
        byte[] d3 = new Syntex1().digest("Hello world");
        assertArrayEquals(d1, d3);
        assertThat( d3, equalTo(d1));
        assertThat( d2, not(equalTo(d1)));

        InputStream in = new ByteArrayInputStream("Hello world".getBytes());
        assertThat(d1, equalTo( new Syntex1().digest(in)));

    }

}