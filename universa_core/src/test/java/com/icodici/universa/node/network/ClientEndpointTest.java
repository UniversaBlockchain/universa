/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Errors;
import com.icodici.universa.node.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
        PublicKey nodeKey = TestKeys.publicKey(0);
        PrivateKey clientKey = TestKeys.privateKey(1);
        client.start(clientKey, nodeKey);
        assert(client.ping());
        ep.changeKeyFor(clientKey.getPublicKey());
        assert(client.ping());
        try {
            client.command("test_error");
            fail("expected exception wasn't trown");
        }
        catch(HttpClient.CommandFailedException e) {
            assertEquals(Errors.COMMAND_FAILED, e.getError().getError());
            assertEquals("test_error", e.getError().getObjectName());
            assertEquals("sample error", e.getError().getMessage());
        }

    }
}