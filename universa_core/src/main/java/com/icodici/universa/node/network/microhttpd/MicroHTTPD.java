/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.NanoHTTPD;

import java.io.*;
import java.net.Socket;

/**
 * A customized version of NanoHTTPD.
 */
public class MicroHTTPD extends NanoHTTPD {
    /**
     * Constructor.
     * @param portToListen is port number
     * @param threadLimit is maximum available threads
     */
    public MicroHTTPD(int portToListen, @Nullable Integer threadLimit) {
        super(portToListen);

        // Even though MicroHTTPSession is capable of limiting the memory,
        // we need a smarter and more capable factory.
        setTempFileManagerFactory(new InMemoryTempFileManager.InMemoryTempFileManagerFactory());

        // Use an ExecutorService-based strategy of thread creation.
        setAsyncRunner(new PooledAsyncRunner(threadLimit, "[port:" + portToListen + "]"));
    }

    @Override
    protected ClientHandler createClientHandler(final Socket finalAccept, final InputStream inputStream) {
        return new MicroHTTPSession.MicroClientHandler(this, inputStream, finalAccept);
    }
}
