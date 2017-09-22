/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Node;
import net.sergeych.farcall.Farcall;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * Interface to the remote Node instance using bitrusted protocol.
 */
public class BitrustedRemoteAdapter extends Node {

    private static LogPrinter log = new LogPrinter("BTRA");
    private final PrivateKey localKey;
    private final PublicKey remoteKey;
    private final int port;
    private final String host;
    private Farcall farcall;
    private Socket socket;
    private Object stateLock = new Object();

    public BitrustedRemoteAdapter(String remoteId, PrivateKey localKey, PublicKey remoteKey, String host, int port) {
        super(remoteId);
        this.localKey = localKey;
        this.remoteKey = remoteKey;
        this.port = port;
        this.host = host;
    }


    @Override
    public ItemResult checkItem(Node caller, HashId itemId, ItemState state, boolean haveCopy) throws IOException, InterruptedException {
        return inConnection(farcall -> {
//            log.d(getId()+" calling checkItem " + itemId + ":" + state + ":" + haveCopy);
            return farcall.sendKeyParams("checkItem",
                                         "itemId", itemId.getDigest(),
                                         "state", state.name(),
                                         "haveCopy", haveCopy)
                    .waitSuccess();
        });
    }

    @Override
    public Approvable getItem(HashId itemId) throws IOException, InterruptedException {
//        log.d(getId()+ ": calling getItem: " + itemId);
        return inConnection(farcall -> {
            Farcall.CommandResult commandResult = farcall.sendKeyParams("getItem",
                                                                        "itemId", itemId.getDigest());
            Object result = commandResult.waitSuccess();
            return (Approvable) result;
        });
    }

    @Override
    public void shutdown() {
        try {
            farcall.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private <T> T inConnection(Function<Farcall, T> block) throws IOException, InterruptedException {
        IOException last = null;
        for (int n = 0; n < 3; n++) {
            try {
                ensureConnected();
                return block.apply(farcall);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                synchronized (stateLock) {
                    try {
                        if (socket != null)
                            socket.close();
                    } catch (IOException iox) {
                    }
                    socket = null;
                    farcall = null;
//                e.printStackTrace();
                    last = (e instanceof IOException) ? (IOException) e : new IOException(e);
                    log.e("retry " + n + ", failed to perform in connection, " + e);
                }
            }
        }
        throw last;
    }

    private void ensureConnected() throws IOException, TimeoutException, InterruptedException {
        synchronized (stateLock) {
            if (socket == null) {
//                    log.i("connecting >>>"+getId());
                Exception lastException = null;
                try {
                    socket = new Socket(host, port);
                    BitrustedConnector connector = new BitrustedConnector(localKey, socket.getInputStream(), socket.getOutputStream());
                    connector.connect(packedKey -> Arrays.equals(packedKey, remoteKey.pack()));
                    farcall = new Farcall(connector);
                    farcall.asyncCommands();
                    farcall.start(command -> {
                        switch (command.getName()) {
                            case "ping":
                                return "pong";
                        }
                        return null;
                    });
                } catch (Exception e) {
                    if (socket != null) {
                        socket.close();
                        socket = null;
                    }
                    throw e;
                }
            }
        }
    }

    @Override
    public boolean checkConnection() {
        try {
            byte[] token = Do.randomBytes(127);
            Binder result = inConnection(f -> {
                return (Binder) f.sendKeyParams(
                        "ping",
                        "token", token
                ).waitSuccess();
            });
            if (result.getStringOrThrow("ping").equals("pong") &&
                    Arrays.equals(token, result.getBinaryOrThrow("token"))
                    ) {
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("---- exception in checkconnection ---");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return "RN<" + getId() + ">";
    }

    public void diconnectRemote() throws IOException, InterruptedException {
        inConnection(f -> (Object) f.send("disconnect").await(100));
    }
}
