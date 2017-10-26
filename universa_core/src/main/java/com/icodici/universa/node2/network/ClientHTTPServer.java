/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;
import com.icodici.universa.node.network.server.UniversaHTTPServer;
import net.sergeych.tools.BufferedLogger;

import java.io.IOException;

public class ClientHTTPServer extends UniversaHTTPServer {

    private final BufferedLogger logger;

    public ClientHTTPServer(PrivateKey privateKey, int port, BufferedLogger logger) throws IOException {
        super(new MicroHTTPDService(), privateKey, port, 32);
        this.logger = logger;
    }

//    @Override
//    public void start() throws Exception {
//        super.start();
//        addSecureEndpoint("state", (params) -> getState());
//    }
//
//    private Binder getState(Binder params) {
//        response.set("status", "establishing");
//    }
}
