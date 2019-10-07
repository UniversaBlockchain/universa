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

import static junit.framework.TestCase.assertEquals;

public class UBotSessionsProTest {

    public static final Map<Integer,PrivateKey> ubotKeys = new HashMap<>();
    public static final int N = 30;
    private static final int ATTEMPTS = 5000;

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

        Client client = new Client("universa.pro", null, TestKeys.privateKey(1));
        int quorumSize = 5;
        int poolSize = 8;
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",poolSize),
                                "quorum",Binder.of("size",quorumSize))));
        executableContract.seal();
        for(int x = 0; x < ATTEMPTS; x++) {
            System.out.println("ATTEMPT " + x);
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id", executableContract.getId());
            requestContract.getStateData().put("method_name", "getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));
            requestContract.seal();
            requestContract.getTransactionPack().addReferencedItem(executableContract);

            System.out.println(client.command("ubotCreateSession", "packedRequest", requestContract.getPackedTransaction()));

            AtomicInteger readyCounter = new AtomicInteger();
            AsyncEvent readyEvent = new AsyncEvent();
            AtomicReference<List<Integer>> pool = new AtomicReference<>();

            for (int i = 0; i < client.size(); i++) {
                int finalI = i;
                Do.inParallel(() -> {
                    while (true) {
                        Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                        System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                        Thread.sleep(500);
                        if (res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                            pool.set(res.getBinderOrThrow("session").getListOrThrow("sessionPool"));
                            if (readyCounter.incrementAndGet() == client.size()) {
                                readyEvent.fire();
                            }
                            break;
                        }
                    }
                });
            }

            readyEvent.await();


            int votingCount = quorumSize + Do.randomInt(poolSize - quorumSize);
            Set<Integer> poolQuorum = new HashSet<>();
            while (poolQuorum.size() < votingCount) {
                poolQuorum.add(Do.sample(pool.get()));
            }

            Set<Client> quorumClients = new HashSet<>();
            poolQuorum.forEach(n -> {
                try {
                    quorumClients.add(new Client("universa.pro", null, ubotKeys.get(n)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            assertEquals(quorumClients.size(), poolQuorum.size());

            HashId storageValue = HashId.createRandom();
            quorumClients.forEach(c -> {
                for (int i = 0; i < c.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",null,"toValue", storageValue);
                            c.getClient(finalI).command("ubotUpdateStorage", "executableContractId", executableContract.getId(), "storageName", "default", "toValue", storageValue);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            });

            while (true) {
                Binder res = client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
                System.out.println(res);
                if (res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && res.getBinderOrThrow("pending").get("default") != null && res.getBinderOrThrow("pending").getBinder("default").size() == 0) {
                    break;
                }
                Thread.sleep(10);
            }

            HashId oldStorageValue = storageValue;
            HashId newStorageValue = HashId.createRandom();

            quorumClients.forEach(c -> {
                for (int i = 0; i < c.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue,"toValue", newStorageValue);
                            c.getClient(finalI).command("ubotUpdateStorage", "executableContractId", executableContract.getId(), "storageName", "default", "toValue", newStorageValue);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            });

            while (true) {
                Binder res = client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
                System.out.println(res);
                if (res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue) && res.getBinderOrThrow("pending").get("default") != null && res.getBinderOrThrow("pending").getBinder("default").size() == 0) {
                    break;
                }
                Thread.sleep(10);
            }

            quorumClients.forEach(c -> {
                for (int i = 0; i < c.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            c.getClient(finalI).command("ubotCloseSession", "executableContractId", executableContract.getId());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            });

            AsyncEvent readyEvent2 = new AsyncEvent();
            AtomicInteger readyCounter2 = new AtomicInteger();

            for (int i = 0; i < client.size(); i++) {
                int finalI = i;
                Do.inParallel(() -> {
                    while (true) {
                        Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                        System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                        Thread.sleep(500);
                        if (res.getBinder("session").isEmpty()) {
                            if (readyCounter2.incrementAndGet() == client.size()) {
                                readyEvent2.fire();
                            }
                            break;
                        }
                    }
                }).failure(new DeferredResult.Handler() {
                    @Override
                    public void handle(Object data) {
                        System.out.println("ERR: " + data);
                    }
                });
            }
            readyEvent2.await();
        }
    }

    @Test
    public void createSessionConcurrentRequests() throws Exception {
        Client client = new Client("universa.pro", null, TestKeys.privateKey(1));
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
                    Thread.sleep(500);
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


    }
}
