/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.LocalNode;
import com.icodici.universa.node.Node;
import net.sergeych.farcall.Command;
import net.sergeych.farcall.Farcall;
import net.sergeych.tools.Binder;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * The class that exports {@link com.icodici.universa.node.LocalNode} to the ourside world using {@link
 * BitrustedConnector} listening to the specified port.
 */
public class BitrustedLocalAdapter {

    private static LogPrinter log = new LogPrinter("BTLA");
    private static ExecutorService pool = Executors.newFixedThreadPool(16);
    private final Future<?> server;
    private final LocalNode localNode;
    private final PrivateKey privateKey;
    private Map<HashId, Node> knownNodes;
    private final ServerSocket serverSocket;
    private boolean stop;

    public BitrustedLocalAdapter(LocalNode localNode,
                                 PrivateKey privateKey,
                                 Map<HashId, Node> knownNodes,
                                 int portToListen) throws IOException {
        this.localNode = localNode;
        this.privateKey = privateKey;
        this.knownNodes = knownNodes;
        log.d("node " + localNode.getId()+" will listen to "+portToListen);
        serverSocket = new ServerSocket(portToListen);
        server = pool.submit(() -> serveIncomingConnections());
    }

    private Object serveIncomingConnections() throws Exception {
        while (!stop) {
            Socket s = serverSocket.accept();
            pool.submit(() -> new Connection(s));
        }
        return null;
    }

    class Connection implements Farcall.Target {

        private final Farcall farcall;
        private Node remoteNode;

        @Override
        public Object onCommand(Command command) throws Exception {
            Binder params = Binder.from(command.getKeyParams());
            switch (command.getName()) {
                case "getItem":
                    return doGetItem(params);
                case "checkItem":
                    return doCheckItem(params);
                default:
                    throw new IllegalArgumentException("unknown command");

            }
        }

        private Object doGetItem(Binder params) throws IOException {
            HashId id = HashId.withDigest(params.getBinary("itemId"));
            Approvable item = localNode.getItem(id);
            log.d(remoteNode + " --> " + localNode + " getItem("+id+") : "+item);
            // Item must have Boss.Adapter for this code to work
            return item;
        }

        private Object doCheckItem(Binder params) throws IOException {
            HashId id = HashId.withDigest(params.getBinaryOrThrow("itemId"));
            ItemState state = ItemState.valueOf(params.getStringOrThrow("state"));
            boolean haveCopy = params.getBooleanOrThrow("haveCopy");
            // ItemResult has BOSS adapter, so it will work like charm
//            log.d("called " + localNode + ": " + id + ":" + state + ":" + haveCopy);
            return localNode.checkItem(remoteNode, id, state, haveCopy);
        }

        Connection(Socket socket) throws IOException, TimeoutException, InterruptedException {
            BitrustedConnector connector = new BitrustedConnector(privateKey,
                                                                  socket.getInputStream(),
                                                                  socket.getOutputStream());
            connector.connect(key -> {
                remoteNode = knownNodes.get(HashId.of(key));
                return remoteNode != null;
            });
            farcall = new Farcall(connector);
            farcall.asyncCommands();
            farcall.start(this);
            log.d(localNode.getId()+" established connection from " + remoteNode);
        }
    }
}
