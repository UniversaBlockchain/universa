/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.ClientHTTPClient;
import net.sergeych.tools.BufferedLogger;
import org.junit.Test;

public class MainTest {
    @Test
    public void waitReady() throws Exception {
        String[] args = new String[] { "--test", "--config", "src/test_node_config_v2"};
        Main.main(args);
        Main.waitReady();
        BufferedLogger l = Main.logger;

        ClientHTTPClient client = new ClientHTTPClient(
                "testNode0",
                "http://localhost:2236",
                TestKeys.privateKey(3),
                Main.getNodePublicKey()
        );

        l.log("client ready");
        l.log("--> "+client.getState());

        Main.shutdown();
        Thread.sleep(100);
        System.out.println("after intercept");
    }

}