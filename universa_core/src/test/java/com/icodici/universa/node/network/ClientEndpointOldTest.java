/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.node.TestCase;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.management.ManagementFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Ignore("outdated vy v2")
public class ClientEndpointOldTest extends TestCase {

    private static final int DEFAULT_PORT = 17172;
    private static final String ROOT_URL = "http://localhost:" + DEFAULT_PORT;

    private ClientEndpointOld ep;
    private UniversaHTTPClient client;

//    @Test
    public void runServer() throws Exception {
        ep = new ClientEndpointOld(null, DEFAULT_PORT, null, null);
        System.out.printf("Running server at port %s, %s\n", DEFAULT_PORT, ManagementFactory.getRuntimeMXBean().getName());
        while (true);
//        ep.shutdown();
    }

    @Test
    public void ping() throws Exception {
        ep = new ClientEndpointOld(null, DEFAULT_PORT, null, null);
        client = new UniversaHTTPClient("testnode1", "http://localhost:" + DEFAULT_PORT);
        UniversaHTTPClient.Answer a = client.request("ping", "hello", "world");
        assertEquals(a.code, 200);
        System.out.println(a.data);
        assertEquals("pong", a.data.getStringOrThrow("ping"));
        assertEquals("world", a.data.getStringOrThrow("hello"));
        ep.shutdown();
    }

    @Test
    public void handshake() throws Exception {
        ep = new ClientEndpointOld(TestKeys.privateKey(0), DEFAULT_PORT, null, null);
        client = new UniversaHTTPClient("testnode1", ROOT_URL);
        PublicKey nodeKey = TestKeys.publicKey(0);
        PrivateKey clientKey = TestKeys.privateKey(1);
        client.start(clientKey, nodeKey);
        assert(client.ping());
        ep.changeKeyFor(clientKey.getPublicKey());
        assert(client.ping());
        try {
            client.command("test_error");
            fail("expected exception wasn't thrown");
        }
        catch(UniversaHTTPClient.CommandFailedException e) {
            assertEquals(Errors.COMMAND_FAILED, e.getError().getError());
            assertEquals("test_error", e.getError().getObjectName());
            assertEquals("sample error", e.getError().getMessage());
        }
        ep.shutdown();
    }
}
