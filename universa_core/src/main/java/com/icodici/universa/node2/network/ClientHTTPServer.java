/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;

import java.io.IOException;

public class ClientHTTPServer extends BasicHttpServer {

    private final BufferedLogger log;

    public ClientHTTPServer(PrivateKey privateKey, int port, BufferedLogger logger) throws IOException {
        super(privateKey, port, 64, logger);
        log = logger;

        addSecureEndpoint("status", (params, session)->Binder.of(
                "status", "initializing",
                "log", log.getLast(10)
        ));
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
