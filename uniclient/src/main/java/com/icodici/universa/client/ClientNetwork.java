package com.icodici.universa.client;

import com.icodici.universa.node.network.HttpClient;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientNetwork {

    private Binder netConfig;

    ArrayList<HttpClient> clients = new ArrayList<>();

    public ClientNetwork() {
        try {
            byte[] bytes = Do.read(getClass()
                                           .getClassLoader()
                                           .getResourceAsStream("config/ndata.boss")
            );
            netConfig = Boss.unpack(bytes);

            netConfig.forEach((nodeId, o) -> {
                Binder nodeData = (Binder) o;
                String host = nodeData.getStringOrThrow("ip");
                int port = nodeData.getIntOrThrow("client_port");
                String url = "http://" + host + ":" + port;
                HttpClient client = new HttpClient(nodeId,url);
                clients.add(client);
            });
        } catch (IOException e) {
            throw new RuntimeException("failed to read ndata resource", e);
        }
    }


    private void addNode(String name) {
        Yaml yaml = new Yaml();
    }

    public int size() {
        return netConfig.size();
    }

    public int checkNetworkState(Reporter reporter) {
        ExecutorService es = Executors.newCachedThreadPool();
        ArrayList<Future<?>> futures = new ArrayList<>();
        AtomicInteger okNodes = new AtomicInteger(0);
        clients.forEach(client -> {
            futures.add(
                    es.submit(() -> {
                        try {
                            reporter.verbose("checking "+client);
                            HttpClient.Answer answer = client.request("ping", "foo", "bar");
                            if (answer.code == 200) {
                                reporter.verbose(client.toString() + " OK");
                                int cnt = okNodes.incrementAndGet();
                                reporter.progress(""+cnt);
                                return;
                            }
                        } catch (IOException e) {
                            reporter.error("CONNECTION_FAILED", client.toString(), "connection failed: "+e);
                        }
                    })
            );
        });
        futures.forEach(f -> {
            try {
                f.get(4, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                reporter.verbose("timout");
                f.cancel(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        es.shutdown();
        return okNodes.get();
    }
}
