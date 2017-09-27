/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.node.TestCase;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClientEndpointTest extends TestCase {

    private ClientEndpoint ep;
    private String rootUrl;
    private HttpClient client;

    @Test
    public void packing() throws Exception {
        List<ErrorRecord> l = new ArrayList<>();
        for(int i=0; i<10; i++) {
            l.add(new ErrorRecord(Errors.NOT_SUPPORTED, "t1", "message_"+i));
        }

        Object s = BossBiMapper.newSerializer().serialize(l);
        Object x = BossBiMapper.getInstance().deserializeObject(s);
        assertThat(x, instanceOf(List.class));
        assertThat(((List)x).get(0), instanceOf(ErrorRecord.class));
        x = Boss.load(Boss.pack(l));
        assertThat(x, instanceOf(List.class));
        assertThat(((List)x).get(0), instanceOf(ErrorRecord.class));

        Binder b = Binder.of("errors", l);
        s = BossBiMapper.newSerializer().serialize(b);
        x = BossBiMapper.getInstance().deserializeObject(s);
        x = ((Map)x).get("errors");
        assertThat(x, instanceOf(List.class));
        assertThat(((List)x).get(0), instanceOf(ErrorRecord.class));

        x = Boss.load(Boss.pack(b));
        x = ((Map)x).get("errors");
        assertThat(x, instanceOf(List.class));
        assertThat(((List)x).get(0), instanceOf(ErrorRecord.class));
    }

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
        ep.shutdown();
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
            fail("expected exception wasn't thrown");
        }
        catch(HttpClient.CommandFailedException e) {
            assertEquals(Errors.COMMAND_FAILED, e.getError().getError());
            assertEquals("test_error", e.getError().getObjectName());
            assertEquals("sample error", e.getError().getMessage());
        }
        ep.shutdown();
    }
}