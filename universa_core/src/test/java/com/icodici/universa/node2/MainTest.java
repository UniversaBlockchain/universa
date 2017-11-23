/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.BasicHttpClient;
import com.icodici.universa.node2.network.Client;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Ignore("start it manually")
public class MainTest {

    @After
    public void tearDown() throws Exception {
        LogPrinter.showDebug(false);
    }

    @Test
    public void startNode() throws Exception {
        String path = new File("src/test_node_config_v2/node1").getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, "--nolog"};
        Main main = new Main(args);
        main.waitReady();
        BufferedLogger l = main.logger;

        Client client = new Client(
                "http://localhost:8080",
                TestKeys.privateKey(3),
                main.getNodePublicKey(),
                null
        );

        Binder data = client.command("status");
        data.getStringOrThrow("status");
//        assertThat(data.getListOrThrow("log").size(), greaterThan(3));
        BasicHttpClient.Answer a = client.request("ping");
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

        URL url = new URL("http://localhost:8080/contracts/" + c.getId().toBase64String());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        byte[] data2 = Do.read(con.getInputStream());

        assertArrayEquals(c.getPackedTransaction(), data2);

        url = new URL("http://localhost:8080/network");
        con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        Binder bres = Boss.unpack((Do.read(con.getInputStream())))
                .getBinderOrThrow("response");
        List<Binder> ni = bres.getBinders("nodes");
        String pubUrls = ni.stream().map(x -> x.getStringOrThrow("url"))
                .collect(Collectors.toList())
                .toString();

        assertEquals("[http://localhost:8080, http://localhost:6002, http://localhost:6004]", pubUrls);

        main.shutdown();
        main.logger.stopInterceptingStdOut();;
        main.logger.getCopy().forEach(x-> System.out.println(x));
    }

    Main createMain(String name,boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2/"+name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};
        Main main = new Main(args);
        main.waitReady();
        return main;
    }

    @Test
    public void localNetwork() throws Exception {
        List<Main> mm = new ArrayList<>();
        for( int i=0; i<3; i++ )
            mm.add(createMain("node"+(i+1), false));
        Main main = mm.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = TestKeys.privateKey(3);

        assertEquals(main.cache, main.node.getCache());
        ItemCache c1 = main.cache;
        ItemCache c2 = main.node.getCache();

        Contract c = new Contract(myKey);
        c.seal();
        assertTrue(c.isOk());

        Client client = new Client(myKey, main.myInfo, null);

        ItemResult r = client.getState(c.getId());
        assertEquals(ItemState.UNDEFINED, r.state);
        System.out.println(":: "+r);

//        LogPrinter.showDebug(true);
        r = client.register(c.getPackedTransaction());
        System.out.println(r);

        while(true) {
            r = client.getState(c.getId());
            System.out.println("-->? " + r);
            Thread.sleep(3000);
            if( !r.state.isPending() )
                break;
        }
        mm.forEach(x->x.shutdown());
//        System.out.println("-->! " + r);

//        assertEquals(ItemState.UNDEFINED, s);
    }

    @Test
    @Ignore("This test nust be started manually")
    public void checkRealNetwork() throws Exception {

        PrivateKey clientKey = TestKeys.privateKey(3);
        Client client = new Client("http://node-17-com.universa.io:8080", clientKey, null);

        Contract c = new Contract(clientKey);
        c.setExpiresAt(ZonedDateTime.now().plusSeconds(300));
        c.seal();
        assertTrue(c.isOk());

        ItemResult r = client.getState(c.getId());
        assertEquals(ItemState.UNDEFINED, r.state);
        System.out.println(":: "+r);


        r = client.getState(c.getId());
        assertEquals(ItemState.UNDEFINED, r.state);
        System.out.println(":: "+r);

        LogPrinter.showDebug(true);
//        r = client.register(c.getLastSealedBinary());
        r = client.register(c.getPackedTransaction());
        System.out.println(r);

        while(true) {
            r = client.getState(c.getId());
            System.out.println("-->? " + r);
            Thread.sleep(50);
            if( !r.state.isPending() )
                break;
        }
//
//        Client client = new Client(myKey, );
    }
}