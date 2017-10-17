/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/17/17.
 *
 */

package com.icodici.universa.node.network.server;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.exception.ClientError;
import com.icodici.universa.node.network.BasicHTTPService;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;


import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;



public class UniversaHTTPServer {

    /**
     * Limit of threads in threadpool; set to null for no limit (to use CachedThreadPool).
     */
    private static final Integer DEFAULT_THREAD_LIMIT = 16;


    private static LogPrinter log = new LogPrinter("UHTP");


    private BasicHTTPService httpService;

    private BasicHTTPService.RequestPreprocessor preprocessor;

    private PrivateKey privateKey;
    private Integer threadLimit;
    private Integer port;


    private ConcurrentHashMap<PublicKey, Session> sessionsByKey = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Long, Session> sessionsById = new ConcurrentHashMap<>();


    private AtomicLong sessionIds = new AtomicLong(
            ZonedDateTime.now().toEpochSecond() +
                    Do.randomInt(0x7FFFffff));


    public UniversaHTTPServer(BasicHTTPService httpService, PrivateKey privateKey, int port, int threadLimit) throws IOException {
        this.httpService = httpService;
        this.privateKey = privateKey;
        this.port = port;
        this.threadLimit = threadLimit;
    }

    public UniversaHTTPServer(BasicHTTPService httpService, PrivateKey privateKey, int port) throws IOException {
        this.httpService = httpService;
        this.privateKey = privateKey;
        this.port = port;
        this.threadLimit = DEFAULT_THREAD_LIMIT;
    }

    public void setRequestPreprocessor(BasicHTTPService.RequestPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }


    public void start() throws Exception {
        this.httpService.start(this.port, this.threadLimit);
        this.addDefaultEndpoints();
    }


    public UniversaHTTPServer addEndpoint(String path, BasicHTTPService.BinderHandler handler) {
        this.httpService.on(path, ((request, response) -> {
            Binder result = new Binder();

            Binder requestParams = runPreprocessorIfExists(request.getParams());

            handler.handle(requestParams, result);

            response.setBody(Boss.pack(result));
        }));

        return this;
    }

    private void addDefaultEndpoints() {
        this.httpService.on("/connect", (request, response) -> {
            Binder requestParams = runPreprocessorIfExists(request.getParams());

            try {
                PublicKey client_key = new PublicKey(requestParams.getBinaryOrThrow("client_key"));
                Binder binder = inSession(client_key, Session::connect);

                response.setBody(Boss.pack(binder));
            } catch (Exception e) {
                log.wtf("Error response", e);
                response.setBody(Boss.pack(new ErrorRecord(Errors.FAILURE, "", e.getMessage())));
            }
        });

        this.httpService.on("/get_token", (request, response) -> {
            Binder requestParams = runPreprocessorIfExists(request.getParams());

            Binder session_id = inSession(requestParams.getLongOrThrow("session_id"),
                    s -> s.getToken(requestParams, this.privateKey));

            response.setBody(Boss.pack(session_id));
        });

        this.httpService.on("/command", ((request, response) -> {
            Binder requestParams = runPreprocessorIfExists(request.getParams());

            Binder result = inSession(requestParams.getLongOrThrow("session_id"),
                    s -> s.command(requestParams));

            response.setBody(Boss.pack(result));
        }));
    }

    public void shutdown() throws Exception {
        this.httpService.close();
        this.httpService = null;
    }


    private Binder runPreprocessorIfExists(Binder params) {
        return this.preprocessor != null ? this.preprocessor.handle(params) : params;
    }

    private Binder inSession(PublicKey key, Implementor function) throws EncryptionError {
        return inSession(getSession(key), function);
    }

    private Binder inSession(Session s, Implementor processor) {
        synchronized (s) {
            try {
                s.clearErrors();
                return s.answer(processor.apply(s));
            } catch (ClientError e) {
                s.addError(e.getErrorRecord());
            } catch (Exception e) {
                s.addError(new ErrorRecord(Errors.FAILURE, "", e.getMessage()));
            }
            return s.answer(null);
        }
    }

    private Binder inSession(long id, Implementor processor) {
        Session s = sessionsById.get(id);
        if (s == null)
            throw new IllegalArgumentException("bad session number");
        return inSession(s, processor);
    }

    public void changeKeyFor(PublicKey clientKey) throws EncryptionError {
        Session session = sessionsByKey.get(clientKey);
        if (session != null)
            session.setSessionKey(null);
    }

    @NonNull
    private Session getSession(PublicKey key) throws EncryptionError {
        synchronized (sessionsByKey) {
            Session r = sessionsByKey.get(key);
            if (r == null) {
                r = new Session(key, sessionIds.getAndIncrement());
                sessionsByKey.put(key, r);
                sessionsById.put(r.getSessionId(), r);
            }
            return r;
        }
    }
}
