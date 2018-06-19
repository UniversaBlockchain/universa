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
import com.icodici.universa.Core;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

@Ignore("start it manually")
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

        main.cache.put(c, null);
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

                try {
                    m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/tu_key.public.unikey")));
                } catch (IOException e) {
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

                try {
                    m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/tu_key.public.unikey")));
                } catch (IOException e) {
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

    @Ignore
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
        Thread.sleep(5000);

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

    @Ignore
    @Test
    public void shutdownCycle() throws Exception {
        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");
        dbUrls.stream().forEach(url -> {
            try {
                clearLedger(url);
            } catch (Exception e) {
            }
        });
        for (int i=0; i < 50; i++) {
            checkShutdown();
            System.out.println("iteration " + i);
            Thread.sleep(5000);
            dbUrls.stream().forEach(url -> {
                try {
                    clearLedger(url);
                } catch (Exception e) {
                }
            });
        }
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

        Thread.sleep(7000);
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

        ts.nodes.forEach(x -> x.shutdown());
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
        Object tuContractLock = new Object();
        Contract tuContract = null;
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


    @Test
    public void test123() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendValue(ChronoField.DAY_OF_MONTH,2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.MONTH_OF_YEAR,2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.YEAR,4);

        System.out.println(now.format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth()-1).format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth()-1).minusMonths(1).format(builder.toFormatter()));
    }

    @Ignore
    @Test
    public void nodeStatsTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);

        Thread.sleep(2000);
        Binder b = testSpace.client.getStats(90);
        int uptime = b.getIntOrThrow("uptime");

        testSpace.nodes.get(0).config.setStatsIntervalSmall(Duration.ofSeconds(4));
        testSpace.nodes.get(0).config.setStatsIntervalBig(Duration.ofSeconds(60));
        testSpace.nodes.get(0).config.getKeysWhiteList().add(issuerKey.getPublicKey());

        while(testSpace.client.getStats(null).getIntOrThrow("uptime") >= uptime) {
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
            Binder binder = testSpace.client.getStats(90);

            assertEquals(binder.getIntOrThrow("smallIntervalApproved"),2);
            int target = i < 15 ? (i+1)*2 : 30;
            assertTrue(binder.getIntOrThrow("bigIntervalApproved") <= target && binder.getIntOrThrow("bigIntervalApproved") >= target-2);
        }

        testSpace.nodes.forEach(x -> x.shutdown());
    }


    @Test
    public void resynItemTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

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
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
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

        testSpace.nodes.forEach(x -> x.shutdown());

    }

    @Test
    public void verboseLevelTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);

        Contract contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(),8000);
        Thread.sleep(2000);
        testSpace.client.setVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING,DatagramAdapter.VerboseLevel.DETAILED,DatagramAdapter.VerboseLevel.NOTHING);
        contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(),8000);

        testSpace.nodes.forEach(x -> x.shutdown());
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

        mm.forEach(x -> x.shutdown());
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

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testTokenContractApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract tokenContract = ContractsService.createTokenContract(issuerPrivateKeys,ownerPublicKeys, "1000000");
        tokenContract.check();
        tokenContract.traceErrors();

        assertTrue(tokenContract.getOwner().isAllowedForKeys(ownerPublicKeys));
        assertTrue(tokenContract.getIssuer().isAllowedForKeys(issuerPrivateKeys));
        assertTrue(tokenContract.getCreator().isAllowedForKeys(issuerPrivateKeys));

        assertFalse(tokenContract.getOwner().isAllowedForKeys(issuerPrivateKeys));
        assertFalse(tokenContract.getIssuer().isAllowedForKeys(ownerPublicKeys));
        assertFalse(tokenContract.getCreator().isAllowedForKeys(ownerPublicKeys));

        assertTrue(tokenContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(tokenContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertEquals(InnerContractsService.getDecimalField(tokenContract, "amount"), new Decimal(1000000));

        assertEquals(tokenContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = tokenContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), 0.01);
        assertEquals(splitJoinParams.get("min_unit"), 0.01);
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List)splitJoinParams.get("join_match_fields")).get(0), "state.origin");

        assertTrue(tokenContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(tokenContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(tokenContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(tokenContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(tokenContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(tokenContract.isPermitted("split_join", issuerPublicKeys));

        ItemResult itemResult = client.register(tokenContract.getPackedTransaction(), 5000);
        System.out.println("token contract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testMintableTokenContractApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract mintableTokenContract = ContractsService.createTokenContractWithEmission(issuerPrivateKeys, ownerPublicKeys, "300000000000");

        mintableTokenContract.check();
        mintableTokenContract.traceErrors();

        ItemResult itemResult = client.register(mintableTokenContract.getPackedTransaction(), 5000);
        System.out.println("mintableTokenContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract emittedContract = ContractsService.createTokenEmission(mintableTokenContract, "100000000000", issuerPrivateKeys);

        emittedContract.check();
        emittedContract.traceErrors();

        assertEquals(emittedContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = emittedContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), 0.01);
        assertEquals(splitJoinParams.get("min_unit"), 0.01);
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List)splitJoinParams.get("join_match_fields")).get(0), "state.origin");


        assertTrue(emittedContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(emittedContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(emittedContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(emittedContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(emittedContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(emittedContract.isPermitted("split_join", issuerPublicKeys));


        itemResult = client.register(emittedContract.getPackedTransaction(), 5000);
        System.out.println("emittedContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        assertEquals(emittedContract.getStateData().getString("amount"), "400000000000");
        assertEquals(ItemState.REVOKED, main.node.waitItem(mintableTokenContract.getId(), 8000).state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testSplitAndJoinApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));
        Set<PrivateKey> issuerPrivateKeys2 = new HashSet<>(Arrays.asList(TestKeys.privateKey(2)));

        Contract contractC = ContractsService.createTokenContract(issuerPrivateKeys,ownerPublicKeys, "100");
        contractC.check();
        contractC.traceErrors();

        ItemResult itemResult = client.register(contractC.getPackedTransaction(), 5000);
        System.out.println("contractC itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // 100 - 30 = 70
        Contract сontractA = ContractsService.createSplit(contractC, "30", "amount", issuerPrivateKeys2, true);
        Contract contractB = сontractA.getNew().get(0);
        assertEquals("70", сontractA.getStateData().get("amount").toString());
        assertEquals("30", contractB.getStateData().get("amount").toString());

        itemResult = client.register(сontractA.getPackedTransaction(), 5000);
        System.out.println("сontractA itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        assertEquals("70", сontractA.getStateData().get("amount").toString());
        assertEquals("30", contractB.getStateData().get("amount").toString());

        assertEquals(ItemState.REVOKED, main.node.waitItem(contractC.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(сontractA.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(contractB.getId(), 5000).state);

        // join 70 + 30 = 100
        Contract contractC2 = ContractsService.createJoin(сontractA, contractB, "amount", issuerPrivateKeys2);
        contractC2.check();
        contractC2.traceErrors();
        assertTrue(contractC2.isOk());

        itemResult = client.register(contractC2.getPackedTransaction(), 5000);
        System.out.println("contractC2 itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        assertEquals(new Decimal(100), contractC2.getStateData().get("amount"));

        assertEquals(ItemState.REVOKED, main.node.waitItem(contractC.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, main.node.waitItem(сontractA.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, main.node.waitItem(contractB.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(contractC2.getId(), 5000).state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testShareContractApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract shareContract = ContractsService.createShareContract(issuerPrivateKeys,ownerPublicKeys,"100");

        shareContract.check();
        shareContract.traceErrors();

        assertTrue(shareContract.getOwner().isAllowedForKeys(ownerPublicKeys));
        assertTrue(shareContract.getIssuer().isAllowedForKeys(issuerPrivateKeys));
        assertTrue(shareContract.getCreator().isAllowedForKeys(issuerPrivateKeys));

        assertFalse(shareContract.getOwner().isAllowedForKeys(issuerPrivateKeys));
        assertFalse(shareContract.getIssuer().isAllowedForKeys(ownerPublicKeys));
        assertFalse(shareContract.getCreator().isAllowedForKeys(ownerPublicKeys));

        assertTrue(shareContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(shareContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertEquals(InnerContractsService.getDecimalField(shareContract, "amount"), new Decimal(100));

        assertEquals(shareContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = shareContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), 1);
        assertEquals(splitJoinParams.get("min_unit"), 1);
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List)splitJoinParams.get("join_match_fields")).get(0), "state.origin");

        assertTrue(shareContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(shareContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(shareContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(shareContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(shareContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(shareContract.isPermitted("split_join", issuerPublicKeys));

        ItemResult itemResult = client.register(shareContract.getPackedTransaction(), 5000);
        System.out.println("shareContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testNotaryContractApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract notaryContract = ContractsService.createNotaryContract(issuerPrivateKeys, ownerPublicKeys);

        notaryContract.check();
        notaryContract.traceErrors();

        assertTrue(notaryContract.getOwner().isAllowedForKeys(ownerPublicKeys));
        assertTrue(notaryContract.getIssuer().isAllowedForKeys(issuerPrivateKeys));
        assertTrue(notaryContract.getCreator().isAllowedForKeys(issuerPrivateKeys));

        assertFalse(notaryContract.getOwner().isAllowedForKeys(issuerPrivateKeys));
        assertFalse(notaryContract.getIssuer().isAllowedForKeys(ownerPublicKeys));
        assertFalse(notaryContract.getCreator().isAllowedForKeys(ownerPublicKeys));

        assertTrue(notaryContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(notaryContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertTrue(notaryContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(notaryContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(notaryContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(notaryContract.isPermitted("change_owner", issuerPublicKeys));

        ItemResult itemResult = client.register(notaryContract.getPackedTransaction(), 5000);
        System.out.println("notaryContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testTwoSignedContractApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> martyPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(2)));
        Set<PublicKey> stepaPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        baseContract.check();
        baseContract.traceErrors();

        Contract twoSignContract = ContractsService.createTwoSignedContract(baseContract, martyPrivateKeys, stepaPublicKeys, false);

        //now emulate sending transaction pack through network ---->

        twoSignContract = Contract.fromPackedTransaction(twoSignContract.getPackedTransaction());

        twoSignContract.addSignatureToSeal(stepaPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();

        ItemResult itemResult = client.register(twoSignContract.getPackedTransaction(), 5000);
        System.out.println("twoSignContract itemResult: " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);


        //now emulate sending transaction pack through network <----

        twoSignContract = Contract.fromPackedTransaction(twoSignContract.getPackedTransaction());

        twoSignContract.addSignatureToSeal(martyPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        System.out.println("Contract with two signature is valid: " + twoSignContract.isOk());

        itemResult = client.register(twoSignContract.getPackedTransaction(), 5000);
        System.out.println("twoSignContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testSlotApi() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Decimal kilobytesAndDaysPerU = client.storageGetRate();
        System.out.println("storageGetRate: " + kilobytesAndDaysPerU);
        assertEquals(new Decimal((int) main.config.getRate("SLOT1")), kilobytesAndDaysPerU);

        Contract simpleContract = new Contract(TestKeys.privateKey(1));
        simpleContract.seal();
        ItemResult itemResult = client.register(simpleContract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        SlotContract slotContract = ContractsService.createSlotContract(new HashSet<>(Arrays.asList(TestKeys.privateKey(1))), new HashSet<>(Arrays.asList(TestKeys.publicKey(1))), nodeInfoProvider);
        slotContract.setNodeInfoProvider(nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, new HashSet<>(Arrays.asList(TestKeys.publicKey(1))));
        itemResult = client.register(stepaTU.getPackedTransaction(), 5000);
        System.out.println("stepaTU itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), stepaTU, 1, 100, new HashSet<>(Arrays.asList(TestKeys.privateKey(1))), false);

        Binder slotInfo = client.querySlotInfo(slotContract.getId());
        System.out.println("slot info is null: " + (slotInfo == null));
        assertNull(slotInfo);

        byte[] simpleContractBytes = client.queryContract(slotContract.getId(), null, simpleContract.getId());
        System.out.println("simpleContractBytes (by contractId): " + simpleContractBytes);
        assertEquals(false, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        simpleContractBytes = client.queryContract(slotContract.getId(), simpleContract.getOrigin(), null);
        System.out.println("simpleContractBytes (by originId): " + simpleContractBytes);
        assertEquals(false, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        client.registerParcel(parcel.pack(), 5000);
        itemResult = client.getState(slotContract.getId());
        System.out.println("slot itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        slotInfo = client.querySlotInfo(slotContract.getId());
        System.out.println("slot info size: " + slotInfo.size());
        assertNotNull(slotInfo);

        simpleContractBytes = client.queryContract(slotContract.getId(), null, simpleContract.getId());
        System.out.println("simpleContractBytes (by contractId) length: " + simpleContractBytes.length);
        assertEquals(true, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        simpleContractBytes = client.queryContract(slotContract.getId(), simpleContract.getOrigin(), null);
        System.out.println("simpleContractBytes (by originId) length: " + simpleContractBytes.length);
        assertEquals(true, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testUnsApi() throws Exception {

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach( m -> {
            m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));
            m.config.setIsFreeRegistrationsAllowedFromYaml(true);
        });

        Decimal namesAndDaysPerU = testSpace.client.unsRate();
        System.out.println("unsRate: " + namesAndDaysPerU);
        assertEquals(testSpace.node.config.getRate("UNS1"), namesAndDaysPerU.doubleValue(), 0.000001);

        Contract simpleContract = new Contract(manufacturePrivateKeys.iterator().next());
        simpleContract.seal();
        ItemResult itemResult = testSpace.client.register(simpleContract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        String unsTestName = "testContractName" + Instant.now().getEpochSecond();

        // check uns contract with origin record
        UnsContract unsContract = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys,
                manufacturePublicKeys, nodeInfoProvider, unsTestName, "test contract name", "http://test.com", simpleContract);
        unsContract.getUnsName(unsTestName).setUnsReducedName(unsTestName);
        unsContract.addSignerKey(authorizedNameServiceKey);
        unsContract.seal();
        unsContract.check();
        unsContract.traceErrors();

        Contract paymentContract = getApprovedTUContract(testSpace);

        Parcel payingParcel = ContractsService.createPayingParcel(unsContract.getTransactionPack(), paymentContract, 1, 2000, manufacturePrivateKeys, false);

        Binder nameInfo = testSpace.client.queryNameRecord(simpleContract.getId());
        String name = nameInfo.getString("name", null);
        System.out.println("name info is null: " + (name == null));
        assertNull(name);

        byte[] unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(false, Arrays.equals(unsContract.getPackedTransaction(), unsContractBytes));

        testSpace.client.registerParcel(payingParcel.pack(), 8000);
        itemResult = testSpace.client.getState(unsContract.getId());
        System.out.println("Uns itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        nameInfo = testSpace.client.queryNameRecord(simpleContract.getId());
        assertNotNull(nameInfo);
        System.out.println("name info size: " + nameInfo.size());
        System.out.println("Name: " + nameInfo.getString("name", ""));
        System.out.println("Description: " + nameInfo.getString("description", ""));
        System.out.println("URL: " + nameInfo.getString("url", ""));
        assertEquals(unsTestName, nameInfo.getString("name", ""));

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(true, Arrays.equals(unsContract.getPackedTransaction(), unsContractBytes));

        // check uns contract with address record
        unsTestName = "testAddressContractName" + Instant.now().getEpochSecond();
        PrivateKey randomPrivKey = new PrivateKey(2048);

        UnsContract unsContract2 = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys,
                manufacturePublicKeys, nodeInfoProvider, unsTestName, "test address name", "http://test.com", randomPrivKey.getPublicKey());
        unsContract2.getUnsName(unsTestName).setUnsReducedName(unsTestName);
        unsContract2.addSignerKey(authorizedNameServiceKey);
        unsContract2.addSignerKey(randomPrivKey);
        unsContract2.seal();
        unsContract2.check();
        unsContract2.traceErrors();

        paymentContract = getApprovedTUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(unsContract2.getTransactionPack(), paymentContract, 1, 2000, manufacturePrivateKeys, false);

        KeyAddress keyAddr = new KeyAddress(randomPrivKey.getPublicKey(), 0, true);
        nameInfo = testSpace.client.queryNameRecord(keyAddr.toString());
        name = nameInfo.getString("name", null);
        System.out.println("name info is null: " + (name == null));
        assertNull(name);

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(false, Arrays.equals(unsContract2.getPackedTransaction(), unsContractBytes));

        testSpace.client.registerParcel(payingParcel.pack(), 8000);
        itemResult = testSpace.client.getState(unsContract2.getId());
        System.out.println("Uns itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        nameInfo = testSpace.client.queryNameRecord(keyAddr.toString());
        assertNotNull(nameInfo);
        System.out.println("name info size: " + nameInfo.size());
        System.out.println("Name: " + nameInfo.getString("name", ""));
        System.out.println("Description: " + nameInfo.getString("description", ""));
        System.out.println("URL: " + nameInfo.getString("url", ""));
        assertEquals(unsTestName, nameInfo.getString("name", ""));

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(true, Arrays.equals(unsContract2.getPackedTransaction(), unsContractBytes));

        testSpace.nodes.forEach(x -> x.shutdown());

    }

    @Test
    public void testRevocationContractsApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));

        Set<PublicKey> ownerPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));

        Contract sourceContract = ContractsService.createShareContract(issuerPrivateKeys,ownerPublicKeys,"100");

        sourceContract.check();
        sourceContract.traceErrors();

        ItemResult itemResult = client.register(sourceContract.getPackedTransaction(), 5000);
        System.out.println("sourceContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, client.getState(sourceContract.getId()).state);

        Contract revokeContract = ContractsService.createRevocation(sourceContract, TestKeys.privateKey(1));

        revokeContract.check();
        revokeContract.traceErrors();

        itemResult = client.register(revokeContract.getPackedTransaction(), 5000);
        System.out.println("revokeContract itemResult: " + itemResult);

        assertEquals(ItemState.APPROVED, client.getState(revokeContract.getId()).state);
        assertEquals(ItemState.REVOKED, client.getState(sourceContract.getId()).state);

        mm.forEach(x -> x.shutdown());

     }

    @Test
    public void testSwapContractsApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> martyPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(Arrays.asList(TestKeys.privateKey(2)));
        Set<PublicKey> martyPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(1)));
        Set<PublicKey> stepaPublicKeys = new HashSet<>(Arrays.asList(TestKeys.publicKey(2)));


        Contract delorean = ContractsService.createTokenContract(martyPrivateKeys, martyPublicKeys, "100", 0.0001);
        delorean.seal();

        delorean.check();
        delorean.traceErrors();

        ItemResult itemResult = client.register(delorean.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract lamborghini = ContractsService.createTokenContract(stepaPrivateKeys, stepaPublicKeys, "100", 0.0001);
        lamborghini.seal();

        lamborghini.check();
        lamborghini.traceErrors();

        itemResult = client.register(lamborghini.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);


        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = Contract.fromPackedTransaction(swapContract.getPackedTransaction());
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = Contract.fromPackedTransaction(swapContract.getPackedTransaction());
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());

        itemResult = client.register(swapContract.getPackedTransaction(), 10000);
        assertEquals(ItemState.APPROVED, itemResult.state);


        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = main.node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = main.node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        deloreanResult = main.node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertEquals(ItemState.APPROVED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepaPublicKeys));

        lamborghiniResult = main.node.waitItem(newLamborghini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
        assertTrue(newLamborghini.getOwner().isAllowedForKeys(martyPublicKeys));

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testSwapSplitJoin_Api2() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> user1PrivateKeySet = new HashSet<>(Arrays.asList(TestKeys.privateKey(1)));
        Set<PrivateKey> user2PrivateKeySet = new HashSet<>(Arrays.asList(TestKeys.privateKey(2)));
        Set<PublicKey> user1PublicKeySet = user1PrivateKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());
        Set<PublicKey> user2PublicKeySet = user2PrivateKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());


        Contract contractTOK92 = ContractsService.createTokenContract(user1PrivateKeySet, user1PublicKeySet, "100", 0.0001);
        Contract contractTOK93 = ContractsService.createTokenContract(user2PrivateKeySet, user2PublicKeySet, "100", 0.001);

        contractTOK92.seal();
        contractTOK92.check();
        contractTOK92.traceErrors();

        ItemResult itemResult = client.register(contractTOK92.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);


        contractTOK93.seal();
        contractTOK93.check();
        contractTOK93.traceErrors();

        itemResult = client.register(contractTOK93.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        System.out.println("--- tokens created ---");

        // TOK92: 100 - 8.02 = 91.98
        Contract user1CoinsSplit = ContractsService.createSplit(contractTOK92, "8.02", "amount", user1PrivateKeySet);
        Contract user1CoinsSplitToUser2 = user1CoinsSplit.getNew().get(0);
        // TOK93: 100 - 10.01 = 89.99
        Contract user2CoinsSplit = ContractsService.createSplit(contractTOK93, "10.01", "amount", user2PrivateKeySet);
        Contract user2CoinsSplitToUser1 = user2CoinsSplit.getNew().get(0);

        user1CoinsSplitToUser2.check();
        user1CoinsSplitToUser2.traceErrors();
        user2CoinsSplitToUser1.check();
        user2CoinsSplitToUser1.traceErrors();

        // exchanging the contracts

        System.out.println("--- procedure for exchange of contracts ---");

        // Step one

        Contract swapContract;
        swapContract = ContractsService.startSwap(user1CoinsSplitToUser2, user2CoinsSplitToUser1, user1PrivateKeySet, user2PublicKeySet, false);

        // Step two

        ContractsService.signPresentedSwap(swapContract, user2PrivateKeySet);

        // Final step

        ContractsService.finishSwap(swapContract, user1PrivateKeySet);

        user1CoinsSplit.seal();
        user2CoinsSplit.seal();
        swapContract.getNewItems().clear();
        swapContract.addNewItems(user1CoinsSplit, user2CoinsSplit);
        swapContract.seal();

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk());

        //now emulate sending transaction pack through network

        swapContract = Contract.fromPackedTransaction(swapContract.getPackedTransaction());

        main.node.registerItem(swapContract);

        assertEquals(ItemState.APPROVED, main.node.waitItem(swapContract.getId(), 5000).state);

        assertEquals(ItemState.APPROVED, main.node.waitItem(user1CoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(user2CoinsSplit.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(user1CoinsSplitToUser2.getId(), 5000).state);
        assertEquals(ItemState.APPROVED, main.node.waitItem(user2CoinsSplitToUser1.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, main.node.waitItem(contractTOK92.getId(), 5000).state);
        assertEquals(ItemState.REVOKED, main.node.waitItem(contractTOK93.getId(), 5000).state);
        assertEquals("8.02", user1CoinsSplitToUser2.getStateData().getStringOrThrow("amount"));
        assertEquals("10.01", user2CoinsSplitToUser1.getStateData().getStringOrThrow("amount"));
        assertFalse(user1CoinsSplitToUser2.getOwner().isAllowedForKeys(user1PublicKeySet));
        assertTrue(user1CoinsSplitToUser2.getOwner().isAllowedForKeys(user2PublicKeySet));
        assertTrue(user2CoinsSplitToUser1.getOwner().isAllowedForKeys(user1PublicKeySet));
        assertFalse(user2CoinsSplitToUser1.getOwner().isAllowedForKeys(user2PublicKeySet));

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void testAddReferenceApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();//manager -
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>(); //issuer
        Set<PrivateKey>  thirdPartyPrivateKeys = new HashSet<>();

        llcPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        thirdPartyPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> thirdPartyPublicKeys = new HashSet<>();
        for (PrivateKey pk : thirdPartyPrivateKeys) {
            thirdPartyPublicKeys.add(pk.getPublicKey());
        }

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "ApricoT");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        jobCertificate.check();
        jobCertificate.traceErrors();

        ItemResult itemResult = client.register(jobCertificate.getPackedTransaction(), 5000);
        System.out.println("sourceContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, client.getState(jobCertificate.getId()).state);

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        List <String> listConditions = new ArrayList<>();
        listConditions.add("ref.definition.issuer == \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "          a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "          z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "          BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "          vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "          Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "          hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "          LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "          MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"");
        listConditions.add("ref.definition.data.issuer == \"ApricoT\"");
        listConditions.add("ref.definition.data.type == \"chief accountant assignment\"");

        ContractsService.addReferenceToContract(llcProperty, jobCertificate, "certification_contract", Reference.TYPE_EXISTING_DEFINITION, listConditions, true);

        llcProperty.check();
        llcProperty.traceErrors();

        itemResult = client.register(llcProperty.getPackedTransaction(), 5000);
        System.out.println("sourceContract itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, client.getState(llcProperty.getId()).state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void paymentTest1() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);


        Contract simpleContract = new Contract(TestKeys.privateKey(1));
        simpleContract.seal();

        Contract stepaTU = InnerContractsService.createFreshTU(100000000, new HashSet<>(Arrays.asList(TestKeys.publicKey(1))));
        ItemResult itemResult = client.register(stepaTU.getPackedTransaction(), 5000);
        System.out.println("stepaTU itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        main.config.setIsFreeRegistrationsAllowedFromYaml(false);

        Parcel parcel = ContractsService.createParcel(simpleContract, stepaTU, 1, new HashSet<>(Arrays.asList(TestKeys.privateKey(1))), false);
        client.registerParcel(parcel.pack(), 5000);
        assertEquals(ItemState.APPROVED, client.getState(simpleContract.getId()).state);

        mm.forEach(x -> x.shutdown());

    }
    protected static final String ROOT_PATH = "./src/test_contracts/";


    protected Contract getApprovedTUContract(TestSpace testSpace) throws Exception {
        synchronized (testSpace.tuContractLock) {
            if (testSpace.tuContract == null) {

                Set<PublicKey> keys = new HashSet();
                keys.add(testSpace.myKey.getPublicKey());
                Contract stepaTU = InnerContractsService.createFreshTU(100000000, keys);
                stepaTU.check();
                stepaTU.traceErrors();
                System.out.println("register new TU ");
                testSpace.node.node.registerItem(stepaTU);
                testSpace.tuContract = stepaTU;
            }
            int needRecreateTuContractNum = 0;
            for (Main m : testSpace.nodes) {
                try {
                    ItemResult itemResult = m.node.waitItem(testSpace.tuContract.getId(), 15000);
                    //assertEquals(ItemState.APPROVED, itemResult.state);
                    if (itemResult.state != ItemState.APPROVED) {
                        System.out.println("TU: node " + m.node + " result: " + itemResult);
                        needRecreateTuContractNum ++;
                    }
                } catch (TimeoutException e) {
                    System.out.println("ping ");
//                    System.out.println(n.ping());
////                    System.out.println(n.traceTasksPool());
//                    System.out.println(n.traceParcelProcessors());
//                    System.out.println(n.traceItemProcessors());
                    System.out.println("TU: node " + m.node + " timeout: ");
                    needRecreateTuContractNum ++;
                }
            }
            int recreateBorder = testSpace.nodes.size() - testSpace.node.config.getPositiveConsensus() - 1;
            if(recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateTuContractNum > recreateBorder) {
                testSpace.tuContract = null;
                Thread.sleep(1000);
                return getApprovedTUContract(testSpace);
            }
            return testSpace.tuContract;
        }
    }

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NSmartContract.NodeInfoProvider() {
        Config config = new Config();
        @Override
        public Set<KeyAddress> getTransactionUnitsIssuerKeys() {
            return config.getTransactionUnitsIssuerKeys();
        }

        @Override
        public String getTUIssuerName() {
            return config.getTUIssuerName();
        }

        @Override
        public int getMinPayment(String extendedType) {
            return config.getMinPayment(extendedType);
        }

        @Override
        public double getRate(String extendedType) {
            return config.getRate(extendedType);
        }

        @Override
        public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
            Set<PublicKey> set = new HashSet<>();
            if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
                set.add(config.getAuthorizedNameServiceCenterKey());
            }
            return set;
        }
    };

    @Test(timeout = 90000)
    public void checkUnsNodeMissedRevocation() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);
        PrivateKey randomPrivKey4 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        String name = "test"+Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);

        UnsName unsName = new UnsName(name, "test description", "http://test.com");
        unsName.setUnsReducedName(name);
        UnsRecord unsRecord = new UnsRecord(randomPrivKey1.getPublicKey());
        unsName.addUnsRecord(unsRecord);
        uns.addUnsName(unsName);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();


        UnsContract uns2 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns2.addSignerKey(authorizedNameServiceKey);

        UnsName unsName2 = new UnsName( name, "test description", "http://test.com");
        unsName2.setUnsReducedName(name);
        UnsRecord unsRecord2 = new UnsRecord(randomPrivKey2.getPublicKey());
        unsName2.addUnsRecord(unsRecord2);
        uns2.addUnsName(unsName2);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivKey2);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        UnsContract uns3 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns3.addSignerKey(authorizedNameServiceKey);

        UnsName unsName3 = new UnsName( name, "test description", "http://test.com");
        unsName3.setUnsReducedName(name);
        UnsRecord unsRecord3 = new UnsRecord(randomPrivKey3.getPublicKey());
        unsName3.addUnsRecord(unsRecord3);
        uns3.addUnsName(unsName3);

        uns3.setNodeInfoProvider(nodeInfoProvider);
        uns3.seal();
        uns3.addSignatureToSeal(randomPrivKey3);
        uns3.addSignatureToSeal(TestKeys.privateKey(8));
        uns3.check();
        uns3.traceErrors();

        //REGISTER UNS1
        Contract paymentContract = getApprovedTUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()).getEntries().size(),1);


        //REVOKE UNS1
        Contract revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns);
        revokingContract.seal();

        paymentContract = getApprovedTUContract(testSpace);
        Parcel parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns.getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));

        //REGISTER UNS2
        paymentContract = getApprovedTUContract(testSpace);
        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()).getEntries().size(),1);

        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size()-1).shutdown();
        Thread.sleep(4000);

        //REVOKE UNS2
        revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns2);
        revokingContract.seal();

        paymentContract = getApprovedTUContract(testSpace);
        parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns2.getId(), 8000).state);


        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));
        //RECREATE NODES
        testSpace.nodes.forEach(m->m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));
        //LAST NODE MISSED UNS2 REVOKE
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName()));

        //REGISTER UNS3
        paymentContract = getApprovedTUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(uns3.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns3.getNew().get(0).getId(), 8000).state);

        NNameRecord nrm = testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName());
        NNameRecord nrmLast = testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName());
        assertEquals(nrm.getEntries().size(),1);
        assertEquals(nrmLast.getEntries().size(),1);
        assertNotEquals(nrm.getEntries().iterator().next().getShortAddress(),nrmLast.getEntries().iterator().next().getShortAddress());
        assertNotEquals(nrm.getEntries().iterator().next().getLongAddress(),nrmLast.getEntries().iterator().next().getLongAddress());

        Thread.sleep(4000);

        nrmLast = testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName());

        assertEquals(nrm.getEntries().size(),1);
        assertEquals(nrmLast.getEntries().size(),1);
        assertEquals(nrm.getEntries().iterator().next().getShortAddress(),nrmLast.getEntries().iterator().next().getShortAddress());
        assertEquals(nrm.getEntries().iterator().next().getLongAddress(),nrmLast.getEntries().iterator().next().getLongAddress());

        testSpace.nodes.forEach(m->m.shutdown());

    }


    @Test(timeout = 90000)
    public void checkUnsNodeMissedRevision() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);
        PrivateKey randomPrivKey4 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        String name = "test"+Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);

        UnsName unsName = new UnsName(name, "test description", "http://test.com");
        unsName.setUnsReducedName(name);
        UnsRecord unsRecord = new UnsRecord(randomPrivKey1.getPublicKey());
        unsName.addUnsRecord(unsRecord);
        uns.addUnsName(unsName);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();


        UnsContract uns2 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns2.addSignerKey(authorizedNameServiceKey);

        UnsName unsName2 = new UnsName(name, "test description", "http://test.com");
        unsName2.setUnsReducedName(name);
        UnsRecord unsRecord2 = new UnsRecord(randomPrivKey2.getPublicKey());
        unsName2.addUnsRecord(unsRecord2);
        uns2.addUnsName(unsName2);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivKey2);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        UnsContract uns3 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns3.addSignerKey(authorizedNameServiceKey);

        UnsName unsName3 = new UnsName(name, "test description", "http://test.com");
        unsName3.setUnsReducedName(name);
        UnsRecord unsRecord3 = new UnsRecord(randomPrivKey3.getPublicKey());
        unsName3.addUnsRecord(unsRecord3);
        uns3.addUnsName(unsName3);

        uns3.setNodeInfoProvider(nodeInfoProvider);
        uns3.seal();
        uns3.addSignatureToSeal(randomPrivKey3);
        uns3.addSignatureToSeal(TestKeys.privateKey(8));
        uns3.check();
        uns3.traceErrors();

        //REGISTER UNS1
        Contract paymentContract = getApprovedTUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()).getEntries().size(),1);


        //REVOKE UNS1
        Contract revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns);
        revokingContract.seal();

        paymentContract = getApprovedTUContract(testSpace);
        Parcel parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns.getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));

        //REGISTER UNS2
        paymentContract = getApprovedTUContract(testSpace);
        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()).getEntries().size(),1);

        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size()-1).shutdown();
        Thread.sleep(4000);

        //UPDATE UNS2

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey4);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns2 = (UnsContract) uns2.createRevision(keys);
        uns2.removeName(name);
        UnsName unsName2_1 = new UnsName(name+"2", "test description", "http://test.com");
        unsName2_1.setUnsReducedName(name+"2");
        UnsRecord unsRecord2_1 = new UnsRecord(randomPrivKey4.getPublicKey());
        unsName2_1.addUnsRecord(unsRecord2_1);
        uns2.addUnsName(unsName2_1);

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();

        parcel = ContractsService.createParcel(uns2,getApprovedTUContract(testSpace),1,manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(uns2.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));

        //RECREATE NODES
        testSpace.nodes.forEach(m->m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));
        //LAST NODE MISSED UNS2 REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName()));

        //REGISTER UNS3
        paymentContract = getApprovedTUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(uns3.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        ir = testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns3.getNew().get(0).getId(), 8000).state);

        NNameRecord nrm = testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName());
        NNameRecord nrmLast = testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName());
        assertEquals(nrm.getEntries().size(),1);
        assertEquals(nrmLast.getEntries().size(),1);
        assertNotEquals(nrm.getEntries().iterator().next().getShortAddress(),nrmLast.getEntries().iterator().next().getShortAddress());
        assertNotEquals(nrm.getEntries().iterator().next().getLongAddress(),nrmLast.getEntries().iterator().next().getLongAddress());

        Thread.sleep(4000);

        nrmLast = testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(unsName.getUnsName());

        assertEquals(nrm.getEntries().size(),1);
        assertEquals(nrmLast.getEntries().size(),1);
        assertEquals(nrm.getEntries().iterator().next().getShortAddress(),nrmLast.getEntries().iterator().next().getShortAddress());
        assertEquals(nrm.getEntries().iterator().next().getLongAddress(),nrmLast.getEntries().iterator().next().getLongAddress());

        testSpace.nodes.forEach(m->m.shutdown());

    }


    @Test(timeout = 90000)
    public void checkUnsNodeMissedSelfRevision() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        String name = "test"+Instant.now().getEpochSecond();
        String name2 = "test2"+Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);

        UnsName unsName = new UnsName(name, "test description", "http://test.com");
        unsName.setUnsReducedName(name);
        UnsRecord unsRecord = new UnsRecord(randomPrivKey1.getPublicKey());
        unsName.addUnsRecord(unsRecord);
        uns.addUnsName(unsName);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();



        //REGISTER UNS1
        Contract paymentContract = getApprovedTUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()).getEntries().size(),1);


        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size()-1).shutdown();
        Thread.sleep(4000);

        //UPDATE UNS

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey2);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns = (UnsContract) uns.createRevision(keys);
        uns.removeName(name);
        UnsName unsName2 = new UnsName(name2, "test description", "http://test.com");
        unsName2.setUnsReducedName(name2);
        UnsRecord unsRecord2 = new UnsRecord(randomPrivKey2.getPublicKey());
        unsName2.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName2);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        Parcel parcel = ContractsService.createParcel(uns, getApprovedTUContract(testSpace), 1, manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(uns.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(unsName.getUnsName()));

        //RECREATE NODES
        testSpace.nodes.forEach(m->m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach( m -> m.config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack())));

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));
        assertNotNull(testSpace.node.node.getLedger().getNameRecord(name2));
        //LAST NODE MISSED UNS REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name2));

        //REGISTER UNS


        keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey3);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns = (UnsContract) uns.createRevision(keys);
        uns.removeName(name2);
        UnsName unsName3 = new UnsName(name, "test description", "http://test.com");
        unsName3.setUnsReducedName(name);
        UnsRecord unsRecord3 = new UnsRecord(randomPrivKey3.getPublicKey());
        unsName3.addUnsRecord(unsRecord3);
        uns.addUnsName(unsName3);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        parcel = ContractsService.createParcel(uns, getApprovedTUContract(testSpace), 1, manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.tuContractLock) {
            testSpace.tuContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(uns.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);


        KeyAddress long1 = unsRecord.getAddresses().get(0).isLong() ? unsRecord.getAddresses().get(0) : unsRecord.getAddresses().get(1);
        KeyAddress long3 = unsRecord3.getAddresses().get(0).isLong() ? unsRecord3.getAddresses().get(0) : unsRecord3.getAddresses().get(1);


        assertNull(testSpace.node.node.getLedger().getNameRecord(name2));
        assertNotNull(testSpace.node.node.getLedger().getNameRecord(name));
        assertEquals(testSpace.node.node.getLedger().getNameRecord(name).getEntries().iterator().next().getLongAddress(),long3.toString());

        //LAST NODE MISSED UNS REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name2));
        assertEquals(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name).getEntries().iterator().next().getLongAddress(),long1.toString());

        Thread.sleep(4000);
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name2));
        assertEquals(testSpace.nodes.get(testSpace.nodes.size()-1).node.getLedger().getNameRecord(name).getEntries().iterator().next().getLongAddress(),long3.toString());



        testSpace.nodes.forEach(m->m.shutdown());

    }

    @Test
    public void environmentSerializationTest() throws Exception{
        UnsName unsName = new UnsName();
        unsName.setUnsName("test");
        unsName.setUnsReducedName("test");

        PrivateKey privateKey = new PrivateKey(2048);
        Contract contract = new Contract(privateKey);
        contract.seal();

        NSmartContract smartContract = new NSmartContract(privateKey);
        smartContract.seal();

        UnsRecord record1 = new UnsRecord(contract.getId());
        UnsRecord record2 = new UnsRecord(privateKey.getPublicKey());

        unsName.addUnsRecord(record1);
        unsName.addUnsRecord(record2);
        ZonedDateTime now = ZonedDateTime.now();
        NNameRecord nnr = new NNameRecord(unsName, now);

        Config.forceInit(NMutableEnvironment.class);


        NNameRecord nnr2 = Boss.load(Boss.pack(nnr));

        assertTrue(nnr2.getEntries().stream().anyMatch(nre -> unsName.getUnsRecords().stream().anyMatch(ur -> ur.equalsTo(nre))));
        assertEquals(nnr2.getEntries().size(),unsName.getRecordsCount());
        assertEquals(nnr2.getName(),unsName.getUnsName());
        assertEquals(nnr2.getNameReduced(),unsName.getUnsReducedName());
        assertEquals(nnr2.getDescription(),unsName.getUnsDescription());
        assertEquals(nnr2.getUrl(),unsName.getUnsURL());
        assertEquals(nnr.expiresAt().toEpochSecond(),nnr2.expiresAt().toEpochSecond());

        NContractStorageSubscription sub = Boss.load(Boss.pack(new NContractStorageSubscription(contract.getPackedTransaction(),now)));
        assertTrue(sub.getContract().getId().equals(contract.getId()));
        assertEquals(sub.expiresAt().toEpochSecond(),now.toEpochSecond());

        Binder kvStore = new Binder();
        kvStore.put("test","test1");
        NImmutableEnvironment environment = new NImmutableEnvironment(smartContract,kvStore,Do.listOf(sub),Do.listOf(nnr2),null);

        environment = Boss.load(Boss.pack(environment));
        assertEquals(environment.get("test",null),"test1");
    }

    @Test
    public void concurrentResyncTest() throws Exception {
        boolean doShutdown = true;
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Set<PrivateKey> issuerKeys = new HashSet<>();
        Set<PublicKey> ownerKeys = new HashSet<>();
        issuerKeys.add(issuerKey);
        ownerKeys.add(issuerKey.getPublicKey());


        ArrayList<Contract> contractsToJoin = new ArrayList<>();

        for(int k = 0; k < 4; k++) {
            if (doShutdown) {
                //shutdown one of nodes
                if (k < 3) {
                    int absentNode = k + 1;
                    testSpace.nodes.get(absentNode).shutdown();
                    testSpace.nodes.remove(absentNode);
                }
            }

            Contract contract = new Contract(issuerKey);
            contract.getDefinition().getData().set("test","test1");
            contract.getStateData().set("amount","100");
            Binder params = Binder.of("field_name", "amount", "join_match_fields",asList("definition.issuer"));
            Role ownerLink = new RoleLink("@owner_link","owner");
            contract.registerRole(ownerLink);
            SplitJoinPermission splitJoinPermission = new SplitJoinPermission(ownerLink,params);
            contract.addPermission(splitJoinPermission);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(),1500);
            assertEquals(testSpace.client.getState(contract.getId()).state,ItemState.APPROVED);

            if(doShutdown) {
                testSpace.nodes.forEach(n -> n.shutdown());
                Thread.sleep(2000);
                testSpace = prepareTestSpace(issuerKey);
                testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
            }
            contractsToJoin.add(contract);
        }


        TestSpace finalTestSpace = testSpace;
        contractsToJoin.forEach(c -> {
            int count = 0;
            for (Main main : finalTestSpace.nodes) {
                try {
                    if(main.node.waitItem(c.getId(),4000).state != ItemState.APPROVED) {
                        count++;
                    }
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Contract " + c.getId() + " is unknown to " + count + "node(s)");
        });

        Contract c = contractsToJoin.remove(Do.randomInt(contractsToJoin.size()));
        Contract main = c.createRevision(issuerKey);
        main.getStateData().set("amount","400");
        main.addRevokingItems(contractsToJoin.get(0),contractsToJoin.get(1),contractsToJoin.get(2));
        main.addSignerKey(issuerKey);
        main.seal();
        contractsToJoin.add(c);
        testSpace.client.register(main.getPackedTransaction(),1500);
        ItemResult ir;
        do {
            ir = testSpace.client.getState(main.getId());
            System.out.println(ir);
            Thread.sleep(1000);
        } while (ir.state.isPending());


        contractsToJoin.forEach(c1 -> {
            int count = 0;
            for (Main main1 : finalTestSpace.nodes) {
                try {
                    if(main1.node.waitItem(c1.getId(),4000).state != ItemState.APPROVED) {
                        count++;
                    }
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Contract " + c.getId() + " is unknown to " + count + "node(s)");
        });

        assertEquals(ir.state,ItemState.APPROVED);

        testSpace.nodes.forEach(x -> x.shutdown());

    }
}