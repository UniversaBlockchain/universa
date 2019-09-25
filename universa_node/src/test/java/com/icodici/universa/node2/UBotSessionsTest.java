package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.icodici.universa.node2.network.VerboseLevel.BASE;
import static junit.framework.TestCase.assertEquals;

public class UBotSessionsTest extends BaseMainTest {

    public static final Map<Integer,PrivateKey> ubotKeys = new HashMap<>();
    public static final int N = 30;
    static {
        try {
            for(int i = 0; i< N; i++) {
                ubotKeys.put(i,new PrivateKey(Do.read("./src/ubot_config/ubot"+i+"/tmp/ubot_"+i+".private.unikey")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void createSession() throws Exception {

        TestSpace ts = prepareTestSpace();
        //ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));
        int quorumSize = 4;;
        int poolSize = 5;
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",poolSize),
                                "quorum",Binder.of("size",quorumSize))));
        executableContract.seal();
        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));
        requestContract.seal();
        requestContract.getTransactionPack().addReferencedItem(executableContract);
        System.out.println(ts.client.command("ubotCreateSession","packedRequest",requestContract.getPackedTransaction()));
        AtomicReference<List<Integer>> pool = new AtomicReference<>();
        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();


        for(int i = 0; i < ts.clients.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.get("session") != null && res.getBinderOrThrow("session").get("state") == null) {
                        continue;
                    }
                    if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                        pool.set(res.getBinderOrThrow("session").getListOrThrow("sessionPool"));
                        if (readyCounter.incrementAndGet() == ts.clients.size()) {
                            readyEvent.fire();
                        }
                        break;
                    }
                }
            }).failure(new DeferredResult.Handler() {
                @Override
                public void handle(Object data) {
                    System.out.println("ERR: "+data);
                }
            });
        }

        readyEvent.await();

        System.out.println(pool);

        Set<Integer> poolQuorum = new HashSet<>();
        while(poolQuorum.size() < quorumSize) {
            poolQuorum.add(Do.sample(pool.get()));
        }

        Set<Client> quorumClients = new HashSet<>();
        poolQuorum.forEach(n-> {
            try {
                quorumClients.add(new Client(".src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(n)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        assertEquals(quorumClients.size(),poolQuorum.size());

        HashId storageValue = HashId.createRandom();
        quorumClients.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",null,"toValue", storageValue);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && res.getBinderOrThrow("pending").get("default") != null && res.getBinderOrThrow("pending").getBinder("default").size() == 0) {
                break;
            }
            Thread.sleep(10);
        }

        Thread.sleep(4000);

        HashId oldStorageValue = storageValue;
        HashId newStorageValue = HashId.createRandom();

        quorumClients.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue,"toValue", newStorageValue);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue) && res.getBinderOrThrow("pending").get("default") != null && res.getBinderOrThrow("pending").getBinder("default").size() == 0) {
                break;
            }
            Thread.sleep(1000);
        }

        ts.shutdown();
    }

    @Test
    public void createSessionConcurrentRequests() throws Exception {
        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.seal();
        System.out.println("EID = " + executableContract.getId());


        List<Contract> requestContracts = new ArrayList<>();
        for(int i = 0; i < client.size();i++) {
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id",executableContract.getId());
            requestContract.getStateData().put("method_name","getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));
            requestContract.seal();
            requestContract.getTransactionPack().addReferencedItem(executableContract);
            requestContracts.add(requestContract);
        }
        for(int i = 0; i < client.size();i++) {
            int finalI = i;
            Do.inParallel(()-> {
                System.out.println(client.getClient(finalI).command("ubotCreateSession", "packedRequest", requestContracts.get(finalI).getPackedTransaction()));
            });
        }

        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();


        for(int i = 0; i < client.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                    Thread.sleep(200);
                    if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                        if(readyCounter.incrementAndGet() == client.size()) {
                            readyEvent.fire();
                        }
                        break;
                    }
                }
            });
        }

        readyEvent.await();
        ts.shutdown();

    }
}
