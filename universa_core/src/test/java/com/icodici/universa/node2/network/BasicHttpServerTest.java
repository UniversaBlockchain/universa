/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicHttpServerTest extends TestCase {

    BufferedLogger log = new BufferedLogger(2048);

//    @Test
//    public void addEndpoint() throws Exception {
//        BasicHttpServer s = new BasicHttpServer(null, 15600, 32, log);
//        BasicHTTPClient c = new BasicHTTPClient("http://localhost:15600");
//        BasicHTTPClient.Answer a = c.request("ping", Binder.of("hello", "world"));
//        System.out.println(":: "+a);
//        s.shutdown();
//    }

    @Test
    public void handshakeAndSecureCommand() throws Exception {
        PrivateKey nodeKey = TestKeys.privateKey(1);
        PrivateKey clientKey = TestKeys.privateKey(2);
        BasicHttpServer s = new BasicHttpServer(nodeKey, 15600, 32, log);
        BasicHTTPClient c = new BasicHTTPClient("http://localhost:15600");
        c.start(clientKey, nodeKey.getPublicKey());

        Binder res = c.command("sping");
        assertEquals("spong", res.getStringOrThrow("sping"));

        s.addSecureEndpoint("getSessionInfo", (params,session)-> {
//            System.out.println("in sec, "+session);
//            System.out.println("\t "+session.getPublicKey());
            return Binder.of("publicKey", session.getPublicKey().info().toString());
        });
        res = c.command("getSessionInfo");
        s.shutdown();
    }

    @Test
    public void testError() throws Exception {
        PrivateKey nodeKey = TestKeys.privateKey(1);
        PrivateKey clientKey = TestKeys.privateKey(2);
        BasicHttpServer s = new BasicHttpServer(nodeKey, 15600, 32, log);
        BasicHTTPClient c = new BasicHTTPClient("http://localhost:15600");
        c.start(clientKey, nodeKey.getPublicKey());
        assertThrows(BasicHTTPClient.CommandFailedException.class, ()->c.command("test_error"));
    }

}