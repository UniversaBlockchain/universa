/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.BasicHTTPClient;
import com.icodici.universa.node2.network.ClientHTTPClient;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.number.OrderingComparison.greaterThan;

public class MainTest {
    @Test
    public void waitReady() throws Exception {
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
        assertThat(data.getListOrThrow("log").size(), greaterThan(3));
        BasicHTTPClient.Answer a = client.request("ping");
        l.log(">>"+a);
        Main.shutdown();
        Thread.sleep(100);
    }

}