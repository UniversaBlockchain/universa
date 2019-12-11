package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.BasicHttpClient;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
        int quorumSize = 4;
        int poolSize = 5;
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",poolSize),
                                "quorum",Binder.of("size",quorumSize))));
        executableContract.getStateData().put("js", "simple JS code");
        executableContract.seal();
        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));

        ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
            Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

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
                quorumClients.add(new Client("./src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(n)));
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
                        //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",null,"toValue", storageValue);
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", storageValue);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(10);
        }


        HashId oldStorageValue = storageValue;
        HashId newStorageValue = HashId.createRandom();

        quorumClients.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue,"toValue", newStorageValue);
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(1000);
        }


        quorumClients.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        c.getClient(finalI).command("ubotCloseSession","executableContractId", executableContract.getId(), "finished", true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        AsyncEvent readyEvent2 = new AsyncEvent();
        AtomicInteger readyCounter2 = new AtomicInteger();

        for(int i = 0; i < ts.clients.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.getBinder("session").isEmpty()) {
                        if (readyCounter2.incrementAndGet() == ts.clients.size()) {
                            readyEvent2.fire();
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

        readyEvent2.await();


        //new session

        requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));

        ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
            Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

        System.out.println(ts.client.command("ubotCreateSession","packedRequest",requestContract.getPackedTransaction()));
        AtomicReference<List<Integer>> pool2 = new AtomicReference<>();
        AtomicInteger readyCounter3 = new AtomicInteger();
        AsyncEvent readyEvent3 = new AsyncEvent();


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
                        pool2.set(res.getBinderOrThrow("session").getListOrThrow("sessionPool"));
                        if (readyCounter3.incrementAndGet() == ts.clients.size()) {
                            readyEvent3.fire();
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

        readyEvent3.await();

        System.out.println(pool2);

        Set<Integer> poolQuorum2 = new HashSet<>();
        while(poolQuorum2.size() < quorumSize) {
            poolQuorum2.add(Do.sample(pool2.get()));
        }

        Set<Client> quorumClients2 = new HashSet<>();
        poolQuorum2.forEach(n-> {
            try {
                quorumClients2.add(new Client("./src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(n)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        assertEquals(quorumClients2.size(),poolQuorum.size());

        HashId newStorageValue2 = HashId.createRandom();
        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",newStorageValue,"toValue", newStorageValue2);
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue2) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(10);
        }


        HashId oldStorageValue2 = newStorageValue2;
        HashId newStorageValue3 = HashId.createRandom();

        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue2,"toValue", newStorageValue3);
                        c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue3);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue3) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(1000);
        }


        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size();i++) {
                int finalI = i;
                Do.inParallel(()->{
                    try {
                        c.getClient(finalI).command("ubotCloseSession","executableContractId", executableContract.getId(), "finished", true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        });

        AsyncEvent readyEvent4 = new AsyncEvent();
        AtomicInteger readyCounter4 = new AtomicInteger();

        for(int i = 0; i < ts.clients.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.getBinder("session").isEmpty()) {
                        if (readyCounter4.incrementAndGet() == ts.clients.size()) {
                            readyEvent4.fire();
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

        readyEvent4.await();

        ts.shutdown();
    }


    @Test
    public void createSessionPaid() throws Exception {

        TestSpace ts = prepareTestSpace();
        //ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));
        int quorumSize = 4;
        int poolSize = 5;
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",poolSize),
                                "quorum",Binder.of("size",quorumSize))));
        executableContract.getStateData().put("js", "simple JS code");
        executableContract.seal();
        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));

        ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
                Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

        int COST = 2;
        Contract u = getApprovedUContract(ts);
        u = u.createRevision(ts.myKey);
        u.getStateData().put("transaction_units",u.getStateData().getIntOrThrow("transaction_units")-COST);
        u.seal();

        System.out.println(ts.client.command("ubotCreateSessionPaid","packedU",u.getPackedTransaction(), "packedRequest",requestContract.getPackedTransaction()));

        AtomicReference<Binder> session = new AtomicReference<>();
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
                        if (readyCounter.incrementAndGet() == ts.clients.size()) {
                            session.set(res.getBinderOrThrow("session"));
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
        Thread.sleep(2000);
        System.out.println(session);

        assertEquals(ts.client.getState(u.getId()).state, ItemState.APPROVED);
        assertEquals(ts.client.getState(ts.uContract.getId()).state, ItemState.REVOKED);


        ts.shutdown();
    }

    @Ignore
    @Test
    public void unloadSessionFromMem() throws Exception {
        TestSpace ts = prepareTestSpace();
        //ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));
        int quorumSize = 1;
        int poolSize = 1;
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

        Client ubotClient = null;

        while (true) {
            Thread.sleep(100);
            Binder res = ts.client.command("ubotGetSession", "executableContractId", executableContract.getId());
            if(res.get("session") != null && res.getBinderOrThrow("session").get("state") == null)
                continue;
            if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                List<Integer> sessionPool = res.getBinderOrThrow("session").getListOrThrow("sessionPool");
                System.out.println("sessionPool: " + sessionPool);
                ubotClient = new Client("./src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(sessionPool.get(0)));
                break;
            }
        }

        HashId storageValue = HashId.createRandom();
        System.out.println("storageValue: " + storageValue);
        for (int c = 0; c < ubotClient.size(); ++c)
            ubotClient.getClient(c).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", storageValue);

        while(true) {
            Thread.sleep(100);
            Binder res = ubotClient.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
        }

        Field privateField = Node.class.getDeclaredField("ubotSessionProcessors");
        privateField.setAccessible(true);
        ConcurrentHashMap<HashId, Node.UBotSessionProcessor> ubotSessionProcessors = (ConcurrentHashMap<HashId, Node.UBotSessionProcessor>) privateField.get(ts.node.node);
        System.out.println("ubotSessionProcessors count: " + ubotSessionProcessors.size());
        assertEquals(1, ubotSessionProcessors.size());

        while (true) {
            Thread.sleep(2000);
            System.out.println("wait for unload ubot processors... ubotSessionProcessors.size = " + ubotSessionProcessors.size());
            if (ubotSessionProcessors.size() == 0)
                break;
        }

        while(true) {
            Thread.sleep(1000);
            Binder res = ubotClient.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println("res: " + res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
        }

        HashId newStorageValue = HashId.createRandom();
        System.out.println("newStorageValue: " + newStorageValue);
        for (int c = 0; c < ubotClient.size(); ++c)
            ubotClient.getClient(c).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue);

        while(true) {
            Thread.sleep(100);
            Binder res = ubotClient.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue) && res.getBinderOrThrow("pending").getBinder("default").size() == 0) {
                break;
            }
        }

        assertEquals(1, ubotSessionProcessors.size());

        ts.shutdown();
    }

    @Test
    public void createSessionWithoutNode() throws Exception {

        int offline = 2;
        TestSpace ts = prepareTestSpace();
        ts.nodes.get(offline).shutdown();
        ts.clients.remove(offline);
        ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));

        int quorumSize = 4;
        int poolSize = 5;
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",poolSize),
                                "quorum",Binder.of("size",quorumSize))));
        executableContract.getStateData().put("js", "simple JS code");
        executableContract.seal();
        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));

        ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
            Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

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
        while (poolQuorum.size() < quorumSize) {
            poolQuorum.add(Do.sample(pool.get()));
        }

        Set<Client> quorumClients = new HashSet<>();
        poolQuorum.forEach(n-> {
            Client client = null;
            while (client == null) {
                try {
                    client = new Client("./src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(n));
                } catch (ConnectException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            quorumClients.add(client);
        });

        assertEquals(quorumClients.size(), poolQuorum.size());

        HashId storageValue = HashId.createRandom();
        quorumClients.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",null,"toValue", storageValue);
                            c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", storageValue);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(storageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(10);
        }

        HashId newStorageValue = HashId.createRandom();

        quorumClients.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue,"toValue", newStorageValue);
                            c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(1000);
        }

        quorumClients.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            c.getClient(finalI).command("ubotCloseSession","executableContractId", executableContract.getId(), "finished", true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        AsyncEvent readyEvent2 = new AsyncEvent();
        AtomicInteger readyCounter2 = new AtomicInteger();

        for(int i = 0; i < ts.clients.size(); i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.getBinder("session").isEmpty()) {
                        if (readyCounter2.incrementAndGet() == ts.clients.size()) {
                            readyEvent2.fire();
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

        readyEvent2.await();

        //new session

        requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));

        ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
            Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

        System.out.println(ts.client.command("ubotCreateSession","packedRequest",requestContract.getPackedTransaction()));
        AtomicReference<List<Integer>> pool2 = new AtomicReference<>();
        AtomicInteger readyCounter3 = new AtomicInteger();
        AsyncEvent readyEvent3 = new AsyncEvent();

        for(int i = 0; i < ts.clients.size(); i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.get("session") != null && res.getBinderOrThrow("session").get("state") == null) {
                        continue;
                    }
                    if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                        pool2.set(res.getBinderOrThrow("session").getListOrThrow("sessionPool"));
                        if (readyCounter3.incrementAndGet() == ts.clients.size()) {
                            readyEvent3.fire();
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

        readyEvent3.await();

        System.out.println(pool2);

        Set<Integer> poolQuorum2 = new HashSet<>();
        while(poolQuorum2.size() < quorumSize) {
            poolQuorum2.add(Do.sample(pool2.get()));
        }

        Set<Client> quorumClients2 = new HashSet<>();
        poolQuorum2.forEach(n-> {
            Client client = null;
            while (client == null) {
                try {
                    client =new Client("./src/test_node_config_v2/test_node_config_v2.json",null,ubotKeys.get(n));
                } catch (ConnectException e) {
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            quorumClients2.add(client);
        });

        assertEquals(quorumClients2.size(), poolQuorum.size());

        HashId newStorageValue2 = HashId.createRandom();
        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",newStorageValue,"toValue", newStorageValue2);
                            c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue2);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue2) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(10);
        }

        HashId newStorageValue3 = HashId.createRandom();

        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            //c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","fromValue",oldStorageValue2,"toValue", newStorageValue3);
                            c.getClient(finalI).command("ubotUpdateStorage","executableContractId", executableContract.getId(),"storageName","default","toValue", newStorageValue3);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        while(true) {
            Binder res = ts.client.command("ubotGetStorage", "executableContractId", executableContract.getId(), "storageNames", Do.listOf("default"));
            System.out.println(res);
            if(res.getBinderOrThrow("current").get("default") != null && res.getBinderOrThrow("current").get("default").equals(newStorageValue3) && (res.getBinderOrThrow("pending").get("default") == null || res.getBinderOrThrow("pending").getBinder("default").size() == 0)) {
                break;
            }
            Thread.sleep(1000);
        }


        quorumClients2.forEach(c->{
            for(int i = 0; i < c.size(); i++)
                if (i != offline) {
                    int finalI = i;
                    Do.inParallel(()->{
                        try {
                            c.getClient(finalI).command("ubotCloseSession","executableContractId", executableContract.getId(), "finished", true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
        });

        AsyncEvent readyEvent4 = new AsyncEvent();
        AtomicInteger readyCounter4 = new AtomicInteger();

        for(int i = 0; i < ts.clients.size(); i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = ts.clients.get(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    Thread.sleep(500);
                    if(res.getBinder("session").isEmpty()) {
                        if (readyCounter4.incrementAndGet() == ts.clients.size()) {
                            readyEvent4.fire();
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

        readyEvent4.await();

        ts.nodes.remove(offline);
        ts.shutdown();
    }

    @Test
    public void createSessionConcurrentRequests() throws Exception {
        TestSpace ts = prepareTestSpace();
        //ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.getStateData().put("js", "simple JS code");
        executableContract.seal();
        System.out.println("EID = " + executableContract.getId());

        List<Contract> requestContracts = new ArrayList<>();
        for (int i = 0; i < client.size(); i++) {
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id",executableContract.getId());
            requestContract.getStateData().put("method_name","getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));

            ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
                Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

            requestContracts.add(requestContract);
        }
        for (int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(()-> {
                System.out.println(client.getClient(finalI).command("ubotCreateSession", "packedRequest", requestContracts.get(finalI).getPackedTransaction()));
            });
        }

        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();

        for (int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                    Thread.sleep(200);
                    if (res.get("session") != null && res.getBinderOrThrow("session").getString("state", "").equals("OPERATIONAL")) {
                        if (readyCounter.incrementAndGet() == client.size()) {
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

    @Test
    public void createSessionConcurrentRequestsPaid() throws Exception {
        TestSpace ts = prepareTestSpace();
        //ts.nodes.forEach(m->m.node.setVerboseLevel(BASE));
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.getStateData().put("js", "simple JS code");
        executableContract.seal();
        System.out.println("EID = " + executableContract.getId());

        int COST = 3;

        List<Contract> requestContracts = new ArrayList<>();
        List<Contract> uContracts = new ArrayList<>();
        for (int i = 0; i < client.size(); i++) {
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id",executableContract.getId());
            requestContract.getStateData().put("method_name","getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));

            ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
                    Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

            requestContracts.add(requestContract);
            Contract u = getApprovedUContract(ts);
            u = u.createRevision(ts.myKey);
            u.getStateData().put("transaction_units",u.getStateData().getIntOrThrow("transaction_units")-COST);
            u.seal();

            uContracts.add(u);
            ts.uContract = null;
        }
        for (int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(()-> {
                System.out.println(client.getClient(finalI).command("ubotCreateSessionPaid", "packedU",uContracts.get(finalI).getPackedTransaction(),"packedRequest", requestContracts.get(finalI).getPackedTransaction()));
            });
        }

        AtomicInteger readyCounter = new AtomicInteger();
        AtomicReference<Binder> session = new AtomicReference<>();
        AsyncEvent readyEvent = new AsyncEvent();

        for (int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                    Thread.sleep(200);
                    if (res.get("session") != null && res.getBinderOrThrow("session").getString("state", "").equals("OPERATIONAL")) {
                        if (readyCounter.incrementAndGet() == client.size()) {
                            session.set(res.getBinderOrThrow("session"));
                            readyEvent.fire();
                        }
                        break;
                    }
                }
            });
        }
        readyEvent.await();
        Thread.sleep(3000);

        HashId id = (HashId) session.get().get("requestId");
        for(int i  = 0; i < client.size();i++) {
            ItemState expectedState = requestContracts.get(i).getId().equals(id) ? ItemState.APPROVED : ItemState.DECLINED;
            assertEquals(client.getState(uContracts.get(i).getId()).state,expectedState);
        }

        ts.shutdown();
    }


    @Ignore
    @Test
    public void createUBotRegistryContract() throws Exception {

        final int N = 30;
//        final String domain = "test-ubot.mainnetwork.io";
//        final String ip = "104.248.143.106";
        final String domain = "localhost";
        final String ip = "127.0.0.1";

        Contract contract = new Contract(TestKeys.privateKey(1));
        contract.getKeysToSignWith().clear();

        List<Binder> topology = new ArrayList<>();
        ListRole listRole = new ListRole("ubots",contract);
        listRole.setMode(ListRole.Mode.ALL);

        for(int i = 0; i < N; i++) {
            PublicKey publicKey = new PublicKey(Do.read("./src/ubot_config/ubot0/config/keys/ubot_"+i+".public.unikey"));
            listRole.addRole(new SimpleRole("ubot"+i,contract,Do.listOf(publicKey.getLongAddress())));
            topology.add(Binder.of(
                    "number",i,
                    "key", Base64u.encodeString(publicKey.pack()),
                    "domain_urls",Do.listOf("https://"+domain+":"+(17000+i)),
                    "direct_urls",Do.listOf("http://"+ip+":"+(17000+i))
                    ));
        }

        contract.addRole(listRole);
        contract.getStateData().put("topology",topology);
        contract.seal();


        new FileOutputStream("ubot_registry_contract.unicon").write(contract.getLastSealedBinary());


        Contract c = new Contract(TestKeys.privateKey(1));
        c.addNewItems(contract);
        c.seal();

//        Client client = new Client("universa.pro",null,yourkey);
//        System.out.println(client.register(c.getPackedTransaction(),10000));


    }


}
