/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Client {

    static {
        Config.forceInit(ItemResult.class);
        Config.forceInit(ErrorRecord.class);
        Config.forceInit(HashId.class);
        Config.forceInit(Contract.class);
    }

    private final PrivateKey clientPrivateKey;

    List<Client> clients;

    private String version;

    public final int size() {
        return nodes.size();
    }

    public boolean ping(int i) {
        try {
            return getClient(i).ping();
        } catch (Exception e) {
        }
        return false;
    }

    private boolean ping() throws IOException {
        client.command("sping");
        return true;
    }

    Client getClient(int i) throws IOException {
        Client c = clients.get(i);
        if (c == null) {
            NodeRecord r = nodes.get(i);
            c = new Client(r.url, clientPrivateKey, r.key);
            clients.set(i, c);
        }
        return c;
    }

    protected interface Executor<T> {
        T execute() throws Exception;
    }

    final BasicHttpClient client;

    public Client(String rootUrlString, PrivateKey clientPrivateKey,
                  PublicKey nodePublicKey) throws IOException {
        client = new BasicHttpClient(rootUrlString);
        this.clientPrivateKey = clientPrivateKey;
        client.start(clientPrivateKey, nodePublicKey);
    }

    public Client(PrivateKey myPrivateKey, NodeInfo nodeInfo) throws IOException {
        client = new BasicHttpClient(nodeInfo.publicUrlString());
        this.clientPrivateKey = myPrivateKey;
        client.start(myPrivateKey, nodeInfo.getPublicKey());
    }

    public Client(String someNodeUrl, PrivateKey clientPrivateKey) throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        loadNetworkFrom(someNodeUrl);
        clients = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            clients.add(null);
        }
        NodeRecord r = Do.sample(nodes);
        client = new BasicHttpClient(r.url);
        client.start(clientPrivateKey, r.key);
    }

    public String getUrl() {
        return client.getUrl();
    }

    public class NodeRecord {
        public final String url;
        public final PublicKey key;

        private NodeRecord(Binder data) throws IOException {
            url = data.getStringOrThrow("url");
            try {
                key = new PublicKey(data.getBinaryOrThrow("key"));
            } catch (EncryptionError encryptionError) {
                throw new IOException("failed to construct node public key", encryptionError);
            }
        }

        @Override
        public String toString() {
            return "Node(" + url + "," + key + ")";
        }
    }

    public List<NodeRecord> getNodes() {
        return nodes;
    }

    private List<NodeRecord> nodes = new ArrayList<>();

    public String getVersion() {
        return version;
    }

    private void loadNetworkFrom(String someNodeUrl) throws IOException {
        URL url = new URL(someNodeUrl + "/network");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
        connection.setRequestMethod("GET");

        if (connection.getResponseCode() != 200)
            throw new IOException("failed to access " + url + ", reponseCode " + connection.getResponseCode());

        Binder bres = Boss.unpack((Do.read(connection.getInputStream())))
                .getBinderOrThrow("response");
        nodes.clear();
        this.version = bres.getStringOrThrow("version");

        for (Binder b : bres.getBinders("nodes"))
            nodes.add(new NodeRecord(b));
    }

    public ItemResult register(byte[] packed) throws ClientError {
        return register(packed, 0);
    }

    public ItemResult register(byte[] packed, long millisToWait) throws ClientError {
        ItemResult lastResult = protect(() -> (ItemResult) client.command("approve", "packedItem", packed)
                .get("itemResult"));
        if (millisToWait > 0 && lastResult.state.isPending()) {
            Instant end = Instant.now().plusMillis(millisToWait);
            try {
                Contract c = Contract.fromPackedTransaction(packed);
                while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                    Thread.currentThread().sleep(100);
                    lastResult = getState(c.getId());
                    System.out.println("test: " + lastResult);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return lastResult;
    }

    public final ItemResult getState(@NonNull Approvable item) throws ClientError {
        return getState(item.getId());
    }

    public ItemResult getState(HashId itemId) throws ClientError {
        return protect(() -> {
//            for (int i = 0; i < nodes.size(); i++) {
//                System.out.println("checking node " + i);
//                ItemResult r = getClient(i).command("getState",
//                                                    "itemId", itemId).getOrThrow("itemResult");
//                System.out.println(">> " + r);
//            }
            return (ItemResult) client.command("getState",
                                               "itemId", itemId).getOrThrow("itemResult");
        });
    }

    public ItemResult getExtendedState(HashId itemId, int nodeNumber) throws ClientError {
        return protect(() -> {
//            for (int i = 0; i < nodes.size(); i++) {
//                System.out.println("checking node " + i);
//                ItemResult r = getClient(i).command("getState",
//                                                    "itemId", itemId).getOrThrow("itemResult");
//                System.out.println(">> " + r);
//            }
            return (ItemResult) client.command("getState",
                                               "itemId", itemId).getOrThrow("itemResult");
        });
    }

    public ItemResult getState(HashId itemId, Reporter reporter) throws ClientError {
        final ExecutorService pool = Executors.newCachedThreadPool();
        final List<ErrorRecord> errors = new ArrayList<>();

        final AsyncEvent<Void> consensusFound = new AsyncEvent<>();
        final int checkConsensus = getNodes().size() / 3;

        final AtomicInteger nodesLeft = new AtomicInteger(nodes.size());

        return protect(() -> {
            final Map<ItemState, List<ItemResult>> states = new HashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                final int nn = i;
                pool.submit(() -> {
                    for (int retry = 0; retry < 5; retry++) {
//                        reporter.verbose("trying "+reporter+" for node "+nn);
                        try {
                            Client c = getClient(nn);
                            ItemResult r = c.command("getState", "itemId", itemId).getOrThrow("itemResult");
                            r.meta.put("url", c.getNodeNumber());
                            synchronized (states) {
                                List<ItemResult> list = states.get(r.state);
                                if (list == null) {
                                    list = new ArrayList();
                                    states.put(r.state, list);
                                }
                                list.add(r);
                                if (r.errors.size() > 0)
                                    reporter.warning("errors from " + c.getNodeNumber() + ": " + r.errors);
                                break;
                            }
                        } catch (IOException e) {
//                            reporter.warning("can't get answer from node " + nn + ", retry #" + retry + ": " + e);
                        }
                    }
                    // Now we should check the consensus
                    states.forEach((itemState, itemResults) -> {
                        if (itemResults.size() >= checkConsensus)
                            if (itemResults.size() >= checkConsensus) {
                                consensusFound.fire();
                                return;
                            }
                    });
                    if (nodesLeft.decrementAndGet() < 1)
                        consensusFound.fire();
                });
            }

            consensusFound.await(5000);

            pool.shutdownNow();

            final ItemResult consensus[] = new ItemResult[1];
            states.forEach((itemState, itemResults) -> {
                if (itemResults.size() >= checkConsensus)
                    consensus[0] = itemResults.get(0);
            });
            if (consensus[0] != null)
                reporter.message("State consensus found:" + consensus[0]);
            else {
                reporter.warning("no consensus found " + states.size());
            }
            if (states.size() > 1) {
                states.entrySet().stream()
                        .sorted(Comparator.comparingInt(o -> o.getValue().size()))
                        .forEach(kv -> {
                            List<ItemResult> itemResults = kv.getValue();
                            reporter.message("" + kv.getKey() + ": " + itemResults.size() + ": " +
                                                     itemResults.stream()
                                                             .map(x -> x.meta.getStringOrThrow("url"))
                                                             .collect(Collectors.toSet())
                            );
                        });
            }
            return consensus[0];
        });
    }

    private int getNodeNumber() {
        return client.getNodeNumber();
    }

    public Binder command(String name, Object... params) throws IOException {
        return client.command(name, params);
    }

    public BasicHttpClient.Answer request(String name, Object... params) throws IOException {
        return client.request(name, params);
    }

    protected final <T> T protect(Executor<T> e) throws ClientError {
        try {
            return e.execute();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ClientError(ex);
        }
    }

    private int positiveConsensus = -1;

    public int getPositiveConsensus() {
        if (positiveConsensus < 1)
            positiveConsensus = (int) Math.floor(nodes.size() * 0.90);
        return positiveConsensus;
    }

}
