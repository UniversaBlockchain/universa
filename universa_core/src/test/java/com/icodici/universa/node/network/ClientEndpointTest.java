/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.universa.node.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientEndpointTest extends TestCase {

    private ClientEndpoint ep;
    private String rootUrl;
    private HttpClient client;

    @Test
    public void ping() throws Exception {
        rootUrl = "http://localhost:17172";
        ep = new ClientEndpoint(null, 17172, null, null);
        client = new HttpClient("testnode1", "http://localhost:17172");
        HttpClient.Answer a = client.request("ping", "hello", "world");
        assertEquals(a.code, 200);
        System.out.println(a.data);
        assertEquals("pong", a.data.getStringOrThrow("ping"));
        assertEquals("world", a.data.getStringOrThrow("hello"));
    }

    @Test
    public void handshake() throws Exception {
        rootUrl = "http://localhost:17172";
        ep = new ClientEndpoint(TestKeys.privateKey(0), 17172, null, null);
        client = new HttpClient("testnode1", "http://localhost:17172");
        client.start(TestKeys.privateKey(1), TestKeys.publicKey(0));
        assert(client.ping());
    }
}