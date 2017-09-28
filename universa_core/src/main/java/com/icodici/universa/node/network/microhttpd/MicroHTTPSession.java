/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.HTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.tempfiles.ITempFile;
import org.nanohttpd.protocols.http.tempfiles.ITempFileManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * {@link HTTPSession} implementation, that has several improvements over the original:
 * • Loads all files only to memory, without any intermediate disk storage.
 * • Boosts the request buffer from 512 bytes to 4096.
 */
public class MicroHTTPSession extends HTTPSession {
    private static int DEFAULT_REQUEST_BUFFER_LEN = 4096;

    /**
     * Custom {@link ClientHandler}, only to generate the {@link MicroHTTPSession} sessions.
     */
    static class MicroClientHandler extends ClientHandler {

        public MicroClientHandler(NanoHTTPD httpd, InputStream inputStream, Socket acceptSocket) {
            super(httpd, inputStream, acceptSocket);
        }

        @Override
        protected HTTPSession createHTTPSession(NanoHTTPD httpd, ITempFileManager tempFileManager, InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
            return new MicroHTTPSession(httpd, tempFileManager, inputStream, outputStream, inetAddress);
        }
    }


    public MicroHTTPSession(NanoHTTPD httpd, ITempFileManager tempFileManager, InputStream inputStream, OutputStream outputStream) {
        super(httpd, tempFileManager, inputStream, outputStream);
    }

    public MicroHTTPSession(NanoHTTPD httpd, ITempFileManager tempFileManager, InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
        super(httpd, tempFileManager, inputStream, outputStream, inetAddress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Integer getMemoryStoreLimit() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int getRequestBufferLen() {
        return DEFAULT_REQUEST_BUFFER_LEN;
    }

    @Override
    protected String saveTmpFile(ByteBuffer b, int offset, int len, String filename_hint) {
        String path = "";
        if (len > 0) {
            try {
                final InMemoryTempFile tempFile = (InMemoryTempFile)tempFileManager.createTempFile(filename_hint);
                final ByteBuffer src = b.duplicate();
                src.position(offset).limit(offset + len);
                tempFile.getOutputByteStream().write(src.array(), offset, len);
                path = tempFile.getName(); // Note the name is not related to any physical file on disk!
            } catch (Exception e) { // Catch exception if any
                throw new Error(e); // we won't recover, so throw an error
            }
        }
        return path;
    }
}
