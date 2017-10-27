/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network;

import net.sergeych.tools.Binder;

import java.io.IOException;

/**
 * Interface for generic HTTP server implementation to be used in the Universa project.
 */
public interface BasicHTTPService extends AutoCloseable {

    /**
     * Start the service in a separate thread, returns as soon as listening port will be opened. Should not
     * return before the port is opened! Must use Executors.newFixedThreadPool for workers.
     *
     * @param port
     * @param maxResponseThreads
     * @throws IOException if port can't be open for listening
     */
    void start(int port, int maxResponseThreads) throws IOException;

    /**
     * Shutdown the service and free all allocated resources. Note it must not shutdown the worker pool,
     * instead, let it finish off normally (e.g. be finalized). Must close listening socket.
     *
     * @throws Exception
     */
    @Override
    void close() throws Exception;

    /**
     * Register request handler which must be called if the {@link Request#getPath()} starts with pathStart.
     *
     * @param pathStart the beginning of the request's path that should be handled by this handler
     * @param handler   to handle such requests
     */
    void on(String pathStart, Handler handler);

    /**
     * A parameter for {@link Request#getParams()} representing fileupload arguments
     */
    interface FileUpload {
        String getFileName();

        default String getMimeType() {
            return "application/octet-stream";
        }

        default String getDisposition() {
            return "inline";
        }

        byte[] getBytes();
    }

    interface Request {
        /**
         * @return path - excluding protocol, domain and query
         */
        String getPath();

        /**
         * @return domain
         */
        String getDomain();

        /**
         * Represent query as Binder of key-values. Values should be decoded, either String or {@link FileUpload}.
         * Contains both query parameters from URL arguments and from forms, where present.
         * Each key is a {@link String}; a value is either a single {@link String} or {@link java.util.List<String>},
         * depending on how many values were provided for the single key.
         *
         * @return
         */
        Binder getParams();

        /**
         * @return request headers in the {@link Binder} form
         */
        Binder getHeaders();

        /**
         * E.g. GET, POST, whatever.
         *
         * @return
         */
        String getMethod();
    }

    /**
     * HTTP response abstraction
     */
    interface Response {
        /**
         * Get mutable headers object. It means that if the caller mutate returned Binder, it should
         * change the response headers sent to the network. All calls to this method should return the
         * same instance!
         *
         * @return response headers.
         */
        Binder getHeaders();

        /**
         * Set the body to specific value.
         *
         * @param bodyAsString body in the form of String. Must return in UTF-8 encoding and set appropriate headers.
         *                     if the mime-type header was not set, set it to application/text
         */
        void setBody(String bodyAsString);

        /**
         * Set the body to specific value.
         *
         * @param bodyAsBytes body in the form of byte array.
         *                    if the mime-type header was not set, set it to application/octet-stream
         */
        void setBody(byte[] bodyAsBytes);
    }

    /**
     * Handler for HTTP requests.
     *
     * If body is already has been set in the Response, this means it already
     * knows about some error.
     */
    interface Handler {
        void handle(Request request, Response response);
    }

    interface BinderHandler {
        void handle(Binder request, Binder response);
    }

    interface RequestPreprocessor {
        Binder handle(Binder request);
    }
}
