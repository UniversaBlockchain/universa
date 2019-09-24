/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.digest.Crc32;
import com.icodici.db.Db;
import com.icodici.db.DbPool;
import com.icodici.db.PooledDb;
import com.icodici.universa.*;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.helpers.Compound;
import com.icodici.universa.contract.helpers.EscrowHelper;
import com.icodici.universa.contract.helpers.SecureLoanHelper;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.*;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.*;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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

            //ts.node.config.getKeysWhiteList().add(TestKeys.publicKey(3));
            ts.node.config.getAddressesWhiteList().add(new KeyAddress(TestKeys.publicKey(3), 0, true));
            Contract testContract = new Contract(ts.myKey);
            testContract.seal();
            assertTrue(testContract.isOk());
            Parcel parcel = createParcelWithFreshU(ts.client, testContract, Do.listOf(ts.myKey));
            ts.client.registerParcelWithState(parcel.pack(), 18000);

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

        byte[] bytes = Do.read("./src/test_contracts/keys/u_key.public.unikey");

        for (int it = 0; it < 10000; it++) {
            PublicKey pk = new PublicKey(bytes);
            pk = null;
            System.gc();
        }
    }

    @Before
    public void beforeMainTest() throws Exception {
        // clearLedgers
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

        // add U issuer test key
        configForProvider.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
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
        return createMain(name, "", nolog);
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
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
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
        String[] args = new String[]{"--test", "--database", dbUrl, nolog ? "--nolog" : ""};

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
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
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
            mm.add(createMain("node" + (i + 1), "_dynamic_test", false));
        }
        //shutdown nodes
        for (Main m : mm) {
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

        PrivateKey universaKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));
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
        assertEquals(rr.state, ItemState.PENDING_POSITIVE);

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with KNOWN node. Should succeed
        Client clientKnown = new Client(universaKey, mm.get(0).myInfo, null);
        clientKnown.register(contract.getPackedTransaction(), 15000);
        while (true) {
            rr = clientKnown.getState(contract.getId());
            Thread.currentThread().sleep(500);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);

        //Make 4th node KNOWN to other nodes
        for (int i = 0; i < 3; i++) {
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
            Thread.currentThread().sleep(500);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);
//        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
//        mm.get(0).setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);

        //Make 4th node UNKNOWN to other nodes
        for (int i = 0; i < 3; i++) {
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
        assertEquals(rr.state, ItemState.PENDING_POSITIVE);

        contract = new Contract(universaKey);
        contract.seal();
        assertTrue(contract.isOk());

        //registering with KNOWN node. Should succeed
        clientKnown.register(contract.getPackedTransaction(), 15000);
        while (true) {
            rr = clientKnown.getState(contract.getId());
            Thread.currentThread().sleep(500);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);

        for (Main m : mm) {
            m.shutdown();
        }
    }

    @Ignore
    @Test // no asserts
    public void networkReconfigurationTestParallel() throws Exception {
        if(1 > 0)
            return;

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
            Parcel parcel = createParcelWithFreshU(client, contract, Do.listOf(myKey));
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
                    client.registerParcelWithState(parcels.get(finalI).pack(), 15000);
                    ItemResult rr;
                    while (true) {
                        rr = client.getState(contract.getId());
                        Thread.currentThread().sleep(500);
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
                    Thread.sleep(nodeSleeps.get(finalI));
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

    @Ignore
    @Test
    public void reconfigurationContractTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));

        List<Main> mm = new ArrayList<>();
        List<PrivateKey> nodeKeys = new ArrayList<>();
        List<PrivateKey> nodeKeysNew = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mm.add(createMain("node" + (i + 1), "_dynamic_test", false));
            if (i < 3)
                nodeKeys.add(new PrivateKey(Do.read("./src/test_node_config_v2_dynamic_test/node" + (i + 1) + "/tmp/node2_" + (i + 1) + ".private.unikey")));
            nodeKeysNew.add(new PrivateKey(Do.read("./src/test_node_config_v2_dynamic_test/node" + (i + 1) + "/tmp/node2_" + (i + 1) + ".private.unikey")));
        }

        List<NodeInfo> netConfig = mm.get(0).netConfig.toList();
        List<NodeInfo> netConfigNew = mm.get(3).netConfig.toList();

        for (Main m : mm) {
            m.shutdown();
        }
        mm.clear();

        Contract configContract = createNetConfigContract(netConfig, issuerKey);

        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");

        for (int i = 0; i < 4; i++) {
            mm.add(createMainFromDb(dbUrls.get(i), false));
        }

        Client client = new Client(TestKeys.privateKey(0), mm.get(0).myInfo, null);

        Parcel parcel = createParcelWithFreshU(client, configContract, Do.listOf(issuerKey));
        client.registerParcelWithState(parcel.pack(), 15000);

        ItemResult rr;
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(500);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);

        configContract = createNetConfigContract(configContract, netConfigNew, nodeKeys);

        parcel = createParcelWithFreshU(client, configContract, nodeKeys);
        client.registerParcelWithState(parcel.pack(), 15000);
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(500);
            if (!rr.state.isPending())
                break;
        }
        assertEquals(rr.state, ItemState.APPROVED);
        Thread.sleep(1000);
        for (Main m : mm) {
            assertEquals(m.config.getPositiveConsensus(), 3);
        }
        configContract = createNetConfigContract(configContract, netConfig, nodeKeys);

        parcel = createParcelWithFreshU(client, configContract, nodeKeys);
        client.registerParcelWithState(parcel.pack(), 15000);
        while (true) {

            rr = client.getState(configContract.getId());
            Thread.currentThread().sleep(500);
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
        for (NodeInfo ni : netConfig) {
            SimpleRole role = new SimpleRole(ni.getName());
            contract.registerRole(role);
            role.addKeyRecord(new KeyRecord(ni.getPublicKey()));
            listRole.addRole(role);
        }
        listRole.setQuorum(netConfig.size() - 1);
        contract.registerRole(listRole);
        contract.getStateData().set("net_config", netConfig);
        List<KeyRecord> creatorKeys = new ArrayList<>();
        for (PrivateKey key : currentConfigKeys) {
            creatorKeys.add(new KeyRecord(key.getPublicKey()));
            contract.addSignerKey(key);
        }
        contract.setCreator(creatorKeys);
        contract.seal();
        return contract;
    }

    private Contract createNetConfigContract(List<NodeInfo> netConfig, PrivateKey issuerKey) throws IOException {
        Contract contract = new Contract();
        contract.setIssuerKeys(issuerKey.getPublicKey());
        contract.registerRole(new RoleLink("creator", "issuer"));
        ListRole listRole = new ListRole("owner");
        for (NodeInfo ni : netConfig) {
            SimpleRole role = new SimpleRole(ni.getName());
            contract.registerRole(role);
            role.addKeyRecord(new KeyRecord(ni.getPublicKey()));
            listRole.addRole(role);
        }
        listRole.setQuorum(netConfig.size() - 1);
        contract.registerRole(listRole);
        RoleLink ownerLink = new RoleLink("ownerlink", "owner");
        ChangeOwnerPermission changeOwnerPermission = new ChangeOwnerPermission(ownerLink);
        HashMap<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("net_config", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        contract.addPermission(changeOwnerPermission);
        contract.addPermission(modifyDataPermission);
        contract.setExpiresAt(ZonedDateTime.now().plusYears(40));
        contract.getStateData().set("net_config", netConfig);
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

        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.network.getVerboseLevel());
        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.node.getVerboseLevel());

        Contract testContract = new Contract(myKey);
        testContract.seal();
        assertTrue(testContract.isOk());
        Parcel parcel = createParcelWithFreshU(client, testContract, Do.listOf(myKey));
        client.registerParcelWithState(parcel.pack(), 1000);
        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());

        main.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        System.out.println("---------- verbose base ---------------");

        Contract testContract2 = new Contract(myKey);
        testContract2.seal();
        assertTrue(testContract2.isOk());
        Parcel parcel2 = createParcelWithFreshU(client, testContract2, Do.listOf(myKey));
        client.registerParcelWithState(parcel2.pack(), 1000);
        ItemResult itemResult2 = client.getState(parcel2.getPayloadContract().getId());

        assertEquals(DatagramAdapter.VerboseLevel.BASE, main.network.getVerboseLevel());
        assertEquals(DatagramAdapter.VerboseLevel.BASE, main.node.getVerboseLevel());

        main.setVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        System.out.println("---------- verbose nothing ---------------");

        Contract testContract3 = new Contract(myKey);
        testContract3.seal();
        assertTrue(testContract3.isOk());
        Parcel parcel3 = createParcelWithFreshU(client, testContract3, Do.listOf(myKey));
        client.registerParcelWithState(parcel3.pack(), 1000);
        ItemResult itemResult3 = client.getState(parcel3.getPayloadContract().getId());

        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.network.getVerboseLevel());
        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.node.getVerboseLevel());

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

        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.network.getUDPVerboseLevel());

        Contract testContract = new Contract(myKey);
        testContract.seal();
        assertTrue(testContract.isOk());
        Parcel parcel = createParcelWithFreshU(client, testContract, Do.listOf(myKey));
        client.registerParcelWithState(parcel.pack(), 1000);
        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        System.out.println("---------- verbose base ---------------");

        Contract testContract2 = new Contract(myKey);
        testContract2.seal();
        assertTrue(testContract2.isOk());
        Parcel parcel2 = createParcelWithFreshU(client, testContract2, Do.listOf(myKey));
        client.registerParcelWithState(parcel2.pack(), 1000);
        ItemResult itemResult2 = client.getState(parcel2.getPayloadContract().getId());

        assertEquals(DatagramAdapter.VerboseLevel.BASE, main.network.getUDPVerboseLevel());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
        System.out.println("---------- verbose detailed ---------------");

        Contract testContract4 = new Contract(myKey);
        testContract4.seal();
        assertTrue(testContract4.isOk());
        Parcel parcel4 = createParcelWithFreshU(client, testContract4, Do.listOf(myKey));
        client.registerParcelWithState(parcel4.pack(), 1000);
        ItemResult itemResult4 = client.getState(parcel4.getPayloadContract().getId());

        assertEquals(DatagramAdapter.VerboseLevel.DETAILED, main.network.getUDPVerboseLevel());

        main.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
        System.out.println("---------- verbose nothing ---------------");

        Contract testContract3 = new Contract(myKey);
        testContract3.seal();
        assertTrue(testContract3.isOk());
        Parcel parcel3 = createParcelWithFreshU(client, testContract3, Do.listOf(myKey));
        client.registerParcelWithState(parcel3.pack(), 1000);
        ItemResult itemResult3 = client.getState(parcel3.getPayloadContract().getId());

        assertEquals(DatagramAdapter.VerboseLevel.NOTHING, main.network.getUDPVerboseLevel());

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
        Parcel parcel = createParcelWithFreshU(client, testContract, Do.listOf(myKey));
        client.registerParcelWithState(parcel.pack(), 5000);
        System.out.println(">> before shutdown state: " + client.getState(parcel.getPayloadContract().getId()));
        System.out.println(">> before shutdown state: " + client.getState(parcel.getPayloadContract().getNew().get(0).getId()));

        main.shutdown();
        Thread.sleep(8000);

        mm.remove(main);
        main = createMain("node1", false);
        mm.add(main);
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }
        Thread.currentThread().sleep(8000);

        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        ItemResult itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());
        System.out.println(">> after shutdown state: " + itemResult + " and new " + itemResult2);

        while (itemResult.state.isPending()) {
            Thread.currentThread().sleep(500);
            itemResult = client.getState(parcel.getPayloadContract().getId());
            System.out.println(">> wait result: " + itemResult);
        }
        itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult2.state);

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
        for (int i = 0; i < 50; i++) {
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

        Parcel parcel = createParcelWithFreshU(client, testContract, Do.listOf(myKey));
        client.registerParcel(parcel.pack());

        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        while (itemResult.state.isPending()) {
            Thread.currentThread().sleep(500);
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
            Thread.currentThread().sleep(500);
            itemResult = client.getState(parcel.getPayloadContract().getId());
            System.out.println(">> wait result: " + itemResult);
        }

        Thread.sleep(7000);
        itemResult = client.getState(parcel.getPayloadContract().getId());
        itemResult2 = client.getState(parcel.getPayloadContract().getNew().get(0).getId());

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult2.state);

        mm.forEach(x -> x.shutdown());
    }

    public synchronized Parcel createParcelWithFreshU(Client client, Contract c, Collection<PrivateKey> keys) throws Exception {
        Set<PublicKey> ownerKeys = new HashSet();
        keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
        Contract stepaU = InnerContractsService.createFreshU(100000000, ownerKeys);
        stepaU.check();
        stepaU.traceErrors();

        PrivateKey clientPrivateKey = client.getSession().getPrivateKey();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));
        client.getSession().setPrivateKey(newPrivateKey);
        client.restart();

        ItemResult itemResult = client.register(stepaU.getPackedTransaction(), 5000);

        client.getSession().setPrivateKey(clientPrivateKey);
        client.restart();

        assertEquals(ItemState.APPROVED, itemResult.state);
        Set<PrivateKey> keySet = new HashSet<>();
        keySet.addAll(keys);
        return ContractsService.createParcel(c, stepaU, 150, keySet);
    }

    @Test
    public void registerContractWithAnonymousId() throws Exception {
        TestSpace ts = prepareTestSpace();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));

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

        testSpace.clients = new ArrayList();
        for (int i = 0; i < 4; i++)
            testSpace.clients.add(new Client(testSpace.myKey, testSpace.nodes.get(i).myInfo, null));
        return testSpace;
    }

    private class TestSpace {
        public List<Main> nodes = null;
        public Main node = null;
        PrivateKey myKey = null;
        Client client = null;
        Object uContractLock = new Object();
        Contract uContract = null;
        public ArrayList<Client> clients;

        public void shutdown() {
            nodes.forEach(n->n.shutdown());
        }

        public Set<PrivateKey> getUKeys() {
            return new HashSet<PrivateKey>(Do.listOf(myKey));
        }
    }

    private static final int MAX_PACKET_SIZE = 512;

    protected void sendBlock(UDPAdapter.Packet packet, DatagramSocket socket, NodeInfo destination) throws InterruptedException {

        byte[] out = packet.makeByteArray();
        DatagramPacket dp = new DatagramPacket(out, out.length, destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort());

        try {
            socket.send(dp);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void sendHello(NodeInfo myNodeInfo, NodeInfo destination, UDPAdapter udpAdapter, DatagramSocket socket) throws InterruptedException {
//      System.out.println(">> send froud from " + myNodeInfo.getNumber() + " to " + destination.getNumber());
        Binder binder = Binder.fromKeysValues(
                "data", myNodeInfo.getNumber()
        );
        UDPAdapter.Packet packet = udpAdapter.createTestPacket(
                new Random().nextInt(Integer.MAX_VALUE),
                myNodeInfo.getNumber(),
                destination.getNumber(),
                UDPAdapter.PacketTypes.HELLO,
                Boss.pack(binder));
        sendBlock(packet, socket, destination);
    }

    protected void sendWelcome(NodeInfo myNodeInfo, NodeInfo destination, UDPAdapter udpAdapter, DatagramSocket socket) throws Exception {
        byte[] payload = new PublicKey(destination.getPublicKey().pack()).encrypt(Do.randomBytes(64));
        UDPAdapter.Packet packet = udpAdapter.createTestPacket(
                new Random().nextInt(Integer.MAX_VALUE),
                myNodeInfo.getNumber(),
                destination.getNumber(),
                UDPAdapter.PacketTypes.WELCOME,
                payload);
        sendBlock(packet, socket, destination);
    }

    protected void sendDataGarbage(NodeInfo myNodeInfo, NodeInfo destination, UDPAdapter udpAdapter, DatagramSocket socket) throws Exception {
        byte[] data = new PublicKey(destination.getPublicKey().pack()).encrypt(Do.randomBytes(64));
        byte[] crc32 = new Crc32().digest(data);
        byte[] payload = new byte[data.length + crc32.length];
        System.arraycopy(data, 0, payload, 0, data.length);
        System.arraycopy(crc32, 0, payload, data.length, crc32.length);
        UDPAdapter.Packet packet = udpAdapter.createTestPacket(
                new Random().nextInt(Integer.MAX_VALUE),
                myNodeInfo.getNumber(),
                destination.getNumber(),
                UDPAdapter.PacketTypes.DATA,
                payload);
        sendBlock(packet, socket, destination);
    }

    @Ignore
    @Test
    public void udpDisruptionTest() throws Exception {
        List<Main> mm = new ArrayList<>();
        final int NODE_COUNT = 4;
        final int PORT_BASE = 12000;
        final int TEST_MODE = UDPAdapter.PacketTypes.HELLO;
        //final int TEST_MODE = UDPAdapter.PacketTypes.WELCOME;
        //final int TEST_MODE = UDPAdapter.PacketTypes.DATA;

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
                    DatagramSocket socket = new DatagramSocket(PORT_BASE + finalI * NODE_COUNT + finalJ);

                    while (alive) {
                        if (TEST_MODE == UDPAdapter.PacketTypes.HELLO)
                            sendHello(source, destination, mm.get(finalI).network.getUDPAdapter(), socket);
                        else if (TEST_MODE == UDPAdapter.PacketTypes.WELCOME)
                            sendWelcome(source, destination, mm.get(finalI).network.getUDPAdapter(), socket);
                        else
                            sendDataGarbage(source, destination, mm.get(finalI).network.getUDPAdapter(), socket);
                        Thread.sleep(4);
                    }
                } catch (Exception e) {
                    System.out.println("runnable exception: " + e.toString());
                }
            }
        }

        List<Thread> threadsList = new ArrayList<>();
        List<TestRunnable> runnableList = new ArrayList<>();
        for (int i = 0; i < NODE_COUNT; i++) {
            for (int j = 0; j < NODE_COUNT; j++) {
                if (j == i)
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
        Client client = new Client(myKey, mm.get(0).myInfo, null);

        Contract contract = new Contract(myKey);
        contract.seal();

        Parcel parcel = createParcelWithFreshU(client, contract, Do.listOf(myKey));
        client.registerParcelWithState(parcel.pack(), 60000);
        ItemResult rr;
        while (true) {
            rr = client.getState(contract.getId());
            if (!rr.state.isPending())
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
        for (int i = 0; i < N; i++) {
            Contract origin = new Contract(myKey);
            origin.seal();
            origins.add(origin);

            Contract newRevision = origin.createRevision(myKey);

            if (i < N / 2) {
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
            int unfinishedNodesCount = random.nextInt(2) + 1;
            Set<Integer> unfinishedNodesNumbers = new HashSet<>();
            while (unfinishedNodesCount > unfinishedNodesNumbers.size()) {
                unfinishedNodesNumbers.add(random.nextInt(NODE_COUNT) + 1);
            }

            System.out.println("item# " + newRevision.getId().toBase64String().substring(0, 6) + " nodes " + unfinishedNodesNumbers.toString());
            int finalI = i;
            for (int j = 0; j < NODE_COUNT; j++) {
                boolean finished = !unfinishedNodesNumbers.contains(j + 1);
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

                if (finished) {
                    if (finalI < N / 2) {
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
                    newRevisionRecord.setState(finalI < N / 2 ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
                }

                originRecord.save();
                ledger.putItem(originRecord, origin, Instant.now().plusSeconds(3600 * 24));
                newRevisionRecord.save();
                ledger.putItem(newRevisionRecord, newRevision, Instant.now().plusSeconds(3600 * 24));
                if (newContractRecord.getState() == ItemState.UNDEFINED) {
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
            m.config.setIsFreeRegistrationsAllowedFromYaml(true);
            mm.add(m);
            Client client = new Client(TestKeys.privateKey(i), m.myInfo, null);
            clients.add(client);
        }

        while (true) {
            try {
                for (int i = 0; i < NODE_COUNT; i++) {
                    clients.get(i).getState(newRevisions.get(0));
                }
                break;
            } catch (ClientError e) {
                Thread.sleep(1000);
                mm.stream().forEach(m -> System.out.println("node#" + m.myInfo.getNumber() + " is " + (m.node.isSanitating() ? "" : "not ") + "sanitating"));
            }
        }

        Contract contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        ItemResult ir = clients.get(0).register(contract.getPackedTransaction(), 10000);
        ir.errors.toString();

        for (int i = 0; i < N; i++) {
            ItemResult rr = clients.get(i % NODE_COUNT).getState(newRevisions.get(i).getId());
            ItemState targetState = i < N / 2 ? ItemState.APPROVED : ItemState.DECLINED;
            assertEquals(rr.state, targetState);
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
        try (DbPool dbPool = new DbPool(url, properties, 64)) {
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
        builder.appendValue(ChronoField.DAY_OF_MONTH, 2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.MONTH_OF_YEAR, 2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.YEAR, 4);

        System.out.println(now.format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth() - 1).format(builder.toFormatter()));
        System.out.println(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth() - 1).minusMonths(1).format(builder.toFormatter()));
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
        //testSpace.nodes.get(0).config.getKeysWhiteList().add(issuerKey.getPublicKey());
        testSpace.nodes.get(0).config.getAddressesWhiteList().add(new KeyAddress(issuerKey.getPublicKey(), 0, true));

        while (testSpace.client.getStats(null).getIntOrThrow("uptime") >= uptime) {
            Thread.sleep(500);
        }

        for (int i = 0; i < 30; i++) {
            Instant now = Instant.now();
            Contract contract = new Contract(issuerKey);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(), 1500);
            contract = new Contract(issuerKey);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(), 1500);

            Thread.sleep(4000 - (Instant.now().toEpochMilli() - now.toEpochMilli()));
            Binder binder = testSpace.client.getStats(90);

            assertEquals(binder.getIntOrThrow("smallIntervalApproved"), 2);
            int target = i < 15 ? (i + 1) * 2 : 30;
            assertTrue(binder.getIntOrThrow("bigIntervalApproved") <= target && binder.getIntOrThrow("bigIntervalApproved") >= target - 2);
        }

        testSpace.nodes.forEach(x -> x.shutdown());
    }


    @Test
    public void resynItemTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        //shutdown one of nodes
        int absentNode = testSpace.nodes.size() - 1;
        testSpace.nodes.get(absentNode).shutdown();
        testSpace.nodes.remove(absentNode);

        //register contract in non full network
        Contract contract = new Contract(issuerKey);
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(), 1500);
        assertEquals(testSpace.client.getState(contract.getId()).state, ItemState.APPROVED);


        //recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(contract.getId()).state, ItemState.APPROVED);


        StateRecord r0 = testSpace.nodes.get(0).node.getLedger().getRecord(contract.getId());
        r0.setExpiresAt(r0.getExpiresAt().minusHours(1));
        r0.save();


        StateRecord r1 = testSpace.nodes.get(1).node.getLedger().getRecord(contract.getId());
        r1.setCreatedAt(r1.getCreatedAt().plusHours(1));
        r1.save();

        //create client with absent node and check the contract
        Client absentNodeClient = new Client(testSpace.myKey, testSpace.nodes.get(absentNode).myInfo, null);
        assertEquals(absentNodeClient.getState(contract.getId()).state, ItemState.UNDEFINED);

        //start resync with a command
        absentNodeClient.resyncItem(contract.getId());

        //make sure resync didn't affect others
        assertEquals(testSpace.client.getState(contract.getId()).state, ItemState.APPROVED);


        //wait for new status
        ItemResult rr;
        while (true) {
            rr = absentNodeClient.getState(contract.getId());
            if (!rr.state.isPending())
                break;
            Thread.sleep(500);
        }
        assertEquals(rr.state, ItemState.APPROVED);


        Average createdAverage = new Average();
        Average expiredAverage = new Average();
        ItemResult ir0 = testSpace.nodes.get(0).node.checkItem(contract.getId());
        ItemResult ir1 = testSpace.nodes.get(1).node.checkItem(contract.getId());
        ItemResult ir2 = testSpace.nodes.get(2).node.checkItem(contract.getId());

        createdAverage.update(ir0.createdAt.toEpochSecond());
        createdAverage.update(ir2.createdAt.toEpochSecond());

        expiredAverage.update(ir1.expiresAt.toEpochSecond());
        expiredAverage.update(ir2.expiresAt.toEpochSecond());

        assertEquals(rr.createdAt.toEpochSecond(), (long) createdAverage.average());
        assertEquals(rr.expiresAt.toEpochSecond(), (long) expiredAverage.average());

        testSpace.nodes.forEach(x -> x.shutdown());

    }

    @Test
    public void resyncFromClient() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        testSpace.nodes.get(testSpace.nodes.size() - 1).shutdown();
        Contract contractMoney = ContractsService.createTokenContract(
                new HashSet<>(asList(TestKeys.privateKey(1))),
                new HashSet<>(asList(TestKeys.publicKey(1))),
                new BigDecimal("9000")
        );
        ItemResult ir1 = testSpace.client.register(contractMoney.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir1.state);

        //recreate nodes
        for (int i = 0; i < testSpace.nodes.size() - 1; ++i)
            testSpace.nodes.get(i).shutdown();
        Thread.sleep(2000);
        testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        System.out.println("\n========== resyncing ==========\n");
        testSpace.nodes.get(testSpace.clients.size() - 1).setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        testSpace.clients.get(testSpace.clients.size() - 1).resyncItem(contractMoney.getId());
        long millisToWait = 60000;
        long waitPeriod = 2000;
        ItemResult ir = null;
        while (millisToWait > 0) {
            Thread.sleep(waitPeriod);
            millisToWait -= waitPeriod;
            ir = testSpace.clients.get(testSpace.clients.size() - 1).getState(contractMoney.getId());
            if (ir.state == ItemState.APPROVED)
                break;
        }
        assertEquals(ItemState.APPROVED, ir.state);
        testSpace.nodes.forEach(n -> n.shutdown());
    }

    @Test
    public void resyncSubItemsTest() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        testSpace.nodes.get(testSpace.nodes.size() - 1).shutdown();

        Contract contractMoney = ContractsService.createTokenContract(
                new HashSet<>(asList(TestKeys.privateKey(1))),
                new HashSet<>(asList(TestKeys.publicKey(1))),
                new BigDecimal("9000")
        );
        ItemResult ir1 = testSpace.client.register(contractMoney.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir1.state);

        List<Contract> splittedList = new ArrayList<>();
        Contract splitNest = contractMoney.createRevision();
        splitNest.addSignerKey(TestKeys.privateKey(1));
        Contract[] contracts = splitNest.split(10);
        Decimal valChange = new Decimal(contractMoney.getStateData().getStringOrThrow("amount"));
        for (int i = 0; i < contracts.length; ++i) {
            Contract splitted = contracts[i];
            Decimal val = new Decimal(i + 1);
            splitted.getStateData().set("amount", val);
            splittedList.add(splitted);
            valChange = valChange.subtract(val);
        }
        splitNest.getStateData().set("amount", valChange);

        splittedList.forEach(c -> System.out.println("splitted amount: " + c.getStateData().getStringOrThrow("amount")));
        System.out.println("contractMoney amount (revoke it): " + contractMoney.getStateData().getStringOrThrow("amount"));
        System.out.println("splitNest amount: " + splitNest.getStateData().getStringOrThrow("amount"));

        Contract splitBatch = new Contract(TestKeys.privateKey(1));
        splitNest.seal();
        splitBatch.addNewItems(splitNest);
        splitBatch.seal();

        ItemResult irSplitBatch = testSpace.client.register(splitBatch.getPackedTransaction(), 5000);
        Thread.sleep(1000);
        assertEquals(ItemState.APPROVED, irSplitBatch.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(splitNest.getId()).state);
        for (Contract c : splittedList)
            assertEquals(ItemState.APPROVED, testSpace.client.getState(c.getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(contractMoney.getId()).state);

        //recreate nodes
        for (int i = 0; i < testSpace.nodes.size() - 1; ++i)
            testSpace.nodes.get(i).shutdown();
        Thread.sleep(2000);
        testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        testSpace.clients.get(testSpace.clients.size() - 1).resyncItem(splitNest.getId());
        Thread.sleep(2000);

        for (Contract c : splitBatch.getNew()) {
            for (int i = 0; i < testSpace.nodes.size(); ++i) {
                Main m = testSpace.nodes.get(i);
                if (c.getId().equals(splitNest.getId())) {
                    assertTrue(m.node.getLedger().getRecord(c.getId()).isApproved());
                } else {
                    if (i < testSpace.nodes.size() - 1)
                        assertTrue(m.node.getLedger().getRecord(c.getId()).isApproved());
                    else
                        assertNull(m.node.getLedger().getRecord(c.getId()));
                }
            }
        }

        //now join all
        testSpace.nodes.get(testSpace.clients.size() - 1).setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        Contract joinAll = splitNest.createRevision();
        joinAll.getStateData().set("amount", "9000");
        splittedList.forEach(c -> joinAll.addRevokingItems(c));
        Decimal joinSum = new Decimal(0);
        for (Contract c : joinAll.getRevoking())
            joinSum = joinSum.add(new Decimal(c.getStateData().getStringOrThrow("amount")));
        joinAll.addSignerKey(TestKeys.privateKey(1));
        joinAll.seal();
        System.out.println("client: " + testSpace.clients.get(testSpace.clients.size() - 1).getUrl());
        ItemResult irJoin = testSpace.clients.get(testSpace.clients.size() - 1).register(joinAll.getPackedTransaction(), 5000);
        Thread.sleep(1000);
        assertEquals(ItemState.APPROVED, irJoin.state);
        System.out.println("joinAll amount: " + joinAll.getStateData().getStringOrThrow("amount"));
        for (Contract c : joinAll.getRevoking()) {
            for (int i = 0; i < testSpace.nodes.size(); ++i) {
                Main m = testSpace.nodes.get(i);
                assertTrue(m.node.getLedger().getRecord(c.getId()).isArchived());
            }
        }
        for (int i = 0; i < testSpace.nodes.size(); ++i) {
            Main m = testSpace.nodes.get(i);
            assertTrue(m.node.getLedger().getRecord(joinAll.getId()).isApproved());
        }

        testSpace.nodes.forEach(n -> n.shutdown());
    }

    @Test
    public void verboseLevelTest() throws Exception {
        PrivateKey issuerKey = new PrivateKey(Do.read("./src/test_contracts/keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);

        Contract contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(), 8000);
        Thread.sleep(2000);
        testSpace.client.setVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING, DatagramAdapter.VerboseLevel.DETAILED, DatagramAdapter.VerboseLevel.NOTHING);
        contract = new Contract(TestKeys.privateKey(3));
        contract.seal();
        testSpace.client.register(contract.getPackedTransaction(), 8000);

        testSpace.nodes.forEach(x -> x.shutdown());
    }


    @Test
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


    @Test
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

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

        Contract tokenContract = ContractsService.createTokenContract(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("1000000"));
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
        assertEquals(splitJoinParams.get("min_value"), "0.01");
        assertEquals(splitJoinParams.get("min_unit"), "0.01");
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List) splitJoinParams.get("join_match_fields")).get(0), "state.origin");

        assertTrue(tokenContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(tokenContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(tokenContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(tokenContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(tokenContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(tokenContract.isPermitted("split_join", issuerPublicKeys));

        ItemResult itemResult = client.register(tokenContract.getPackedTransaction(), 5000);
        System.out.println("token contract : " + itemResult);
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

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

        Contract mintableTokenContract = ContractsService.createTokenContractWithEmission(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("300000000000"));

        mintableTokenContract.check();
        mintableTokenContract.traceErrors();

        ItemResult itemResult = client.register(mintableTokenContract.getPackedTransaction(), 5000);
        System.out.println("mintableTokenContract : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract emittedContract = ContractsService.createTokenEmission(mintableTokenContract, new BigDecimal("100000000000"), issuerPrivateKeys);

        emittedContract.check();
        emittedContract.traceErrors();

        assertEquals(emittedContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = emittedContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), "0.01");
        assertEquals(splitJoinParams.get("min_unit"), "0.01");
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List) splitJoinParams.get("join_match_fields")).get(0), "state.origin");


        assertTrue(emittedContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(emittedContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(emittedContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(emittedContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(emittedContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(emittedContract.isPermitted("split_join", issuerPublicKeys));


        itemResult = client.register(emittedContract.getPackedTransaction(), 5000);
        System.out.println("emittedContract : " + itemResult);
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

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));
        Set<PrivateKey> issuerPrivateKeys2 = new HashSet<>(asList(TestKeys.privateKey(2)));

        Contract contractC = ContractsService.createTokenContract(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("100"));
        contractC.check();
        contractC.traceErrors();

        ItemResult itemResult = client.register(contractC.getPackedTransaction(), 5000);
        System.out.println("contractC : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // 100 - 30 = 70
        Contract сontractA = ContractsService.createSplit(contractC, new BigDecimal("30"), "amount", issuerPrivateKeys2, true);
        Contract contractB = сontractA.getNew().get(0);
        assertEquals("70", сontractA.getStateData().get("amount").toString());
        assertEquals("30", contractB.getStateData().get("amount").toString());

        itemResult = client.register(сontractA.getPackedTransaction(), 5000);
        System.out.println("сontractA : " + itemResult);
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
        System.out.println("contractC2 : " + itemResult);
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

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

        Contract shareContract = ContractsService.createShareContract(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("100"));

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
        assertEquals(((List) splitJoinParams.get("join_match_fields")).get(0), "state.origin");

        assertTrue(shareContract.isPermitted("revoke", ownerPublicKeys));
        assertTrue(shareContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(shareContract.isPermitted("change_owner", ownerPublicKeys));
        assertFalse(shareContract.isPermitted("change_owner", issuerPublicKeys));

        assertTrue(shareContract.isPermitted("split_join", ownerPublicKeys));
        assertFalse(shareContract.isPermitted("split_join", issuerPublicKeys));

        ItemResult itemResult = client.register(shareContract.getPackedTransaction(), 5000);
        System.out.println("shareContract : " + itemResult);
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

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> issuerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

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
        System.out.println("notaryContract : " + itemResult);
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

        Set<PrivateKey> martyPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PublicKey> stepaPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

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
        System.out.println("twoSignContract : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);


        //now emulate sending transaction pack through network <----

        twoSignContract = Contract.fromPackedTransaction(twoSignContract.getPackedTransaction());

        twoSignContract.addSignatureToSeal(martyPrivateKeys);

        twoSignContract.check();
        twoSignContract.traceErrors();
        System.out.println("Contract with two signature is valid: " + twoSignContract.isOk());

        itemResult = client.register(twoSignContract.getPackedTransaction(), 5000);
        System.out.println("twoSignContract : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }







    @Test
    public void testRevocationContractsApi() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));

        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));

        Contract sourceContract = ContractsService.createShareContract(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("100"));

        sourceContract.check();
        sourceContract.traceErrors();

        ItemResult itemResult = client.register(sourceContract.getPackedTransaction(), 5000);
        System.out.println("sourceContract : " + itemResult);
        assertEquals(ItemState.APPROVED, client.getState(sourceContract.getId()).state);

        Contract revokeContract = ContractsService.createRevocation(sourceContract, TestKeys.privateKey(1));

        revokeContract.check();
        revokeContract.traceErrors();

        itemResult = client.register(revokeContract.getPackedTransaction(), 5000);
        System.out.println("revokeContract : " + itemResult);

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

        Set<PrivateKey> martyPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PublicKey> martyPublicKeys = new HashSet<>(asList(TestKeys.publicKey(1)));
        Set<PublicKey> stepaPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));


        Contract delorean = ContractsService.createTokenContract(martyPrivateKeys, martyPublicKeys, new BigDecimal("100"), new BigDecimal("0.0001"));
        delorean.seal();

        delorean.check();
        delorean.traceErrors();

        ItemResult itemResult = client.register(delorean.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract lamborghini = ContractsService.createTokenContract(stepaPrivateKeys, stepaPublicKeys, new BigDecimal("100"), new BigDecimal("0.0001"));
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
            if (c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if (c.getParent().equals(lamborghini.getId())) {
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

        Set<PrivateKey> user1PrivateKeySet = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> user2PrivateKeySet = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PublicKey> user1PublicKeySet = user1PrivateKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());
        Set<PublicKey> user2PublicKeySet = user2PrivateKeySet.stream().map(prv -> prv.getPublicKey()).collect(Collectors.toSet());


        Contract contractTOK92 = ContractsService.createTokenContract(user1PrivateKeySet, user1PublicKeySet, new BigDecimal("100"), new BigDecimal("0.0001"));
        Contract contractTOK93 = ContractsService.createTokenContract(user2PrivateKeySet, user2PublicKeySet, new BigDecimal("100"), new BigDecimal("0.001"));

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
        Contract user1CoinsSplit = ContractsService.createSplit(contractTOK92, new BigDecimal("8.02"), "amount", user1PrivateKeySet);
        Contract user1CoinsSplitToUser2 = user1CoinsSplit.getNew().get(0);
        // TOK93: 100 - 10.01 = 89.99
        Contract user2CoinsSplit = ContractsService.createSplit(contractTOK93, new BigDecimal("10.01"), "amount", user2PrivateKeySet);
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
        Set<PrivateKey> llcPrivateKeys = new HashSet<>(); //issuer
        Set<PrivateKey> thirdPartyPrivateKeys = new HashSet<>();

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
        System.out.println("sourceContract : " + itemResult);
        assertEquals(ItemState.APPROVED, client.getState(jobCertificate.getId()).state);

        Contract llcProperty = ContractsService.createNotaryContract(llcPrivateKeys, stepaPublicKeys);

        List<String> listConditions = new ArrayList<>();
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
        System.out.println("sourceContract : " + itemResult);
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

        Contract stepaU = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        ItemResult itemResult = client.register(stepaU.getPackedTransaction(), 5000);
        System.out.println("stepaU itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        main.config.setIsFreeRegistrationsAllowedFromYaml(false);

        Parcel parcel = ContractsService.createParcel(simpleContract, stepaU, 1, new HashSet<>(asList(TestKeys.privateKey(1))), false);
        client.registerParcelWithState(parcel.pack(), 5000);
        assertEquals(ItemState.APPROVED, client.getState(simpleContract.getId()).state);

        mm.forEach(x -> x.shutdown());

    }

    protected static final String ROOT_PATH = "./src/test_contracts/";


    protected Contract getApprovedUContract(TestSpace testSpace) throws Exception {
        synchronized (testSpace.uContractLock) {
            if (testSpace.uContract == null) {

                Set<PublicKey> keys = new HashSet();
                keys.add(testSpace.myKey.getPublicKey());
                Contract stepaU = InnerContractsService.createFreshU(100000000, keys);
                stepaU.check();
                stepaU.traceErrors();
                System.out.println("register new U ");
                testSpace.node.node.registerItem(stepaU);
                testSpace.uContract = stepaU;
            }
            int needRecreateUContractNum = 0;
            for (Main m : testSpace.nodes) {
                try {
                    ItemResult itemResult = m.node.waitItem(testSpace.uContract.getId(), 15000);
                    //assertEquals(ItemState.APPROVED, itemResult.state);
                    if (itemResult.state != ItemState.APPROVED) {
                        System.out.println("U: node " + m.node + " result: " + itemResult);
                        needRecreateUContractNum++;
                    }
                } catch (TimeoutException e) {
                    System.out.println("ping ");
//                    System.out.println(n.ping());
////                    System.out.println(n.traceTasksPool());
//                    System.out.println(n.traceParcelProcessors());
//                    System.out.println(n.traceItemProcessors());
                    System.out.println("U: node " + m.node + " timeout: ");
                    needRecreateUContractNum++;
                }
            }
            int recreateBorder = testSpace.nodes.size() - testSpace.node.config.getPositiveConsensus() - 1;
            if (recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateUContractNum > recreateBorder) {
                testSpace.uContract = null;
                Thread.sleep(1000);
                return getApprovedUContract(testSpace);
            }
            return testSpace.uContract;
        }
    }

    private Config configForProvider = new Config();

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NodeConfigProvider(configForProvider);



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

        for (int k = 0; k < 4; k++) {
            if (doShutdown) {
                //shutdown one of nodes
                if (k < 3) {
                    int absentNode = k + 1;
                    testSpace.nodes.get(absentNode).shutdown();
                    testSpace.nodes.remove(absentNode);
                }
            }

            Contract contract = new Contract(issuerKey);
            contract.getDefinition().getData().set("test", "test1");
            contract.getStateData().set("amount", "100");
            Binder params = Binder.of("field_name", "amount", "join_match_fields", asList("definition.issuer"));
            Role ownerLink = new RoleLink("@owner_link", "owner");
            contract.registerRole(ownerLink);
            SplitJoinPermission splitJoinPermission = new SplitJoinPermission(ownerLink, params);
            contract.addPermission(splitJoinPermission);
            contract.seal();
            testSpace.client.register(contract.getPackedTransaction(), 1500);
            assertEquals(testSpace.client.getState(contract.getId()).state, ItemState.APPROVED);

            if (doShutdown) {
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
                    if (main.node.waitItem(c.getId(), 4000).state != ItemState.APPROVED) {
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
        main.getStateData().set("amount", "400");
        main.addRevokingItems(contractsToJoin.get(0), contractsToJoin.get(1), contractsToJoin.get(2));
        main.addSignerKey(issuerKey);
        main.seal();
        contractsToJoin.add(c);
        testSpace.client.register(main.getPackedTransaction(), 1500);
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
                    if (main1.node.waitItem(c1.getId(), 4000).state != ItemState.APPROVED) {
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

        assertEquals(ir.state, ItemState.APPROVED);

        testSpace.nodes.forEach(x -> x.shutdown());

    }

    private static final String REFERENCE_CONDITION_PREFIX = "ref.id==";
    private static final String REFERENCE_CONDITION2 = "ref.state.revision==1";

    @Test
    public void tttt() throws Exception {
        boolean refAsNew = true;

        PrivateKey key = TestKeys.privateKey(1);
        TestSpace testSpace = prepareTestSpace(key);

        testSpace.nodes.forEach(m -> {
            m.config.setIsFreeRegistrationsAllowedFromYaml(true);
        });

        Contract contractMark = new Contract(key);
        contractMark.seal();
        HashId origin = contractMark.getId();

        Contract contractMark2 = new Contract(key);
        contractMark2.seal();
        HashId origin2 = contractMark2.getId();


        Contract contract = new Contract(key);

        SimpleRole issuer = new SimpleRole("issuer");
        issuer.addKeyRecord(new KeyRecord(key.getPublicKey()));

        Reference ref = new Reference(contract);
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setName(origin.toString());

        List<Object> conditionsList = new ArrayList<>();
        conditionsList.add(REFERENCE_CONDITION_PREFIX + "\"" + origin.toBase64String() + "\"");
        conditionsList.add(REFERENCE_CONDITION2);
        Binder conditions = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList);
        ref.setConditions(conditions);


        Reference ref2 = new Reference(contract);
        ref2.type = Reference.TYPE_EXISTING_STATE;
        ref2.setName(origin2.toString());

        List<Object> conditionsList2 = new ArrayList<>();
        conditionsList2.add(REFERENCE_CONDITION_PREFIX + "\"" + origin2.toBase64String() + "\"");
        conditionsList2.add(REFERENCE_CONDITION2);
        Binder conditions2 = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList2);
        ref2.setConditions(conditions2);


        //???
        contract.addReference(ref);
        issuer.addRequiredReference(ref, Role.RequiredMode.ALL_OF);
        contract.addReference(ref2);
        issuer.addRequiredReference(ref2, Role.RequiredMode.ALL_OF);

        contract.registerRole(issuer);
        contract.setOwnerKeys(key);
        contract.seal();
        ItemResult ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //NO matching item for issuer reference in transaction pack
        assertEquals(ir.state, ItemState.DECLINED);

        contract.seal();
        contract.getTransactionPack().addReferencedItem(contractMark);
        contract.getTransactionPack().addReferencedItem(contractMark2);
        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //matching item for issuer reference is not APPROVED
        assertEquals(ir.state, ItemState.DECLINED);


        if (refAsNew) {
            contract.addNewItems(contractMark);
            contract.addNewItems(contractMark2);
        } else {
            testSpace.client.register(contractMark.getPackedTransaction(), 5000);
            testSpace.client.register(contractMark2.getPackedTransaction(), 5000);
        }

        contract.seal();
        if (!refAsNew) {
            contract.getTransactionPack().addReferencedItem(contractMark);
            contract.getTransactionPack().addReferencedItem(contractMark2);
        }
        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //all ok
        assertEquals(ir.state, ItemState.APPROVED);


        //no markContract is required referenced items of transaction pack
        contract = contract.createRevision(key);
        contract.addSignerKey(key);
        contract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        contract.seal();

        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
        //all ok

        assertEquals(ir.state, ItemState.APPROVED);

        testSpace.nodes.forEach(n -> n.shutdown());
    }


    @Test
    public void testMarkReferences5() throws Exception {

        PrivateKey key = TestKeys.privateKey(1);
        TestSpace testSpace = prepareTestSpace(key);

        testSpace.nodes.forEach(m -> {
            m.config.setIsFreeRegistrationsAllowedFromYaml(true);
        });

        Contract contractMark = new Contract(key);
        contractMark.seal();
        HashId origin = contractMark.getId();

        Contract contractMark2 = new Contract(key);
        contractMark2.seal();
        HashId origin2 = contractMark2.getId();

        Contract contractMark3 = new Contract(key);
        contractMark3.seal();
        HashId origin3 = contractMark3.getId();

        Contract contractMark4 = new Contract(key);
        contractMark4.seal();
        HashId origin4 = contractMark4.getId();

        Contract contractMark5 = new Contract(key);
        contractMark5.seal();
        HashId origin5 = contractMark5.getId();


        Contract contract = new Contract(key);

        SimpleRole issuer = new SimpleRole("issuer");
        issuer.addKeyRecord(new KeyRecord(key.getPublicKey()));

        Reference ref = new Reference(contract);
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setName(origin.toString());

        List<Object> conditionsList = new ArrayList<>();
        conditionsList.add(REFERENCE_CONDITION_PREFIX + "\"" + origin.toBase64String() + "\"");
        conditionsList.add(REFERENCE_CONDITION2);
        Binder conditions = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList);
        ref.setConditions(conditions);

        contract.addReference(ref);
        issuer.addRequiredReference(ref, Role.RequiredMode.ALL_OF);


        Reference ref2 = new Reference(contract);
        ref2.type = Reference.TYPE_EXISTING_STATE;
        ref2.setName(origin2.toString());

        List<Object> conditionsList2 = new ArrayList<>();
        conditionsList2.add(REFERENCE_CONDITION_PREFIX + "\"" + origin2.toBase64String() + "\"");
        conditionsList2.add(REFERENCE_CONDITION2);
        Binder conditions2 = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList2);
        ref2.setConditions(conditions2);

        contract.addReference(ref2);
        issuer.addRequiredReference(ref2, Role.RequiredMode.ALL_OF);


        Reference ref3 = new Reference(contract);
        ref3.type = Reference.TYPE_EXISTING_STATE;
        ref3.setName(origin3.toString());

        List<Object> conditionsList3 = new ArrayList<>();
        conditionsList3.add(REFERENCE_CONDITION_PREFIX + "\"" + origin3.toBase64String() + "\"");
        conditionsList3.add(REFERENCE_CONDITION2);
        Binder conditions3 = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList3);
        ref3.setConditions(conditions3);

        contract.addReference(ref3);
        issuer.addRequiredReference(ref3, Role.RequiredMode.ALL_OF);

        Reference ref4 = new Reference(contract);
        ref4.type = Reference.TYPE_EXISTING_STATE;
        ref4.setName(origin4.toString());

        List<Object> conditionsList4 = new ArrayList<>();
        conditionsList4.add(REFERENCE_CONDITION_PREFIX + "\"" + origin4.toBase64String() + "\"");
        conditionsList4.add(REFERENCE_CONDITION2);
        Binder conditions4 = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList4);
        ref4.setConditions(conditions4);

        contract.addReference(ref4);
        issuer.addRequiredReference(ref4, Role.RequiredMode.ALL_OF);


        Reference ref5 = new Reference(contract);
        ref5.type = Reference.TYPE_EXISTING_STATE;
        ref5.setName(origin5.toString());

        List<Object> conditionsList5 = new ArrayList<>();
        conditionsList5.add(REFERENCE_CONDITION_PREFIX + "\"" + origin5.toBase64String() + "\"");
        conditionsList5.add(REFERENCE_CONDITION2);
        Binder conditions5 = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList5);
        ref5.setConditions(conditions5);

        contract.addReference(ref5);
        issuer.addRequiredReference(ref5, Role.RequiredMode.ALL_OF);


        contract.registerRole(issuer);
        contract.setOwnerKeys(key);
        contract.seal();
        ItemResult ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //NO matching item for issuer reference in transaction pack
        assertEquals(ir.state, ItemState.DECLINED);

        contract.seal();
        contract.getTransactionPack().addReferencedItem(contractMark);
        contract.getTransactionPack().addReferencedItem(contractMark2);
        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //matching item for issuer reference is not APPROVED
        assertEquals(ir.state, ItemState.DECLINED);


        contract.addNewItems(contractMark);
        contract.addNewItems(contractMark2);
        contract.addNewItems(contractMark3);

        testSpace.client.register(contractMark4.getPackedTransaction(), 5000);
        testSpace.client.register(contractMark5.getPackedTransaction(), 5000);

        contract.seal();

        contract.getTransactionPack().addReferencedItem(contractMark4);
        contract.getTransactionPack().addReferencedItem(contractMark5);

        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);

        //all ok
        assertEquals(ir.state, ItemState.APPROVED);


        //no markContract is required referenced items of transaction pack
        contract = contract.createRevision(key);
        contract.addSignerKey(key);
        contract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        contract.seal();

        ir = testSpace.client.register(contract.getPackedTransaction(), 5000);
        //all ok

        assertEquals(ir.state, ItemState.APPROVED);

        testSpace.nodes.forEach(n -> n.shutdown());
    }


    @Test
    public void conversionSchemeUtoUTN() throws Exception {
        /////////////////////////////////////////////////
        //PREPARATION
        /////////////////////////////////////////////////
        PrivateKey universaAdminKey = TestKeys.privateKey(10);
        PrivateKey utnIssuerKey = TestKeys.privateKey(11);
        PrivateKey uIssuerKey = new PrivateKey(Do.read(Config.uKeyPath));
        PrivateKey userKey = TestKeys.privateKey(12);
        Set<PrivateKey> userKeys = new HashSet<>();
        userKeys.add(userKey);

        SimpleRole universaAdmin = new SimpleRole("universa_admin");
        universaAdmin.addKeyRecord(new KeyRecord(universaAdminKey.getPublicKey()));

        TestSpace testSpace = prepareTestSpace(userKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Set<PrivateKey> utnIssuer = new HashSet<>();
        utnIssuer.add(utnIssuerKey);

        Set<PublicKey> ownerKeys = new HashSet<>();
        ownerKeys.add(userKey.getPublicKey());
        Contract utnContract = ContractsService.createTokenContract(utnIssuer, ownerKeys, new BigDecimal("100000"));
        @NonNull ItemResult ir = testSpace.client.register(utnContract.getPackedTransaction());

        while (ir.state.isPending()) {
            Thread.sleep(500);
            ir = testSpace.client.getState(utnContract.getId());
        }

        assertEquals(ir.state, ItemState.APPROVED);
        /////////////////////////////////////////////////
        //PREPARATION END
        /////////////////////////////////////////////////


        //CREATE COMPOUND
        Contract compound = ContractsService.createSplit(utnContract, new BigDecimal("150"), "amount", new HashSet<>(), true);
        Contract paymentInUTNs = (Contract) compound.getNewItems().iterator().next();
        paymentInUTNs.setOwnerKeys(universaAdminKey);

        //CALCULATE AMOUNT OF U
        BigDecimal utnsPayed = new BigDecimal(paymentInUTNs.getStateData().getString("amount"));
        int unitsToIssue = utnsPayed.intValue() * 100;

        //CREATE U CONTRACT
        //Create standart U contract
        Contract uContract = InnerContractsService.createFreshU(unitsToIssue, ownerKeys);
        Contract consent = ContractsService.addConsent(uContract, universaAdminKey.getPublicKey().getLongAddress());
        uContract.seal();
        compound.addNewItems(uContract);

        consent.addSignerKey(universaAdminKey);
        consent.seal();


        //TRY TO REGISTER COMPOUND WITH INVALID ISSUING REFERENCE INSIDE U
        compound.seal();
        compound.addSignatureToSeal(userKeys);


        //attempt to register
        ir = testSpace.client.register(compound.getPackedTransaction());
        while (ir.state.isPending()) {
            Thread.sleep(500);
            ir = testSpace.client.getState(compound.getId());
        }
        //blocked by uIssueBlocker reference
        assertEquals(ir.state, ItemState.DECLINED);


        compound.seal();
        compound.addSignatureToSeal(userKeys);
        //CREATE BATCH CONTAINING compound and its consent
        Contract batch = ContractsService.createBatch(userKeys, compound, consent);


        //REGISTER BATCH WITH VALID REFERENCE
        //reseal compound
        //attempt to register (uIssueBlocker reference is now valid)
        ir = testSpace.client.register(batch.getPackedTransaction());
        while (ir.state.isPending()) {
            Thread.sleep(500);
            ir = testSpace.client.getState(batch.getId());
        }
        //so everything is fine
        assertEquals(ir.state, ItemState.APPROVED);


        //REGISTER SAMPLE CONTRACT WITH PAYMENT
        //Try to register new contract and use uContract as payment
        Contract sampleContract = new Contract(userKey);
        sampleContract.seal();
        Parcel parcel = ContractsService.createParcel(sampleContract, uContract, 1, userKeys);
        testSpace.client.registerParcelWithState(parcel.pack(), 5000);
        do {
            Thread.sleep(500);
            ir = testSpace.client.getState(sampleContract.getId());
        } while (ir.state.isPending());
        //so everything is fine
        ItemResult pr = testSpace.client.getState(parcel.getPaymentContract().getId());
        assertEquals(pr.state, ItemState.APPROVED);
        assertEquals(ir.state, ItemState.APPROVED);
        uContract = parcel.getPaymentContract();


        //REVOKE consent
        Contract revocation = ContractsService.createRevocation(consent, universaAdminKey);
        ir = testSpace.client.register(revocation.getPackedTransaction());
        while (ir.state.isPending()) {
            Thread.sleep(500);
            ir = testSpace.client.getState(revocation.getId());
        }
        //uIssueBlocker is revoked
        assertEquals(ir.state, ItemState.APPROVED);


        //REGISTER ANOTHER SAMPLE CONTRACT WITH PAYMENT
        //Try to register new contract and use uContract as payment
        sampleContract = new Contract(userKey);
        sampleContract.seal();
        parcel = ContractsService.createParcel(sampleContract, uContract, 1, userKeys);
        testSpace.client.registerParcelWithState(parcel.pack(), 5000);
        do {
            Thread.sleep(500);
            ir = testSpace.client.getState(sampleContract.getId());
        } while (ir.state.isPending());
        //so everything should be fine so revokation of uIssueBlocker should not affect later usage of U
        assertEquals(ir.state, ItemState.APPROVED);

        testSpace.nodes.forEach(n -> n.shutdown());
    }


    @Test
    public void refTest() throws Exception {
        PrivateKey key = TestKeys.privateKey(0);

        Contract contract1 = new Contract(key);
        Contract contract2 = new Contract(key);
        Contract contract3 = new Contract(key);
        Contract contract4 = new Contract(key);
        contract4.seal();

        contract1.addNewItems(contract2);
        contract3.addNewItems(contract4);

        Reference reference = new Reference(contract2);
        reference.name = "consent_" + contract4.getId();
        reference.type = Reference.TYPE_EXISTING_STATE;

        List<Object> conditionsList = new ArrayList<>();
        conditionsList.add(REFERENCE_CONDITION_PREFIX + "\"" + contract4.getId().toBase64String() + "\"");
        conditionsList.add(REFERENCE_CONDITION2);
        Binder conditions = Binder.of(Reference.conditionsModeType.all_of.name(), conditionsList);
        reference.setConditions(conditions);


        contract2.addReference(reference);
        contract2.getIssuer().addRequiredReference(reference, Role.RequiredMode.ALL_OF);

        contract3.seal();
        contract2.seal();
        contract1.seal();

        Contract c = ContractsService.createBatch(Do.listOf(key), contract1, contract3);


        TestSpace testSpace = prepareTestSpace();
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        ItemResult ir = testSpace.client.register(c.getPackedTransaction(), 10000);
        while (ir.state.isPending()) {
            ir = testSpace.client.getState(c.getId());
        }

        assertEquals(ir.state, ItemState.APPROVED);

        testSpace.nodes.forEach(n -> n.shutdown());
    }


    @Test
    public void randomReferences() throws Exception {
        Random random = new Random();
        TestSpace testSpace = prepareTestSpace();
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        final int CONTRACTS_IN_BATCH = 20;
        final int REFS_COUNT = 1000;

        List<PrivateKey> keys100 = new ArrayList<>();
        for (int i = 0; i < CONTRACTS_IN_BATCH; ++i)
            keys100.add(new PrivateKey(2048));

        List<Contract> contracts100 = new ArrayList<>();
        for (int i = 0; i < CONTRACTS_IN_BATCH; ++i) {
            Contract c = new Contract(keys100.get(i));
            c.getStateData().put("some_value", 9000 + random.nextInt(1000));
            contracts100.add(c);
        }

        Map<Integer, Integer> refsCasesCounter = new HashMap<>();
        for (int i = 1; i < REFS_COUNT; ++i) {
            int refForContract = random.nextInt(keys100.size());
            int refCase = random.nextInt(4);
            refsCasesCounter.put(refCase, refsCasesCounter.getOrDefault(refCase, 0) + 1);
            Reference ref = new Reference(contracts100.get(refForContract));
            switch (refCase) {
                case 0:
                    ref.type = Reference.TYPE_EXISTING_STATE;
                    ref.setConditions(Binder.of(Reference.conditionsModeType.all_of.name(), asList("ref.issuer==" + keys100.get(random.nextInt(keys100.size())).getPublicKey().getShortAddress())));
                    break;
                case 1:
                    ref.type = Reference.TYPE_EXISTING_DEFINITION;
                    ref.setConditions(Binder.of(Reference.conditionsModeType.all_of.name(), asList("ref.owner==" + keys100.get(random.nextInt(keys100.size())).getPublicKey().getLongAddress())));
                    break;
                case 2:
                    ref.type = Reference.TYPE_EXISTING_STATE;
                    ref.setConditions(Binder.of(
                            Reference.conditionsModeType.all_of.name(),
                            asList(
                                    "ref.state.data.some_value==" + contracts100.get(random.nextInt(contracts100.size())).getStateData().getStringOrThrow("some_value")
                            )));
                    break;
                case 3:
                    ref.type = Reference.TYPE_EXISTING_STATE;
                    ref.setConditions(Binder.of(
                            Reference.conditionsModeType.all_of.name(),
                            asList("ref.state.data.some_value<=1000")));
                    break;
            }
            contracts100.get(refForContract).addReference(ref);
        }
        System.out.println("\nrefs cases:");
        refsCasesCounter.forEach((k, v) -> System.out.println("  case " + k + ": " + v));

        Contract batch = new Contract(new PrivateKey(2048));
        for (int i = 0; i < keys100.size(); ++i)
            batch.addNewItems(contracts100.get(i));
        batch.seal();

        ItemResult ir = testSpace.client.register(batch.getPackedTransaction(), 30000);
        if (ir.errors.size() > 0) {
            System.out.println("\n\nerrors:");
            ir.errors.forEach(e -> System.out.println("  " + e));
            System.out.println();
        }
        //assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.DECLINED, ir.state); // must be declined due to ref.state.data.some_value<=1000

        testSpace.nodes.forEach(n -> n.shutdown());
    }

    @Ignore
    @Test
    public void registerFromFile() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Path path = Paths.get("/tmp/not3.unicon");
        byte[] testTransactionPackBytes = Files.readAllBytes(path);
        Contract contract = Contract.fromPackedTransaction(testTransactionPackBytes);

        System.out.println("======================");
        System.out.println("check(): " + contract.check());
        System.out.println("------- errors -------");
        contract.traceErrors();
        int i = 0;
        for (Approvable a : contract.getNewItems()) {
            Contract nc = (Contract) a;
            System.out.println("------- errors n" + i + " ----");
            System.out.println("  check: " + nc.check());
            nc.traceErrors();
            ++i;
        }
        System.out.println("======================");

        System.out.println("hashId: " + contract.getId().toBase64String());
        testSpace.node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        ItemResult itemResult = testSpace.client.register(testTransactionPackBytes, 5000);
        ItemResult itemResult2 = testSpace.client.getState(contract.getId());

        System.out.println("itemResult: " + itemResult);
        System.out.println("itemResult2: " + itemResult2);

        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void getStateWithNoLedgerCache() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        testSpace.nodes.forEach(m -> ((PostgresLedger) m.node.getLedger()).enableCache(false));

        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size() - 1).shutdown();
        Thread.sleep(4000);

        Contract rev1 = new Contract(TestKeys.privateKey(0));
        rev1.getStateData().set("field1", 33);
        Permission permission = new ChangeNumberPermission(rev1.getOwner(), Binder.of("field_name", "field1"));
        rev1.addPermission(permission);
        rev1.seal();
        ItemResult ir1 = testSpace.client.register(rev1.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir1.state);

        Contract rev2 = rev1.createRevision();
        rev2.getStateData().set("field1", 34);
        rev2.addSignerKey(TestKeys.privateKey(0));
        rev2.seal();
        ItemResult ir2 = testSpace.client.register(rev2.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir2.state);
        ir1 = testSpace.client.register(rev1.getPackedTransaction(), 5000);
        assertEquals(ItemState.REVOKED, ir1.state);

        testSpace.nodes.forEach(m -> m.shutdown());
    }

    @Test
    public void resyncWithNoLedgerCache() throws Exception {
        TestSpace testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        testSpace.nodes.forEach(m -> ((PostgresLedger) m.node.getLedger()).enableCache(false));
        testSpace.nodes.get(testSpace.nodes.size() - 1).shutdown();

        NSmartContract rev1 = new NSmartContract(TestKeys.privateKey(0));
        rev1.getStateData().set("field1", 33);
        Permission permission = new ChangeNumberPermission(rev1.getOwner(), Binder.of("field_name", "field1"));
        rev1.addPermission(permission);
        rev1.seal();
        ItemResult ir1 = testSpace.client.register(rev1.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir1.state);

        Contract rev2 = rev1.createRevision();
        rev2.getStateData().set("field1", 34);
        rev2.addSignerKey(TestKeys.privateKey(0));
        rev2.seal();
        ItemResult ir2 = testSpace.client.register(rev2.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, ir2.state);
        ir1 = testSpace.client.register(rev1.getPackedTransaction(), 5000);
        assertEquals(ItemState.REVOKED, ir1.state);

        //recreate nodes
        for (int i = 0; i < testSpace.nodes.size() - 1; ++i)
            testSpace.nodes.get(i).shutdown();
        Thread.sleep(2000);
        testSpace = prepareTestSpace(TestKeys.privateKey(0));
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        //put some environment for rev1
        Ledger ledger = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger();
        assertNull(ledger.getEnvironment(rev1.getId()));
        NImmutableEnvironment environment = new NImmutableEnvironment(rev1, new Binder(), Do.listOf(), Do.listOf(), Do.listOf(), Do.listOf(), null, null);
        ledger.saveEnvironment(environment);
        assertNotNull(ledger.getEnvironment(rev1.getId()));

        System.out.println("\n========== resyncing ==========\n");
        testSpace.nodes.get(testSpace.clients.size() - 1).setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        testSpace.clients.get(testSpace.clients.size() - 1).resyncItem(rev1.getId());
        long millisToWait = 60000;
        long waitPeriod = 2000;
        ItemResult ir = null;
        while (millisToWait > 0) {
            Thread.sleep(waitPeriod);
            millisToWait -= waitPeriod;
            ir = testSpace.clients.get(testSpace.clients.size() - 1).getState(rev1.getId());
            if (ir.state == ItemState.REVOKED)
                break;
        }
        assertEquals(ItemState.REVOKED, ir.state);
        assertNull(ledger.getEnvironment(rev1.getId()));

        testSpace.nodes.forEach(n -> n.shutdown());
    }

    @Test
    public void checkWhiteListKey() throws Exception {
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

        Parcel parcel = createParcelWithFreshU(client, testContract, Do.listOf(myKey));
        ItemResult itemResult = client.registerParcelWithState(parcel.pack(), 15000);

        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkNotWhiteListKey() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        //remove all keys and addresses from white list
        mm.forEach(x -> {
            x.config.getKeysWhiteList().clear();
            x.config.getAddressesWhiteList().clear();
        });

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

        Set<PublicKey> ownerKeys = new HashSet();
        Collection<PrivateKey> keys = Do.listOf(myKey);
        keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
        Contract stepaU = InnerContractsService.createFreshU(100000000, ownerKeys);
        stepaU.check();
        stepaU.traceErrors();

        PrivateKey clientPrivateKey = client.getSession().getPrivateKey();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));
        client.getSession().setPrivateKey(newPrivateKey);
        client.restart();

        ItemResult itemResult = client.register(stepaU.getPackedTransaction(), 5000);

        client.getSession().setPrivateKey(clientPrivateKey);
        client.restart();

        //check error
        assertEquals(itemResult.errors.size(), 1);
        assertEquals(itemResult.errors.get(0).getMessage(), "command needs client key from whitelist");

        assertEquals(ItemState.UNDEFINED, itemResult.state);
        Set<PrivateKey> keySet = new HashSet<>();
        keySet.addAll(keys);
        Parcel parcel = ContractsService.createParcel(testContract, stepaU, 150, keySet);
        client.registerParcelWithState(parcel.pack(), 15000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.UNDEFINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkWhiteListAddress() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));

        //remove all keys and addresses from white list
        mm.forEach(x -> {
            x.config.getKeysWhiteList().clear();
            x.config.getAddressesWhiteList().clear();
        });

        //add address to white list
        mm.forEach(x -> x.config.getAddressesWhiteList().add(new KeyAddress(newPrivateKey.getPublicKey(), 0, true)));

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

        Set<PublicKey> ownerKeys = new HashSet();
        Collection<PrivateKey> keys = Do.listOf(myKey);
        keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
        Contract stepaU = InnerContractsService.createFreshU(100000000, ownerKeys);
        stepaU.check();
        stepaU.traceErrors();

        PrivateKey clientPrivateKey = client.getSession().getPrivateKey();
        client.getSession().setPrivateKey(newPrivateKey);
        client.restart();

        ItemResult itemResult = client.register(stepaU.getPackedTransaction(), 5000);

        client.getSession().setPrivateKey(clientPrivateKey);
        client.restart();

        assertEquals(ItemState.APPROVED, itemResult.state);
        Set<PrivateKey> keySet = new HashSet<>();
        keySet.addAll(keys);
        Parcel parcel = ContractsService.createParcel(testContract, stepaU, 150, keySet);
        client.registerParcelWithState(parcel.pack(), 15000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkLimitRequestsForKey() throws Exception {

        PrivateKey myKey = TestKeys.privateKey(3);
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Main main = mm.get(0);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute() * 2; i++) {
            Contract testContract = new Contract(myKey);
            testContract.seal();
            assertTrue(testContract.isOk());

            Set<PublicKey> ownerKeys = new HashSet();
            Collection<PrivateKey> keys = Do.listOf(myKey);
            keys.stream().forEach(key -> ownerKeys.add(key.getPublicKey()));
            Contract stepaU = InnerContractsService.createFreshU(100000000, ownerKeys);
            stepaU.check();
            stepaU.traceErrors();

            client.register(stepaU.getPackedTransaction());

            Set<PrivateKey> keySet = new HashSet<>();
            keySet.addAll(keys);
            Parcel parcel = ContractsService.createParcel(testContract, stepaU, 150, keySet);
            client.registerParcel(parcel.pack());
        }

        // alignment by begin of minute
        long seconds = ZonedDateTime.now().toEpochSecond() % 60;
        if (seconds > 57)
            Thread.currentThread().sleep(3000);

        // reaching requests limit
        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute(); i++)
            System.out.println(">> storage rate: " + client.storageGetRate());

        String exception = "";
        try {
            client.storageGetRate();            // limited request
        } catch (Exception e) {
            System.out.println("Client exception: " + e.toString());
            exception = e.getMessage();
        }

        assertEquals(exception, "ClientError: COMMAND_FAILED exceeded the limit of requests for key per minute," +
                " please call again after a while");

        System.out.println("Wait 1 munite...");
        Thread.currentThread().sleep(60000);

        // alignment by begin of minute
        seconds = ZonedDateTime.now().toEpochSecond() % 60;
        if (seconds > 57)
            Thread.currentThread().sleep(3000);

        // reaching requests limit
        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute(); i++)
            System.out.println(">> uns rate: " + client.unsRate());

        exception = "";
        try {
            client.unsRate();                   // limited request
        } catch (Exception e) {
            System.out.println("Client exception: " + e.toString());
            exception = e.getMessage();
        }

        assertEquals(exception, "ClientError: COMMAND_FAILED exceeded the limit of requests for key per minute," +
                " please call again after a while");

        System.out.println("Wait 1 munite...");
        Thread.currentThread().sleep(60000);

        // alignment by begin of minute
        seconds = ZonedDateTime.now().toEpochSecond() % 60;
        if (seconds > 57)
            Thread.currentThread().sleep(3000);

        // reaching requests limit
        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute(); i++)
            client.getStats(90);

        exception = "";
        try {
            client.getStats(90);                // limited request
        } catch (Exception e) {
            System.out.println("Client exception: " + e.toString());
            exception = e.getMessage();
        }

        assertEquals(exception, "ClientError: COMMAND_FAILED exceeded the limit of requests for key per minute," +
                " please call again after a while");

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkUnlimitRequestsForKey() throws Exception {

        PrivateKey myKey = TestKeys.privateKey(3);
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Main main = mm.get(0);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Set<PublicKey> ownerKeys = new HashSet();
        Set<PrivateKey> keys = new HashSet();
        keys.add(myKey);
        ownerKeys.add(myKey.getPublicKey());
        Contract payment = InnerContractsService.createFreshU(100000000, ownerKeys);
        payment.check();
        payment.traceErrors();

        client.register(payment.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(false));

        // alignment by begin of minute
        long seconds = ZonedDateTime.now().toEpochSecond() % 60;
        if (seconds > 57)
            Thread.currentThread().sleep(3000);

        // reaching requests limit
        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute(); i++)
            System.out.println(">> storage rate: " + client.storageGetRate());

        String exception = "";
        try {
            client.storageGetRate();            // limited request
        } catch (Exception e) {
            System.out.println("Client exception: " + e.toString());
            exception = e.getMessage();
        }

        assertEquals(exception, "ClientError: COMMAND_FAILED exceeded the limit of requests for key per minute," +
                " please call again after a while");

        // set unlimited requests
        Contract unlimitContract = ContractsService.createRateLimitDisablingContract(
                myKey.getPublicKey(), payment, main.config.getRateLimitDisablingPayment(), keys);

        unlimitContract.check();
        unlimitContract.traceErrors();
        assertTrue(unlimitContract.isOk());

        ItemResult itemResult = client.register(unlimitContract.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        itemResult = client.getState(unlimitContract.getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.APPROVED, itemResult.state);

        // unlimited requests
        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute() * 2; i++)
            System.out.println(">> storage rate: " + client.storageGetRate());

        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute() * 2; i++)
            System.out.println(">> uns rate: " + client.unsRate());

        for (int i = 0; i < main.config.getLimitRequestsForKeyPerMinute() * 2; i++)
            client.getStats(90);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkUnlimitRequestsBadParam() throws Exception {

        PrivateKey myKey = TestKeys.privateKey(3);
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Main main = mm.get(0);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Set<PublicKey> ownerKeys = new HashSet();
        Set<PrivateKey> keys = new HashSet();
        keys.add(myKey);
        ownerKeys.add(myKey.getPublicKey());
        Contract payment = InnerContractsService.createFreshU(100000000, ownerKeys);
        payment.check();
        payment.traceErrors();

        client.register(payment.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(false));

        //set unlimited requests,amount < 5 (main.config.getRateLimitDisablingPayment())
        Contract unlimitContract = ContractsService.createRateLimitDisablingContract(
                myKey.getPublicKey(), payment, 1, keys);

        //set unlimited requests,amount > 5 (main.config.getRateLimitDisablingPayment())
        Contract unlimitContract1 = ContractsService.createRateLimitDisablingContract(
                myKey.getPublicKey(), payment, main.config.getRateLimitDisablingPayment() + 1, keys);

        unlimitContract.check();
        unlimitContract.traceErrors();
        assertTrue(unlimitContract.isOk());

        unlimitContract1.check();
        unlimitContract1.traceErrors();
        assertTrue(unlimitContract1.isOk());

        System.out.println("Processing cost " + unlimitContract.getProcessedCostU());

        // registering unlimitContract

        ItemResult itemResult = client.register(unlimitContract.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        System.out.println();

        assertEquals((itemResult.errors.get(0).getMessage()), "Payment for setting unlimited requests" +
                " must be 5U");

        itemResult = client.getState(unlimitContract.getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.UNDEFINED, itemResult.state);

        // registering unlimitContract1

        itemResult = client.register(unlimitContract1.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        System.out.println();

        assertEquals((itemResult.errors.get(0).getMessage()), "Payment for setting unlimited requests" +
                " must be 5U");

        itemResult = client.getState(unlimitContract1.getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.UNDEFINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void checkUnlimitRequestsBadKey() throws Exception {

        PrivateKey myKey = TestKeys.privateKey(3);

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Main main = mm.get(0);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Set<PublicKey> ownerKeys = new HashSet();
        Set<PrivateKey> keys = new HashSet();

        keys.add(myKey);

        ownerKeys.add(myKey.getPublicKey());
        Contract payment = InnerContractsService.createFreshU(100, ownerKeys);
        payment.check();
        payment.traceErrors();

        client.register(payment.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(false));

        Contract unlimitContract = ContractsService.createRateLimitDisablingContract(
                myKey.getPublicKey(), payment, main.config.getRateLimitDisablingPayment(), keys);

        // get unlimited key
        byte[] packedKey = unlimitContract.getTransactional().getData().getBinary("unlimited_key");
        PublicKey key = new PublicKey(packedKey);

        // invalid format of key for unlimited requests
        assertFalse((packedKey == null) || ((key == null)));

        // check payment
        assertEquals(unlimitContract.getRevoking().get(0).getStateData().getIntOrThrow("transaction_units") -
                unlimitContract.getStateData().getIntOrThrow("transaction_units"), 5);

        unlimitContract.check();
        unlimitContract.traceErrors();
        assertTrue(unlimitContract.isOk());

        unlimitContract.getTransactional().getData().set("unlimited_key", "badkeybadkeybadkeybadkey");
        unlimitContract.seal();

        // registering unlimitContract
        client.register(unlimitContract.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        System.out.println();

        ItemResult itemResult = client.getState(unlimitContract.getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.UNDEFINED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void checkLimitRequestsTestU() throws Exception {

        PrivateKey myKey = TestKeys.privateKey(3);

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Main main = mm.get(0);
        Client client = null;
        try {
            client = new Client(myKey, main.myInfo, null);
        } catch (Exception e) {
            System.out.println("prepareClient exception: " + e.toString());
        }

        Set<PublicKey> ownerKeys = new HashSet();
        Set<PrivateKey> keys = new HashSet();

        keys.add(myKey);

        ownerKeys.add(myKey.getPublicKey());
        Contract payment = InnerContractsService.createFreshU(100, ownerKeys, true);
        payment.check();
        payment.traceErrors();

        client.register(payment.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        ItemResult itemResult = client.getState(payment.getId());
        System.out.println(">> state payment: " + itemResult);

        System.out.println(payment.getStateData().getString("transaction_units"));
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(myKey);
        payment.getStateData().set("transaction_units", 0);
        payment.setOwnerKeys(ownerKeys);
        payment.addSignerKey(myKey);
        payment.seal();

        client.register(payment.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        itemResult = client.getState(payment.getId());
        System.out.println(">> state payment: " + itemResult);

        System.out.println(payment.getStateData().getString("transaction_units"));
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.config.setIsFreeRegistrationsAllowedFromYaml(false));

        Contract unlimitContract = ContractsService.createRateLimitDisablingContract(
                myKey.getPublicKey(), payment, main.config.getRateLimitDisablingPayment(), keys);

        client.register(unlimitContract.getPackedTransaction());
        Thread.currentThread().sleep(5000);

        itemResult = client.getState(unlimitContract.getId());
        System.out.println(">> state: " + itemResult);

        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());

    }

    @Test
    public void ecsrowComplete() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();


        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // complete escrow contract(by external escrow contract)
        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        // sign completed internal escrow contract by issuer
        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by customer
        completedEscrow.addSignatureToSeal(customerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by executor
        completedEscrow.addSignatureToSeal(executorPrivateKeys);

        completedEscrow.check();
        completedEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(completedEscrow.getPackedTransaction(), 5000);
        System.out.println("completedEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // transfer payment to executor
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result executor payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCancel() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        // sign canceled internal escrow contract by issuer
        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by customer
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by arbitrator
        canceledEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        canceledEscrow.check();
        canceledEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(canceledEscrow.getPackedTransaction(), 5000);
        System.out.println("canceledEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // return payment to customer
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result customer payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCompleteFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // complete escrow contract(by external escrow contract)
        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        // sign completed internal escrow contract by issuer
        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by executor
        completedEscrow.addSignatureToSeal(executorPrivateKeys);

        completedEscrow.check();
        completedEscrow.traceErrors();

        // try register signed escrow contract (without customer sign)
        itemResult = client.register(completedEscrow.getPackedTransaction(), 5000);
        System.out.println("completedEscrow : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // try transfer payment to executor
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // try register payment for executor
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCancelFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);


        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        // sign canceled internal escrow contract by issuer
        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by customer
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);

        canceledEscrow.check();
        canceledEscrow.traceErrors();

        // try register signed escrow contract (without signs of executor or arbitrator)
        itemResult = client.register(canceledEscrow.getPackedTransaction(), 5000);
        System.out.println("canceledEscrow : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // try return payment to customer
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // try register payment for customer
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCompleteAndTakePaymentFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // complete escrow contract(by external escrow contract)
        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        // sign completed internal escrow contract by issuer
        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by customer
        completedEscrow.addSignatureToSeal(customerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by executor
        completedEscrow.addSignatureToSeal(executorPrivateKeys);

        completedEscrow.check();
        completedEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(completedEscrow.getPackedTransaction(), 5000);
        System.out.println("completedEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // try return payment to customer (escrow completed)
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // try register payment for customer
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCancelAndTakePaymentFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        // sign canceled internal escrow contract by issuer
        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by customer
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by arbitrator
        canceledEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        canceledEscrow.check();
        canceledEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(canceledEscrow.getPackedTransaction(), 5000);
        System.out.println("canceledEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // try transfer payment to executor (escrow canceled)
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // try register payment for executor
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowInternalComplete() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create internal escrow contract
        Contract escrow = ContractsService.createInternalEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check internal escrow contract status
        assertEquals(escrow.getStateData().getString("status", "null"), "opened");

        // add escrow user data
        escrow.getStateData().set("description", "Simple escrow");
        escrow.getStateData().set("percent", 10);
        escrow.seal();

        // modify payment for escrow contract
        payment = ContractsService.modifyPaymentForEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // complete escrow contract(by external escrow contract)
        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        // sign completed internal escrow contract by issuer
        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by customer
        completedEscrow.addSignatureToSeal(customerPrivateKeys);
        completedEscrow = Contract.fromPackedTransaction(completedEscrow.getPackedTransaction());

        // sign completed internal escrow contract by executor
        completedEscrow.addSignatureToSeal(executorPrivateKeys);

        // check escrow user data
        assertEquals(completedEscrow.getStateData().getString("description", "null"), "Simple escrow");
        assertEquals(completedEscrow.getStateData().getLong("percent", 0), 10);

        completedEscrow.check();
        completedEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(completedEscrow.getPackedTransaction(), 5000);
        System.out.println("completedEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // transfer payment to executor
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result executor payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowInternalCancel() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create internal escrow contract
        Contract escrow = ContractsService.createInternalEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check internal escrow contract status
        assertEquals(escrow.getStateData().getString("status", "null"), "opened");

        // add escrow user data
        escrow.getStateData().set("description", "Simple escrow");
        escrow.getStateData().set("percent", 10);
        escrow.seal();

        // modify payment for escrow contract
        payment = ContractsService.modifyPaymentForEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);

        itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        // sign canceled internal escrow contract by issuer
        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by customer
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);
        canceledEscrow = Contract.fromPackedTransaction(canceledEscrow.getPackedTransaction());

        // sign canceled internal escrow contract by arbitrator
        canceledEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        // check escrow user data
        assertEquals(canceledEscrow.getStateData().getString("description", "null"), "Simple escrow");
        assertEquals(canceledEscrow.getStateData().getLong("percent", 0), 10);

        canceledEscrow.check();
        canceledEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(canceledEscrow.getPackedTransaction(), 5000);
        System.out.println("canceledEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // return payment to customer
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result customer payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowCompleteWithRepack() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add internal escrow user data
        Contract internalEscrow = escrow.getNew().get(0);
        internalEscrow.getStateData().set("description", "Simple internal escrow");
        internalEscrow.getStateData().set("percent", 20);
        internalEscrow.seal();

        // repack external escrow contract
        escrow = ContractsService.createExternalEscrowContract(internalEscrow, issuerPrivateKeys);

        // add external escrow user data
        escrow.getStateData().set("description", "Simple external escrow");
        escrow.getStateData().set("percent", 20);
        escrow.seal();

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // complete escrow contract(by external escrow contract)
        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        // sign completed internal escrow contract by issuer
        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        /////////completedEscrow = imitateSendingTransactionToPartner(completedEscrow);

        // sign completed internal escrow contract by customer
        completedEscrow.addSignatureToSeal(customerPrivateKeys);
        //////////completedEscrow = imitateSendingTransactionToPartner(completedEscrow);

        // sign completed internal escrow contract by executor
        completedEscrow.addSignatureToSeal(executorPrivateKeys);

        // check internal escrow user data
        assertEquals(completedEscrow.getStateData().getString("description", "null"), "Simple internal escrow");
        assertEquals(completedEscrow.getStateData().getLong("percent", 0), 20);

        // check internal escrow user data
        assertEquals(escrow.getStateData().getString("description", "null"), "Simple external escrow");
        assertEquals(escrow.getStateData().getLong("percent", 0), 20);

        completedEscrow.check();
        completedEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(completedEscrow.getPackedTransaction(), 5000);
        System.out.println("completedEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // transfer payment to executor
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result executor payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowReOpenFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        // sign canceled internal escrow contract by issuer
        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        //canceledEscrow = imitateSendingTransactionToPartner(canceledEscrow);

        // sign canceled internal escrow contract by customer
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);
        ///canceledEscrow = imitateSendingTransactionToPartner(canceledEscrow);

        // sign canceled internal escrow contract by arbitrator
        canceledEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        canceledEscrow.check();
        canceledEscrow.traceErrors();

        // register signed escrow contract
        itemResult = client.register(canceledEscrow.getPackedTransaction(), 5000);
        System.out.println("canceledEscrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // try re-open escrow contract
        Contract revisionEscrow = canceledEscrow.createRevision(issuerPrivateKeys);
        revisionEscrow.getStateData().set("status", "opened");
        revisionEscrow.seal();
        revisionEscrow.addSignatureToSeal(customerPrivateKeys);
        revisionEscrow.addSignatureToSeal(executorPrivateKeys);
        revisionEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        // try register re-open escrow contract
        itemResult = client.register(revisionEscrow.getPackedTransaction(), 5000);
        System.out.println("revisionEscrow : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // try re-complete escrow contract
        revisionEscrow = canceledEscrow.createRevision(issuerPrivateKeys);
        revisionEscrow.getStateData().set("status", "completed");
        revisionEscrow.seal();
        revisionEscrow.addSignatureToSeal(customerPrivateKeys);
        revisionEscrow.addSignatureToSeal(executorPrivateKeys);
        revisionEscrow.addSignatureToSeal(arbitratorPrivateKeys);

        // try register re-open escrow contract
        itemResult = client.register(revisionEscrow.getPackedTransaction(), 5000);
        System.out.println("revisionEscrow : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // return payment to customer
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        // register result customer payment
        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void ecsrowTakePaymentFail() throws Exception {

        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));
        Set<PrivateKey> customerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(2)));
        Set<PrivateKey> executorPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(3)));

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> executorPublicKeys = new HashSet<>();
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        // init payment for escrow contract
        Contract payment = InnerContractsService.createFreshU(100, customerPublicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        payment = payment.createRevision(customerPrivateKeys);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys, arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, payment, customerPrivateKeys, customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        escrow.check();
        escrow.traceErrors();

        itemResult = client.register(escrow.getPackedTransaction(), 5000);
        System.out.println("escrow : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = main.node.waitItem(payment.getId(), 8000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // try taking payment from issuer (without closed escrow)
        Contract newPayment = ContractsService.takeEscrowPayment(issuerPrivateKeys, payment);

        newPayment.getTransactionPack().addReferencedItem(escrow);

        newPayment.check();
        newPayment.traceErrors();

        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // try taking payment from customer (without closed escrow)
        newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, payment);

        newPayment.getTransactionPack().addReferencedItem(escrow);

        newPayment.check();
        newPayment.traceErrors();

        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // try taking payment from executor (without closed escrow)
        newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, payment);

        newPayment.getTransactionPack().addReferencedItem(escrow);

        newPayment.check();
        newPayment.traceErrors();

        itemResult = client.register(newPayment.getPackedTransaction(), 5000);
        System.out.println("newPayment : " + itemResult);
        assertEquals(ItemState.DECLINED, itemResult.state);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testPermanetMode() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> privateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : privateKeys) {
            publicKeys.add(pk.getPublicKey());
        }

        Contract payment = InnerContractsService.createFreshU(100, publicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check keeping contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping = mm.get(i).node.getLedger().getKeepingItem(payment.getId());
            assertNotNull(keeping);
            assertTrue(Arrays.equals(payment.getPackedTransaction(), keeping));
        }

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.setExpiresAt(ZonedDateTime.now().plusSeconds(5));
        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        baseContract.check();
        baseContract.traceErrors();

        itemResult = client.register(baseContract.getPackedTransaction(), 5000);
        System.out.println("contract : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check keeping contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping = mm.get(i).node.getLedger().getKeepingItem(baseContract.getId());
            assertNotNull(keeping);
            assertTrue(Arrays.equals(baseContract.getPackedTransaction(), keeping));
        }

        Thread.sleep(8000);

        itemResult = client.getState(baseContract.getId());
        System.out.println("expired : " + itemResult);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        // check for expired contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping = mm.get(i).node.getLedger().getKeepingItem(baseContract.getId());
            assertNotNull(keeping);
            assertTrue(Arrays.equals(baseContract.getPackedTransaction(), keeping));
        }

        main.config.setIsFreeRegistrationsAllowedFromYaml(false);

        Contract parcelContract = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        parcelContract.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
        parcelContract.addSignerKey(manufacturePrivateKey);
        parcelContract.seal();

        parcelContract.check();
        parcelContract.traceErrors();

        Parcel parcel = createParcelWithFreshU(client, parcelContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check keeping contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping = mm.get(i).node.getLedger().getKeepingItem(parcel.getPayloadContract().getId());
            assertNotNull(keeping);
            assertTrue(Arrays.equals(parcel.getPayloadContract().getPackedTransaction(), keeping));
        }

        // create revision
        Contract revisionContract = parcelContract.createRevision(manufacturePrivateKey);
        revisionContract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        revisionContract.addSignerKey(TestKeys.privateKey(1));
        revisionContract.seal();

        revisionContract.check();
        revisionContract.traceErrors();

        parcel = createParcelWithFreshU(client, revisionContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check keeping contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping_root = mm.get(i).node.getLedger().getKeepingItem(parcelContract.getId());
            byte[] keeping_revision = mm.get(i).node.getLedger().getKeepingItem(revisionContract.getId());
            assertNotNull(keeping_root);
            assertNotNull(keeping_revision);
            assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
            assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));
        }

        // revoke contract
        Contract revokeContract = ContractsService.createRevocation(revisionContract, manufacturePrivateKey);
        revokeContract.seal();

        revokeContract.check();
        revokeContract.traceErrors();

        parcel = createParcelWithFreshU(client, revokeContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revocation : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        itemResult = client.getState(revisionContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check keeping contract
        for (int i = 1; i < 4; i++) {
            byte[] keeping_root = mm.get(i).node.getLedger().getKeepingItem(parcelContract.getId());
            byte[] keeping_revision = mm.get(i).node.getLedger().getKeepingItem(revisionContract.getId());
            byte[] keeping_revoke = mm.get(i).node.getLedger().getKeepingItem(revokeContract.getId());
            assertNotNull(keeping_root);
            assertNotNull(keeping_revision);
            assertNotNull(keeping_revoke);
            assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
            assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));
            assertTrue(Arrays.equals(revokeContract.getPackedTransaction(), keeping_revoke));
        }

        HashSet<PrivateKey> own = new HashSet<>(Do.listOf(manufacturePrivateKey));
        // create revision
        Contract rootContract = ContractsService.createTokenContract(own,
                new HashSet<>(Do.listOf(manufacturePrivateKey.getPublicKey())), "10000");
        rootContract.seal();


        parcel = createParcelWithFreshU(client, rootContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);


        List<Contract> splitres = ContractsService.createSplitJoin(Do.listOf(rootContract), Do.listOf("100", "200", "300", "400"), Do.listOf(
                TestKeys.publicKey(1).getShortAddress(),
                TestKeys.publicKey(2).getShortAddress(),
                TestKeys.publicKey(3).getShortAddress(),
                TestKeys.publicKey(4).getShortAddress()), own, "amount");

        parcel = createParcelWithFreshU(client, splitres.get(0), privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("split : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);


        List<HashId> ids = client.getChildren(rootContract.getId(), 5).getListOrThrow("ids");
        System.out.println("children : " + ids);


        List<HashId> ids2 = client.getChildren(rootContract.getId(), 3).getListOrThrow("ids");

        List<HashId> ids3 = client.getChildren(rootContract.getId(), 3, 3).getListOrThrow("ids");

        HashSet set1 = new HashSet(ids);
        HashSet set2 = new HashSet(ids2);
        set2.addAll(ids3);

        assertEquals(set1, set2);

        assertEquals(ids.size(), splitres.size());

        assertTrue(splitres.stream().allMatch(c -> ids.contains(c.getId())));

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testPermanetModeSearchTags() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);


        for (int i = 0; i < 4; i++) {
            mm.get(i).config.setIsFreeRegistrationsAllowedFromYaml(true);
            assertTrue(mm.get(i).config.isPermanetMode());
        }

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        String value1 = HashId.createRandom().toBase64String();
        String value2 = HashId.createRandom().toBase64String();
        String value3 = HashId.createRandom().toBase64String();

        Contract c = new Contract(TestKeys.privateKey(1));
        c.getDefinition().getData().put("search_tags",Binder.of("tag1",value1,"tag2",value2,"tag3",value3));
        c.seal();
        assertEquals(client.register(c.getPackedTransaction(),8000).state,ItemState.APPROVED);

        Binder res = client.getContract(null, Binder.of("tag1", value1, "any_of", Binder.of("tag2", value2, "tag3", "AAA")), 10, 0);
        assertEquals(res.getListOrThrow("contractIds").size(),1);

        res = client.getContract(null, Binder.of("tag1", value1, "all_of", Binder.of("tag2", value2, "tag3", "AAA")), 10, 0);
        assertNull(res);

        res = client.getContract(null, Binder.of("tag1", "AAA", "all_of", Binder.of("tag2", value2, "tag3", value3)), 10, 0);
        assertNull(res);

        res = client.getContract(null, Binder.of("tag1", value1, "all_of", Binder.of("tag2", value2, "tag3", value3)), 10, 0);
        assertEquals(res.getListOrThrow("contractIds").size(),1);

        mm.forEach(x -> x.shutdown());
    }



    @Test
    public void testPermanetApiGetBodyGood() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> privateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : privateKeys) {
            publicKeys.add(pk.getPublicKey());
        }

        Contract payment = InnerContractsService.createFreshU(100, publicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check getBody
        byte[] keeping = client.getBody(payment.getId());
        assertNotNull(keeping);
        assertTrue(Arrays.equals(payment.getPackedTransaction(), keeping));

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.setExpiresAt(ZonedDateTime.now().plusSeconds(5));
        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        baseContract.check();
        baseContract.traceErrors();

        itemResult = client.register(baseContract.getPackedTransaction(), 5000);
        System.out.println("contract : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check getBody
        keeping = client.getBody(baseContract.getId());
        assertNotNull(keeping);
        assertTrue(Arrays.equals(baseContract.getPackedTransaction(), keeping));

        Thread.sleep(8000);

        itemResult = client.getState(baseContract.getId());
        System.out.println("expired : " + itemResult);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        // check getBody for expired contract
        keeping = client.getBody(baseContract.getId());
        assertNotNull(keeping);
        assertTrue(Arrays.equals(baseContract.getPackedTransaction(), keeping));

        main.config.setIsFreeRegistrationsAllowedFromYaml(false);

        Contract parcelContract = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        parcelContract.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
        parcelContract.addSignerKey(manufacturePrivateKey);
        parcelContract.seal();

        parcelContract.check();
        parcelContract.traceErrors();

        Parcel parcel = createParcelWithFreshU(client, parcelContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check getBody
        keeping = client.getBody(parcel.getPayloadContract().getId());
        assertNotNull(keeping);
        assertTrue(Arrays.equals(parcel.getPayloadContract().getPackedTransaction(), keeping));

        // create revision
        Contract revisionContract = parcelContract.createRevision(manufacturePrivateKey);
        revisionContract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        revisionContract.addSignerKey(TestKeys.privateKey(1));
        revisionContract.seal();

        revisionContract.check();
        revisionContract.traceErrors();

        parcel = createParcelWithFreshU(client, revisionContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check getBody
        byte[] keeping_root = client.getBody(parcelContract.getId());
        byte[] keeping_revision = client.getBody(revisionContract.getId());
        assertNotNull(keeping_root);
        assertNotNull(keeping_revision);
        assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
        assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));

        // revoke contract
        Contract revokeContract = ContractsService.createRevocation(revisionContract, manufacturePrivateKey);
        revokeContract.seal();

        revokeContract.check();
        revokeContract.traceErrors();

        parcel = createParcelWithFreshU(client, revokeContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revocation : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        itemResult = client.getState(revisionContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check getBody
        keeping_root = client.getBody(parcelContract.getId());
        keeping_revision = client.getBody(revisionContract.getId());
        byte[] keeping_revoke = client.getBody(revokeContract.getId());

        assertNotNull(keeping_root);
        assertNotNull(keeping_revision);
        assertNotNull(keeping_revoke);
        assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
        assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));
        assertTrue(Arrays.equals(revokeContract.getPackedTransaction(), keeping_revoke));

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testPermanetApiGetBodyNull() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> privateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : privateKeys) {
            publicKeys.add(pk.getPublicKey());
        }

        Contract payment = InnerContractsService.createFreshU(100, publicKeys, true);

        payment.check();
        payment.traceErrors();

        ItemResult itemResult = client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        Contract baseContract = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        baseContract.setExpiresAt(ZonedDateTime.now().plusSeconds(5));
        baseContract.addSignerKey(manufacturePrivateKey);
        baseContract.seal();

        baseContract.check();
        baseContract.traceErrors();

        itemResult = client.register(baseContract.getPackedTransaction(), 5000);
        System.out.println("contract : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        // check getBody (can be null)
        byte[] stranger = client.getBody(HashId.createRandom());
        assertNull(stranger);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testPermanetApiGetBodyResync() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> privateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : privateKeys) {
            publicKeys.add(pk.getPublicKey());
        }

        Contract parcelContract = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        parcelContract.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
        parcelContract.addSignerKey(manufacturePrivateKey);
        parcelContract.seal();

        parcelContract.check();
        parcelContract.traceErrors();

        Parcel parcel = createParcelWithFreshU(client, parcelContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        HashId origin = parcelContract.getId();

        // delete body from node db
        Db db = ((PostgresLedger) main.node.getLedger()).getDb();
        try (PreparedStatement statement = db.statement("delete from kept_items where origin = ?")) {
            statement.setBytes(1, origin.getDigest());
            db.updateWithStatement(statement);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        // check getKeepingItem for null
        assertNull(main.node.getLedger().getKeepingItem(parcelContract.getId()));

        // check getBody
        byte[] keeping = client.getBody(parcel.getPayloadContract().getId());
        assertNotNull(keeping);
        assertTrue(Arrays.equals(parcel.getPayloadContract().getPackedTransaction(), keeping));

        // create revision
        Contract revisionContract = parcelContract.createRevision(manufacturePrivateKey);
        revisionContract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        revisionContract.addSignerKey(TestKeys.privateKey(1));
        revisionContract.seal();

        revisionContract.check();
        revisionContract.traceErrors();

        parcel = createParcelWithFreshU(client, revisionContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // delete bodies from node db
        try (PreparedStatement statement = db.statement("delete from kept_items where origin = ?")) {
            statement.setBytes(1, origin.getDigest());
            db.updateWithStatement(statement);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        // check getKeepingItem for nulls
        assertNull(main.node.getLedger().getKeepingItem(parcelContract.getId()));
        assertNull(main.node.getLedger().getKeepingItem(revisionContract.getId()));

        // check getBody
        byte[] keeping_root = client.getBody(parcelContract.getId());
        byte[] keeping_revision = client.getBody(revisionContract.getId());
        assertNotNull(keeping_root);
        assertNotNull(keeping_revision);
        assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
        assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));

        Thread.sleep(2000);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testGetContractAPIforOneActive() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> privateKeys = new HashSet<>(asList(TestKeys.privateKey(0)));

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : privateKeys) {
            publicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract parcelContract = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        parcelContract.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
        parcelContract.addSignerKey(manufacturePrivateKey);
        parcelContract.seal();

        parcelContract.check();
        parcelContract.traceErrors();

        Parcel parcel = createParcelWithFreshU(client, parcelContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        ItemResult itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Thread.sleep(2000);

        HashId origin = parcelContract.getId();

        // check getContract
        Binder result = client.getContract(origin);
        byte[] keeping_root = result.getBytesOrThrow("packedContract").toArray();
        assertTrue(Arrays.equals(parcelContract.getPackedTransaction(), keeping_root));
        //assertFalse(result.containsKey("contractIds"));

        // create revision
        Contract revisionContract = parcelContract.createRevision(manufacturePrivateKey);
        revisionContract.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        revisionContract.addSignerKey(TestKeys.privateKey(1));
        revisionContract.seal();

        revisionContract.check();
        revisionContract.traceErrors();

        parcel = createParcelWithFreshU(client, revisionContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check getContract
        result = client.getContract(origin);
        byte[] keeping_revision = result.getBytesOrThrow("packedContract").toArray();
        assertTrue(Arrays.equals(revisionContract.getPackedTransaction(), keeping_revision));
        //assertFalse(result.containsKey("contractIds"));

        // check getContract by revision id (can be null)
        result = client.getContract(revisionContract.getId());
        assertNull(result);

        // revoke contract
        Contract revokeContract = ContractsService.createRevocation(revisionContract, manufacturePrivateKey);
        revokeContract.seal();

        revokeContract.check();
        revokeContract.traceErrors();

        parcel = createParcelWithFreshU(client, revokeContract, privateKeys);
        client.registerParcelWithState(parcel.pack(), 8000);

        itemResult = client.getState(parcel.getPayloadContract().getId());
        System.out.println("revocation : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = client.getState(parcelContract.getId());
        System.out.println("revision : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        itemResult = client.getState(revisionContract.getId());
        System.out.println("root : " + itemResult);
        assertEquals(ItemState.REVOKED, itemResult.state);

        Thread.sleep(2000);

        // check getContract (can be null)
        result = client.getContract(origin);
        assertNull(result);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    public void testGetContractAPIforManyActive() throws Exception {

        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());

        // try cancel permanet mode
        main.config.setPermanetMode(false);
        assertTrue(main.config.isPermanetMode());

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>(asList(TestKeys.privateKey(1)));
        Set<PublicKey> ownerPublicKeys = new HashSet<>(asList(TestKeys.publicKey(2)));
        Set<PrivateKey> issuerPrivateKeys2 = new HashSet<>(asList(TestKeys.privateKey(2)));

        List<Contract> splits = new ArrayList<>();
        Integer amountTokens = 1000;

        Contract tokenContract = ContractsService.createTokenContract(issuerPrivateKeys, ownerPublicKeys, new BigDecimal("1000"));
        tokenContract.check();
        tokenContract.traceErrors();

        HashId origin = tokenContract.getId();

        ItemResult itemResult = client.register(tokenContract.getPackedTransaction(), 5000);
        System.out.println("tokenContract : " + itemResult + " = " + amountTokens.toString());
        assertEquals(ItemState.APPROVED, itemResult.state);

        for (Integer i = 1; i < 41; i++) {
            Contract contractRemainder = ContractsService.createSplit(tokenContract, new BigDecimal(i), "amount", issuerPrivateKeys2, true);
            Contract contractSplit = contractRemainder.getNew().get(0);
            amountTokens -= i;

            splits.add(contractSplit);

            itemResult = client.register(contractRemainder.getPackedTransaction(), 5000);
            System.out.println("contractRemainder : " + itemResult + " = " + amountTokens.toString());
            assertEquals(ItemState.APPROVED, itemResult.state);

            assertEquals(amountTokens.toString(), contractRemainder.getStateData().get("amount").toString());
            assertEquals(i.toString(), contractSplit.getStateData().get("amount").toString());
            assertEquals(ItemState.REVOKED, main.node.waitItem(tokenContract.getId(), 5000).state);
            assertEquals(ItemState.APPROVED, main.node.waitItem(contractRemainder.getId(), 5000).state);
            assertEquals(ItemState.APPROVED, main.node.waitItem(contractSplit.getId(), 5000).state);

            tokenContract = contractRemainder;
        }

        // check getContract (default limit 100)
        Binder result = client.getContract(origin);
        List<byte[]> allKeepingIds = result.getListOrThrow("contractIds");
        assertFalse(result.containsKey("packedContract"));
        assertEquals(allKeepingIds.size(), splits.size() + 1);

        final HashId hash = tokenContract.getId();
        assertTrue(allKeepingIds.stream().anyMatch(id -> Arrays.equals(hash.getDigest(), id)));

        splits.forEach(s -> assertTrue(allKeepingIds.stream().anyMatch(id -> Arrays.equals(s.getId().getDigest(), id))));

        // check getContract with limit
        result = client.getContract(origin, 10);
        List<byte[]> keepingIds = result.getListOrThrow("contractIds");
        assertFalse(result.containsKey("packedContract"));
        assertEquals(keepingIds.size(), 10);

        assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hash.getDigest(), id)));

        for (int i = splits.size() - 1; i > splits.size() - 10; i--) {
            final HashId hashSplit = splits.get(i).getId();
            assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hashSplit.getDigest(), id)));
        }

        // set limit in node config
        mm.forEach(x -> x.config.setQueryContractsLimit(20));

        // check getContract (default limit 100)
        result = client.getContract(origin);
        keepingIds = result.getListOrThrow("contractIds");
        assertFalse(result.containsKey("packedContract"));
        assertEquals(keepingIds.size(), 20);

        assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hash.getDigest(), id)));

        for (int i = splits.size() - 1; i > splits.size() - 20; i--) {
            final HashId hashSplit = splits.get(i).getId();
            assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hashSplit.getDigest(), id)));
        }

        // check getContract with limit
        result = client.getContract(origin, 30);
        keepingIds = result.getListOrThrow("contractIds");
        assertFalse(result.containsKey("packedContract"));
        assertEquals(keepingIds.size(), 20);

        assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hash.getDigest(), id)));

        for (int i = splits.size() - 1; i > splits.size() - 20; i--) {
            final HashId hashSplit = splits.get(i).getId();
            assertTrue(keepingIds.stream().anyMatch(id -> Arrays.equals(hashSplit.getDigest(), id)));
        }

        mm.forEach(x -> x.shutdown());
    }


    @Test
    public void joinFromDifferentOwners() throws Exception {

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Thread.sleep(2000);

        PrivateKey issuer = TestKeys.privateKey(11);
        PrivateKey owner1 = TestKeys.privateKey(12);
        PrivateKey owner2 = TestKeys.privateKey(13);
        PrivateKey owner3 = TestKeys.privateKey(14);

        Contract token = ContractsService.createTokenContract(new HashSet<>(Do.listOf(issuer)), new HashSet<>(Do.listOf(owner1.getPublicKey())), new BigDecimal("3000"));
        assertEquals(ts.client.register(token.getPackedTransaction(), 15000).state, ItemState.APPROVED);

        Contract part1 = token.createRevision(owner1);
        Contract[] parts = part1.split(2);
        Contract part2 = parts[0];
        Contract part3 = parts[1];


        part1.setOwnerKey(owner1.getPublicKey());
        part1.getStateData().set("amount", "1000");
        part2.setOwnerKey(owner2.getPublicKey());
        part2.getStateData().set("amount", "1000");
        part3.setOwnerKey(owner3.getPublicKey());
        part3.getStateData().set("amount", "1000");

        part2.seal();
        part3.seal();

        part1.seal();


        ItemResult rr = ts.client.register(part1.getPackedTransaction(), 15000);
        System.out.println(rr.errors);
        assertEquals(rr.state, ItemState.APPROVED);
        assertEquals(ts.client.getState(part2.getId()).state, ItemState.APPROVED);
        assertEquals(ts.client.getState(part3.getId()).state, ItemState.APPROVED);


        part1 = Contract.fromPackedTransaction(part1.getPackedTransaction());
        part2 = Contract.fromPackedTransaction(part2.getPackedTransaction());
        part3 = Contract.fromPackedTransaction(part3.getPackedTransaction());


        Contract badJoin = part1.createRevision(owner1);
        badJoin.addRevokingItems(part2);
        badJoin.addRevokingItems(part3);
        badJoin.getStateData().set("amount", "3000");
        badJoin.seal();
        badJoin.check();
        assertEquals(ts.client.register(badJoin.getPackedTransaction(), 15000).state, ItemState.DECLINED);

        Contract goodJoin = part1.createRevision(owner1, owner2, owner3);
        goodJoin.addRevokingItems(part2);
        goodJoin.addRevokingItems(part3);
        goodJoin.getStateData().set("amount", "3000");
        goodJoin.seal();
        assertEquals(ts.client.register(goodJoin.getPackedTransaction(), 15000).state, ItemState.APPROVED);


        ts.nodes.forEach(m -> m.shutdown());

    }

    Contract createComplexConctract(PrivateKey key, int subcontracts) {
        Contract root = new Contract(key);
        for (int i = 0; i < subcontracts; i++) {
            Contract c = new Contract(key);
            c.getKeysToSignWith().clear();
            root.addNewItems(c);
        }
        root.seal();
        return root;
    }

    public void assertAlmostSame(ZonedDateTime t1, ZonedDateTime t2, long expectedDelta) {
        if (t1 == null && t2 == null)
            return;
        long delta = Math.abs(t1.toEpochSecond() - t2.toEpochSecond());
        MatcherAssert.assertThat(delta, Matchers.is(Matchers.lessThan(expectedDelta)));
    }




    @Test
    public void splitJoinCaseTest() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Set<PrivateKey> issuersPriv = new HashSet<>();
        Set<PublicKey> ownersPub = new HashSet<>();
        Set<PrivateKey> ownersPriv = new HashSet<>();
        issuersPriv.add(TestKeys.privateKey(1));
        ownersPub.add(TestKeys.publicKey(2));
        ownersPriv.add(TestKeys.privateKey(2));
        Contract token = ContractsService.createTokenContract(issuersPriv, ownersPub, new BigDecimal("10000"));
        assertTrue(token.check());
        assertEquals(ts.client.register(token.getPackedTransaction(), 15000).state, ItemState.APPROVED);

        Contract token1 = ContractsService.createSplit(token, new BigDecimal("5000"), "amount", ownersPriv, true);
        assertTrue(token1.check());
        Contract token2 = (Contract) token1.getNewItems().iterator().next();

        token1 = Contract.fromPackedTransaction(token1.getPackedTransaction());
        token2 = Contract.fromPackedTransaction(token2.getPackedTransaction());


        assertEquals(ts.client.register(token1.getPackedTransaction(), 15000).state, ItemState.APPROVED);
        assertEquals(ts.client.getState(token2.getId()).state, ItemState.APPROVED);


        List<Contract> contracts = ContractsService.createSplitJoin(Do.listOf(token1, token2),
                Do.listOf("6000", "1000", "500", "500"),
                Do.listOf(TestKeys.publicKey(4).getShortAddress(), TestKeys.publicKey(5).getShortAddress(), TestKeys.publicKey(6).getShortAddress(), TestKeys.publicKey(7).getShortAddress()),
                ownersPriv, "amount");


        assertEquals(ts.client.register(contracts.get(0).getPackedTransaction(), 15000).state, ItemState.APPROVED);
        ts.shutdown();
    }

    @Test
    public void fakeParent() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract contract = new Contract(TestKeys.privateKey(1));
        contract.seal();
        assertEquals(ts.client.register(contract.getPackedTransaction(), 15000).state, ItemState.APPROVED);


        Contract contract2 = new Contract(TestKeys.privateKey(2));
        contract2.addRevokingItems(contract);
        contract2.getState().setParent(contract.getId());
        contract2.seal();

        assertEquals(ts.client.register(contract2.getPackedTransaction(), 15000).state, ItemState.DECLINED);
        assertEquals(ts.client.getState(contract.getId()).state, ItemState.APPROVED);

        ts.shutdown();
    }


    @Test
    public void informerTest() throws Exception {
        TestSpace ts = prepareTestSpace();
        Contract payment = getApprovedUContract(ts);
        Set<PrivateKey> paymentKeys = new HashSet();
        paymentKeys.add(ts.myKey);

        //Case 1: contract too old - U spent
        Contract c = new Contract(TestKeys.privateKey(1));
        Binder s = BossBiMapper.serialize(c);
        ZonedDateTime cr = ZonedDateTime.now().minusMonths(1);
        s.getBinder("definition").set("created_at", cr);
        s.getBinder("state").set("created_at", cr);
        c = BossBiMapper.deserialize(s);
        c.getKeysToSignWith().add(TestKeys.privateKey(1));
        c.seal();
        Parcel parcel = ContractsService.createParcel(c, payment, 1, paymentKeys);
        ItemResult res = ts.client.registerParcelWithState(parcel.pack(), 15000);
        assertSame(res.state, ItemState.UNDEFINED);
        assertEquals(1, res.errors.size());
        System.out.println(res.errors);
        //U was spent. updating payment contract;
        payment = parcel.getPayment().getContract();


        //Case 2: payload contract corrupted - U spent
        //TODO: invent case where contract is unpacked without problems but still causes exception during check()

        //Case 3: insufficient payment - U spent
        c = new Contract(TestKeys.privateKey(1));
        for (int i = 0; i < 20; i++) {
            c.addNewItems(new Contract(TestKeys.privateKey(1)));
        }
        c.seal();
        parcel = ContractsService.createParcel(c, payment, 1, paymentKeys);
        res = ts.client.registerParcelWithState(parcel.pack(), 15000);
        assertSame(res.state, ItemState.UNDEFINED);
        assertEquals(1, res.errors.size());
        System.out.println(res.errors);
        payment = parcel.getPayment().getContract();


        //Case 4: declined contract - U spent
        c = new Contract(TestKeys.privateKey(1));
        Contract ri = new Contract(TestKeys.privateKey(2));
        ri.seal();
        c.addRevokingItems(ri);
        c.seal();
        parcel = ContractsService.createParcel(c, payment, 1, paymentKeys);
        res = ts.client.registerParcelWithState(parcel.pack(), 15000);
        assertTrue(res.state == ItemState.DECLINED);
        assertEquals(1, res.errors.size());
        System.out.println(res.errors);
        payment = parcel.getPayment().getContract();


        //Case 5: approved contract - U spent
        c = new Contract(TestKeys.privateKey(1));
        c.seal();
        parcel = ContractsService.createParcel(c, payment, 1, paymentKeys);
        res = ts.client.registerParcelWithState(parcel.pack(), 15000);
        assertSame(res.state, ItemState.APPROVED);
        payment = parcel.getPayment().getContract();

        //Case 6: wrong U - U wasn't spent
        c = new Contract(TestKeys.privateKey(1));
        c.seal();
        parcel = ContractsService.createParcel(c, payment, -1, paymentKeys);
        res = ts.client.registerParcelWithState(parcel.pack(), 15000);
        ItemResult state = ts.client.getState(parcel.getPaymentContract().getId());
        assertSame(state.state, ItemState.DECLINED);
        assertSame(res.state, ItemState.UNDEFINED);
        assertEquals(0, res.errors.size());

        //DO not update payment contract. Old one still valid
        //payment = parcel.getPayment().getContract();
        ts.shutdown();
    }



    public static final String ACC_TYPE_FIELD = "type";
    public static final String ACC_TYPE_PATH = "state.data." + ACC_TYPE_FIELD;
    public static final String ACC_TYPE_PERSONAL = "personal";
    public static final String ACC_TYPE_BANK = "bank";

    public static final String ACC_CURRENCY_FIELD = "currency";
    public static final String ACC_CURRENCY_PATH = "state.data." + ACC_CURRENCY_FIELD;
    public static final String ACC_CURRENCY_RUR = "RUR";
    public static final String ACC_CURRENCY_EUR = "EUR";

    public static final String ACC_COMMISSION_PERCENT_FIELD = "commission_percent";
    public static final String ACC_COMMISSION_ACCOUNT_FIELD = "commission_account";

    public static final String ACC_COMMISSION_PERCENT_PATH = "state.data." + ACC_COMMISSION_PERCENT_FIELD;
    public static final String ACC_COMMISSION_ACCOUNT_PATH = "state.data." + ACC_COMMISSION_ACCOUNT_FIELD;


    public static final String TOKEN_VALUE_FIELD = "amount";
    public static final String TOKEN_ACCOUNT_FIELD = "account";

    public static final String TOKEN_ACCOUNT_PATH = "state.data." + TOKEN_ACCOUNT_FIELD;
    public static final String TOKEN_VALUE_PATH = "state.data." + TOKEN_VALUE_FIELD;

    public Contract[] makeTransfer(Contract token, BigDecimal amount, Contract fromAccount, Contract toAccount, Contract commissionAccount, Set<PrivateKey> keysToSignTransferWith, Client client) throws ClientError, Quantiser.QuantiserException {
        assertEquals(token.get(TOKEN_ACCOUNT_PATH), fromAccount.getId().toBase64String());
        assertEquals(fromAccount.get(ACC_COMMISSION_ACCOUNT_PATH), commissionAccount.getId().toBase64String());
        assertTrue(fromAccount.getOwner().isAllowedForKeys(keysToSignTransferWith));
        token = token.createRevision(keysToSignTransferWith);
        int partsCount = new BigDecimal(fromAccount.get(ACC_COMMISSION_PERCENT_PATH).toString()).compareTo(new BigDecimal("0")) > 0 ? 2 : 1;
        Contract[] parts = token.split(partsCount);
        Contract transfer = parts[0];
        Contract commission = partsCount > 1 ? parts[1] : null;

        transfer.getStateData().set(TOKEN_VALUE_FIELD, amount);
        transfer.getKeysToSignWith().clear();
        BigDecimal rest = new BigDecimal(token.getStateData().getString(TOKEN_VALUE_FIELD))
                .subtract(new BigDecimal(transfer.getStateData().getString(TOKEN_VALUE_FIELD)));

        if (commission != null) {
            commission.getStateData().set(TOKEN_VALUE_FIELD, new BigDecimal(fromAccount.get(ACC_COMMISSION_PERCENT_PATH).toString()).multiply(amount));
            commission.getKeysToSignWith().clear();
            rest = rest.subtract(new BigDecimal(commission.getStateData().getString(TOKEN_VALUE_FIELD)));
        }

        token.getStateData().set(TOKEN_VALUE_FIELD, rest);
        token.seal();
        token.getTransactionPack().addReferencedItem(fromAccount);
        assertTrue(token.check());


//        ItemResult ir = client.register(token.getPackedTransaction(), 8000);
//        assertEquals(ir.state,ItemState.APPROVED);


        transfer = transfer.createRevision(keysToSignTransferWith);
        transfer.getStateData().set(TOKEN_ACCOUNT_FIELD, toAccount.getId().toBase64String());
        transfer.getKeysToSignWith().clear();
        transfer.seal();

        Contract batch;

        if (commission != null) {
            commission = commission.createRevision(keysToSignTransferWith);
            commission.getStateData().set(TOKEN_ACCOUNT_FIELD, fromAccount.get(ACC_COMMISSION_ACCOUNT_PATH));
            commission.getTransactionalData().set("transfer_id", transfer.getId().toBase64String());
            commission.getKeysToSignWith().clear();

            batch = ContractsService.createBatch(keysToSignTransferWith, transfer, commission, token);
        } else {
            batch = ContractsService.createBatch(keysToSignTransferWith, transfer, token);
        }


        batch.getTransactionPack().addReferencedItem(fromAccount);
        batch.getTransactionPack().addReferencedItem(toAccount);
        batch.getTransactionPack().addReferencedItem(commissionAccount);
        ItemResult ir = client.register(batch.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state, ItemState.APPROVED);

        return new Contract[]{token, transfer, commission};
    }

    @Test
    public void demo() throws Exception {
        PrivateKey person1 = TestKeys.privateKey(1);
        PrivateKey person2 = TestKeys.privateKey(2);
        PrivateKey bank = TestKeys.privateKey(3);


        Contract commissionAccRur = new Contract(bank);
        Contract commissionAccEur = new Contract(bank);
        commissionAccRur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);
        commissionAccEur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_BANK);

        commissionAccRur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        commissionAccEur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);

        commissionAccRur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
        commissionAccEur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");

        commissionAccRur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);
        commissionAccEur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, null);

        commissionAccRur.seal();
        commissionAccEur.seal();


        Contract account1rur = new Contract(person1);
        Contract account2rur = new Contract(person2);

        Contract account2eur = new Contract(person2);

        account1rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
        account2rur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);
//        account1eur.getStateData().put(ACC_TYPE_PATH,ACC_TYPE_PERSONAL);
        account2eur.getStateData().put(ACC_TYPE_FIELD, ACC_TYPE_PERSONAL);

        account1rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
        account2rur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_RUR);
//        account1eur.getStateData().put(ACC_CURRENCY_PATH,ACC_CURRENCY_EUR);
        account2eur.getStateData().put(ACC_CURRENCY_FIELD, ACC_CURRENCY_EUR);

        account1rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.1");
        account2rur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.0");
//        account1eur.getStateData().put(ACC_COMMISSION_PERCENT_PATH,"0.0");
        account2eur.getStateData().put(ACC_COMMISSION_PERCENT_FIELD, "0.2");

        account1rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
        account2rur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccRur.getId().toBase64String());
//        account1eur.getStateData().put(ACC_COMMISSION_ACCOUNT_PATH,commissionAccEur.getId());
        account2eur.getStateData().put(ACC_COMMISSION_ACCOUNT_FIELD, commissionAccEur.getId().toBase64String());

        account1rur.seal();
        account2rur.seal();
//        account1eur.seal();
        account2eur.seal();


        Contract rurToken = new Contract(bank);


        SimpleRole tokenOwner = new SimpleRole("owner");
        tokenOwner.addRequiredReference("canplayaccowner", Role.RequiredMode.ALL_OF);
        rurToken.registerRole(tokenOwner);
        rurToken.getStateData().put("account", account1rur.getId().toBase64String());
        rurToken.getStateData().put(TOKEN_VALUE_FIELD, "100000");

        RoleLink rl = new RoleLink("@owner", "owner");
        rurToken.registerRole(rl);
        SplitJoinPermission sjp =
                new SplitJoinPermission(rl, Binder.of("field_name", TOKEN_VALUE_FIELD,
                        "join_match_fields", Do.listOf("state.origin", TOKEN_ACCOUNT_PATH)));
        rurToken.addPermission(sjp);


        ModifyDataPermission mdp =
                new ModifyDataPermission(rl, Binder.of("fields", Binder.of(TOKEN_ACCOUNT_FIELD, null)));
        rurToken.addPermission(mdp);


        Reference canplayaccowner = new Reference(rurToken);
        canplayaccowner.name = "canplayaccowner";
        List<String> conditions = new ArrayList<>();
        conditions.add("ref.id==this." + TOKEN_ACCOUNT_PATH);
        conditions.add("this can_play ref.owner");
        canplayaccowner.setConditions(Binder.of("all_of", conditions));
        rurToken.addReference(canplayaccowner);


        Reference refAccount = new Reference(rurToken);
        refAccount.name = "refAccount";
        refAccount.setConditions(Binder.of("all_of", Do.listOf("this." + TOKEN_ACCOUNT_PATH + " == ref.id")));
        rurToken.addReference(refAccount);

        Reference refParent = new Reference(rurToken);
        refParent.name = "refParent";
        refParent.setConditions(Binder.of("any_of", Do.listOf(
                "this.state.parent == ref.id",
                "this.state.parent undefined"
        )));
        rurToken.addReference(refParent);

        Reference refParentAccount = new Reference(rurToken);
        refParentAccount.name = "refParentAccount";
        refParentAccount.setConditions(Binder.of("any_of", Do.listOf(
                "refParent." + TOKEN_ACCOUNT_PATH + " == ref.id",
                "this.state.parent undefined"
        )));
        rurToken.addReference(refParentAccount);


        Reference transferCheck = new Reference(rurToken);
        transferCheck.name = "transferCheck";
        transferCheck.setConditions(Binder.of("any_of", Do.listOf(
                //root contract
                "this.state.parent undefined",

                //OR there was no transfer
                "refParent." + TOKEN_ACCOUNT_PATH + " == this." + TOKEN_ACCOUNT_PATH,

                //OR transfer is correct
                Binder.of("all_of", Do.listOf(

                        //transfer to account with same currency
                        "refAccount." + ACC_CURRENCY_PATH + " == refParentAccount." + ACC_CURRENCY_PATH,

                        //AND one of the cases
                        Binder.of("any_of", Do.listOf(
                                // contract is a commission itself
                                Binder.of("all_of", Do.listOf(
                                        "this." + TOKEN_ACCOUNT_PATH + "==refParentAccount." + ACC_COMMISSION_ACCOUNT_PATH
                                )),

                                // OR commission is set to zero
                                Binder.of("all_of", Do.listOf(
                                        "refParentAccount." + ACC_COMMISSION_PERCENT_PATH + "::number==0.0"
                                )),

                                // OR commission exists in pack
                                Binder.of("all_of", Do.listOf(
                                        "ref." + TOKEN_VALUE_PATH + "::number == this." + TOKEN_VALUE_PATH + "::number * refParentAccount." + ACC_COMMISSION_PERCENT_PATH + "::number",
                                        //"ref."+TOKEN_VALUE_PATH+"::number == refParentAccount."+ACC_COMMISSION_PERCENT_PATH+"::number",
                                        "ref." + TOKEN_ACCOUNT_PATH + " == refParentAccount." + ACC_COMMISSION_ACCOUNT_PATH,
                                        "ref.transactional.data.transfer_id == this.id"

                                ))
                        ))
                ))
        )));
        rurToken.addReference(transferCheck);
        rurToken.seal();


        // init network in permanet mode
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), "_permanet", false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        main.config.getKeysWhiteList().add(TestKeys.publicKey(20));
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        assertTrue(main.config.isPermanetMode());
        for (int i = 1; i < 4; i++)
            assertTrue(mm.get(i).config.isPermanetMode());


        assertEquals(client.register(commissionAccEur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(commissionAccRur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account1rur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account2rur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        assertEquals(client.register(account2eur.getPackedTransaction(), 8000).state, ItemState.APPROVED);
        rurToken.getTransactionPack().addReferencedItem(account1rur);
        ItemResult ir = client.register(rurToken.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state, ItemState.APPROVED);


        Contract[] result = makeTransfer(rurToken, new BigDecimal("5000"), account1rur, account2rur, commissionAccRur, new HashSet<>(Do.listOf(person1)), client);
        //commmission exists
        assertNotNull(result[2]);

        result = makeTransfer(result[1], new BigDecimal("2000"), account2rur, account1rur, commissionAccRur, new HashSet<>(Do.listOf(person2)), client);
        //transfer w/o commission
        assertNull(result[2]);

        mm.forEach(m->m.shutdown());
    }

    @Test
    public void multipleRevisions() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract c1 = new Contract(TestKeys.privateKey(1));
        c1.seal();
        ts.client.register(c1.getPackedTransaction(),8000);

        ts.clients.forEach(cl -> {
            try {
                assertEquals(cl.getState(c1.getId()).state, ItemState.APPROVED);
            } catch (ClientError clientError) {
                clientError.printStackTrace();
                assertTrue(false);
            }
        });

        Contract c2 = c1.createRevision(TestKeys.privateKey(1));
        c2.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        c2.getKeysToSignWith().clear();
        c2.seal();


        Contract c3 = c2.createRevision(TestKeys.privateKey(2));
        c3.setOwnerKeys(TestKeys.privateKey(3).getPublicKey());
        c3.getKeysToSignWith().clear();
        c3.seal();

        Contract batch = ContractsService.createBatch(Do.listOf(TestKeys.privateKey(1), TestKeys.privateKey(2), TestKeys.privateKey(3)), c2,c3);
        ItemResult ir = ts.client.register(batch.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state,ItemState.APPROVED);


        ts.clients.forEach(cl -> {
            try {
                assertEquals(cl.getState(c2.getId()).state, ItemState.REVOKED);
                assertEquals(cl.getState(c3.getId()).state, ItemState.APPROVED);
            } catch (ClientError clientError) {
                clientError.printStackTrace();
                assertTrue(false);
            }
        });
        ts.shutdown();
    }

    @Test
    public void multipleRevisionsRollback() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract c1 = new Contract(TestKeys.privateKey(1));
        c1.seal();
        ts.client.register(c1.getPackedTransaction(),8000);

        ts.clients.forEach(cl -> {
            try {
                assertEquals(cl.getState(c1.getId()).state, ItemState.APPROVED);
            } catch (ClientError clientError) {
                clientError.printStackTrace();
                assertTrue(false);
            }
        });

        Contract c2 = c1.createRevision(TestKeys.privateKey(1));
        c2.setOwnerKeys(TestKeys.privateKey(2).getPublicKey());
        c2.getKeysToSignWith().clear();
        c2.seal();


        Contract c3 = c2.createRevision(TestKeys.privateKey(2));
        c3.setOwnerKeys(TestKeys.privateKey(3).getPublicKey());
        c3.getKeysToSignWith().clear();
        c3.seal();

        Contract unregistered = new Contract(TestKeys.privateKey(10));
        RoleLink rl = new RoleLink("@revoke", "owner");
        unregistered.registerRole(rl);
        unregistered.addPermission(new RevokePermission(rl));
        unregistered.getSealedByKeys().clear();
        unregistered.seal();

        //registerAndCheckApproved(unregistered);

        System.out.println("==============================================");


        Contract revokesUnregistered = new Contract(TestKeys.privateKey(10));
        revokesUnregistered.addRevokingItems(unregistered);
        revokesUnregistered.seal();

        //manual tweaking the status of unregistered contract
        ts.nodes.get(0).node.getLedger().findOrCreate(unregistered.getId()).setState(ItemState.APPROVED).save();
        ts.nodes.get(1).node.getLedger().findOrCreate(unregistered.getId()).setState(ItemState.REVOKED).save();


        Contract batch = ContractsService.createBatch(Do.listOf(TestKeys.privateKey(1), TestKeys.privateKey(2), TestKeys.privateKey(3)), c2,c3,revokesUnregistered);
        ItemResult ir = ts.client.register(batch.getPackedTransaction(), 8000);
        System.out.println(ir.errors);
        assertEquals(ir.state,ItemState.DECLINED);


        ts.clients.forEach(cl -> {
            try {
                assertEquals(cl.getState(c1.getId()).state, ItemState.APPROVED);
                assertEquals(cl.getState(c2.getId()).state, ItemState.UNDEFINED);
                assertEquals(cl.getState(c3.getId()).state, ItemState.UNDEFINED);
            } catch (ClientError clientError) {
                clientError.printStackTrace();
                assertTrue(false);
            }
        });
        ts.shutdown();
    }

    @Test
    public void dupesTest() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));


        Contract c = new Contract(TestKeys.privateKey(1));
        c.seal();
        assertEquals(ts.client.register(c.getPackedTransaction(),9000).state,ItemState.APPROVED);


        Contract c1 = c.createRevision(TestKeys.privateKey(1));
        c1.setOwnerKeys(TestKeys.privateKey(2));
        c1.getState().setBranchNumber(1);

        Contract c2 = c.createRevision(TestKeys.privateKey(1));
        c2.setOwnerKeys(TestKeys.privateKey(2));
        c2.getState().setBranchNumber(2);

        Contract batch = ContractsService.createBatch(Do.listOf(TestKeys.privateKey(1)),c1,c2);
        ItemResult ir = ts.client.register(batch.getPackedTransaction(), 9000);
        System.out.println(ir.errors);
        assertEquals(ir.state,ItemState.DECLINED);

        ts.shutdown();

    }

    @Test
    public void getTopologyFirstTime() throws Exception {
        String someNodeUrl = "http://node-1-pro.utoken.io";
        PrivateKey clientKey = new PrivateKey(2048);

        createTopologyForCustomNetwork(someNodeUrl,"my_personal_network");
        Client c = new Client("my_personal_network",null,clientKey);
        System.out.println(c.getVersion() + " " + c.size() + " " + c.getNodeNumber());

    }

    private void createTopologyForCustomNetwork(String url, String networkName) throws IOException {
        PrivateKey key = new PrivateKey(2048);
        Client cOld = new Client(url,key,null);
        Client.NodeRecord nr = Do.sample(cOld.getNodes());
        Client cNew = new Client(nr.url,key,null,false,nr.key);
        FileOutputStream fos = new FileOutputStream(System.getProperty("user.home") + "/.universa/topology/" + networkName + ".json");
        fos.write(JsonTool.toJsonString(cNew.getTopology()).getBytes());
        fos.close();
    }

    @Test
    public void httpProxy() throws Exception {
        List<Main> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            nodes.add(createMain("node" + (i + 1), false));
        nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        PrivateKey myKey = TestKeys.privateKey(3);
        ArrayList<Client> clientProxyToNode = new ArrayList<>();
        for (int i = 0; i < 4; ++i) {
            Client clientProxyTo_i = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),myKey);

            clientProxyTo_i.startProxyToNode(i, null);
            clientProxyToNode.add(clientProxyTo_i);
        }

        HashId testHashId = HashId.createRandom();
        nodes.get(0).node.getLedger().findOrCreate(testHashId).setState(ItemState.APPROVED).save();
        nodes.get(1).node.getLedger().findOrCreate(testHashId).setState(ItemState.DECLINED).save();
        nodes.get(2).node.getLedger().findOrCreate(testHashId).setState(ItemState.REVOKED).save();
        nodes.get(3).node.getLedger().findOrCreate(testHashId).setState(ItemState.DISCARDED).save();

        System.out.println("\n\n-------------------");

        ItemResult answer = clientProxyToNode.get(0).getState(testHashId);
        System.out.println(answer);
        assertEquals(ItemState.APPROVED, answer.state);
        answer = clientProxyToNode.get(1).getState(testHashId);
        System.out.println(answer);
        assertEquals(ItemState.DECLINED, answer.state);
        answer = clientProxyToNode.get(2).getState(testHashId);
        System.out.println(answer);
        assertEquals(ItemState.REVOKED, answer.state);
        answer = clientProxyToNode.get(3).getState(testHashId);
        System.out.println(answer);
        assertEquals(ItemState.DISCARDED, answer.state);
        System.out.println("-------------------\n\n");

        nodes.forEach(n->n.shutdown());
    }

    @Test
    public void httpProxyRecursion() throws Exception {
        List<Main> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            nodes.add(createMain("node" + (i + 1), false));
        nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        PrivateKey myKey = TestKeys.privateKey(3);
        Client clientProxy = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),myKey);
        clientProxy.startProxyToNode(1, null);

        HashId testHashId = HashId.createRandom();
        nodes.get(0).node.getLedger().findOrCreate(testHashId).setState(ItemState.APPROVED).save();
        nodes.get(1).node.getLedger().findOrCreate(testHashId).setState(ItemState.DECLINED).save();
        nodes.get(2).node.getLedger().findOrCreate(testHashId).setState(ItemState.REVOKED).save();
        nodes.get(3).node.getLedger().findOrCreate(testHashId).setState(ItemState.DISCARDED).save();

        System.out.println("\n\n-------------------");

        Client targetClient = clientProxy.getClient(2);
        BasicHttpClientSession targetSession = targetClient.getSession();
        Binder cmd = Binder.of("command", "getState", "params", Binder.of("itemId", testHashId));
        Binder commandParams = Binder.of("session_id", targetSession.getSessionId(), "params", targetSession.getSessionKey().etaEncrypt(Boss.pack(cmd)));

        Binder answer = clientProxy.command("proxy", "url", targetClient.getUrl(), "command", "proxyCommand", "params", commandParams);
        String answerError = answer.getStringOrThrow("error");
        System.out.println("try recursion v1... answerError: " + answerError);
        assertEquals("COMMAND_FAILED [proxy] Unable to proxy command 'proxy'", answerError);

        System.out.println("-------------------\n\n");

        nodes.forEach(n->n.shutdown());
    }

    @Test
    public void httpProxyRecursion2() throws Exception {
        List<Main> nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            nodes.add(createMain("node" + (i + 1), false));
        nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        PrivateKey myKey = TestKeys.privateKey(3);
        Client clientProxy = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),myKey);

        //don't start proxy here. we will make proxy request manually

        HashId testHashId = HashId.createRandom();
        nodes.get(0).node.getLedger().findOrCreate(testHashId).setState(ItemState.APPROVED).save();
        nodes.get(1).node.getLedger().findOrCreate(testHashId).setState(ItemState.DECLINED).save();
        nodes.get(2).node.getLedger().findOrCreate(testHashId).setState(ItemState.REVOKED).save();
        nodes.get(3).node.getLedger().findOrCreate(testHashId).setState(ItemState.DISCARDED).save();

        System.out.println("\n\n-------------------");

        Client targetClient1 = clientProxy.getClient(1);
        Client targetClient2 = clientProxy.getClient(2);
        BasicHttpClientSession targetSession1 = targetClient1.getSession();
        BasicHttpClientSession targetSession2 = targetClient2.getSession();
        Binder cmd2 = Binder.of("command", "getState", "params", Binder.of("itemId", testHashId));
        Binder command2Params = Binder.of("session_id", targetSession2.getSessionId(), "params", targetSession2.getSessionKey().etaEncrypt(Boss.pack(cmd2)));
        Binder answer = clientProxy.command("proxy", "url", targetClient2.getUrl(), "command", "command", "params", command2Params);

        Binder answerBinder = Boss.load(answer.getBinaryOrThrow("result"));
        String answerError = answerBinder.getBinderOrThrow("response").getStringOrThrow("response");
        System.out.println("try recursion v2... answerError: " + answerError);
        assertEquals("Access denied. Command 'command' is not allowed with 'proxy', use 'proxyCommand' instead.", answerError);

        System.out.println("-------------------\n\n");

        nodes.forEach(n->n.shutdown());
    }



    @Test
    public void networkStatusTest() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n-> {
            try {
                n.config.getAddressesWhiteList().add(TestKeys.publicKey(1).getLongAddress());
            } catch (EncryptionError encryptionError) {
                encryptionError.printStackTrace();
            }
        });

        Client client = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),TestKeys.privateKey(1));

        HashId id1 = HashId.createRandom();
        ts.nodes.forEach(n->n.node.getLedger().findOrCreate(id1).setState(ItemState.APPROVED).save());
        assertTrue(client.isApprovedByNetwork(id1,0.9,3000));

        HashId id2 = HashId.createRandom();
        for(int i = 0; i < ts.nodes.size();i++) {
            ts.nodes.get(i).node.getLedger().findOrCreate(id2).setState(i==0?ItemState.DECLINED:ItemState.APPROVED);
        }
        for(int i = 0; i < 10;i++) {
            assertFalse(client.isApprovedByNetwork(id2, 0.8, 3000));
        }

        ts.shutdown();
        try {
            client.isApprovedByNetwork(HashId.createRandom(),0.1,3000);
        } catch (ClientError e) {
            System.out.println(e.getErrorRecord());
            assertEquals(e.getErrorRecord().getError(), Errors.COMMAND_PENDING);
        }
    }





    @Test
    public void pingTest() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n-> {
            try {
                n.config.getAddressesWhiteList().add(TestKeys.publicKey(1).getLongAddress());
            } catch (EncryptionError encryptionError) {
                encryptionError.printStackTrace();
            }
        });

        Client client1 = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),TestKeys.privateKey(10));

        try {
            Binder resErr  = client1.getClient(0).pingNode(3,10000);
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }


        Client client = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),TestKeys.privateKey(1));

        try {
            Binder resErr  = client.getClient(0).pingNode(33,10000);
            fail();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        for(int i = 0; i < client.size();i++) {
            for(int j = 0; j < client.size();j++) {
                if(j == i)
                    continue;
                Binder res = client.getClient(i).pingNode(j + 1, 10000);

                System.out.println(i+"->"+j+" " +res);
                assertTrue(res.getIntOrThrow("UDP") >= 0);
                assertTrue(res.getIntOrThrow("UDP") < 10);

                assertTrue(res.getIntOrThrow("TCP") >= 0);
                assertTrue(res.getIntOrThrow("TCP") < 15);
            }
        }


        ts.nodes.remove(ts.nodes.size()-1).shutdown();

        for(int i = 0; i < client.size()-1;i++) {
            for(int j = 0; j < client.size();j++) {
                if(j == i)
                    continue;
                Binder res = client.getClient(i).pingNode(j + 1, 500);
                if(j < client.size()-1) {
                    assertTrue(res.getIntOrThrow("UDP") >= 0);
                    assertTrue(res.getIntOrThrow("UDP") < 10);
                    assertTrue(res.getIntOrThrow("TCP") >= 0);
                    assertTrue(res.getIntOrThrow("TCP") < 10);
                } else {
                    assertEquals(res.getIntOrThrow("UDP"),-1);
                    assertEquals(res.getIntOrThrow("TCP"),-1);
                }
            }
        }

        ts.shutdown();
    }

    public static Contract joinWithDelay(Contract personUnits, Contract bankUnits, Duration delay, Set<PrivateKey> ownersKeys, String fieldName) {
        Contract result = personUnits.createRevision(ownersKeys);
        result.addRevokingItems(bankUnits);

        BigDecimal sum = new BigDecimal(personUnits.getStateData().get(fieldName).toString()).add(new BigDecimal(bankUnits.getStateData().get(fieldName).toString()));

        result.getStateData().put(fieldName, sum.toString());

        Reference ownerRef = new Reference(result);
        ownerRef.type = Reference.TYPE_TRANSACTIONAL;
        ownerRef.name = "refOwner";
        ownerRef.setConditions(Binder.of("all_of",Do.listOf(
                "now>this.transactional.data.valid_from")));
        result.addReference(ownerRef);
        result.getOwner().addRequiredReference("refOwner", Role.RequiredMode.ALL_OF);
        result.getTransactionalData().put("valid_from",ZonedDateTime.now().plus(delay));

        return result;
    }

    @Test
    public void joinWithDelayTest() throws Exception{

        PrivateKey bankKey = TestKeys.privateKey(1);
        PrivateKey personKey = TestKeys.privateKey(2);


        Contract token = ContractsService.createTokenContract(new HashSet<>(Do.listOf(bankKey)),new HashSet<>(Do.listOf(bankKey.getPublicKey())),new BigDecimal("1000"));
        token.seal();
        assertTrue(token.check());

        Contract bankUnits = ContractsService.createSplit(token,new BigDecimal("200"),"amount",new HashSet<>(Do.listOf(bankKey)));
        Contract personalUnits = (Contract) bankUnits.getNewItems().iterator().next();
        personalUnits.setOwnerKey(TestKeys.privateKey(2));
        personalUnits.seal();
        bankUnits.seal();
        assertTrue(bankUnits.check());


        final long DELAY = 3000;

        Contract joinResult = joinWithDelay(personalUnits,bankUnits,Duration.ofMillis(DELAY),new HashSet<>(Do.listOf(bankKey,personKey)),"amount");
        joinResult.seal();
        assertTrue(joinResult.check());



        Contract transferBeforeDelayPassed = joinResult.createRevision(personKey);
        transferBeforeDelayPassed.setOwnerKey(TestKeys.publicKey(3));
        transferBeforeDelayPassed.seal();
        assertFalse(transferBeforeDelayPassed.check());

        //Timestamps are trunicated to seconds. We must add a second to delay to be sure.
        Thread.sleep(DELAY+1000);


        Contract transferAfterDelayPassedWrongKey = joinResult.createRevision(bankKey);
        transferAfterDelayPassedWrongKey.setOwnerKey(TestKeys.publicKey(3));
        transferAfterDelayPassedWrongKey.seal();
        transferAfterDelayPassedWrongKey.check();
        assertFalse(transferAfterDelayPassedWrongKey.isOk());

        Contract transferAfterDelayPassed = joinResult.createRevision(personKey);
        transferAfterDelayPassed.setOwnerKey(TestKeys.publicKey(3));
        transferAfterDelayPassed.seal();
        transferAfterDelayPassed.check();
        assertTrue(transferAfterDelayPassed.isOk());

    }

    @Test
    public void secureLoanTestDefault() throws Exception{

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey lenderKey = TestKeys.privateKey(1);
        PrivateKey borrowerKey = TestKeys.privateKey(2);

        KeyAddress lenderAddress = lenderKey.getPublicKey().getLongAddress();
        KeyAddress borrowerAddress = borrowerKey.getPublicKey().getLongAddress();


        //create token for lender
        Contract token = ContractsService.createTokenContract(new HashSet<>(Do.listOf(TestKeys.privateKey(15))),new HashSet<>(Do.listOf(lenderKey.getPublicKey())),new BigDecimal("1000"));
        token.seal();

        assertEquals(ts.client.register(token.getPackedTransaction(),8000).state,ItemState.APPROVED);

        //split 200 and transfer to borrower
        token = ContractsService.createSplit(token,new BigDecimal("200"),"amount",new HashSet<>(Do.listOf(lenderKey)));
        token.setCreatorKeys(lenderKey.getPublicKey());
        Contract rest = (Contract) token.getNewItems().iterator().next();
        rest.setCreatorKeys(lenderKey.getPublicKey());
        rest.setOwnerKeys(borrowerAddress);
        rest.seal();
        token.seal();
        ItemResult ir = ts.client.register(token.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //create collateral on behalf of borrower
        Contract collateral = new Contract(TestKeys.privateKey(15));
        collateral.setOwnerKeys(borrowerKey);
        collateral.getStateData().put("description","This is very valuable contract");
        collateral.seal();
        assertEquals(ts.client.register(collateral.getPackedTransaction(),8000).state,ItemState.APPROVED);

        Contract[] res = SecureLoanHelper.initSecureLoan(null,lenderAddress, borrowerAddress, token, Duration.ofSeconds(3), collateral, "1000",false,token.getOrigin(),null,null);

        Contract secureLoan = res[0];

        secureLoan.addSignatureToSeal(borrowerKey);
        secureLoan.addSignatureToSeal(lenderKey);

        ir = ts.client.register(secureLoan.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);



        //colateral isn't owned by neither borrower nor lender.
        Contract col1 = collateral.createRevision(borrowerKey,lenderKey);
        col1.setOwnerKeys(TestKeys.publicKey(4));
        col1.seal();
        col1.getTransactionPack().addReferencedItem(secureLoan);
        ir = ts.client.register(col1.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.DECLINED);


        //DEFAULT IS NOT AVAILABLE
        Contract defaultAttempt = SecureLoanHelper.defaultSecureLoan(secureLoan)[0];
        defaultAttempt.addSignatureToSeal(lenderKey);
        ir = ts.client.register(defaultAttempt.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.DECLINED);


        Thread.sleep(6000);

        //DEFAULT IS AVAILABLE
        res = SecureLoanHelper.defaultSecureLoan(secureLoan);
        secureLoan = res[0];
        collateral = res[1];
        secureLoan.addSignatureToSeal(lenderKey);
        ir = ts.client.register(secureLoan.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //colateral is now owner by lender
        Contract col2 = collateral.createRevision(lenderKey);
        col2.setOwnerKeys(TestKeys.publicKey(4));
        col2.seal();

        col2 = Contract.fromPackedTransaction(col2.getPackedTransaction());
        assertTrue(col2.check());
        ir = ts.client.register(col2.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        ts.shutdown();
    }



    @Test
    public void secureLoanTestRepayment() throws Exception{

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey lenderKey = TestKeys.privateKey(1);
        PrivateKey borrowerKey = TestKeys.privateKey(2);

        KeyAddress lenderAddress = lenderKey.getPublicKey().getLongAddress();
        KeyAddress borrowerAddress = borrowerKey.getPublicKey().getLongAddress();


        //create token for lender
        Contract token = ContractsService.createTokenContract(new HashSet<>(Do.listOf(TestKeys.privateKey(15))),new HashSet<>(Do.listOf(lenderKey.getPublicKey())),"1000");
        token.seal();

        assertEquals(ts.client.register(token.getPackedTransaction(),8000).state,ItemState.APPROVED);

        //split 200 and transfer to borrower
        token = ContractsService.createSplit(token,new BigDecimal("200"),"amount",new HashSet<>(Do.listOf(lenderKey)));
        token.setCreatorKeys(lenderAddress);
        Contract rest = (Contract) token.getNewItems().iterator().next();
        rest.setCreatorKeys(lenderAddress);
        rest.setOwnerKeys(borrowerAddress);
        rest.seal();
        token.seal();
        assertEquals(ts.client.register(token.getPackedTransaction(),8000).state,ItemState.APPROVED);


        //create collateral on behalf of borrower
        Contract collateral = new Contract(borrowerKey);
        collateral.getStateData().put("description","This is very valuable contract");
        collateral.seal();
        assertEquals(ts.client.register(collateral.getPackedTransaction(),8000).state,ItemState.APPROVED);

        //init loan contract
        Contract[] res =  SecureLoanHelper.initSecureLoan(Binder.of("description","bla bla bla"),lenderAddress, borrowerAddress, token, Duration.ofSeconds(30), collateral, "1000",false,token.getOrigin(),null,null);

        Contract secureLoan = res[0];

        //add signatures

        secureLoan.addSignatureToSeal(borrowerKey);
        secureLoan.addSignatureToSeal(lenderKey);

        ItemResult ir = ts.client.register(secureLoan.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        token = res[1];


        //prepare repayment using second part
        token = token.createRevision(borrowerKey);
        token.addRevokingItems(rest);
        token.getStateData().set("amount","1000");
        token.seal();
        ir = ts.client.register(token.getPackedTransaction(),8000);
        System.out.println(ir);

        //set loan contract ot REPAID
        res = SecureLoanHelper.repaySecureLoan(secureLoan,token);
        secureLoan = res[0];
        secureLoan.addSignatureToSeal(borrowerKey);

        ir = ts.client.register(secureLoan.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        //Check that token and collateral aren't available
        Contract tokenAtempt = token.createRevision(lenderKey);
        tokenAtempt.setOwnerKeys(TestKeys.publicKey(5));
        tokenAtempt.seal();
        tokenAtempt.getTransactionPack().addReferencedItem(secureLoan);
        ir = ts.client.register(tokenAtempt.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.DECLINED);

        Contract collateralAtempt = collateral.createRevision(borrowerKey);
        collateralAtempt.setOwnerKeys(TestKeys.publicKey(5));
        collateralAtempt.seal();
        collateralAtempt.getTransactionPack().addReferencedItem(secureLoan);
        ir = ts.client.register(collateralAtempt.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.DECLINED);

        //close loan contract
        res = SecureLoanHelper.closeSecureLoan(secureLoan);
        secureLoan = res[0];
        token = res[1];
        collateral = res[2];
        secureLoan.addSignatureToSeal(lenderKey);
        secureLoan.addSignatureToSeal(borrowerKey);
        ir = ts.client.register(secureLoan.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //Check that token and collateral are available
        tokenAtempt = token.createRevision(lenderKey);
        tokenAtempt.setOwnerKeys(TestKeys.publicKey(5));
        tokenAtempt.seal();
        ir = ts.client.register(tokenAtempt.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        collateralAtempt = collateral.createRevision(borrowerKey);
        collateralAtempt.setOwnerKeys(TestKeys.publicKey(5));
        collateralAtempt.seal();
        ir = ts.client.register(collateralAtempt.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        ts.shutdown();

    }


    @Test
    public void secureLoanTestDefaultMintableSimple() throws Exception{

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey lenderKey = TestKeys.privateKey(1);
        PrivateKey borrowerKey = TestKeys.privateKey(2);

        KeyAddress lenderAddress = lenderKey.getPublicKey().getLongAddress();
        KeyAddress borrowerAddress = borrowerKey.getPublicKey().getLongAddress();


        //create 800 tokens for lender
        Contract token = ContractsService.createMintableTokenContract(new HashSet<>(Do.listOf(TestKeys.privateKey(15))), new HashSet<>(Do.listOf(lenderKey.getPublicKey())), new BigDecimal("800"), new BigDecimal("0.1"), "uEUR", "UniEuro", "Coin description");
        //change public key to address in issuer. it is important for reference matching
        token.seal();
        assertEquals(ts.client.register(token.getPackedTransaction(),8000).state,ItemState.APPROVED);


        //create collateral on behalf of borrower
        Contract collateral = new Contract(borrowerKey);
        collateral.getStateData().put("description","This is very valuable contract");
        collateral.seal();
        assertEquals(ts.client.register(collateral.getPackedTransaction(),8000).state,ItemState.APPROVED);

        //INIT LOAN
        Contract[] res =  SecureLoanHelper.initSecureLoan(new Binder(),lenderAddress, borrowerAddress, token, Duration.ofSeconds(3), collateral, "1000",true,null,token.getIssuer().getSimpleAddress(), (String) token.getDefinition().getData().get("currency"));
        Contract secureLoan = res[0];


        Compound compound = new Compound();
        compound.addContract("loan",secureLoan,null);
        compound.getCompoundContract().addSignatureToSeal(borrowerKey);
        compound.getCompoundContract().addSignatureToSeal(lenderKey);

        ItemResult ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        Thread.sleep(6000);

        //DEFAULT LOAN
        res = SecureLoanHelper.defaultSecureLoan(secureLoan);

        secureLoan = res[0];


        compound = new Compound();
        compound.addContract("loan",secureLoan,null);
        compound.getCompoundContract().addSignatureToSeal(lenderKey);


        collateral = res[1];
        ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //colateral is now owner by lender
        collateral = collateral.createRevision(lenderKey);
        collateral.setOwnerKeys(TestKeys.publicKey(5));
        collateral.seal();
        ir = ts.client.register(collateral.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        ts.shutdown();
    }


    @Test
    public void secureLoanTestRepaymentMintableSimple() throws Exception{

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));


        PrivateKey lenderKey = TestKeys.privateKey(1);
        PrivateKey borrowerKey = TestKeys.privateKey(2);

        KeyAddress lenderAddress = lenderKey.getPublicKey().getLongAddress();
        KeyAddress borrowerAddress = borrowerKey.getPublicKey().getLongAddress();


        //create 800 tokens for lender
        Contract token = ContractsService.createMintableTokenContract(new HashSet<>(Do.listOf(TestKeys.privateKey(15))), new HashSet<>(Do.listOf(lenderKey.getPublicKey())), new BigDecimal("800"), new BigDecimal("0.1"), "uEUR", "UniEuro", "Coin description");
        //change public key to address in issuer. it is important for reference matching
        token.seal();
        assertEquals(ts.client.register(token.getPackedTransaction(),8000).state,ItemState.APPROVED);

        //create 200 tokens for borrower
        Contract rest = ContractsService.createMintableTokenContract(new HashSet<>(Do.listOf(TestKeys.privateKey(15))), new HashSet<>(Do.listOf(borrowerKey.getPublicKey())), new BigDecimal("200"), new BigDecimal("0.1"), "uEUR", "UniEuro", "Coin description");
        //change public key to address in issuer. it is important for reference matching
        rest.seal();
        assertEquals(ts.client.register(rest.getPackedTransaction(),8000).state,ItemState.APPROVED);


        //create collateral on behalf of borrower
        Contract collateral = new Contract(borrowerKey);
        collateral.getStateData().put("description","This is very valuable contract");
        collateral.seal();
        assertEquals(ts.client.register(collateral.getPackedTransaction(),8000).state,ItemState.APPROVED);



        PrivateKey issuerKey = TestKeys.privateKey(13);

        //init loan contract
        Contract[] res =  SecureLoanHelper.initSecureLoan(Do.listOf(issuerKey.getPublicKey().getLongAddress()),null, lenderAddress, borrowerAddress, token, Duration.ofSeconds(300), collateral, "1000",true,null,token.getIssuer().getSimpleAddress(), (String) token.getDefinition().getData().get("currency"));
        Contract secureLoan = res[0];
        secureLoan.seal();

        Compound compound = new Compound();
        compound.addContract("loan",secureLoan,null);
        //add signatures
        compound.getCompoundContract().addSignatureToSeal(borrowerKey);
        compound.getCompoundContract().addSignatureToSeal(lenderKey);
        compound.getCompoundContract().addSignatureToSeal(issuerKey);

        ItemResult ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        token = res[1];


        //prepare repayment using second contract of 200
        token = token.createRevision(borrowerKey);
        token.addRevokingItems(rest);
        token.getStateData().set("amount","1000");
        token.seal();
        ir = ts.client.register(token.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //set loan contract ot REPAID
        res = SecureLoanHelper.repaySecureLoan(secureLoan,token);
        secureLoan = res[0];

        compound = new Compound();
        compound.addContract("loan",secureLoan,null);
        //add signatures
        compound.getCompoundContract().addSignatureToSeal(borrowerKey);

        ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //close loan contract
        res = SecureLoanHelper.closeSecureLoan(secureLoan);
        secureLoan = res[0];
        token = res[1];
        collateral = res[2];


        compound = new Compound();
        compound.addContract("loan",secureLoan,null);
        //add signatures
        compound.getCompoundContract().addSignatureToSeal(borrowerKey);
        compound.getCompoundContract().addSignatureToSeal(lenderKey);

        ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //Check that token and collateral are available
        token = token.createRevision(lenderKey);
        token.setOwnerKeys(TestKeys.publicKey(5));
        token.seal();
        ir = ts.client.register(token.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        token = collateral.createRevision(borrowerKey);
        token.setOwnerKeys(TestKeys.publicKey(5));
        token.seal();
        ir = ts.client.register(token.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        ts.shutdown();

    }

    @Test
    public void testMainTopology() throws Exception {
        TestSpace ts = prepareTestSpace();
        Client c = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),TestKeys.privateKey(1));
        System.out.println(c.getVersion());
        ts.shutdown();
    }

    @Test
    public void testReferencesToPermissionsAndCustomRoles() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Client c = new Client("./src/test_node_config_v2/test_node_config_v2.json",System.getProperty("java.io.tmpdir"),ts.myKey);


        Contract contract = new Contract(TestKeys.privateKey(0));
        Contract contract2 = new Contract(TestKeys.privateKey(0));
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(new RoleLink("@owner", contract,"owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );

        ChangeNumberPermission changeNumberPermission2 = new ChangeNumberPermission(new RoleLink("@owner", contract2,"owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );
        changeNumberPermission.setId("changenumber");
        changeNumberPermission2.setId("changenumber2");

        contract.addRole(new RoleLink("link_to_issuer",contract,"issuer"));
        contract.addPermission(changeNumberPermission);
        contract2.addPermission(changeNumberPermission2);
        contract.seal();


        Reference reference = new Reference(contract2);
        reference.name = "test1";
        reference.setConditions(Binder.of("all_of", Do.listOf("ref.id==\""+contract.getId().toBase64String()+"\"","this.definition.permissions.changenumber2==ref.definition.permissions.changenumber")));
        contract2.addReference(reference);
        contract2.addNewItems(contract);
        contract2.seal();
        assertEquals(c.register(contract2.getPackedTransaction(),8000).state,ItemState.APPROVED);
        assertTrue(c.isApprovedByNetwork(contract2.getId(),0.9,8000));
        ts.shutdown();
    }


    @Test
    public void escrowHelperTest() throws Exception {
        KeyAddress issuerAddress = TestKeys.publicKey(9).getShortAddress();
        KeyAddress contractorAddress = TestKeys.publicKey(10).getShortAddress();
        KeyAddress customerAddress = TestKeys.publicKey(11).getShortAddress();
        KeyAddress arbitratorAddress = TestKeys.publicKey(12).getShortAddress();
        KeyAddress storageServiceAddress = TestKeys.publicKey(13).getShortAddress();

        PrivateKey issuerKey = TestKeys.privateKey(9);
        PrivateKey contractorKey = TestKeys.privateKey(10);
        PrivateKey customerKey = TestKeys.privateKey(11);
        PrivateKey arbitratorKey = TestKeys.privateKey(12);
        PrivateKey storageServiceKey = TestKeys.privateKey(13);



        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->{n.config.setIsFreeRegistrationsAllowedFromYaml(true); n.config.getAddressesWhiteList().add(ts.client.getSession().getPrivateKey().getPublicKey().getLongAddress());});


        Contract payment;
        Contract escrow;
        ItemResult ir;
        Contract[] res;

        //INIT->ASSIGN->COMPLETE->CLOSE(customer accepts work)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),Binder.of("data","asdasd123"),Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofDays(500),payment)[0];
        escrow.addSignatureToSeal(issuerKey);
        escrow.addSignatureToSeal(customerKey);

        ir = ts.client.register(escrow.getPackedTransaction(),8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = EscrowHelper.completeEscrow(escrow,Binder.of("description","Completion details"))[0];

        Compound compound = new Compound();
        compound.addContract("escrow",escrow,null);
        //add signatures
        compound.getCompoundContract().addSignatureToSeal(contractorKey);
        compound.getCompoundContract().addSignatureToSeal(storageServiceKey);



        ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = compound.getContract("escrow");

        res = EscrowHelper.closeByCustomer(escrow);
        escrow = res[0];

        compound = new Compound();
        compound.addContract("escrow",escrow,null);
        //add signatures
        compound.getCompoundContract().addSignatureToSeal(customerKey);
        compound.getCompoundContract().addSignatureToSeal(storageServiceKey);

        ir = ts.client.register(compound.getCompoundContract().getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = compound.getContract("escrow");

        res = EscrowHelper.obtainPaymentOnClosedEscrow(escrow);
        payment = res[0];
        payment.addSignatureToSeal(contractorKey);
        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = payment.createRevision(contractorKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //INIT->ASSIGN->COMPLETE->CLOSE(arbitration)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),null,Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofDays(500),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = EscrowHelper.completeEscrow(escrow,Binder.of("description","Completion details"))[0];
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(storageServiceKey);


        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        res = EscrowHelper.closeByArbitration(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(arbitratorKey);
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(storageServiceKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        res = EscrowHelper.obtainPaymentOnClosedEscrow(escrow);
        payment = res[0];
        payment.addSignatureToSeal(contractorKey);
        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = payment.createRevision(contractorKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);




        //INIT->ASSIGN->COMPLETE->CANCEL(cancel agreed)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),Binder.of("aaa","111"),Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofDays(500),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = EscrowHelper.completeEscrow(escrow,Binder.of("description","Completion details"))[0];
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(storageServiceKey);


        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        res = EscrowHelper.cancelEscrow(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = res[1];
        payment = payment.createRevision(customerKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //INIT->ASSIGN->COMPLETE->CANCEL(arbitration)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),null,Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofDays(500),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        escrow = EscrowHelper.completeEscrow(escrow,Binder.of("description","Completion details"))[0];
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(storageServiceKey);


        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        res = EscrowHelper.cancelEscrow(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(arbitratorKey);
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(storageServiceKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = res[1];
        payment = payment.createRevision(customerKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);





        //INIT->ASSIGN->CANCEL(contractor,customer)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),null,Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofDays(500),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        res = EscrowHelper.cancelEscrow(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(contractorKey);
        escrow.addSignatureToSeal(customerKey);


        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = res[1];
        payment = payment.createRevision(customerKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();
        payment.getTransactionPack().addReferencedItem(escrow);
        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        //INIT->ASSIGN->CANCEL(customer,timeout)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);


        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),null,Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofSeconds(3),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        escrow = EscrowHelper.assignEscrow(escrow,contractorAddress,Binder.of("description","Assignment is done with additional info..."))[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(contractorKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        Thread.sleep(3000);

        res = EscrowHelper.cancelEscrow(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(customerKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = res[1];
        payment = payment.createRevision(customerKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        //INIT->CANCEL(customer)

        payment = new Contract(customerKey);
        payment.seal();
        assertEquals(ts.client.register(payment.getPackedTransaction(),8000).state,ItemState.APPROVED);


        escrow = EscrowHelper.initEscrow(Do.listOf(issuerAddress),null,Binder.of("description","This is description the work to be done"),customerAddress,arbitratorAddress,storageServiceAddress,Duration.ofSeconds(30000),payment)[0];
        escrow.addSignatureToSeal(customerKey);
        escrow.addSignatureToSeal(issuerKey);

        assertEquals(ts.client.register(escrow.getPackedTransaction(),8000).state,ItemState.APPROVED);

        res = EscrowHelper.cancelEscrow(escrow);
        escrow = res[0];
        escrow.addSignatureToSeal(customerKey);

        ir = ts.client.register(escrow.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);

        payment = res[1];
        payment = payment.createRevision(customerKey);
        payment.setOwnerKeys(TestKeys.publicKey(20));
        payment.seal();

        ir = ts.client.register(payment.getPackedTransaction(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);


        ts.shutdown();
    }




    public ItemResult registerWithMinimumKeys(Contract contract, Collection<PrivateKey> signatures, TestSpace ts, int payingParcelAmount) throws Exception {
        for(PrivateKey key : signatures) {
            HashSet<PrivateKey> currentKeys = new HashSet<>(signatures);
            currentKeys.remove(key);
            contract.getKeysToSignWith().clear();
            contract.getKeysToSignWith().addAll(currentKeys);
            contract.seal();
            Contract u = getApprovedUContract(ts);

            Parcel parcel;
            if(payingParcelAmount > 0) {
                parcel = ContractsService.createPayingParcel(contract.getTransactionPack(),u,1,payingParcelAmount,new HashSet<PrivateKey>(Do.listOf(ts.myKey)),false);
            } else {
                parcel = ContractsService.createParcel(contract,u,1,new HashSet<PrivateKey>(Do.listOf(ts.myKey)));
            }
            assertEquals(ts.client.registerParcelWithState(parcel.pack(),8000).state,ItemState.DECLINED);
            synchronized (ts.uContractLock) {
                ts.uContract = parcel.getPaymentContract();
            }
        }

        HashSet<PrivateKey> currentKeys = new HashSet<>(signatures);
        contract.getKeysToSignWith().clear();
        contract.getKeysToSignWith().addAll(currentKeys);
        contract.seal();
        Contract u = getApprovedUContract(ts);

        Parcel parcel;
        if(payingParcelAmount > 0) {
            parcel = ContractsService.createPayingParcel(contract.getTransactionPack(),u,10,payingParcelAmount,new HashSet<PrivateKey>(Do.listOf(ts.myKey)),false);
        } else {
            parcel = ContractsService.createParcel(contract,u,10, new HashSet<>(Do.listOf(ts.myKey)));
        }
        ItemResult ir = ts.client.registerParcelWithState(parcel.pack(), 8000);
        System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);
        synchronized (ts.uContractLock) {
            if(payingParcelAmount > 0) {
                ts.uContract = (Contract) parcel.getPayloadContract().getNewItems().stream().filter(c -> c.isU(ts.node.config.getUIssuerKeys(), ts.node.config.getUIssuerName())).findFirst().get();
            } else {
                ts.uContract = parcel.getPaymentContract();
            }
        }
        return ir;
    }


    public void inspectNew(Client client, Contract contract) {
        contract.getNewItems().forEach(c->{
            try {
                System.out.println("NEW:"+client.getState(c.getId()));
            } catch (ClientError clientError) {
                clientError.printStackTrace();
            }

            inspectNew(client, (Contract) c);
            inspectRevokes(client, (Contract) c);
        });
    }

    public void inspectRevokes(Client client, Contract contract) {
        contract.getRevokingItems().forEach(c->{
            try {
                System.out.println("REV:"+client.getState(c.getId()));
            } catch (ClientError clientError) {
                clientError.printStackTrace();
            }
        });
    }

    @Ignore
    @Test
    public void inspectContract() throws Exception {
        Contract contract = Contract.fromPackedTransaction(Do.read("."));
        contract.check();
        Client client = new Client("mainnet",null,TestKeys.privateKey(0));

        System.out.println("THIS:"+client.getState(contract.getId()));
        inspectNew(client,contract);
        inspectRevokes(client,contract);


        System.out.println(contract.getErrors());
        System.out.println(contract.getProcessedCostU());
    }


    @Test
    public void errorsFromParcel() throws Exception {
        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Contract contract = new Contract(TestKeys.privateKey(1));
        contract.setIssuerKeys(TestKeys.privateKey(1).getPublicKey().getLongAddress());
        contract.setOwnerKeys(TestKeys.privateKey(1).getPublicKey().getLongAddress());
        contract.seal();
        assertEquals(ts.client.register(contract.getPackedTransaction(),8000).state,ItemState.APPROVED);

        contract = contract.createRevision(TestKeys.privateKey(2));
        contract.setOwnerKeys(TestKeys.privateKey(2));
        contract.seal();

        contract.check();
        Contract u = getApprovedUContract(ts);
        Parcel p = ContractsService.createParcel(contract,u,contract.getProcessedCostU(),ts.getUKeys());
        ItemResult ir = ts.client.registerParcelWithState(p.pack(), 8000);
        assertEquals(ir.state,ItemState.DECLINED);
        assertEquals(ir.errors.size(),contract.getErrors().size());


    }


    @Test
    public void testCompound() throws Exception {
        Contract referencedItem = new Contract(TestKeys.privateKey(1));
        referencedItem.seal();

        Contract contract = new Contract(TestKeys.privateKey(2));
        contract.seal();
        contract.getTransactionPack().addReferencedItem(referencedItem);

        Contract contract2 = new Contract(TestKeys.privateKey(2));
        contract2.seal();
        contract2.getTransactionPack().addReferencedItem(referencedItem);


        Compound compound = new Compound();

        compound.addContract("main",contract,Binder.of("foo","bar"));
        compound.addContract("second",contract2,Binder.of("key","value"));



        compound = new Compound(compound.getCompoundContract().getPackedTransaction());

        System.out.println(compound.getTags());
        System.out.println(compound.getData("main"));
        Contract main = compound.getContract("main");
        assertEquals(main.getId(),contract.getId());
        assertEquals(compound.getContract("second").getId(),contract2.getId());

        assertEquals(main.getTransactionPack().getReferencedItems().keySet(),contract.getTransactionPack().getReferencedItems().keySet());

    }


}
