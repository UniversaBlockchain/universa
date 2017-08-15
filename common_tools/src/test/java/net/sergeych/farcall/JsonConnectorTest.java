/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.farcall;

import net.sergeych.farcall.JsonConnector;
import net.sergeych.tools.Do;
import net.sergeych.tools.StreamConnector;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by sergeych on 12.04.16.
 */
public class JsonConnectorTest {


    @Test(timeout = 100)
    public void send() throws Exception {

        StreamConnector sa = new StreamConnector();
        JsonConnector jsc = new JsonConnector(sa.getInputStream(), sa.getOutputStream());

        jsc.send(Do.map("hello", "мыльня"));
        Map<String, Object> res = jsc.receive();
        assertEquals(1, res.size());
        assertEquals("мыльня", res.get("hello"));
    }

}