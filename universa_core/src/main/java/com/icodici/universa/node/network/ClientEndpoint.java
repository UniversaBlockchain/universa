/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.node.LocalNode;
import fi.iki.elonen.NanoHTTPD;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Ut;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP endpoint for client requests
 * <p>
 * We might need to use threadpool later (https://github.com/NanoHttpd/nanohttpd/wiki/Example:-Using-a-ThreadPool)
 */
public class ClientEndpoint {

    private Server instance;
    private final NetworkBuilder networkBuilder;
    private LocalNode localNode;
    private int port;

    public void shutdown() {
        if (instance != null)
            synchronized (instance) {
                instance.shutdown();
                instance = null;
            }
    }

    // todo: get rid of Nanohttpd - it SAVES TEMP FILE - and this can't be avoided
    // as a variant, we can reqrite it to make proper use of the TempFileFactory which
    // capability to substitute temp files it partially ignores
    public class Server extends NanoHTTPD {
        public Server(int portToListen) {
            super(portToListen);
        }

        class BinaryResponse extends Response {
            public BinaryResponse(IStatus status, byte[] data) {
                super(status,
                      "application/octet-stream",
                      new ByteArrayInputStream(data),
                      data.length
                );
            }
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> filesMap = new HashMap<>();
            try {
                session.parseBody(filesMap);
                String tempFileName = filesMap.get("requestData");
                byte[] data = Do.read(new FileInputStream(tempFileName));
                Binder params = Boss.unpack(data);
                return processRequest(session.getUri(), params);
            } catch (IOException e) {
                e.printStackTrace();
                return errorResponse(e);
            } catch (ResponseException e) {
                e.printStackTrace();
                return errorResponse(e);
            }
        }

        private Response processRequest(String uri, Binder params) {
            try {
                Binder result = null;
                switch (uri) {
                    case "/ping":
                        result = Binder.fromKeysValues("ping", "pong");
                        result.putAll(params);
                        break;
                    default:
                        return errorResponse(Response.Status.NOT_FOUND, "BAD_COMMAND", "command not supported");
                    case "/network":
                        result = getNetworkDirectory();
                        break;
                    case "/stop":
                        if (true) {
                            networkBuilder.shutdown();
                            result = Binder.fromKeysValues("stopped", "ok");
                        } else
                            result = Binder.fromKeysValues("can;t stop", "insufficient rights");
                }
                return makeResponse(Response.Status.OK, result);
            } catch (
                    Exception e)

            {
                return errorResponse(e);
            }
        }

        private void shutdown() {
            closeAllConnections();
            stop();
        }

        private Response errorResponse(Response.Status code, String errorCode, String text) {
            return reponseKeysValues(code, "error", errorCode, "text", text);
        }

        private Response errorResponse(Throwable t) {
            t.printStackTrace();
            return reponseKeysValues(Response.Status.INTERNAL_ERROR,
                                     "error", "INTERROR", "text", t.getMessage()
            );
        }

        private Response reponseKeysValues(Response.Status status, Object... data) {
            return makeResponse(status, Binder.fromKeysValues(data));
        }

        private Response makeResponse(Response.Status status, Binder data) {
            return new BinaryResponse(status, Boss.pack(data));
        }
    }

    private Binder networkDirectory = null;

    private Binder getNetworkDirectory() {
        synchronized (this) {
            if (null == networkDirectory) {
                Binder network = new Binder();
                networkBuilder.nodeInfo().forEach(ni -> {
                    network.put(ni.getNodeId(),
                                Binder.fromKeysValues(
                                        "port", ni.getClientPort(),
                                        "ip", ni.getHost()
                                )
                    );
                    networkDirectory = network;
                });
            }
            return networkDirectory;
        }
    }

    private class Registration {
        PublicKey publicKey;
        byte[] sessionKey;
        byte[] encryptedAnswer;

        Registration(PublicKey key) throws EncryptionError {
            publicKey = key;
            sessionKey = Do.randomBytes(32);
            Binder data = Binder.fromKeysValues(
                    "sk", sessionKey
            );
            encryptedAnswer = publicKey.encrypt(Boss.pack(data));
        }

    }

    ConcurrentHashMap<PublicKey, Registration> registrations = new ConcurrentHashMap<>();

    Binder requestToken(Binder params) throws EncryptionError {
        PublicKey remoteKey = params.getOrThrow("key");
        Registration r;
        synchronized (registrations) {
            r = registrations.get(remoteKey);
            if (r == null) {
                r = new Registration(remoteKey);
                registrations.put(remoteKey, r);
            }
        }
        return Binder.fromKeysValues("encryptedToken", r.encryptedAnswer);
    }

    public ClientEndpoint(int port, LocalNode localNode, NetworkBuilder nb) throws IOException {
        this.port = port;
        this.networkBuilder = nb;
        instance = new Server(port);
        this.localNode = localNode;
        instance.start();
    }


}
