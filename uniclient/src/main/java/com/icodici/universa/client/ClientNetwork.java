package com.icodici.universa.client;

import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
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

    public ClientNetwork() throws IOException {
        for (int i = 1; i < 10; i++) {
            try {
                client = new Client("http://node-" +
                                            Do.randomIntInRange(1, 10) +
                                            "-com.universa.io:8080", CLIMain.getPrivateKey());
                break;
            } catch (IOException e) {
                reporter.warning("failed to read network from node " + i);
            }
        }
        if (client == null)
            throw new IOException("failed to connect to to the universa network");
        reporter.verbose("Read Universa network configuration: " + client.size() + " nodes");
        reporter.verbose("Network version: " + client.getVersion());
    }

    public ItemResult register(byte[] packedContract) throws ClientError {
        return client.register(packedContract);
    }

    public ItemResult check(String base64Id) throws ClientError {
        return client.getState(HashId.withDigest(base64Id));
    }

    public int size() {
        return client.size();
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
