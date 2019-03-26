package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.ParcelProcessingState;
import com.icodici.universa.node2.network.BasicHttpClientSession;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientNetwork {

    static Reporter reporter = CLIMain.getReporter();

    Client client;

    public ClientNetwork(BasicHttpClientSession session) throws IOException {
        this(session, false);
    }

    public ClientNetwork(BasicHttpClientSession session, boolean delayedStart) throws IOException {
        for (int i = 1; i < 10; i++) {
            try {
                client = new Client("http://node-" +
                        Do.randomIntInRange(1, 10) +
                        "-com.utoken.io:8080", CLIMain.getPrivateKey(), session, delayedStart);
                break;
            } catch (IOException e) {
                reporter.warning("failed to read network from node " + i);
            }
        }
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public ClientNetwork(String nodeUrl, BasicHttpClientSession session) throws IOException {
        this(nodeUrl, session, false);
    }

    public ClientNetwork(String nodeUrl, BasicHttpClientSession session, boolean delayedStart) throws IOException {
        client = new Client(nodeUrl, CLIMain.getPrivateKey(), session, delayedStart);
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public ClientNetwork(String nodeUrl, PrivateKey privateKey, BasicHttpClientSession session) throws IOException {
        client = new Client(nodeUrl, privateKey, session);
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.message("Network version: " + client.getVersion());
    }

    public void start(BasicHttpClientSession session) throws IOException {
        client.start(session);
    }

    public ItemResult register(byte[] packedContract) throws ClientError {
        return client.register(packedContract, 0);
    }

    public boolean ping() throws IOException {
        return client.ping();
    }

    /**
     * Register packed binary contract and wait for the consensus.
     *
     * @param packedContract
     * @param millisToWait wait for the consensus as long as specified time, <= 0 means no wait (returns some pending
     *                     state from registering).
     * @return last item status returned by the network
     * @throws ClientError
     */
    public ItemResult register(byte[] packedContract, long millisToWait) throws ClientError {
        return client.register(packedContract, millisToWait);
    }

    /**
     * Register packed binary contract and wait for the consensus.
     *
     * @param packedParcel
     * @param millisToWait wait for the consensus as long as specified time, <= 0 means no wait (returns some pending
     *                     state from registering).
     * @return last item status returned by the network
     * @throws ClientError
     */
    public boolean registerParcel(byte[] packedParcel, long millisToWait) throws ClientError {
        try {
            client.registerParcelWithState(packedParcel, millisToWait);
            return true;
        } catch (ClientError e) {
            if (e.getErrorRecord().getError() == Errors.COMMAND_PENDING)
                return true;
            else
                return false;
        }
    }

    public ItemResult resync(String base64Id) throws ClientError {
        return client.resyncItem(HashId.withDigest(base64Id));
    }

    public ItemResult check(String base64Id) throws ClientError {
        return client.getState(HashId.withDigest(base64Id),reporter);
    }

    public ItemResult check(HashId id) throws ClientError {
        return client.getState(id, reporter);
    }

    public ParcelProcessingState getParcelProcessingState(HashId id) throws ClientError {
        return client.getParcelProcessingState(id);
    }

    public int size() {
        return client.size();
    }

    public BasicHttpClientSession getSession() throws IllegalStateException {
        return client.getSession();
    }

    public int getNodeNumber() {
        return client.getNodeNumber();
    }

    public int checkNetworkState(Reporter reporter) {
        ExecutorService es = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        AtomicInteger okNodes = new AtomicInteger(0);
        final List<Client.NodeRecord> nodes = client.getNodes();
        for (int nn = 0; nn < client.size(); nn++) {
            final int nodeNumber = nn;
            futures.add(
                    es.submit(() -> {
                        final String url = nodes.get(nodeNumber).url;
                        reporter.verbose("Checking node " + url);
                        for (int i = 0; i < 5; i++) {
                            try {
                                if (client.ping(nodeNumber)) {
                                    okNodes.getAndIncrement();
                                    reporter.verbose("Got an answer from " + url);
                                    return;
                                }
                                reporter.message("retry #" + (i+1) + " on connection failure: " + url);
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        reporter.error(Errors.NOT_READY.name(), url, "failed to connect");
                    })
            );
        }
        futures.forEach(f -> {
            try {
                f.get(4, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                reporter.verbose("node test is timed out");
                f.cancel(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        es.shutdown();
        int n = okNodes.get();
        if (n >= client.size() * 0.12)
            reporter.message("Universa network is active, " + n + " node(s) are reachable");
        else
            reporter.error("NOT_READY", "network", "Universa network is temporarily inaccessible, reachable nodes: " + n);
        return n;
    }

}
