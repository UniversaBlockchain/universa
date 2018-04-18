/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.db.DbPool;
import com.icodici.db.PooledDb;
import com.icodici.universa.Approvable;
import com.icodici.universa.Core;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.xml.crypto.Data;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class MainTest {

    @Ignore
    @Test
    public void checkMemoryLeaks() throws Exception {

        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");
        List<Ledger> ledgers = new ArrayList<>();

        for (int it = 0; it < 100; it++) {
            if (it % 10 == 0)
                System.out.println("Iteration " + it);
            dbUrls.stream().forEach(url -> {
                try {
                    clearLedger(url);
                    PostgresLedger ledger = new PostgresLedger(url);
                    ledgers.add(ledger);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            TestSpace ts = prepareTestSpace();

            ts.node.config.getKeysWhiteList().add(TestKeys.publicKey(3));
            Contract testContract = new Contract(ts.myKey);
            testContract.seal();
            assertTrue(testContract.isOk());
            Parcel parcel = createParcelWithFreshTU(ts.client, testContract, Do.listOf(ts.myKey));
            ts.client.registerParcel(parcel.pack(), 18000);

            Contract testContract2 = new Contract(ts.myKey);
            testContract2.seal();
            assertTrue(testContract2.isOk());
            ts.client.register(testContract2.getPackedTransaction(), 18000);

            ts.nodes.forEach(x -> x.shutdown());

            ts.myKey = null;
            ts.nodes.clear();
            ts.node = null;
            ts.nodes = null;
            ts.client = null;
            ts = null;

            ledgers.stream().forEach(ledger -> ledger.close());
            ledgers.clear();
            System.gc();
            Thread.sleep(2000);
        }
    }

    @Ignore
    @Test
    public void checkPublicKeyMemoryLeak() throws Exception {

        byte[] bytes = Do.read("./src/test_contracts/keys/tu_key.public.unikey");

        for (int it = 0; it < 10000; it++) {
            PublicKey pk = new PublicKey(bytes);
            pk = null;
            System.gc();
        }
    }
    @Before
    public void clearLedgers() throws Exception {
        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");
        List<Ledger> ledgers = new ArrayList<>();
        dbUrls.stream().forEach(url -> {
            try {
                clearLedger(url);
                PostgresLedger ledger = new PostgresLedger(url);
                ledgers.add(ledger);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        LogPrinter.showDebug(false);
    }

    @Test
    public void startNode() throws Exception {
        String path = new File("src/test_node_config_v2/node1").getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, "--nolog"};
        Main main = new Main(args);
        main.waitReady();
        BufferedLogger l = main.logger;

        Client client = new Client(
                "http://localhost:8080",
                TestKeys.privateKey(3),
                main.getNodePublicKey(),
                null
        );

        Binder data = client.command("status");
        data.getStringOrThrow("status");
//        assertThat(data.getListOrThrow("log").size(), greaterThan(3));
        BasicHttpClient.Answer a = client.request("ping");
        assertEquals("200: {ping=pong}", a.toString());

        Contract c = new Contract();
        c.setIssuerKeys(TestKeys.publicKey(3));
        c.addSignerKey(TestKeys.privateKey(3));
        c.registerRole(new RoleLink("owner", "issuer"));
        c.registerRole(new RoleLink("creator", "issuer"));
        c.setExpiresAt(ZonedDateTime.now().plusDays(2));
        byte[] sealed = c.seal();
//        Bytes.dump(sealed);

        Contract c1 = new Contract(sealed);
        assertArrayEquals(c.getLastSealedBinary(), c1.getLastSealedBinary());

        main.cache.put(c, ItemResult.UNDEFINED);
        assertNotNull(main.cache.get(c.getId()));

        URL url = new URL("http://localhost:8080/contracts/" + c.getId().toBase64String());
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        byte[] data2 = Do.read(con.getInputStream());

        assertArrayEquals(c.getPackedTransaction(), data2);

        url = new URL("http://localhost:8080/network");
        con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        assertEquals(200, con.getResponseCode());
        Binder bres = Boss.unpack((Do.read(con.getInputStream())))
                .getBinderOrThrow("response");
        List<Binder> ni = bres.getBinders("nodes");
        String pubUrls = ni.stream().map(x -> x.getStringOrThrow("url"))
                .collect(Collectors.toList())
                .toString();

        assertEquals("[http://localhost:8080, http://localhost:6002, http://localhost:6004, http://localhost:6006]", pubUrls);

        main.shutdown();
        main.logger.stopInterceptingStdOut();
        main.logger.getCopy().forEach(x -> System.out.println(x));
    }

    Main createMain(String name, boolean nolog) throws InterruptedException {
        return createMain(name,"",nolog);
    }

    Main createMain(String name, String postfix, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2" + postfix + "/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }
                //m.config.getKeysWhiteList().add(m.config.getTransactionUnitsIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + name);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }

    Main createMainFromDb(String dbUrl, boolean nolog) throws InterruptedException {
        String[] args = new String[]{"--test","--database", dbUrl, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }
                //m.config.getKeysWhiteList().add(m.config.getTransactionUnitsIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + dbUrl);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }

    @Test
    public void networkReconfigurationTestSerial() throws Exception {

        //create 4 nodes from config file. 3 know each other. 4th knows everyone. nobody knows 4th
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mm.add(createMain("node" + (i + 1),"_dynamic_test", false));
        }
        //shutdown nodes
        for(Main m : mm) {
            m.shutdown();
        }
        mm.clear();

        //initialize same nodes from db
        List<String> dbUrls = new ArrayList<>();
        Thread.sleep(1000);
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");

        for (int i = 0; i < 4; i++) {
            mm.add(createMainFromDb(dbUrls.get(i), false));
        }

        PrivateKey myKey = TestKeys.privateKey(3);
        Main main = mm.get(3);

        PrivateKey universaKey = new PrivateKey(Do.read("./src/test_contracts/keys/tu_key.private.unikey"));
        Contract contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with UNKNOWN node. Shouldn't succeed
        int attempts = 3;

        Client client = new Client(universaKey, main.myInfo, null);

        ItemResult rr = client.register(contract.getPackedTransaction(), 5000);
        while (attempts-- > 0) {
            rr = client.getState(contract.getId());
            System.out.println(rr);
            Thread.currentThread().sleep(1000);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state,ItemState.PENDING_POSITIVE);

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with KNOWN node. Should succeed
        Client clientKnown = new Client(universaKey, mm.get(0).myInfo, null);
        clientKnown.register(contract.getPackedTransaction(), 15000);
        while (true) {
            rr = clientKnown.getState(contract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state,ItemState.APPROVED);

        //Make 4th node KNOWN to other nodes
        for(int i = 0; i < 3; i++) {
            mm.get(i).node.addNode(main.myInfo);
        }

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

//        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
//        mm.get(0).setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);

        client.register(contract.getPackedTransaction(), 15000);
        while (true) {
            rr = client.getState(contract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state,ItemState.APPROVED);
//        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
//        mm.get(0).setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);

        //Make 4th node UNKNOWN to other nodes
        for(int i = 0; i < 3; i++) {
            mm.get(i).node.removeNode(main.myInfo);
        }

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with UNKNOWN node. Shouldn't succeed
        attempts = 3;
        rr = client.register(contract.getPackedTransaction(), 15000);
        while (attempts-- > 0) {
            rr = client.getState(contract.getId());
            Thread.currentThread().sleep(1000);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state,ItemState.PENDING_POSITIVE);

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with KNOWN node. Should succeed
        clientKnown.register(contract.getPackedTransaction(), 15000);
        while (true) {
            rr = clientKnown.getState(contract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state,ItemState.APPROVED);

        for(Main m : mm) {
            m.shutdown();
        }
    }

    @Test // no asserts
    public void networkReconfigurationTestParallel() throws Exception {
        //create 4 nodes from config file. 3 know each other. 4th knows everyone. nobody knows 4th
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mm.add(createMain("node" + (i + 1), "_dynamic_test", false));
        }
        //shutdown nodes
        for (Main m : mm) {
            m.shutdown();
        }
        mm.clear();

        //initialize same nodes from db
        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");

        Random rand = new Random();
        rand.setSeed(new Date().getTime());

        final ArrayList<Integer> clientSleeps = new ArrayList<>();
        final ArrayList<Integer> nodeSleeps = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mm.add(createMainFromDb(dbUrls.get(i), false));
            nodeSleeps.add(rand.nextInt(100));
        }

        PrivateKey myKey = TestKeys.privateKey(3);

        final ArrayList<Client> clients = new ArrayList<>();
        final ArrayList<Integer> clientNodes = new ArrayList<>();
        final ArrayList<Contract> contracts = new ArrayList<>();
        final ArrayList<Parcel> parcels = new ArrayList<>();
        final ArrayList<Boolean> contractsApproved = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Contract contract = new Contract(myKey);
            contract.seal();
            assertTrue(contract.isOk());
            contracts.add(contract);
            contractsApproved.add(false);
            NodeInfo info = mm.get(rand.nextInt(3)).myInfo;
            clientNodes.add(info.getNumber());
            Client client = new Client(TestKeys.privateKey(i), info, null);
            clients.add(client);
            clientSleeps.add(rand.nextInt(100));
            Parcel parcel = createParcelWithFreshTU(client,contract,Do.listOf(myKey));
            parcels.add(parcel);
        }

        Semaphore semaphore = new Semaphore(-39);
        final AtomicInteger atomicInteger = new AtomicInteger(40);
        for (int i = 0; i < 40; i++) {
            int finalI = i;
            Thread th = new Thread(() -> {
                try {
                    //Thread.sleep(clientSleeps.get(finalI));
                    Thread.sleep(clientSleeps.get(finalI));
                    Contract contract = contracts.get(finalI);
                    Client client = clients.get(finalI);
                    System.out.println("Register item " + contract.getId().toBase64String() + " @ node #" + clientNodes.get(finalI));
                    client.registerParcel(parcels.get(finalI).pack(), 15000);
                    ItemResult rr;
                    while (true) {
                        rr = client.getState(contract.getId());
                        Thread.currentThread().sleep(50);
                        if (!rr.state.isPending())
                            break;
                    }
                    assertEquals(rr.state, ItemState.APPROVED);
                    semaphore.release();
                    atomicInteger.decrementAndGet();
                    contractsApproved.set(finalI, true);
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                    fail(clientError.getMessage());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            });
            th.start();
        }

        for (int i = 0; i < 3; i++) {
            int finalI = i;
            Thread th = new Thread(() -> {
                try {
                    //Thread.sleep(nodeSleeps.get(finalI));
                    Thread.sleep(nodeSleeps.get(finalI)  );
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
                System.out.println("Adding new node @ node #" + (finalI + 1));
                mm.get(finalI).node.addNode(mm.get(3).myInfo);
                System.out.println("Done new node @ node #" + (finalI + 1));

            });
            th.start();
        }

        Thread.sleep(5000);

        if (!semaphore.tryAcquire(15, TimeUnit.SECONDS)) {
            for (int i = 0; i < contractsApproved.size(); i++) {
                if (!contractsApproved.get(i)) {
                    System.out.println("Stuck item:" + contracts.get(i).getId().toBase64String());
                }
            }

            System.out.print("Client sleeps: ");
            for (Integer s : clientSleeps) {
                System.out.print(s + ", ");
            }
            System.out.println();

            System.out.print("Node sleeps: ");
            for (Integer s : nodeSleeps) {
                System.out.print(s + ", ");
            }
            System.out.println();

            fail("Items stuck: " + atomicInteger.get());
        }

        for (Main m : mm) {
            m.shutdown();
        }
        System.gc();
    }

    @Test
    public void reconfigurationContractTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));

        List<Main> mm = new ArrayList<>();
        List<PrivateKey> nodeKeys = new ArrayList<>();
        List<PrivateKey> nodeKeysNew = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mm.add(createMain("node" + (i + 1), "_dynamic_test", false));
            if(i < 3)
                nodeKeys.add(new PrivateKey(Do.read("./src/test_node_config_v2_dynamic_test/node" + (i + 1) + "/tmp/node2_" + (i + 1) + ".private.unikey")));
            nodeKeysNew.add(new PrivateKey(Do.read("./src/test_node_config_v2_dynamic_test/node" + (i + 1) + "/tmp/node2_" + (i + 1) + ".private.unikey")));
        }

        List<NodeInfo> netConfig = mm.get(0).netConfig.toList();
        List<NodeInfo> netConfigNew = mm.get(3).netConfig.toList();

        for (Main m : mm) {
            m.shutdown();
        }
        mm.clear();

        Contract configContract = createNetConfigContract(netConfig,issuerKey);

        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");

        for (int i = 0; i < 4; i++) {
            mm.add(createMainFromDb(dbUrls.get(i), false));
        }

        Client client = new Client(TestKeys.privateKey(0), mm.get(0).myInfo, null);

        Parcel parcel = createParcelWithFreshTU(client, configContract,Do.listOf(issuerKey));
        client.registerParcel(parcel.pack(),15000);

        ItemResult rr;
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);

        configContract = createNetConfigContract(configContract,netConfigNew,nodeKeys);

        parcel = createParcelWithFreshTU(client, configContract,nodeKeys);
        client.registerParcel(parcel.pack(),15000);
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);
        Thread.sleep(1000);
        for (Main m : mm) {
            assertEquals(m.config.getPositiveConsensus(), 3);
        }
        configContract = createNetConfigContract(configContract,netConfig,nodeKeys);

        parcel = createParcelWithFreshTU(client, configContract,nodeKeys);
        client.registerParcel(parcel.pack(),15000);
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(50);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);
        Thread.sleep(1000);
        for (Main m : mm) {
            assertEquals(m.config.getPositiveConsensus(), 2);
        }

        for (Main m : mm) {
            m.shutdown();
        }
    }

    private Contract createNetConfigContract(Contract contract, List<NodeInfo> netConfig, Collection<PrivateKey> currentConfigKeys) throws IOException {
        contract = contract.createRevision();
        ListRole listRole = new ListRole("owner");
        for(NodeInfo ni: netConfig) {
            SimpleRole role = new SimpleRole(ni.getName());
            contract.registerRole(role);
            role.addKeyRecord(new KeyRecord(ni.getPublicKey()));
            listRole.addRole(role);
        }
        listRole.setQuorum(netConfig.size()-1);
        contract.registerRole(listRole);
        contract.getStateData().set("net_config",netConfig);
        List<KeyRecord> creatorKeys = new ArrayList<>();
        for(PrivateKey key : currentConfigKeys) {
            creatorKeys.add(new KeyRecord(key.getPublicKey()));
            contract.addSignerKey(key);
        }
        contract.setCreator(creatorKeys);
        contract.seal();
        return contract;
    }

    private Contract createNetConfigContract(List<NodeInfo> netConfig,PrivateKey issuerKey) throws IOException {
        Contract contract = new Contract();
        contract.setIssuerKeys(issuerKey.getPublicKey());
        contract.registerRole(new RoleLink("creator", "issuer"));
        ListRole listRole = new ListRole("owner");
        for(NodeInfo ni: netConfig) {
            SimpleRole role = new SimpleRole(ni.getName());
            contract.registerRole(role);
            role.addKeyRecord(new KeyRecord(ni.getPublicKey()));
            listRole.addRole(role);
        }
        listRole.setQuorum(netConfig.size()-1);
        contract.registerRole(listRole);
        RoleLink ownerLink = new RoleLink("ownerlink","owner");
        ChangeOwnerPermission changeOwnerPermission = new ChangeOwnerPermission(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("net_config",null);
        Binder modifyDataParams = Binder.of("fields",fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink,modifyDataParams);
        contract.addPermission(changeOwnerPermission);
        contract.addPermission(modifyDataPermission);
        contract.setExpiresAt(ZonedDateTime.now().plusYears(40));
        contract.getStateData().set("net_config",netConfig);
        contract.addSignerKey(issuerKey);
        contract.seal();
        return contract;
    }

    @Test
    public void checkVerbose() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(3);

        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }
        System.out.println("---------- verbose nothing ---------------");

        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.network.getVerboseLevel());
        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.node.getVerboseLevel());

        Contract testContract = new Contract(myKey);
        testContract.seal();
        assertTrue(testContract.isOk());
        Parcel parcel = createParcelWithFreshTU(client, testContract,Do.listOf(myKey));
        client.registerParcel(parcel.pack(), 1000);
        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());

        main.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        System.out.println("---------- verbose base ---------------");

        Contract testContract2 = new Contract(myKey);
        testContract2.seal();
        assertTrue(testContract2.isOk());
        Parcel parcel2 = createParcelWithFreshTU(client, testContract2,Do.listOf(myKey));
        client.registerParcel(parcel2.pack(), 1000);
        ItemResult itemResult2 = client.getState(parcel2.getPayloadContract().getId());

        assertEquals (DatagramAdapter.VerboseLevel.BASE, main.network.getVerboseLevel());
        assertEquals (DatagramAdapter.VerboseLevel.BASE, main.node.getVerboseLevel());

        main.setVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        System.out.println("---------- verbose nothing ---------------");

        Contract testContract3 = new Contract(myKey);
        testContract3.seal();
        assertTrue(testContract3.isOk());
        Parcel parcel3 = createParcelWithFreshTU(client, testContract3,Do.listOf(myKey));
        client.registerParcel(parcel3.pack(), 1000);
        ItemResult itemResult3 = client.getState(parcel3.getPayloadContract().getId());

        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.network.getVerboseLevel());
        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.node.getVerboseLevel());

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkUDPVerbose() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(3);

        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }
        System.out.println("---------- verbose nothing ---------------");

        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.network.getUDPVerboseLevel());

        Contract testContract = new Contract(myKey);
        testContract.seal();
        assertTrue(testContract.isOk());
        Parcel parcel = createParcelWithFreshTU(client, testContract,Do.listOf(myKey));
        client.registerParcel(parcel.pack(), 1000);
        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        System.out.println("---------- verbose base ---------------");

        Contract testContract2 = new Contract(myKey);
        testContract2.seal();
        assertTrue(testContract2.isOk());
        Parcel parcel2 = createParcelWithFreshTU(client, testContract2,Do.listOf(myKey));
        client.registerParcel(parcel2.pack(), 1000);
        ItemResult itemResult2 = client.getState(parcel2.getPayloadContract().getId());

        assertEquals (DatagramAdapter.VerboseLevel.BASE, main.network.getUDPVerboseLevel());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
        System.out.println("---------- verbose detailed ---------------");

        Contract testContract4 = new Contract(myKey);
        testContract4.seal();
        assertTrue(testContract4.isOk());
        Parcel parcel4 = createParcelWithFreshTU(client, testContract4,Do.listOf(myKey));
        client.registerParcel(parcel4.pack(), 1000);
        ItemResult itemResult4 = client.getState(parcel4.getPayloadContract().getId());

        assertEquals (DatagramAdapter.VerboseLevel.DETAILED, main.network.getUDPVerboseLevel());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        System.out.println("---------- verbose nothing ---------------");

        Contract testContract3 = new Contract(myKey);
        testContract3.seal();
        assertTrue(testContract3.isOk());
        Parcel parcel3 = createParcelWithFreshTU(client, testContract3,Do.listOf(myKey));
        client.registerParcel(parcel3.pack(), 1000);
        ItemResult itemResult3 = client.getState(parcel3.getPayloadContract().getId());

        assertEquals (DatagramAdapter.VerboseLevel.NOTHING, main.network.getUDPVerboseLevel());

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkShutdown() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(3);

        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Contract testContract = new Contract(myKey);
        for (int i = 0; i < 10; i++) {
            Contract nc = new Contract(myKey);
            testContract.addNewItems(nc);
        }
        testContract.seal();
        assertTrue(testContract.isOk());
        Parcel parcel = createParcelWithFreshTU(client, testContract,Do.listOf(myKey));
        client.registerParcel(parcel.pack());
        System.out.println(">> before shutdown state: " + client.getState(parcel.getPayloadContract().getId()));
        System.out.println(">> before shutdown state: " + client.getState(parcel.getPayloadContract().getNew().get(0).getId()));

        main.shutdown();

        mm.remove(main);
        main = createMain("node1", false);
        mm.add(main);
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }
        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        ItemResult itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());
        System.out.println(">> after shutdown state: " + itemResult + " and new " + itemResult2);

        while (itemResult.state.isPending()) {
            Thread.currentThread().sleep(100);
            itemResult = client.getState(parcel.getPayloadContract().getId());
            System.out.println(">> wait result: " + itemResult);
        }
        itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());

        assertEquals (ItemState.UNDEFINED, itemResult.state);
        assertEquals (ItemState.UNDEFINED, itemResult2.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkRestartUDP() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(3);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Contract testContract = new Contract(myKey);
        for (int i = 0; i < 10; i++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            testContract.addNewItems(nc);
        }
        testContract.seal();
        assertTrue(testContract.isOk());

        Parcel parcel = createParcelWithFreshTU(client, testContract,Do.listOf(myKey));
        client.registerParcel(parcel.pack());

        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        while (itemResult.state.isPending()) {
            Thread.currentThread().sleep(100);
            itemResult = client.getState(parcel.getPayloadContract().getId());
            System.out.println(">> wait result: " + itemResult);
        }
        ItemResult itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());

        System.out.println(">> before restart state: " + itemResult);
        System.out.println(">> before restart state: " + itemResult2);

        main.restartUDPAdapter();
        main.waitReady();

        itemResult = client.getState(parcel.getPayloadContract().getId());
        itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());
        System.out.println(">> after restart state: " + itemResult + " and new " + itemResult2);

        while (itemResult.state.isPending()) {
            Thread.currentThread().sleep(100);
            itemResult = client.getState(parcel.getPayloadContract().getId());
            System.out.println(">> wait result: " + itemResult);
        }

        Thread.sleep(5000);
        itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());

        assertEquals (ItemState.APPROVED, itemResult.state);
        assertEquals (ItemState.APPROVED, itemResult2.state);

        mm.forEach(x -> x.shutdown());
    }

    public synchronized Parcel createParcelWithFreshTU(Client client, Contract c, Collection<PrivateKey> keys) throws Exception {
        Set<PublicKey> ownerKeys = new HashSet();
        keys.stream().forEach(key->ownerKeys.add(key.getPublicKey()));
        Contract stepaTU = InnerContractsService.createFreshTU(100000000, ownerKeys);
        stepaTU.check();
        stepaTU.traceErrors();

        PrivateKey clientPrivateKey = client.getSession().getPrivateKey();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/tu_key.private.unikey"));
        client.getSession().setPrivateKey(newPrivateKey);
        client.restart();

        ItemResult itemResult = client.register(stepaTU.getPackedTransaction(), 5000);

        client.getSession().setPrivateKey(clientPrivateKey);
        client.restart();

        assertEquals(ItemState.APPROVED, itemResult.state);
        Set<PrivateKey> keySet = new HashSet<>();
        keySet.addAll(keys);
        return ContractsService.createParcel(c, stepaTU, 150, keySet);
    }

    @Test
    public void registerContractWithAnonymousId() throws Exception {
        TestSpace ts = prepareTestSpace();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/tu_key.private.unikey"));

        byte[] myAnonId = newPrivateKey.createAnonymousId();

        Contract contract = new Contract();
        contract.setExpiresAt(ZonedDateTime.now().plusDays(90));
        Role r = contract.setIssuerKeys(AnonymousId.fromBytes(myAnonId));
        contract.registerRole(new RoleLink("owner", "issuer"));
        contract.registerRole(new RoleLink("creator", "issuer"));
        contract.addPermission(new ChangeOwnerPermission(r));
        contract.addSignerKey(newPrivateKey);
        contract.seal();

        assertTrue(contract.isOk());
        System.out.println("contract.check(): " + contract.check());
        contract.traceErrors();

        ts.client.getSession().setPrivateKey(newPrivateKey);
        ts.client.restart();

        ItemResult itemResult = ts.client.register(contract.getPackedTransaction(), 5000);

        assertEquals(ItemState.APPROVED, itemResult.state);

        ts.node.shutdown();
    }

    private TestSpace prepareTestSpace() throws Exception {
        return prepareTestSpace(TestKeys.privateKey(3));
    }

    private TestSpace prepareTestSpace(PrivateKey key) throws Exception {
        TestSpace testSpace = new TestSpace();
        testSpace.nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            testSpace.nodes.add(createMain("node" + (i + 1), false));
        testSpace.node = testSpace.nodes.get(0);
        assertEquals("http://localhost:8080", testSpace.node.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", testSpace.node.myInfo.publicUrlString());
        testSpace.myKey = key;
        testSpace.client = new Client(testSpace.myKey, testSpace.node.myInfo, null);
        return testSpace;
    }

    private class TestSpace {
        public List<Main> nodes = null;
        public Main node = null;
        PrivateKey myKey = null;
        Client client = null;
    }

    private static final int MAX_PACKET_SIZE = 512;
    protected void sendBlock(UDPAdapter.Block block, DatagramSocket socket) throws InterruptedException {

        if(!block.isValidToSend()) {
            block.prepareToSend(MAX_PACKET_SIZE);
        }

        List<DatagramPacket> outs = new ArrayList(block.getDatagrams().values());

        try {

            for (DatagramPacket d : outs) {
                socket.send(d);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void sendHello(NodeInfo myNodeInfo, NodeInfo destination, UDPAdapter udpAdapter, DatagramSocket socket) throws InterruptedException {
//      System.out.println(">> send froud from " + myNodeInfo.getNumber() + " to " + destination.getNumber());
        Binder binder = Binder.fromKeysValues(
                "data", myNodeInfo.getNumber()
        );
        UDPAdapter.Block block = udpAdapter.createTestBlock(myNodeInfo.getNumber(), destination.getNumber(),
                new Random().nextInt(Integer.MAX_VALUE), UDPAdapter.PacketTypes.HELLO,
                destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort(),
                Boss.pack(binder));
        sendBlock(block, socket);
    }

    @Ignore
    @Test
    public void udpDisruptionTest() throws Exception{
        List<Main> mm = new ArrayList<>();
        final int NODE_COUNT = 4;
        final int PORT_BASE = 12000;

        for (int i = 0; i < NODE_COUNT; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }
//        mm.get(0).setUDPVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
//        mm.get(1).setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);

        class TestRunnable implements Runnable {

            int finalI;
            int finalJ;
            boolean alive = true;

            @Override
            public void run() {
                try {
                    NodeInfo source = mm.get(finalI).myInfo;
                    NodeInfo destination = mm.get(finalJ).myInfo;
                    DatagramSocket socket = new DatagramSocket(PORT_BASE+ finalI*NODE_COUNT+finalJ);

                    while (alive) {
                        sendHello(source,destination,mm.get(finalI).network.getUDPAdapter(),socket);
                    }
                } catch (Exception e) {
                    System.out.println("runnable exception: " + e.toString());
                }
            }
        }

        List<Thread> threadsList = new ArrayList<>();
        List<TestRunnable> runnableList = new ArrayList<>();
        for(int i = 0; i < NODE_COUNT; i++) {
            for(int j = 0; j < NODE_COUNT;j++) {
                if(j == i)
                    continue;
                final int finalI = i;
                final int finalJ = j;
                TestRunnable runnableSingle = new TestRunnable();
                runnableList.add(runnableSingle);
                threadsList.add(
                new Thread(() -> {
                    runnableSingle.finalI = finalI;
                    runnableSingle.finalJ = finalJ;
                    runnableSingle.run();

                }));
            }
        }

        for (Thread th : threadsList) {
            th.start();
        }
        Thread.sleep(5000);

        PrivateKey myKey = TestKeys.privateKey(0);
        Client client = new Client(myKey,mm.get(0).myInfo,null);

        Contract contract = new Contract(myKey);
        contract.seal();

        Parcel parcel = createParcelWithFreshTU(client,contract,Do.listOf(myKey));
        client.registerParcel(parcel.pack(),60000);
        ItemResult rr;
        while(true) {
            rr = client.getState(contract.getId());
            if(!rr.state.isPending())
                break;
        }

        assertEquals(rr.state, ItemState.APPROVED);

        for (TestRunnable tr : runnableList) {
            tr.alive = false;
        }
        for (Thread th : threadsList) {
            th.interrupt();
        }
        mm.forEach(x -> x.shutdown());
    }

    @Ignore
    @Test
    public void dbSanitationTest() throws Exception {
        final int NODE_COUNT = 4;
        PrivateKey myKey = TestKeys.privateKey(NODE_COUNT);

        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");
        List<Ledger> ledgers = new ArrayList<>();
        dbUrls.stream().forEach(url -> {
            try {
//                clearLedger(url);
                PostgresLedger ledger = new PostgresLedger(url);
                ledgers.add(ledger);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Random random = new Random(123);

        List<Contract> origins = new ArrayList<>();
        List<Contract> newRevisions = new ArrayList<>();
        List<Contract> newContracts = new ArrayList<>();

        final int N = 100;
        for(int i = 0; i < N; i++) {
            Contract origin = new Contract(myKey);
            origin.seal();
            origins.add(origin);

            Contract newRevision = origin.createRevision(myKey);

            if(i < N/2) {
                //ACCEPTED
                newRevision.setOwnerKeys(TestKeys.privateKey(NODE_COUNT + 1).getPublicKey());
            } else {
                //DECLINED
                //State is equal
            }

            Contract newContract = new Contract(myKey);
            newRevision.addNewItems(newContract);
            newRevision.seal();

            newContracts.add(newContract);
            newRevisions.add(newRevision);
            int unfinishedNodesCount = random.nextInt(2)+1;
            Set<Integer> unfinishedNodesNumbers = new HashSet<>();
            while(unfinishedNodesCount > unfinishedNodesNumbers.size()) {
                unfinishedNodesNumbers.add(random.nextInt(NODE_COUNT)+1);
            }

            System.out.println("item# "+ newRevision.getId().toBase64String().substring(0,6) + " nodes " + unfinishedNodesNumbers.toString());
            int finalI = i;
            for(int j = 0; j < NODE_COUNT;j++) {
                boolean finished = !unfinishedNodesNumbers.contains(j+1);
                Ledger ledger = ledgers.get(j);


                StateRecord originRecord = ledger.findOrCreate(origin.getId());
                originRecord.setExpiresAt(origin.getExpiresAt());
                originRecord.setCreatedAt(origin.getCreatedAt());

                StateRecord newRevisionRecord = ledger.findOrCreate(newRevision.getId());
                newRevisionRecord.setExpiresAt(newRevision.getExpiresAt());
                newRevisionRecord.setCreatedAt(newRevision.getCreatedAt());

                StateRecord newContractRecord = ledger.findOrCreate(newContract.getId());
                newContractRecord.setExpiresAt(newContract.getExpiresAt());
                newContractRecord.setCreatedAt(newContract.getCreatedAt());

                if(finished) {
                    if(finalI < N/2) {
                        originRecord.setState(ItemState.REVOKED);
                        newContractRecord.setState(ItemState.APPROVED);
                        newRevisionRecord.setState(ItemState.APPROVED);
                    } else {
                        originRecord.setState(ItemState.APPROVED);
                        newContractRecord.setState(ItemState.UNDEFINED);
                        newRevisionRecord.setState(ItemState.DECLINED);
                    }
                } else {
                    originRecord.setState(ItemState.LOCKED);
                    originRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                    newContractRecord.setState(ItemState.LOCKED_FOR_CREATION);
                    newContractRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                    newRevisionRecord.setState(finalI < N/2 ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
                }

                originRecord.save();
                ledger.putItem(originRecord,origin, Instant.now().plusSeconds(3600*24));
                newRevisionRecord.save();
                ledger.putItem(newRevisionRecord,newRevision, Instant.now().plusSeconds(3600*24));
                if(newContractRecord.getState() == ItemState.UNDEFINED) {
                    newContractRecord.destroy();
                } else {
                    newContractRecord.save();
                }
            }
        }
        ledgers.stream().forEach(ledger -> ledger.close());
        ledgers.clear();

        List<Main> mm = new ArrayList<>();
        List<Client> clients = new ArrayList<>();

        for (int i = 0; i < NODE_COUNT; i++) {
            Main m = createMain("node" + (i + 1), false);
            mm.add(m);
            Client client = new Client(TestKeys.privateKey(i), m.myInfo, null);
            clients.add(client);
        }

        while (true) {
            try {
                for(int i =0; i < NODE_COUNT; i++) {
                    clients.get(i).getState(newRevisions.get(0));
                }
                break;
            } catch (ClientError e) {
                Thread.sleep(1000);
                mm.stream().forEach( m -> System.out.println("node#" +m.myInfo.getNumber() + " is " +  (m.node.isSanitating() ? "" : "not ") + "sanitating"));
            }
        }

        Contract contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        ItemResult ir = clients.get(0).register(contract.getPackedTransaction(), 10000);
        ir.errors.toString();

        for(int i = 0; i < N; i++) {
            ItemResult rr = clients.get(i%NODE_COUNT).getState(newRevisions.get(i).getId());
            ItemState targetState = i < N/2 ? ItemState.APPROVED : ItemState.DECLINED;
            assertEquals(rr.state,targetState);
        }
        Thread.sleep(1000);
        mm.stream().forEach(m -> m.shutdown());
        Thread.sleep(1000);

        dbUrls.stream().forEach(url -> {
            try {
                PostgresLedger ledger = new PostgresLedger(url);
                assertTrue(ledger.findUnfinished().isEmpty());
                ledger.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void clearLedger(String url) throws Exception {
        Properties properties = new Properties();
        try(DbPool dbPool = new DbPool(url, properties, 64)) {
            try (PooledDb db = dbPool.db()) {
                try (PreparedStatement statement = db.statement("delete from items;")
                ) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = db.statement("delete from ledger;")
                ) {
                    statement.executeUpdate();
                }
            }
        }
    }

    @Ignore
    @Test
    public void nodeStatsTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);

        Thread.sleep(2000);
        int uptime = testSpace.client.getStats().getIntOrThrow("uptime");

        testSpace.nodes.get(0).config.setStatsIntervalSmall(Duration.ofSeconds(4));
        testSpace.nodes.get(0).config.setStatsIntervalBig(Duration.ofSeconds(60));
        testSpace.nodes.get(0).config.getKeysWhiteList().add(issuerKey.getPublicKey());

        while(testSpace.client.getStats().getIntOrThrow("uptime") >= uptime) {
            Thread.sleep(500);
        }

        for (int i = 0; i < 30; i++) {
            Instant now = Instant.now();
            Contract contract = new Contract(issuerKey);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(),1500);
            contract = new Contract(issuerKey);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(),1500);

            Thread.sleep(4000-(Instant.now().toEpochMilli()-now.toEpochMilli()));
            Binder binder = testSpace.client.getStats();
            assertEquals(binder.getIntOrThrow("smallIntervalApproved"),2);
            int target = i < 15 ? (i+1)*2 : 30;
            assertTrue(binder.getIntOrThrow("bigIntervalApproved") <= target && binder.getIntOrThrow("bigIntervalApproved") >= target-2);
        }
    }


    @Test
    public void resynItemTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);

        //shutdown one of nodes
        int absentNode = testSpace.nodes.size()-1;
        testSpace.nodes.get(absentNode).shutdown();
        testSpace.nodes.remove(absentNode);

        //register contract in non full network
        Contract contract = new Contract(issuerKey);
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(),1500);
        assertEquals(testSpace.client.getState(contract.getId()).state,ItemState.APPROVED);


        //recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n->n.shutdown());
        Thread.sleep(1000);
        testSpace = prepareTestSpace(issuerKey);
        assertEquals(testSpace.client.getState(contract.getId()).state,ItemState.APPROVED);


        //create client with absent node and check the contract
        Client absentNodeClient = new Client(testSpace.myKey,testSpace.nodes.get(absentNode).myInfo,null);
        assertEquals(absentNodeClient.getState(contract.getId()).state,ItemState.UNDEFINED);

        //start resync with a command
        absentNodeClient.resyncItem(contract.getId());

        //make sure resync didn't affect others
        assertEquals(testSpace.client.getState(contract.getId()).state,ItemState.APPROVED);


        //wait for new status
        ItemResult rr;
        while(true) {
            rr = absentNodeClient.getState(contract.getId());
            if(!rr.state.isPending())
                break;
            Thread.sleep(100);
        }
        assertEquals(rr.state,ItemState.APPROVED);

    }



    @Test(timeout = 30000)
    public void freeRegistrationsAllowedFromCoreVersion() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        ItemState expectedState = ItemState.UNDEFINED;
        if (Core.VERSION.contains("private"))
            expectedState = ItemState.APPROVED;
        System.out.println("Core.VERSION: " + Core.VERSION);
        System.out.println("expectedState: " + expectedState);
        ItemResult itemResult = client.register(contract.getPackedTransaction(), 5000);
        System.out.println("itemResult: " + itemResult);
        assertEquals(expectedState, itemResult.state);
    }



    @Test(timeout = 30000)
    public void freeRegistrationsAllowedFromConfigOrVersion() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        ItemState expectedState = ItemState.APPROVED;
        System.out.println("expectedState: " + expectedState);
        ItemResult itemResult = client.register(contract.getPackedTransaction(), 5000);
        System.out.println("itemResult: " + itemResult);
        assertEquals(expectedState, itemResult.state);

        main.config.setIsFreeRegistrationsAllowedFromYaml(false);
        contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        expectedState = ItemState.UNDEFINED;
        if (Core.VERSION.contains("private"))
            expectedState = ItemState.APPROVED;
        System.out.println("Core.VERSION: " + Core.VERSION);
        System.out.println("expectedState: " + expectedState);
        itemResult = client.register(contract.getPackedTransaction(), 5000);
        System.out.println("itemResult: " + itemResult);
        assertEquals(expectedState, itemResult.state);
    }

}