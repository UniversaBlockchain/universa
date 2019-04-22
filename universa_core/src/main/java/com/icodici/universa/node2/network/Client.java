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
import com.icodici.universa.*;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ExtendedSignature;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.*;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.*;
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

    private final PublicKey nodePublicKey;

    List<Client> clients;

    private String version;
    private List<Binder> topology;

    /**
     *  Get topology of the network. Binders contain the following fields: number - node number, name - node name, direct_urls - urls to access node by IP, domain_urls - urls to access node by hostname if access by IP is impossible (web browser by https),  key - base64 of node's {@link PublicKey}.
     * @return binders list with network topology info.
     */

    public List<Binder> getTopology() {
        return topology;
    }

    /**
     * Get size of the network.
     * @return nodes count
     */
    public final int size() {
        return nodes.size();
    }

    /**
     * Ping network node by number
     * @param i index of node in topology (not node's number)
     * @return was the ping successful
     */

    public boolean ping(int i) {
        try {
            return getClient(i).ping();
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * Ping the node client is currently connected to
     * @return was the ping successful
     */

    public boolean ping() throws IOException {
        httpClient.command("sping");
        return true;
    }

    /**
     * Get the client connection to network node by number. Instance of {@link Client} provided by this method is unable to perform network-wise operations like {@link #getTopology()}, {@link #ping(int)} ()}, {@link #size()} etc
     * @param i index of node in topology (not node's number)
     * @return connected to node
     */

    public Client getClient(int i) throws IOException {
        Client c = clients.get(i);
        if (c == null) {
            NodeRecord r = nodes.get(i);
            c = new Client(r.url, clientPrivateKey, r.key, null);
            if(topology != null) {
                c.httpClient.nodeNumber = topology.get(i).getIntOrThrow("number");
            }
            clients.set(i, c);
        }
        return c;
    }

    /**
     * Set log levels for different node compononets: node,network и udp. Command requires network administrator key
     * @param node log level for node
     * @param network log level for network
     * @param udp log level for udp
     * @return dummy ItemResult
     */

    public ItemResult setVerboseLevel(int node, int network, int udp) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("setVerbose",
                    "node", VerboseLevel.intToString(node),
                    "network", VerboseLevel.intToString(network),
                    "udp", VerboseLevel.intToString(udp));

            Object ir = result.getOrThrow("itemResult");
            if (ir instanceof ItemResult)
                return (ItemResult) ir;

            if (ir instanceof String)
                System.out.println(">> " + ir);

            return ItemResult.UNDEFINED;
        });
    }

    @Deprecated
    public void setNodes(List<NodeRecord> nodes) {
        this.nodes = nodes;
        clients = new ArrayList<>(size());
        clients.addAll(Collections.nCopies(nodes.size(),null));
    }

    protected interface Executor<T> {
        T execute() throws Exception;
    }

    final BasicHttpClient httpClient;

    /**
     * Start new client protocol session. It doesn't load network configuration. Only creates client protocol session with given node
     * @param rootUrlString node url
     * @param clientPrivateKey client private key
     * @param nodePublicKey node key
     * @param session set to null or to the reconstructed instance
     * @throws IOException
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     */

    @Deprecated
    public Client(String rootUrlString, PrivateKey clientPrivateKey,
                  PublicKey nodePublicKey, BasicHttpClientSession session) throws IOException {
        httpClient = new BasicHttpClient(rootUrlString);
        this.clientPrivateKey = clientPrivateKey;
        this.nodePublicKey = nodePublicKey;
        httpClient.start(clientPrivateKey, nodePublicKey, session);
    }

    /**
     * Start new client protocol session. It doesn't load network configuration. Only creates client protocol session with given node

     * @param myPrivateKey client private key
     * @param nodeInfo node info specifying node public key and url
     * @param session set to null or to the reconstructed instance
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(PrivateKey myPrivateKey, NodeInfo nodeInfo, BasicHttpClientSession session) throws IOException {
        httpClient = new BasicHttpClient(nodeInfo.publicUrlString());
        this.clientPrivateKey = myPrivateKey;
        this.nodePublicKey = nodeInfo.getPublicKey();
        httpClient.start(myPrivateKey, nodeInfo.getPublicKey(), session);
    }

    /**
     * Start new client protocol session. It loads network configuration and creates client protocol session with random node

     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session) throws IOException {
        this(someNodeUrl, clientPrivateKey, session, false);
    }

    /**
     * Create new client protocol session. It loads network configuration and creates client protocol session with random node. Allows delayed start of http client

     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @param delayedStart indicates if start of http client should be delayed
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session, boolean delayedStart) throws IOException {
        this(someNodeUrl,clientPrivateKey,session,delayedStart,null);
    }

    /**
     * Create new client protocol session. It loads network configuration and creates client protocol session with random node. Allows delayed start of http client

     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @param delayedStart indicates if start of http client should be delayed
     * @param verifyWith key to verify loaded network info. Must be node's key user believe he connect's to.
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session, boolean delayedStart, PublicKey verifyWith) throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        loadNetworkFrom(someNodeUrl, verifyWith);

        clients = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            clients.add(null);
        }
        NodeRecord r = Do.sample(nodes);
        httpClient = new BasicHttpClient(r.url);
        this.nodePublicKey = r.key;
        if(!delayedStart)
            httpClient.start(clientPrivateKey, r.key, session);
    }


    /**
     * Creates client connection to network base on known topology. Updated topology of network is loaded and passes consensus-based check. Connection is then established to random node. Updated topology is stored in folder passed and is available for later use (by its name). The library contains a single named topology “mainnet” by default. It is used to connect Universa MainNet.

     * @param topologyInput name of known topology (without .json) or path to json-file containing topology.
     * @param topologyCacheDir path to where named topologies are stored for later use. Pass null to use standard ~/.universa/topology
     * @param clientPrivateKey private key for client connection.  Client key put limit to getState calls per minute and also restricts access to a some of operations available the network administrator only.
     * @throws IOException
     */

    public Client(String topologyInput, String topologyCacheDir, PrivateKey clientPrivateKey) throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        TopologyBuilder tb = new TopologyBuilder(topologyInput, topologyCacheDir);
        topology = tb.getTopology();
        version = tb.getVersion();

        for(int i = 0; i < topology.size();i++) {
            String keyString = topology.get(i).getString("key");
            topology.get(i).put("key", Base64.decodeCompactString(keyString));
            nodes.add(new NodeRecord(topology.get(i)));
            topology.get(i).put("key",keyString);
        }


        clients = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            clients.add(null);
        }
        int i = Do.randomInt(topology.size());
        int nodeNumber = topology.get(i).getIntOrThrow("number");
        NodeRecord r = nodes.get(i);
        httpClient = new BasicHttpClient(r.url);
        httpClient.nodeNumber = nodeNumber;
        this.nodePublicKey = r.key;
        httpClient.start(clientPrivateKey, r.key, null);

    }



    /**
     * Start http client (used with constructor passing delayedStart = true)

     * @param session set to null or to the reconstructed instance
     * @throws IOException
     */
    @Deprecated
    public void start(BasicHttpClientSession session) throws IOException {
        httpClient.start(clientPrivateKey, nodePublicKey, session);
    }

    /**
     * Restart client connection to node
     * @throws IOException
     */
    public void restart() throws IOException {
        httpClient.restart();
    }

    /**
     * Get url of the node this client is currently connected to
     *
     * @return url of the node
     */
    public String getUrl() {
        return httpClient.getUrl();
    }


    /**
     * Get current session of this client
     *
     * @return session
     */

    public BasicHttpClientSession getSession() throws IllegalStateException {
        return httpClient.getSession();
    }

    /**
     *
     * Get session of the client currently connected to node with given number
     *
     * @param i index of node in topology (not node's number)
     * @return session
     */
    public BasicHttpClientSession getSession(int i) throws IllegalStateException, IOException {
        return getClient(i).httpClient.getSession();
    }

    /**
     * Class that stores minimal node information such as url and public key
     */
    public class NodeRecord {
        public final String url;
        public final PublicKey key;

        private NodeRecord(Binder data) throws IOException {
            if(data.containsKey("direct_urls")) {
                List<String> directUrls = data.getListOrThrow("direct_urls");
                url = directUrls.get(0);
            } else {
                url = data.getStringOrThrow("url");
            }

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

    /**
     * Get list of nodes in
     * @return list of network nodes if constructor loading network configuration was used. Empty list otherwise
     * @deprecated use {@link #getTopology()}instead.
     */
    @Deprecated
    public List<NodeRecord> getNodes() {
        return nodes;
    }

    private List<NodeRecord> nodes = new ArrayList<>();

    /**
     * Get network version
     * @return version
     */
    public String getVersion() {
        return version;
    }

    private void loadNetworkFrom(String someNodeUrl, PublicKey verifyWith) throws IOException {
        URL url = new URL(someNodeUrl + "/" + (verifyWith == null ? "network" : "topology"));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
        connection.setConnectTimeout(10000);
        connection.setRequestMethod("GET");
        if (connection.getResponseCode() != 200)
            throw new IOException("failed to access " + url + ", reponseCode " + connection.getResponseCode());

        byte[] bytes = (Do.read(connection.getInputStream()));


        Binder bres = Boss.unpack(bytes)
                .getBinderOrThrow("response");

        List<NodeRecord> nodes = new ArrayList<>();
        List<Binder> topology = null;
        String version;

        if(verifyWith != null) {
            byte[] packedData = bres.getBinaryOrThrow("packed_data");
            byte[] signature = bres.getBinaryOrThrow("signature");
            if(ExtendedSignature.verify(verifyWith,signature,packedData) == null) {
                throw new IOException("failed to verify node " + url + ", with " + verifyWith);
            }
            bres = Boss.unpack(packedData);

            topology = bres.getListOrThrow("nodes");
        }
        version = bres.getStringOrThrow("version");
        for (Binder b : bres.getBinders("nodes"))
            nodes.add(new NodeRecord(b));

        if(topology != null) {
            for (Object o : topology) {
                Binder b = (Binder) o;
                b.put("key", new PublicKey(b.getBinaryOrThrow("key")));
            }
        }

        if(topology != null) {
            for (Object o : topology) {
                Binder b = (Binder) o;
                b.put("key", ((PublicKey) b.get("key")).packToBase64String());
            }
        }

        this.nodes = nodes;
        this.topology = topology;
        this.version = version;
    }

    /**
     * Register contract on the network (no payment). This operation requires either special client key or special network configuration that
     * allows free registrations. Method doesn't wait until registration is complete. If contract is known to the node already its status is
     * returned. Otherwise method returns either PENDING if registration have started or UNDEFINED if haven't.
     *
     * @param packed {@link com.icodici.universa.contract.TransactionPack} binary
     * @return registration initiation result
     * @throws ClientError
     */
    public ItemResult register(byte[] packed) throws ClientError {
        return register(packed, 0);
    }

    /**
     * Register contract on the network (no payment). This operation requires either special client key or special network configuration that
     * allows free registrations. Method starts registration and waits until either registration is complete or waiting time is over. Method
     * returns either one of pending statuses (PENDING/PENDING_POSITIVE/PENDING_NEGATIVE) if registration in progress or UNDEFINED if something
     * went wrokng registration haven't started or 'final' status - the result of registration
     *
     * @param packed {@link com.icodici.universa.contract.TransactionPack} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return result of registration or current state of registration (if wasn't finished yet)
     * @throws ClientError
     */
    public ItemResult register(byte[] packed, long millisToWait) throws ClientError {
        Object binderResult = protect(() -> httpClient.command("approve", "packedItem", packed)
                .get("itemResult"));
        if(binderResult instanceof ItemResult) {
            ItemResult lastResult = (ItemResult) binderResult;
            if (millisToWait > 0 && lastResult.state.isPending()) {
                Instant end = Instant.now().plusMillis(millisToWait);
                try {
                    Contract c = Contract.fromPackedTransaction(packed);
                    int interval = 1000;
                    while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                        Thread.currentThread().sleep(interval);
                        if (interval > 300)
                            interval -= 350;
                        lastResult = getState(c.getId());
                        //System.out.println("test: " + lastResult);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Quantiser.QuantiserException e) {
                    throw new ClientError(e);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return lastResult;
        }

        System.err.println("test: " + binderResult);
        return ItemResult.UNDEFINED;
    }

    /**
     * Register contract on the network using parcel (to provide payment). Method doesn't wait until registration is complete.
     * Method returns right away. It is then required to manually query parcel processing state using {@link #getParcelProcessingState(HashId)}
     * while it is {@link ParcelProcessingState#isProcessing()}. The status of the actual contract {@link Parcel#getPayload()} can be checked
     * with {@link #getState(HashId)} then. Getting {@link ItemState#UNDEFINED} on payload contract means something went wrong with payment.
     * Either it is not valid or insufficient
     * @param packed {@link Parcel} binary
     * @return if registration initiation was successful
     * @throws ClientError
     */
    public boolean registerParcel(byte[] packed) throws ClientError {
        try {
            registerParcelWithState(packed, 0);
            return true;
        } catch (ClientError e) {
            if (e.getErrorRecord().getError() == Errors.COMMAND_PENDING)
                return true;
            else
                return false;
        }
    }

    /**
     * Register contract on the network with parcel (includes payment)
     * @param packed {@link Parcel} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return result of registration
     * @throws ClientError
     * @deprecated use {@link #registerParcelWithState(byte[], long)} instead.
     */
    @Deprecated
    public boolean registerParcel(byte[] packed, long millisToWait) throws ClientError {
        try {
            registerParcelWithState(packed, millisToWait);
            return true;
        } catch (ClientError e) {
            if (e.getErrorRecord().getError() == Errors.COMMAND_PENDING)
                return true;
            else
                return false;
        }
    }

    /**
     * Register contract on the network using parcel. Version of {@link #registerParcel(byte[])} that waits until registration is complete
     * for a given amount of time.
     *
     * @param packed {@link Parcel} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return either final result of registration or last known status of registration. Getting {@link ItemState#UNDEFINED} means either
     * payment wasn't processed yet or something is wrong with it (invalid or insufficient)
     * @throws ClientError
     */
    public ItemResult registerParcelWithState(byte[] packed, long millisToWait) throws ClientError {
        Object result = protect(() -> httpClient.command("approveParcel", "packedItem", packed)
                .get("result"));
        if(result instanceof String) {
//            System.out.println(">> registerParcel " + result);
            throw new ClientError(Errors.FAILURE, "registerParcel", "approveParcel returns: " + result);
        } else {
            if (millisToWait > 0) {
                Instant end = Instant.now().plusMillis(millisToWait);
                try {
                    Parcel parcel = Parcel.unpack(packed);
                    ParcelProcessingState pState = getParcelProcessingState(parcel.getId());
                    int interval = 1000;
                    while (Instant.now().isBefore(end) && pState.isProcessing()) {
//                        System.out.println("parcel state is: " + pState);
                        Thread.currentThread().sleep(interval);
                        interval -= 350;
                        interval = Math.max(interval, 300);
                        pState = getParcelProcessingState(parcel.getId());
                    }
//                    System.out.println("parcel state is: " + pState);
                    ItemResult lastResult = getState(parcel.getPayloadContract().getId());
                    while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                        Thread.currentThread().sleep(interval);
                        interval -= 350;
                        interval = Math.max(interval, 300);
                        lastResult = getState(parcel.getPayloadContract().getId());
                        System.out.println("test: " + lastResult);
                    }
                    return lastResult;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new ClientError(e);
                } catch (Quantiser.QuantiserException e) {
                    throw new ClientError(e);
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new ClientError(e);
                }
            } else {
                throw new ClientError(Errors.COMMAND_PENDING, "registerParcel", "waiting time is up, please update payload state later");
            }
        }
    }


    /**
     * Get state of the contract on the node client is currently connected to. Note: limits are applied to number of {@link #getState(Approvable)} calls
     * per minute per client key. Make sure method is not called too ofter with the same client connection.
     * @param item to get state of
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */

    public final ItemResult getState(@NonNull Approvable item) throws ClientError {
        return getState(item.getId());
    }

    /**
     * Get state of the contract on the node client is currently connected to by its id. Note: limits are applied to number of {@link #getState(Approvable)} calls
     * per minute per client key. Make sure method is not called too ofter with the same client connection.
     * @param itemId to get state by
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */

    public ItemResult getState(HashId itemId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getState",
                    "itemId", itemId);

            Object ir = result.getOrThrow("itemResult");
            if (ir instanceof ItemResult)
                return (ItemResult) ir;

            if (ir instanceof String)
                System.out.println(">> " + ir);

            return ItemResult.UNDEFINED;
        });
    }

    /**
     * Force synchronization with the rest of the network of given item by its id. May require special client key / network configuration
     * @param itemId to synchronize
     * @return known {@link ItemState} before synchronization. Query the state later to get it synchronized
     * @throws ClientError
     */
    public ItemResult resyncItem(HashId itemId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("resyncItem",
                    "itemId", itemId);

            Object ir = result.getOrThrow("itemResult");
            if (ir instanceof ItemResult)
                return (ItemResult) ir;

            if (ir instanceof String)
                System.out.println(">> " + ir);

            return ItemResult.UNDEFINED;
        });
    }

    /**
     * Get statistics of the node. Accessible to node owners (with node {@link PrivateKey} as session key) and network admins
     * @return dictionary containing uptime, ledger size, and number of contracts approved for a minute, hour and since restart
     * @throws ClientError
     */
    public Binder getStats() throws ClientError {
        return getStats(null);
    }

    /**
     * Get extended statistics of the node including by-day payments in "U". Accessible to node owners (with node {@link PrivateKey} as session key) and network admins
     * @return dictionary containing uptime, ledger size, and number of contracts approved for a minute, hour and since restart. it also contains by-day payments information
     * @param showPaymentsDays the number of days to provide payments volume for
     * @throws ClientError
     */
    public Binder getStats(Integer showPaymentsDays) throws ClientError {
        return protect(() -> httpClient.command("getStats","showDays",showPaymentsDays));
    }

    /**
     * Get processing state of given parcel
     * @param parcelId id of the parcel to get state of
     * @return processing state of the parcel
     * @throws ClientError
     */
    public ParcelProcessingState getParcelProcessingState(HashId parcelId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getParcelProcessingState",
                    "parcelId", parcelId);

            Object ps = result.getOrThrow("processingState");
            if (ps instanceof ParcelProcessingState)
                return (ParcelProcessingState) ps;

            return ParcelProcessingState.valueOf(result.getBinder("processingState").getStringOrThrow("state"));
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
            return (ItemResult) httpClient.command("getState",
                                               "itemId", itemId).getOrThrow("itemResult");
        });
    }


    /**
     * Get state of the contract on the network. This method uses multiple connections with different nodes to collects statuses.
     * Note: limits are applied to number of {@link #getState(Approvable)} calls per minute per client key. Make sure method is
     * not called too ofter with the same client connection.
     *
     * @param reporter reported used to log current status, errors from nodes if any etc
     * @param itemId to get state by
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */

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
//                            System.out.println(c.getUrl()+" #"+c.getNodeNumber()+" -> "+r);
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

            consensusFound.await(10000);

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

    /**
     * Get number of node cliet connection is established with
     * @return number of node
     */
    public int getNodeNumber() {
        return httpClient.getNodeNumber();
    }
    /**
     * Execude custom command on the node
     * @param name name of the command
     * @param params parameters of the command
     * @return execution result
     */
    public Binder command(String name, Object... params) throws IOException {
        return httpClient.command(name, params);
    }

    public BasicHttpClient.Answer request(String name, Object... params) throws IOException {
        return httpClient.request(name, params);
    }

    protected final <T> T protect(Executor<T> e) throws ClientError {
        try {
            return e.execute();
        } catch (Exception ex) {
            //ex.printStackTrace();
            throw new ClientError(ex);
        }
    }

    private int positiveConsensus = -1;

    //TODO: get it from the node
    public int getPositiveConsensus() {
        if (positiveConsensus < 1)
            positiveConsensus = (int) Math.floor(nodes.size() * 0.90);
        return positiveConsensus;
    }

    /**
     * Get current network rate for operating SLOT1 contracts
     * @return kilobyte-days per U rate
     * @throws ClientError
     */
    public Decimal storageGetRate() throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("storageGetRate");
            String U = result.getStringOrThrow("U");
            return new Decimal(U);
        });
    }

    /**
     * Look for state data of slot contract
     * @param slotId slot contract id
     * @return {@link Binder} containing state data of slot contract
     * @throws ClientError
     */

    public Binder querySlotInfo(HashId slotId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("querySlotInfo", "slot_id", slotId.getDigest());
            Binder binder = result.getBinder("slot_state", null);
            return binder;
        });
    }

    /**
     * Look for contract stored by given slot contract id. Contract is specified by either id or origin
     * @param slotId id of slot contract storing queried contract
     * @param originId queried contract origin
     * @param contractId queried contract id
     * @return {@link com.icodici.universa.contract.TransactionPack} of stored contract or null
     * @throws ClientError
     */
    public byte[] queryContract(HashId slotId, HashId originId, HashId contractId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryContract",
                    "slot_id", slotId.getDigest(),
                    "origin_id", originId==null?null:originId.getDigest(),
                    "contract_id", contractId==null?null:contractId.getDigest()
            );
            try {
                Bytes bytes = result.getBytesOrThrow("contract");
                return bytes.getData();
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }

    /**
     * Get current network rate for operating UNS1 contracts
     * @return name-days per U rate
     * @throws ClientError
     */
    public Decimal unsRate() throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("unsRate");
            String U = result.getStringOrThrow("U");
            return new Decimal(U);
        });
    }

    /**
     * Look for the name assosiated with a given origin
     * @param origin to look for
     * @return {@link Binder} containing name, description and url associated with origin or null
     * @throws ClientError
     */
    public Binder queryNameRecord(HashId origin) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryNameRecord", "origin", origin.getDigest());
            return result;
        });
    }

    /**
     * Look for the name assosiated with a given address
     * @param address to look for
     * @return {@link Binder} containing name, description and url associated with address or null
     * @throws ClientError
     */
    public Binder queryNameRecord(String address) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryNameRecord", "address", address);
            return result;
        });
    }

    /**
     * Look for {@link com.icodici.universa.contract.services.UnsContract} that registers the name given
     * @param name to look for
     * @return packed {@link com.icodici.universa.contract.services.UnsContract} if found. Otherwise null
     * @throws ClientError
     */
    public byte[] queryNameContract(String name) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryNameContract", "name", name);
            try {
                Bytes bytes = result.getBytesOrThrow("packedContract");
                return bytes.getData();
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }

    /**
     * Get the contract with the given contract id.
     * @param itemId contract hash
     * @return contract with the given contract id
     * @throws ClientError
     */
    public byte[] getBody(HashId itemId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getBody", "itemId", itemId);
            try {
                Bytes bytes = result.getBytesOrThrow("packedContract");
                return bytes.getData();
            } catch (IllegalArgumentException e) {
                return null;
            }
        });
    }

    /**
     * Get the body of active contract with the given origin (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param origin contract origin
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */

    public Binder getContract(HashId origin, int limit, int offset) throws ClientError {
        return getContract(origin,null,limit,offset);
    }

    /**
     * Get the body of active contract with the given origin (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param origin contract origin
     * @param tags tags to search for
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin, Binder tags, int limit, int offset) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getContract", "origin", origin, "limit", limit, "offset", offset, "tags", tags);
            if (result.size() > 0) {
                if (result.containsKey("contractIds")) {
                    List<byte[]> contractIds = new ArrayList<>();
                    List<HashId> ids = new ArrayList<>();
                    for (Object id: result.getListOrThrow(("contractIds"))) {
                        contractIds.add(((Bytes) id).getData());
                        ids.add(HashId.withDigest(((Bytes) id).getData()));
                    }
                    result.put("contractIds", contractIds);
                    result.put("ids", ids);
                }
                
                if(result.containsKey("packedContract")) {
                    TransactionPack tp = TransactionPack.unpack(result.getBinaryOrThrow("packedContract"));
                    HashId id = (HashId) result.getListOrThrow("ids").get(0);
                    if(tp.getContract().getId().equals(id)) {
                        result.put("contract", tp.getContract());
                    } else {
                        result.put("contract", tp.getSubItem(id));
                    }

                }
                return result;
            } else
                return null;
        });
    }

    /**
     * Get the body of active contract with the given origin (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param origin contract origin
     * @param limit of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin, int limit) throws ClientError {
        return getContract(origin,limit,0);
    }

    /**
     * Get the body of active contract with the given parent (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param parent id of parent contract
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, int limit, int offset) throws ClientError {
        return getChildren(parent,null,limit,offset);
    }

    /**
     * Get the body of active contract with the given parent (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param parent id of parent contract
     * @param tags tags to search for (state.data.search_tags.key=value)
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, Map<String,String> tags, int limit, int offset) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getContract", "parent", parent, "limit", limit,"offset", offset, "tags", tags);
            if (result.size() > 0) {
                if (result.containsKey("contractIds")) {
                    List<byte[]> contractIds = new ArrayList<>();
                    List<HashId> ids = new ArrayList<>();
                    for (Object id: result.getListOrThrow(("contractIds"))) {
                        contractIds.add(((Bytes) id).getData());
                        ids.add(HashId.withDigest(((Bytes) id).getData()));
                    }
                    result.put("contractIds", contractIds);
                    result.put("ids", ids);
                }

                if(result.containsKey("packedContract")) {
                    TransactionPack tp = TransactionPack.unpack(result.getBinaryOrThrow("packedContract"));
                    HashId id = (HashId) result.getListOrThrow("ids").get(0);
                    if(tp.getContract().getId().equals(id)) {
                        result.put("contract", tp.getContract());
                    } else {
                        result.put("contract", tp.getSubItem(id));
                    }

                }
                return result;
            } else
                return null;
        });
    }

    /**
     * Get the body of active contract with the given parent (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param parent id of parent contract
     * @param limit of list items
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, int limit) throws ClientError {
        return getChildren(parent,limit,0);
    }

    /**
     * Get the body of active contract with the given origin (if one active contract is returned),
     * or list of IDs active contracts (if there are more than one).
     * List of IDs contracts contains contract hash digests as byte arrays.
     * @param origin contract origin
     * @return {@link Binder} containing packed transaction or limited list of IDs active contracts or null (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getContract", "origin", origin);
            if (result.size() > 0) {
                if (result.containsKey("contractIds")) {
                    List<byte[]> contractIds = new ArrayList<>();
                    for (Object id: result.getListOrThrow(("contractIds")))
                        contractIds.add(((Bytes)id).getData());
                    result.put("contractIds", contractIds);
                }
                return result;
            } else
                return null;
        });
    }

    /**
     * Get current network rate for operating FOLLOWER1 contracts
     * @return {@link Binder} containing origins-days per U rate and callback price in U
     * @throws ClientError
     */
    public Binder followerGetRate() throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("followerGetRate");
            return result;
        });
    }

    /**
     * Look for state data of follower contract
     * @param followerId follower contract id
     * @return {@link Binder} containing state data of follower contract
     * @throws ClientError
     */

    public Binder queryFollowerInfo(HashId followerId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryFollowerInfo", "follower_id", followerId.getDigest());
            Binder binder = result.getBinder("follower_state", null);
            return binder;
        });
    }
}
