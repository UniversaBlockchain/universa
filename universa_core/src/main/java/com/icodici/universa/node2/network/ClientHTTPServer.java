/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node2.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHTTPServer extends BasicHttpServer {

    private final BufferedLogger log;
    private ItemCache cache;
    private ParcelCache parcelCache;
    private NetConfig netConfig;

    private boolean localCors = false;

    private ExecutorService es = Executors.newFixedThreadPool(40);

    public ClientHTTPServer(PrivateKey privateKey, int port, BufferedLogger logger) throws IOException {
        super(privateKey, port, 32, logger);
        log = logger;

        addSecureEndpoint("status", (params, session) -> Binder.of(
                "status", "initializing",
                "log", log.getLast(10)
        ));

        on("/contracts", (request, response) -> {
            String encodedString = request.getPath().substring(11);

            // this is a bug - path has '+' decoded as ' '
            encodedString = encodedString.replace(' ', '+');

            byte[] data = null;
            if (encodedString.equals("cache_test")) {
                data = "the cache test data".getBytes();
            } else {
                HashId id = HashId.withDigest(encodedString);
                if (cache != null) {
                    Contract c = (Contract) cache.get(id);
                    if (c != null) {
                        data = c.getPackedTransaction();
                    }
                }
            }
            if (data != null) {
                // contracts are immutable: cache forever
                Binder hh = response.getHeaders();
                hh.put("Expires", "Thu, 31 Dec 2037 23:55:55 GMT");
                hh.put("Cache-Control", "max-age=315360000");
                response.setBody(data);
            } else
                response.setResponseCode(404);
        });

        on("/parcels", (request, response) -> {
            String encodedString = request.getPath().substring(9);

            // this is a bug - path has '+' decoded as ' '
            encodedString = encodedString.replace(' ', '+');

            byte[] data = null;
            if (encodedString.equals("cache_test")) {
                data = "the cache test data".getBytes();
            } else {
                HashId id = HashId.withDigest(encodedString);
                if (parcelCache != null) {
                    Parcel p = (Parcel) parcelCache.get(id);
                    if (p != null) {
                        data = p.pack();
                    }
                }
            }
            if (data != null) {
                // contracts are immutable: cache forever
                Binder hh = response.getHeaders();
                hh.put("Expires", "Thu, 31 Dec 2037 23:55:55 GMT");
                hh.put("Cache-Control", "max-age=315360000");
                response.setBody(data);
            } else
                response.setResponseCode(404);
        });

        addEndpoint("/network", (Binder params, Result result) -> {
            if (networkData == null) {
                List<Binder> nodes = new ArrayList<Binder>();
                result.putAll(
                        "version", Main.NODE_VERSION,
                        "nodes", nodes
                );
                if (netConfig != null) {
                    netConfig.forEachNode(node -> {
                        nodes.add(Binder.of(
                                "url", node.publicUrlString(),
                                "key", node.getPublicKey().pack()
                        ));
                    });
                }
            }

        });

        addSecureEndpoint("getState", this::getState);
        addSecureEndpoint("approve", this::approve);
        addSecureEndpoint("approveParcel", this::approveParcel);
        addSecureEndpoint("startApproval", this::startApproval);
        addSecureEndpoint("throw_error", this::throw_error);
    }

    private Binder throw_error(Binder binder, Session session) throws IOException {
        throw new IOException("just a test");
    }

    private Binder approve(Binder params, Session session) throws IOException, Quantiser.QuantiserException {
        checkNode();
        //System.out.println("Request to approve, package size: " + params.getBinaryOrThrow("packedItem").length);
        return Binder.of(
                "itemResult",
                node.registerItem(Contract.fromPackedTransaction(params.getBinaryOrThrow("packedItem")))
        );
    }

    private Binder approveParcel(Binder params, Session session) throws IOException, Quantiser.QuantiserException {
        checkNode();
        System.out.println("Request to approve parcel, package size: " + params.getBinaryOrThrow("packedItem").length);
        return Binder.of(
                "result",
                node.registerParcel(Parcel.unpack(params.getBinaryOrThrow("packedItem")))
        );
    }

    static AtomicInteger asyncStarts = new AtomicInteger();

    private Binder startApproval(final Binder params, Session session) throws IOException, Quantiser.QuantiserException {
        int n = asyncStarts.incrementAndGet();
        AtomicInteger k = new AtomicInteger();
        params.getListOrThrow("packedItems").forEach((item) ->
                es.execute(() -> {
                    try {
                        checkNode();
                        System.out.println("Request to start registration #"+n+":"+k.incrementAndGet());
                        node.registerItem(Contract.fromPackedTransaction(((Bytes)item).toArray()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
        );
        return new Binder();
    }

    private Binder getState(Binder params, Session session) throws CommandFailedException {
        checkNode();
        return Binder.of("itemResult",
                node.checkItem((HashId) params.get("itemId")));
    }

    private void checkNode() throws CommandFailedException {
        if (node == null) {
            throw new CommandFailedException(Errors.NOT_READY, "", "please call again after a while");
        }
    }

    static private Binder networkData = null;

    @Override
    public void on(String path, BasicHTTPService.Handler handler) {
        super.on(path, (request, response) -> {
            if (localCors) {
                Binder hh = response.getHeaders();
                hh.put("Access-Control-Allow-Origin", "*");
                hh.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                hh.put("Access-Control-Allow-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Age  nt,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range");
                hh.put("Access-Control-Expose-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range");
            }
            handler.handle(request, response);
        });
    }

    private Node node;

    public ItemCache getCache() {
        return cache;
    }

    public void setCache(ItemCache cache) {
        this.cache = cache;
    }

    public ParcelCache getParcelCache() {
        return parcelCache;
    }

    public void setParcelCache(ParcelCache cache) {
        this.parcelCache = cache;
    }

    public void setNetConfig(NetConfig netConfig) {
        this.netConfig = netConfig;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public boolean isLocalCors() {
        return localCors;
    }

    public void setLocalCors(boolean localCors) {
        this.localCors = localCors;
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
