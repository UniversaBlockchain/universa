/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.*;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongycastle.util.encoders.Base64;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP endpoint for client requests.
 * <p>
 * Key authentication, two steps, client calls serber:
 * <p>
 * connect(my_public_key, client_salt) -> server_nonce get_token(signed(my_public_key, server_nonce, client_nonce)) ->
 * signed(node_key, server_nonce, encrypted(my_public_key, session_key))
 * <p>
 * Threadpool is used, and controlled by setting THREAD_LIMIT to some specific value, or to null for CachedThreadPool.
 */
public class BasicHttpServer {

    //    public void changeKeyFor(PublicKey clientKey) throws EncryptionError {
//        Session session = sessionsByKey.get(clientKey);
//        if (session != null)
//            session.sessionKey = null;
//    }
//
    private interface Implementor {
        Binder apply(Session session) throws Exception;
    }

    protected BasicHTTPService service;
    private final BufferedLogger log;
    private PrivateKey myKey;

    BasicHttpServer(PrivateKey key, int port, int maxTrheads, BufferedLogger log) throws IOException {
        this.myKey = key;
        this.log = log;
        service = new MicroHTTPDService();

        addEndpoint("/ping", params -> onPing(params));
        addEndpoint("/connect", params -> onConnect(params));
        addEndpoint("/get_token", params -> inSession(params.getLongOrThrow("session_id"), s -> s.getToken(params)));
        addEndpoint("/command", params -> inSession(params.getLongOrThrow("session_id"), s -> s.command(params)));

        service.start(port, maxTrheads);
    }

    public void on(String path, BasicHTTPService.Handler handler) {
        service.on(path, handler);
    }

    private Binder onConnect(Binder params) throws ClientError {
        try {
            PublicKey clientKey = new PublicKey(params.getBinaryOrThrow("client_key"));
            return inSession(clientKey, session -> session.connect());
        } catch (Exception e) {
            throw new ClientError(Errors.BAD_CLIENT_KEY, "client_key", "bad client key");
        }
    }

    class Result extends Binder {
        private int status = 200;

        public void setStatus(int code) {
            status = code;
        }
    }

    public interface Endpoint {
        public void execute(Binder params, Result result) throws Exception;
    }

    public interface SimpleEndpoint {
        Binder execute(Binder params) throws Exception;
    }

    public interface SecureEndpoint {
        Binder execute(Binder params, Session session) throws Exception;
    }

    private final ConcurrentHashMap<String, SecureEndpoint> secureEndpoints = new ConcurrentHashMap<>();

    public void addSecureEndpoint(String commandName, SecureEndpoint ep) {
        secureEndpoints.put(commandName, ep);
    }

    public void addEndpoint(String path, Endpoint ep) {
        on(path, (request, response) -> {
            Binder result;
            try {
                Result epResult = new Result();
//                System.out.println("extracted params: " + extractParams(request));
                ep.execute(extractParams(request), epResult);
                result = Binder.of(
                        "result", "ok",
                        "response", epResult);
            } catch (Exception e) {
                result = Binder.of(
                        "result", "error",
                        "error", e.toString(),
                        "errorClass", e.getClass().getName()
                );
            }
            response.setBody(Boss.pack(result));
        });
    }

    void addEndpoint(String path, SimpleEndpoint sep) {
        addEndpoint(path, (params, result) -> {
            result.putAll(sep.execute(params));
        });
    }

    public Binder extractParams(BasicHTTPService.Request request) {
        Binder rp = request.getParams();
        String sparams = rp.getString("requestData64", null);
        if (sparams != null) {
            byte [] x = Base64.decode(sparams);
            return Boss.unpack(x);
        } else {
            BasicHTTPService.FileUpload rd = (BasicHTTPService.FileUpload) rp.get("requestData");
            if (rd != null) {
                byte[] data = rd.getBytes();
                return Boss.unpack(data);
            }
        }
        return Binder.EMPTY;
    }

    private Binder onPing(Binder params) {
        Binder result = Binder.fromKeysValues("ping", "pong");
        result.putAll(params);
        return result;
    }

    public void shutdown() {
        try {
            service.close();
        } catch (Exception e) {
        }
    }


    private Binder inSession(PublicKey key, Implementor function) throws EncryptionError {
        return inSession(getSession(key), function);
    }

    private Binder inSession(Session s, Implementor processor) {
        synchronized (s) {
            try {
                s.errors.clear();
                return s.answer(processor.apply(s));
            } catch (ClientError e) {
                s.errors.add(e.getErrorRecord());
            } catch (Exception e) {
                s.errors.add(new ErrorRecord(Errors.FAILURE, "", e.getMessage()));
            }
            return s.answer(null);
        }
    }

    //
    private Binder inSession(long id, Implementor processor) {
        Session s = sessionsById.get(id);
        if (s == null)
            throw new IllegalArgumentException("bad session number");
        return inSession(s, processor);
    }

    //
//    private Response processRequest(String uri, Binder params) {
//        try {
//            final Binder result;
//            switch (uri) {
//                case "/ping":
//                    result = Binder.fromKeysValues("ping", "pong");
//                    result.putAll(params);
//                    break;
//                case "/network":
//                    result = getNetworkDirectory();
//                    break;
//                case "/connect":
//                    try {
//                        PublicKey clientKey = new PublicKey(params.getBinaryOrThrow("client_key"));
//                        result = inSession(clientKey, session -> session.connect());
//                    } catch (Exception e) {
//                        return errorResponse(
//                                Status.OK,
//                                new ErrorRecord(Errors.BAD_CLIENT_KEY,
//                                                "client_key",
//                                                e.getMessage()
//                                )
//                        );
//                    }
//                    break;
//                case "/get_token":
//                    result = inSession(params.getLongOrThrow("session_id"), s -> s.getToken(params));
//                    break;
//                case "/command":
//                    result = inSession(params.getLongOrThrow("session_id"), s -> s.command(params));
//                    break;
//                default:
//                    return errorResponse(
//                            Status.OK,
//                            new ErrorRecord(Errors.UNKNOWN_COMMAND,
//                                            "uri",
//                                            "command not supported: " + uri
//                            )
//                    );
//            }
//            return makeResponse(Status.OK, result);
//        } catch (Exception e) {
//            return errorResponse(e);
//        }
//    }
//
//
    ConcurrentHashMap<PublicKey, Session> sessionsByKey = new ConcurrentHashMap<>();
    ConcurrentHashMap<Long, Session> sessionsById = new ConcurrentHashMap<>();

    @NonNull
    private Session getSession(PublicKey key) throws EncryptionError {
        synchronized (sessionsByKey) {
            Session r = sessionsByKey.get(key);
            if (r == null) {
                r = new Session(key);
                sessionsByKey.put(key, r);
                sessionsById.put(r.sessionId, r);
            }
            return r;
        }
    }

    private AtomicLong sessionIds = new AtomicLong(
            ZonedDateTime.now().toEpochSecond() +
                    Do.randomInt(0x7FFFffff));

    protected class Session {

        private PublicKey publicKey;
        private SymmetricKey sessionKey;
        private byte[] serverNonce;
        private byte[] encryptedAnswer;
        private long sessionId = sessionIds.incrementAndGet();


        protected Session(PublicKey key) throws EncryptionError {
            publicKey = key;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        private void createSessionKey() throws EncryptionError {
            if (sessionKey == null) {
                sessionKey = new SymmetricKey();
                Binder data = Binder.fromKeysValues(
                        "sk", sessionKey.pack()
                );
                encryptedAnswer = publicKey.encrypt(Boss.pack(data));
            }
        }

        Binder connect() {
            if (serverNonce == null)
                serverNonce = Do.randomBytes(48);
            return Binder.fromKeysValues(
                    "server_nonce", serverNonce,
                    "session_id", ""+sessionId
            );
        }

        Binder getToken(Binder data) {
            // Check the answer is properly signed
            byte[] signedAnswer = data.getBinaryOrThrow("data");
            try {
                if (publicKey.verify(signedAnswer, data.getBinaryOrThrow("signature"), HashType.SHA512)) {
                    Binder params = Boss.unpack(signedAnswer);
                    // now we can check the results
                    if (!Arrays.equals(params.getBinaryOrThrow("server_nonce"), serverNonce))
                        addError(Errors.BAD_VALUE, "server_nonce", "does not match");
                    else {
                        // Nonce is ok, we can return session token
                        createSessionKey();
                        Binder result = Binder.fromKeysValues(
                                "client_nonce", params.getBinaryOrThrow("client_nonce"),
                                "encrypted_token", encryptedAnswer
                        );
                        byte[] packed = Boss.pack(result);
                        return Binder.fromKeysValues(
                                "data", packed,
                                "signature", myKey.sign(packed, HashType.SHA512)
                        );
                    }
                }
            } catch (Exception e) {
                addError(Errors.BAD_VALUE, "signed_data", "wrong or tampered data block:" + e.getMessage());
            }
            return null;
        }

        private Binder answer(Binder result) {
            if (result == null)
                result = new Binder();
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            return result;
        }

        private void addError(Errors code, String object, String message) {
            errors.add(new ErrorRecord(code, object, message));
        }

        private List<ErrorRecord> errors = Collections.synchronizedList(new ArrayList<>());

        public Binder command(Binder params) throws ClientError, EncryptionError {
            // decrypt params and execute command
            Binder result = null;
            try {
                result = Binder.fromKeysValues(
                        "result",
                        executeAuthenticatedCommand(
                                Boss.unpack(
                                        sessionKey.decrypt(params.getBinaryOrThrow("params"))
                                )
                        )
                );
            } catch (Exception e) {
                ErrorRecord r = (e instanceof ClientError) ? ((ClientError) e).getErrorRecord() :
                        new ErrorRecord(Errors.COMMAND_FAILED, "", e.getMessage());
                result = Binder.fromKeysValues(
                        "error", r
                );
            }
            // encrypt and return result
            return Binder.fromKeysValues(
                    "result",
                    sessionKey.encrypt(Boss.pack(result))
            );
        }

        private Binder executeAuthenticatedCommand(Binder params) throws ClientError {
            String cmd = params.getStringOrThrow("command");
            try {
                switch (cmd) {
                    case "hello":
                        return Binder.fromKeysValues(
                                "status", "OK",
                                "message", "welcome to the Universa"
                        );
                    case "sping":
                        return Binder.fromKeysValues("sping", "spong");

                    case "test_error":
                        throw new IllegalAccessException("sample error");
                    default:
                        SecureEndpoint sep = secureEndpoints.get(cmd);
                        if (sep != null)
                            return sep.execute(params.getBinder("params", Binder.EMPTY), this);
                }
//            } catch (ClientError e) {
//                throw e;
//            } catch (ClientError error) {
//                throw error;
            } catch (Exception e) {
                e.printStackTrace();
                if (e instanceof ClientError)
                    throw (ClientError) e;
                throw new ClientError(Errors.COMMAND_FAILED, cmd, e.getMessage());
            }
            throw new ClientError(Errors.UNKNOWN_COMMAND, "command", "unknown: " + cmd);
        }
    }


}