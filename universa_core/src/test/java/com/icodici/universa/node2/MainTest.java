/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.BasicHTTPClient;
import com.icodici.universa.node2.network.ClientHTTPClient;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MainTest {
    @Test
    public void startNode() throws Exception {
        String[] args = new String[] { "--test", "--config", "../../deploy/samplesrv", "--nolog"};
        Main.main(args);
        Main.waitReady();
        BufferedLogger l = Main.logger;

        ClientHTTPClient client = new ClientHTTPClient(
                "http://localhost:2052",
                TestKeys.privateKey(3),
                Main.getNodePublicKey()
        );

        l.log("client ready");
        Binder data = client.command("status");
        data.getStringOrThrow("status");
//        assertThat(data.getListOrThrow("log").size(), greaterThan(3));
        BasicHTTPClient.Answer a = client.request("ping");
        l.log(">>"+a);

//        URL url = new URL("http://localhost:2052/contracts/1234597899=");

        Contract c = new Contract();
        c.setIssuerKeys(TestKeys.publicKey(3));
        c.addSignerKey(TestKeys.privateKey(3));
        c.registerRole(new RoleLink("owner", "issuer"));
        c.registerRole(new RoleLink("creator", "issuer"));
        c.setExpiresAt(ZonedDateTime.now().plusDays(2));
        byte[] sealed = c.seal();
//        Bytes.dump(sealed);

        Contract c1 = new Contract(sealed);
        assertArrayEquals(c.getLastSealedBinary(), c1.getLastSealedBinary());

        Main.cache.put(c);
        assertNotNull(Main.cache.get(c.getId()));

        System.out.println("source id       "+c.getId().toBase64String());
//        URL url = new URL("http://localhost:2052/contracts/cache_test");
        URL url = new URL("http://localhost:2052/contracts/"+c.getId().toBase64String());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        System.out.println(con.getResponseCode());
            System.out.println(con.getHeaderFields());
        byte[] data2 = Do.read(con.getInputStream());

        assertArrayEquals(c.getLastSealedBinary(), data2);

        url = new URL("http://localhost:2052/network");
        con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        System.out.println(con.getResponseCode());
        System.out.println(con.getHeaderFields());
        Binder bres = Boss.unpack((Do.read(con.getInputStream())))
                .getBinderOrThrow("response");
        List<Binder> ni = bres.getBinders("nodes");
        System.out.println("\n\n" +
                ni.stream().map(x->x.getStringOrThrow("url"))
                           .collect(Collectors.toList()));

        Main.shutdown();
        Thread.sleep(100);
    }

}