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
import com.icodici.universa.Decimal;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.math.BigDecimal;
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

    /**
     *  Get nodes count in network.
     * @return loaded nodes count if constructor with someNodeUrl used otherwise 0
     */
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

    public boolean ping() throws IOException {
        httpClient.command("sping");
        return true;
    }

    Client getClient(int i) throws IOException {
        Client c = clients.get(i);
        if (c == null) {
            NodeRecord r = nodes.get(i);
            c = new Client(r.url, clientPrivateKey, r.key, null);
            clients.set(i, c);
        }
        return c;
    }

    public ItemResult setVerboseLevel(int node, int network, int udp) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("setVerbose",
                    "node", DatagramAdapter.VerboseLevel.intToString(node),
                    "network", DatagramAdapter.VerboseLevel.intToString(network),
                    "udp", DatagramAdapter.VerboseLevel.intToString(udp));

            Object ir = result.getOrThrow("itemResult");
            if (ir instanceof ItemResult)
                return (ItemResult) ir;

            if (ir instanceof String)
                System.out.println(">> " + ir);

            return ItemResult.UNDEFINED;
        });
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
     */
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
     * @throws IOException
     */
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
     * @throws IOException
     */

    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session) throws IOException {
        this(someNodeUrl, clientPrivateKey, session, false);
    }

    /**
     * Create new client protocol session. It loads network configuration and creates client protocol session with random node. Allows delayed start of http client

     * @param someNodeUrl url on some node in network
     * @param clientPrivateKey client private key
     * @param session set to null or to the reconstructed instance
     * @param delayedStart indicates if start of http client should be delayed
     * @throws IOException
     */

    public Client(String someNodeUrl, PrivateKey clientPrivateKey, BasicHttpClientSession session, boolean delayedStart) throws IOException {
        this.clientPrivateKey = clientPrivateKey;
        loadNetworkFrom(someNodeUrl);
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
     * Start http client (used with constructor passing delayedStart = true)

     * @param session set to null or to the reconstructed instance
     * @throws IOException
     */
    public void start(BasicHttpClientSession session) throws IOException {
        httpClient.start(clientPrivateKey, nodePublicKey, session);
    }

    /**
     * Restart http client
     * @throws IOException
     */
    public void restart() throws IOException {
        httpClient.restart();
    }

    /**
     * Get url of the node client is connected to
     *
     * @return url of the node
     */
    public String getUrl() {
        return httpClient.getUrl();
    }


    /**
     * Get session of http client
     *
     * @return session
     */

    public BasicHttpClientSession getSession() throws IllegalStateException {
        return httpClient.getSession();
    }

    /**
     * Get session of http client connected to node with given number
     *
     * @param i number of the node to return session with
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

    /**
     * Get list of nodes in
     * @return list of network nodes if constructor loading network configuration was used. Empty list otherwise
     */
    public List<NodeRecord> getNodes() {
        return nodes;
    }

    private List<NodeRecord> nodes = new ArrayList<>();

    /**
     * Get network version
     * @return client version if constructor loading network configuration was used otherwise null
     */
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

    /**
     * Register contract on the network without payment. May require special client key / network configuration
     * @param packed {@link com.icodici.universa.contract.TransactionPack} binary
     * @return result of registration
     * @throws ClientError
     */

    public ItemResult register(byte[] packed) throws ClientError {
        return register(packed, 0);
    }

    /**
     * Register contract on the network without payment. May require special client key / network configuration and wait for some of the final {@link ItemState}
     * no longer that time given
     * @param packed {@link com.icodici.universa.contract.TransactionPack} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return result of registration
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
                    while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                        Thread.currentThread().sleep(100);
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
     * Register contract on the network with parcel (includes payment)
     * @param packed {@link Parcel} binary
     * @return result of registration
     * @throws ClientError
     */
    public boolean registerParcel(byte[] packed) throws ClientError {
        return registerParcel(packed, 0);
    }

    /**
     * Register contract on the network with parcel (includes payment) and wait for some of the final {@link ItemState}
     * @param packed {@link Parcel} binary
     * @param millisToWait maximum time to wait for final {@link ItemState}
     * @return result of registration
     * @throws ClientError
     */

    public boolean registerParcel(byte[] packed, long millisToWait) throws ClientError {
        Object result = protect(() -> httpClient.command("approveParcel", "packedItem", packed)
                .get("result"));
        if(result instanceof String) {
            System.out.println(">> registerParcel " + result);
        } else {
            if (millisToWait > 0) {
                Instant end = Instant.now().plusMillis(millisToWait);
                try {
                    Parcel parcel = Parcel.unpack(packed);
                    Node.ParcelProcessingState pState = getParcelProcessingState(parcel.getId());
                    while (Instant.now().isBefore(end) && pState.isProcessing()) {
                        System.out.println("parcel state is: " + pState);
                        Thread.currentThread().sleep(100);
                        pState = getParcelProcessingState(parcel.getId());
                    }
                    System.out.println("parcel state is: " + pState);
                    ItemResult lastResult = getState(parcel.getPayloadContract().getId());
                    while (Instant.now().isBefore(end) && lastResult.state.isPending()) {
                        Thread.currentThread().sleep(100);
                        lastResult = getState(parcel.getPayloadContract().getId());
                        System.out.println("test: " + lastResult);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Quantiser.QuantiserException e) {
                    throw new ClientError(e);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return (boolean) result;
        }

        return false;
    }


    /**
     * Look for known state of approvable item on the network
     * @param item to find state of
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */

    public final ItemResult getState(@NonNull Approvable item) throws ClientError {
        return getState(item.getId());
    }

    /**
     * Look for known state of item by given id
     * @param itemId to find state of
     * @return known {@link ItemState} if exist or ItemState.UNDEFINED
     * @throws ClientError
     */

    public ItemResult getState(HashId itemId) throws ClientError {
        return protect(() -> {
//            for (int i = 0; i < nodes.size(); i++) {
//                System.out.println("checking node " + i);
//                ItemResult r = getClient(i).command("getState",
//                                                    "itemId", itemId).getOrThrow("itemResult");
//                System.out.println(">> " + r);
//            }

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
    public Node.ParcelProcessingState getParcelProcessingState(HashId parcelId) throws ClientError {
        return protect(() -> {
            Binder result = httpClient.command("getParcelProcessingState",
                    "parcelId", parcelId);

            Object ps = result.getOrThrow("processingState");
            if (ps instanceof Node.ParcelProcessingState)
                return (Node.ParcelProcessingState) ps;

            return Node.ParcelProcessingState.valueOf(result.getBinder("processingState").getStringOrThrow("state"));
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
            Double U = result.getDouble("U");
            return new Decimal(BigDecimal.valueOf(U));
        });
    }

    /**
     * Look for state data of slot contract
     * @param slotId slot contract id
     * @return state data of slot contract
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
            Double U = result.getDouble("U");
            return new Decimal(BigDecimal.valueOf(U));
        });
    }

    /**
     * Look for the name assosiated with a given origin
     * @param origin to look for
     * @return binder containing name, description and url associated with origin or null
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
     * @return binder containing name, description and url associated with address or null
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
}
