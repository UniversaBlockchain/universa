/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HttpEndpointTest {

    private HttpEndpoint ep;
    private String rootUrl;
    private HttpClient client;

    @Test
    public void ping() throws Exception {
        rootUrl = "http://localhost:17172";
        ep = new HttpEndpoint(17172);
        client = new HttpClient("http://localhost:17172");
        HttpClient.Answer a = client.request("ping", "hello", "world");
        assertEquals(a.code, 200);
        System.out.println(a.data);
        assertEquals("pong", a.data.getStringOrThrow("ping"));
        assertEquals("world", a.data.getStringOrThrow("hello"));
    }
}