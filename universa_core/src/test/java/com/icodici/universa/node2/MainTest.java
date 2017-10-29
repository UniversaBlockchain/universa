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
        String[] args = new String[]{"--test", "--config", "/Users/sergeych/dev/new_universa/universa_core/src/test_node_config_v2/node1", "--nolog"};
        Main main = new Main(args);
        main.waitReady();
        BufferedLogger l = main.logger;

        ClientHTTPClient client = new ClientHTTPClient(
                "http://localhost:6000",
                TestKeys.privateKey(3),
                main.getNodePublicKey()
        );

        Binder data = client.command("status");
        data.getStringOrThrow("status");
//        assertThat(data.getListOrThrow("log").size(), greaterThan(3));
        BasicHTTPClient.Answer a = client.request("ping");
        assertEquals("200: {ping=pong}", a.toString());


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

        main.cache.put(c);
        assertNotNull(main.cache.get(c.getId()));

        URL url = new URL("http://localhost:6000/contracts/" + c.getId().toBase64String());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        byte[] data2 = Do.read(con.getInputStream());

        assertArrayEquals(c.getLastSealedBinary(), data2);

        url = new URL("http://localhost:6000/network");
        con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        Binder bres = Boss.unpack((Do.read(con.getInputStream())))
                .getBinderOrThrow("response");
        List<Binder> ni = bres.getBinders("nodes");
        String pubUrls = ni.stream().map(x -> x.getStringOrThrow("url"))
                .collect(Collectors.toList())
                .toString();

        assertEquals("[http://localhost:8080, http://localhost:8080, http://localhost:8080]", pubUrls);

        main.shutdown();
        main.logger.stopInterceptingStdOut();;
        main.logger.getCopy().forEach(x-> System.out.println(x));
    }

}