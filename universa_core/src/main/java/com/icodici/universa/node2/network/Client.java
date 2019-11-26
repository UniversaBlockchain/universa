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
import com.icodici.universa.contract.*;
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
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Client {


    static {
        Config.forceInit(NodeConfigProvider.class);
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
     * Get the topology of the network.
     *
     * The {@link Binder}s in the result list contain the following fields:
     * "number" - node number,
     * "name" - node name,
     * "direct_urls" - the urls to access the node by IP (directly),
     * "domain_urls" - the urls to access node by hostname if the access by IP is impossible
     * (for example, inside the web browser for https access),
     * "key" - {@link PublicKey} of the node, Base64-encoded.
     *
     * @return the list with the network topology info
     */
    public List<Binder> getTopology() {
        return topology;
    }

    /**
     * Get the size of the network.
     *
     * @return nodes count
     */
    public final int size() {
        return nodes.size();
    }

    /**
     * Ping the network node by its index.
     *
     * @param i index of node in topology (not the node's number)
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
     * Ping the currently connected node.
     *
     * @return was the ping successful
     */
    public boolean ping() throws IOException {
        httpClient.command("sping");
        return true;
    }

    /**
     * Get the client connection to network node (by the node index).
     *
     * The instance of {@link Client} returned by this method will be unable to perform network-wise operations
     * like {@link #getTopology()}, {@link #ping(int)} ()}, {@link #size()} etc.
     *
     * @param i index of node in topology (not the node number)
     * @return connected to node
     */
    public Client getClient(int i) throws IOException {
        Client c = clients.get(i);
        if (c == null) {
            NodeRecord r = nodes.get(i);
            c = new Client(r.url, clientPrivateKey, r.key, null);
            if (topology != null) {
                c.httpClient.nodeNumber = topology.get(i).getIntOrThrow("number");
            }
            clients.set(i, c);
        }
        return c;
    }

    /**
     * Set log levels for different node components: node, network и udp.
     *
     * The command requires the network administrator key to use.
     *
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

    public Binder getServiceContracts() throws ClientError {
        return protect(() -> {
            Binder answer = httpClient.command("getServiceContracts");
            return answer.getBinderOrThrow("contracts");
        });
    }

    protected interface Executor<T> {
        T execute() throws Exception;
    }

    final BasicHttpClient httpClient;

    /**
     * Start the new client protocol session.
     * This method doesn't load the network configuration.
     * It only creates the client protocol session with the given node.
     *
     * @param rootUrlString node url
     * @param clientPrivateKey client private key
     * @param nodePublicKey node key
     * @param session set to null or to the reconstructed instance
     * @throws IOException
     */
    public Client(String rootUrlString, PrivateKey clientPrivateKey,
                  PublicKey nodePublicKey, BasicHttpClientSession session) throws IOException {
        httpClient = new BasicHttpClient(rootUrlString);
        this.clientPrivateKey = clientPrivateKey;
        this.nodePublicKey = nodePublicKey;
        httpClient.start(clientPrivateKey, nodePublicKey, session);
    }

    /**
     * Start the new client protocol session.
     * This method doesn't load the network configuration.
     * It only creates the client protocol session with the given node.
     *
     * @param myPrivateKey client private key
     * @param nodeInfo node info specifying node public key and url
     * @param session set to null or to the reconstructed instance
     * @deprecated use {@link #Client(String, String, PrivateKey)} instead.
     * @throws IOException
     */
    @Deprecated
    public Client(PrivateKey myPrivateKey, NodeInfo nodeInfo, BasicHttpClientSession session)
            throws IOException {
        httpClient = new BasicHttpClient(nodeInfo.publicUrlString());
        this.clientPrivateKey = myPrivateKey;
        this.nodePublicKey = nodeInfo.getPublicKey();
        httpClient.start(myPrivateKey, nodeInfo.getPublicKey(), session);
    }

    /**
     * Start the new client protocol session.
     * This method loads the network configuration and creates the client protocol session with some random node.
     *
     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @deprecated use {@link #Client(String, String, PrivateKey)} instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session)
            throws IOException {
        this(someNodeUrl, clientPrivateKey, session, false);
    }

    /**
     * Start the new client protocol session.
     * This method loads the network configuration and creates the client protocol session with random node.
     * Allows delayed start of http client.
     *
     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @param delayedStart indicates if start of http client should be delayed
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session, boolean delayedStart)
            throws IOException {
        this(someNodeUrl,clientPrivateKey,session,delayedStart,null);
    }

    /**
     * Start the new client protocol session.
     * This method loads the network configuration and creates the client protocol session with random node.
     * Allows delayed start of http client.
     *
     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @param delayedStart indicates if start of http client should be delayed
     * @param verifyWith key to verify loaded network info. Must be the key of the node,
     *                   for which user is attempting to connect
     * @deprecated use {@link #Client(String, String, PrivateKey)}instead.
     * @throws IOException
     */
    @Deprecated
    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session,
                  boolean delayedStart, PublicKey verifyWith)
            throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        loadNetworkFrom(someNodeUrl, verifyWith);

        clients = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            clients.add(null);
        }
        NodeRecord r = Do.sample(nodes);
        httpClient = new BasicHttpClient(r.url);
        this.nodePublicKey = r.key;
        if (!delayedStart)
            httpClient.start(clientPrivateKey, r.key, session);
    }


    /**
     * Start the new client protocol session, according to the known topology.
     * The last known network topology is loaded and then being checked according to the consensus.
     * The connection is then established to random node.
     * The updated topology is stored in the file format in some folder (given as an argument)
     * and is available for later use (by its name).
     * The library contains a single named topology “mainnet” by default, it is used to connect Universa MainNet;
     * you can pass it to use by default name.
     *
     * @param topologyInput name of known topology (without .json) or path to json-file containing topology.
     *                      Pass "mainnet" to use the default topology.
     * @param topologyCacheDir path where the named topologies are stored for later use. Pass null to use the standard
     *                         ~/.universa/topology path
     * @param clientPrivateKey private key for client connection.
     *                         The client key defines the limit of getState calls per minute and also restricts
     *                         access to a some of operations available the network administrator only.
     * @throws IOException
     */
    public Client(String topologyInput, String topologyCacheDir, PrivateKey clientPrivateKey)
            throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        TopologyBuilder tb = new TopologyBuilder(topologyInput, topologyCacheDir);
        topology = tb.getTopology();
        version = tb.getVersion();

        for (Binder topologyItem: topology) {
            String keyString = topologyItem.getString("key");
            topologyItem.put("key", Base64.decodeCompactString(keyString));
            nodes.add(new NodeRecord(topologyItem));
            topologyItem.put("key", keyString);
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
     *
     * @param session set to null or to the reconstructed instance
     * @throws IOException
     */
    @Deprecated
    public void start(BasicHttpClientSession session) throws IOException {
        httpClient.start(clientPrivateKey, nodePublicKey, session);
    }

    /**
     * Restart the client connection to node
     *
     * @throws IOException
     */
    public void restart() throws IOException {
        httpClient.restart();
    }

    /**
     * Get url of the currently connected node.
     *
     * @return url of the node
     */
    public String getUrl() {
        return httpClient.getUrl();
    }

    /**
     * Get the current session of this client.
     *
     * @return session
     */
    public BasicHttpClientSession getSession() throws IllegalStateException {
        return httpClient.getSession();
    }

    /**
     * Get the client session of the client for the connection to some node, by the node index.getState
     *
     * @param i index of node in topology (not the node number)
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
            if (data.containsKey("direct_urls")) {
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
     * Get the list of known nodes.
     *
     * @return list of network nodes if constructor loading network configuration was used. Empty list otherwise
     * @deprecated use {@link #getTopology()}instead.
     */
    @Deprecated
    public List<NodeRecord> getNodes() {
        return nodes;
    }

    private List<NodeRecord> nodes = new ArrayList<>();

    /**
     * Get the network version.
     *
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

        if (verifyWith != null) {
            byte[] packedData = bres.getBinaryOrThrow("packed_data");
            byte[] signature = bres.getBinaryOrThrow("signature");
            if (ExtendedSignature.verify(verifyWith, signature, packedData) == null) {
                throw new IOException("failed to verify node " + url + ", with " + verifyWith);
            }
            bres = Boss.unpack(packedData);

            topology = bres.getListOrThrow("nodes");
        }
        version = bres.getStringOrThrow("version");
        for (Binder b : bres.getBinders("nodes"))
            nodes.add(new NodeRecord(b));

        if (topology != null) {
            for (Object o : topology) {
                Binder b = (Binder) o;
                b.put("key", new PublicKey(b.getBinaryOrThrow("key")));
            }
        }

        if (topology != null) {
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
     * Register contract on the network (no payment).
     * This operation requires either special client key or special network configuration that
     * allows free registrations.
     * This method doesn't wait until registration is complete. If contract is known to the node already its status is
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
     * Register the contract on the network (no payment).
     * This operation requires either special client key or special network configuration that
     * allows free registrations.
     * This method starts the registration and waits until either registration is complete or waiting time is over.
     * The method returns either one of pending statuses (PENDING/PENDING_POSITIVE/PENDING_NEGATIVE)
     * if the registration in progress; or UNDEFINED if something went wrong like registration haven't started;
     * or 'final' status - the result of registration
     *
     * @param packed {@link com.icodici.universa.contract.TransactionPack} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return result of registration or current state of registration (if wasn't finished yet)
     * @throws ClientError
     */
    public ItemResult register(byte[] packed, long millisToWait) throws ClientError {
        Object binderResult = protect(() -> httpClient.command("approve", "packedItem", packed)
                .get("itemResult"));
        if (binderResult instanceof ItemResult) {
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
     * Register the contract on the network using parcel (to provide payment).
     * This method doesn't wait until registration is complete and returns right away.
     * The caller is then required to manually query the parcel processing state
     * using {@link #getParcelProcessingState(HashId)}
     * while it is {@link ParcelProcessingState#isProcessing()}.
     * The status of the actual contract {@link Parcel#getPayload()} can be checked
     * with {@link #getState(HashId)} then.
     * Getting {@link ItemState#UNDEFINED} on payload contract means something went wrong with payment:
     * either it is not valid or insufficient U.
     *
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
     * Register the contract on the network with parcel (to provide payment).
     *
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
     * Register the contract on the network using parcel (to provide payment).
     * Version of {@link #registerParcel(byte[])} that waits until registration is complete
     * for a given amount of time.
     *
     * @param packed {@link Parcel} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return either final result of registration or last known status of registration.
     * Getting {@link ItemState#UNDEFINED} means either
     * payment wasn't processed yet or something is wrong with it (invalid or insufficient U)
     * @throws ClientError
     */
    public ItemResult registerParcelWithState(byte[] packed, long millisToWait) throws ClientError {
        Object result = protect(() -> httpClient.command("approveParcel", "packedItem", packed)
                .get("result"));
        if (result instanceof String) {
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
                throw new ClientError(Errors.COMMAND_PENDING, "registerParcel",
                        "waiting time is up, please update payload state later");
            }
        }
    }

    /**
     * Send the PaidOperation to network and wait for it processing is complete.
     * @param packed {@ling PaidOperation} binary
     * @param millisToWait maximum time to wait
     * @return TODO: ???
     * @throws ClientError
     */
    public ItemResult registerPaidOperationWithState(byte[] packed, long millisToWait) throws ClientError {
        Object result = protect(() -> httpClient.command("approvePaidOperation", "packedItem", packed)
                .get("result"));
        if (result instanceof String) {
            throw new ClientError(Errors.FAILURE, "registerPaidOperationWithState", "approvePaidOperation returns: " + result);
        } else {
            if (millisToWait > 0) {
                Instant end = Instant.now().plusMillis(millisToWait);
                try {
                    PaidOperation paidOperation = PaidOperation.unpack(packed);
                    Thread.sleep(100);
                    ParcelProcessingState pState = getPaidOperationProcessingState(paidOperation.getId());
                    int interval = 1000;
                    // first, PaidOperation should be completed
                    //System.out.println("pState is: " + pState);
                    while (Instant.now().isBefore(end) && pState.isProcessing()) {
                        Thread.sleep(interval);
                        interval -= 350;
                        interval = Math.max(interval, 300);
                        pState = getPaidOperationProcessingState(paidOperation.getId());
                        //System.out.println("pState is: " + pState);
                    }
                    // PaidOperationProcessor commits/rollbacks final payment state after processing of its operation;
                    // so next, payment state should not be pending
                    ItemResult lastResult = getState(paidOperation.getPaymentContract().getId());
                    //System.out.println("test: " + lastResult);
                    while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                        Thread.sleep(interval);
                        interval -= 350;
                        interval = Math.max(interval, 300);
                        lastResult = getState(paidOperation.getPaymentContract().getId());
                        //System.out.println("test: " + lastResult);
                    }
                    return lastResult;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new ClientError(e);
                }
            } else {
                throw new ClientError(Errors.COMMAND_PENDING, "registerPaidOperationWithState",
                        "waiting time is up, please update payload state later");
            }
        }
    }

    /**
     * Get the state of the contract on the currently connected node.
     * Note: limits are applied to number of {@link #getState(Approvable)} calls
     * per minute per client key. Make sure method is not called too often with the same client connection.
     *
     * @param item to get state of
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */
    public final ItemResult getState(@NonNull Approvable item) throws ClientError {
        return getState(item.getId());
    }

    /**
     * Get the state of the contract (given by its id) on the currently connected node.
     * Note: limits are applied to number of {@link #getState(Approvable)} calls
     * per minute per client key. Make sure method is not called too often with the same client connection.
     *
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
     * Check if the contract has APPROVED status across the network.
     *
     * The method queries the status from multiple random different nodes until either gets enough replies to consider it approved, or collects a negative consensus sufficient to consider it is not approved (whatever happens earlier).
     *
     * “Enough” factor (for “enough replies”) is specified using the {@code trustLevel} parameter (what ratio of the total node count you would consider trusted).
     *
     * @implNote: according to the common Universa consensus rules, the negative consensus is defined as <code>10% of total nodes count + 1</code>. DECLINED/REVOKED/UNDEFINED statuses are treated as negative consensus.
     *
     * @apiNote: Method can be used for contract that are currently processed by network. It will wait until the pending statuses become final.
     *
     * @param itemId to get state of
     * @param trustLevel a value from 0 (exclusive) to 0.9; how many nodes (of all ones available in the network) do you need
     * @param millisToWait maximum time to get the positive or negative result from the network. If result is not received
     * within given time {@link ClientError} is thrown
     *
     *
     * @return if item is APPROVED by network
     * @throws ClientError
     */
    public boolean isApprovedByNetwork(HashId itemId, double trustLevel, long millisToWait) throws ClientError {
        if(trustLevel > 0.9)
            trustLevel = 0.9;

        Instant end = Instant.now().plusMillis(millisToWait);
        int Nt = (int) Math.ceil(size()*trustLevel);
        if(Nt < 1) {
            Nt = 1;
        }
        int N10 = (int) (Math.floor(size()*0.1)) + 1;

        int Nn = (Nt + 1) > N10 ? Nt + 1 : N10;


        Set<Integer> unprocessedIdxs = ConcurrentHashMap.newKeySet();
        Set<Integer> retryIdxs = ConcurrentHashMap.newKeySet();

        Map<ItemState,Set<Integer>> responseIdxs = new HashMap<>();
        responseIdxs.put(ItemState.APPROVED, ConcurrentHashMap.newKeySet());
        responseIdxs.put(ItemState.REVOKED, ConcurrentHashMap.newKeySet());
        responseIdxs.put(ItemState.DECLINED, ConcurrentHashMap.newKeySet());
        responseIdxs.put(ItemState.UNDEFINED, ConcurrentHashMap.newKeySet());

        Set<Integer> positiveIdxs = ConcurrentHashMap.newKeySet();
        Set<Integer> negativeIdxs = ConcurrentHashMap.newKeySet();

        unprocessedIdxs.addAll(IntStream.range(0, size()).boxed().collect(Collectors.toSet()));

        while(true) {
            AtomicInteger running = new AtomicInteger(0);
            for (int i = 0; i < Nn; i++) {

                Set<Integer> targetSet = unprocessedIdxs.size() > 0 ? unprocessedIdxs : retryIdxs;

                int size = targetSet.size();


                if(size > 0) {
                    running.incrementAndGet();
                    int rand = Do.randomInt(size);
                    Iterator<Integer> it = targetSet.iterator();
                    while(rand > 0) {
                        rand--;
                        it.next();
                    }
                    final int index = it.next();
                    it.remove();

                    Do.inParallel(() -> getClient(index).getState(itemId))
                            .failure(data -> {
                                retryIdxs.add(index);
                                running.decrementAndGet();
                            })
                            .success(data -> {
                                ItemResult ir = (ItemResult) data;
                                if (ir.state.isPending() || ir.state == ItemState.LOCKED || ir.state == ItemState.LOCKED_FOR_CREATION || ir.state == ItemState.LOCKED_FOR_CREATION_REVOKED) {
                                    retryIdxs.add(index);
                                } else {
                                    responseIdxs.get(ir.state).add(index);
                                    (ir.state.isApproved() ? positiveIdxs : negativeIdxs).add(index);
                                }
                                running.decrementAndGet();
                            });
                }
            }


            while (running.get() > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {

                }
                if(negativeIdxs.size() >= N10) {
                    return false;
                }

                if(positiveIdxs.size() >= Nt) {
                    return true;
                }

                if(Instant.now().isAfter(end)) {
                    throw new ClientError(Errors.COMMAND_PENDING, "isApprovedByNetwork",
                            "waiting time is up, but no result can be provided");
                }
            }
        }

    }

    /**
     * Force synchronization of some item (given by its id) with the rest of the network.
     * May require special client key / network configuration.
     *
     * @param itemId to synchronize
     * @return known {@link ItemState} before synchronization. Query the state later to get it synchronized.
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
     * Get the statistics of the node.
     * Accessible to the node owners (with node {@link PrivateKey} as session key) and the network admins only.
     *
     * @return dictionary containing uptime, ledger size, and number of contracts approved
     * for a minute, hour and since restart.
     * @throws ClientError
     */
    public Binder getStats() throws ClientError {
        return getStats(null);
    }

    /**
     * Get the extended statistics of the node including by-day payments in "U".
     * Accessible to the node owners (with node {@link PrivateKey} as session key) and the network admins only.
     *
     * @return dictionary containing uptime, ledger size, and number of contracts approved
     * for a minute, hour and since restart. It also contains by-day payments information.
     * @param showPaymentsDays the number of days to provide payments volume for
     * @throws ClientError
     */
    public Binder getStats(Integer showPaymentsDays) throws ClientError {
        return protect(() -> httpClient.command("getStats","showDays", showPaymentsDays));
    }

    /**
     * Pings given node from the node client currently connected to
     *
     * Accessible to the network admins only.
     *
     * @return dictionary containing UDP and TCP indicating delays. Values are set to -1 in case of ping timeout
     *
     * @param nodeNumber node number to send ping to
     * @param timeoutMillis maximum waiting time
     *
     * @throws ClientError
     */
    public Binder pingNode(Integer nodeNumber, Integer timeoutMillis) throws ClientError {
        return protect(() -> httpClient.command("pingNode","nodeNumber", nodeNumber,"timeoutMillis", timeoutMillis));
    }

    /**
     * Get the processing state of given parcel.
     *
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

    /**
     * Get the processing state of given PaidOperation.
     *
     * @param operationId id of the {@link com.icodici.universa.contract.PaidOperation} to get state of
     * @return processing state of the operation, from ParcelProcessingState enum
     * @throws ClientError
     */
    public ParcelProcessingState getPaidOperationProcessingState(HashId operationId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getPaidOperationProcessingState",
                    "operationId", operationId);

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
     * Get the state of the contract according to the network consensus.
     *
     * This method uses multiple connections to several nodes simultaneously to collect the statuses.
     * Note: limits are applied to number of {@link #getState(Approvable)} calls per minute for each particular
     * client key.
     * Make sure the method is not called too often with the same client connection.
     *
     * @param reporter reporter used to log the current status, or errors from nodes if any etc
     * @param itemId to get state by
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */
    public ItemResult getState(HashId itemId, Reporter reporter) throws ClientError {
        final ExecutorService pool = Executors.newCachedThreadPool();
        final List<ErrorRecord> errors = new ArrayList<>();

        final AsyncEvent<Void> consensusFound = new AsyncEvent<>();
        final int checkConsensus = size() / 3;

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
     * Get the node number of rhe currently connected node.
     *
     * @return node number
     */
    public int getNodeNumber() {
        return httpClient.getNodeNumber();
    }

    /**
     * Execute some custom command on the node.
     *
     * @param name name of the command
     * @param params parameters of the command
     * @return execution result
     */
    public Binder command(String name, Object... params) throws IOException {
        return httpClient.command(name, params);
    }

    /**
     * Convert this client into proxy to targetNode.
     * After calling this method, all subsequent {@link #command(String, Object...)} will works through selected proxy node.
     * @param targetNode index of node in topology (not the node number)
     * @param targetSession saved session to target node. Pass here null to create new session.
     * @throws IOException
     */
    public void startProxyToNode(int targetNode, BasicHttpClientSession targetSession) throws IOException {
        httpClient.startProxyToNode(nodes.get(targetNode), targetSession);
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
     * Get the current network rate for operating SLOT1 contracts
     *
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
     * Look for state data of some slot contract.
     *
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
     * Look for the contract stored using the given slot contract id.
     * The contract is specified by either id or origin (at least one of them should be not {@code null})
     *
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
                    "origin_id", (originId == null? null: originId.getDigest()),
                    "contract_id", (contractId == null? null: contractId.getDigest())
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
     * Get the current network rate for operating UNS1 contracts.
     *
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
     * Get the current network config provider.
     *
     * Config provider is used by {@link com.icodici.universa.contract.services.NSmartContract}
     *
     * @return name-days per U rate
     * @throws ClientError
     */
    public NodeConfigProvider getConfigProvider() throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getConfigProvider");
            NodeConfigProvider provider = (NodeConfigProvider) result.get("provider");
            return provider;
        });
    }

    /**
     * Look for the name associated with a given origin (passed as an argument).
     *
     * @param origin to look for
     * @return {@link Binder} containing names - an array of objects containing name and description associated with given origin
     * or {@code null} if not found
     * @throws ClientError
     */
    public Binder queryNameRecord(HashId origin) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryNameRecord", "origin", origin.getDigest());
            return result;
        });
    }

    /**
     * Look for the name associated with some address (passed as an argument).
     *
     * @param address to look for
     * @return {@link Binder} containing names - an array of objects containing name and description associated with given origin
     * or {@code null} if not found
     * @throws ClientError
     */
    public Binder queryNameRecord(String address) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("queryNameRecord", "address", address);
            return result;
        });
    }

    /**
     * Query the  {@link com.icodici.universa.contract.services.UnsContract} that registers the name
     * (passed as the argument).
     *
     * @param name to look for
     * @return packed {@link com.icodici.universa.contract.services.UnsContract} if found;
     * or {@code null} if not found
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
     *
     * @param itemId contract hash
     * @return contract with the given contract id; or {@code null} if not found
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
     * Get the body of active contract with the given origin (if ony one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param origin contract origin
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin, int limit, int offset) throws ClientError {
        return getContract(origin, null, limit, offset);
    }

    private static final void processGetContractResults(Binder result) throws IOException {
        if (Objects.requireNonNull(result).containsKey("contractIds")) {
            List<byte[]> contractIds = new ArrayList<>();
            List<HashId> ids = new ArrayList<>();
            for (Object id : result.getListOrThrow(("contractIds"))) {
                final byte[] contractsAsBytes = ((Bytes) id).getData();
                contractIds.add(contractsAsBytes);
                ids.add(HashId.withDigest(contractsAsBytes));
            }
            result.put("contractIds", contractIds);
            result.put("ids", ids);
        }

        if (result.containsKey("packedContract")) {
            TransactionPack tp = TransactionPack.unpack(result.getBinaryOrThrow("packedContract"));
            HashId id = (HashId) result.getListOrThrow("ids").get(0);
            if (tp.getContract().getId().equals(id)) {
                result.put("contract", tp.getContract());
            } else {
                result.put("contract", tp.getSubItem(id));
            }
        }
    }

    /**
     * Get the body of the active contract with the given origin (if only one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param origin contract origin
     * @param tags tags to search for
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin, Binder tags, int limit, int offset) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command(
                    "getContract", "origin", origin, "limit", limit, "offset", offset, "tags", tags);
            if (!result.isEmpty()) {
                processGetContractResults(result);
                return result;
            } else
                return null;
        });
    }

    /**
     * Get the body of the active contract with the given origin (if only one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param origin contract origin
     * @param limit of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getContract(HashId origin, int limit) throws ClientError {
        return getContract(origin, limit,0);
    }

    /**
     * Get the body of the active contract with the given parent (if only one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param parent id of parent contract
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, int limit, int offset) throws ClientError {
        return getChildren(parent,null, limit, offset);
    }

    /**
     * Get the body of the active contract with the given parent (if only one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param parent id of parent contract
     * @param tags tags to search for (state.data.search_tags.key=value)
     * @param limit of list items
     * @param offset of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, Map<String, String> tags, int limit, int offset) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command(
                    "getContract", "parent", parent, "limit", limit, "offset", offset, "tags", tags);
            if (!result.isEmpty()) {
                processGetContractResults(result);
                return result;
            } else
                return null;
        });
    }

    /**
     * Get the body of the active contract with the given parent (if only one active contract is returned),
     * orthe  list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param parent id of parent contract
     * @param limit of list items
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
     * @throws ClientError
     */
    public Binder getChildren(HashId parent, int limit) throws ClientError {
        return getChildren(parent, limit,0);
    }

    /**
     * Get the body of active contract with the given origin (if only one active contract is returned),
     * or the list of IDs for the active contracts (if there are more than one in result).
     * The list of contract IDs contains contract hash digests as byte arrays.
     *
     * @param origin contract origin
     * @return {@link Binder} containing the packed transaction
     * or (at the "contractIds" key) limited list of IDs for the active contracts;
     * or {@code null} (if no active contracts found)
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
     * Get current network rate to run the FOLLOWER1 contracts.
     *
     * @return {@link Binder} containing data for the following keys:
     * "rateOriginDays" - origins-days per U rate;
     * "rateCallback" - callback cost in U.
     * @throws ClientError
     */
    public Binder followerGetRate() throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("followerGetRate");
            return result;
        });
    }

    /**
     * Lookup the state data of the follower contract in the network.
     *
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
