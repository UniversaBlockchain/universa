/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import org.junit.Test;

public class MainTest {
    @Test
    public void waitReady() throws Exception {
        String[] args = new String[] { "--test", "--config", "src/test_node_config_v2"};
        Main.main(args);
        Main.waitReady();
        System.out.println("main ready");
        Main.shutdown();
    }

}