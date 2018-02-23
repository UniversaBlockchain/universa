/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTest;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.BasicHttpClient;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Ignore("start it manually")
public class MainTest {

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

        main.cache.put(c);
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

        assertEquals("[http://localhost:8080, http://localhost:6002, http://localhost:6004]", pubUrls);

        main.shutdown();
        main.logger.stopInterceptingStdOut();
        ;
        main.logger.getCopy().forEach(x -> System.out.println(x));
    }

    Main createMain(String name, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
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

    @Test
    public void localNetwork() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mm.add(createMain("node" + (i + 1), false));
        }

        Main main = mm.get(0);
//        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
//        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = TestKeys.privateKey(3);

//        assertEquals(main.cache, main.node.getCache());
//        ItemCache c1 = main.cache;
//        ItemCache c2 = main.node.getCache();

//        Client client = new Client(myKey, main.myInfo, null);


        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = false;
//        assertTrue(singleContract.isOk());

//        ItemResult r = client.getState(singleContract.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(r);


        contractsForThreads = new ArrayList<>();
        for (int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);

//            ItemResult r = client.getState(contract.getId());
//            assertEquals(ItemState.UNDEFINED, r.state);
//            System.out.println(r);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register


        for (int i = 0; i < N; i++) {

            if (createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for (int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M - 1));

            ts1 = new Date().getTime();

            for (Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    Client client = null;
                    try {
                        synchronized (this) {
                            client = new Client(myKey, main.myInfo, null);
                        }
                        long t = System.nanoTime();
                        ItemResult rr = null;
                        rr = client.register(c.getPackedTransaction(), 15000);
                        System.out.println("multi thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));

                    } catch (ClientError clientError) {
                        clientError.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                ItemResult rr = null;
                try {
                    Client client = null;
                    client = new Client(myKey, main.myInfo, null);
                    rr = client.register(finalSingleContract.getPackedTransaction(), 15000);
                    System.out.println("single thread: " + rr + " time: " + ((System.nanoTime() - t) * 1e-9));
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);

        mm.forEach(x -> x.shutdown());
    }

    @Test
    @Ignore("This test nust be started manually")
    public void checkRealNetwork() throws Exception {

        int numThraeds = 8;
        List<PrivateKey> keys = new ArrayList<>();
        for (int j = 0; j < numThraeds; j++) {
            PrivateKey key = new PrivateKey(Do.read("./src/test_contracts/keys/" + j + ".private.unikey"));
            keys.add(key);
        }


        List<Contract> contractsForThreads = new ArrayList<>();
        for (int j = 0; j < numThraeds; j++) {
            PrivateKey key = keys.get(0);
            Contract contract = new Contract(key);

            for (int k = 0; k < 500; k++) {
                Contract nc = new Contract(key);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            contractsForThreads.add(contract);
        }

        List<Thread> threadList = new ArrayList<>();
        long t1 = new Date().getTime();
        for (int i = 0; i < numThraeds; i++) {
            final int ii = i;
            Thread thread = new Thread(() -> {

                PrivateKey clientKey = keys.get(ii);
                Client client = null;
                try {
                    client = new Client("http://node-1-sel1.universa.io:8080", clientKey, null);

                    Contract c = contractsForThreads.get(ii);
                    ItemResult r = client.register(c.getPackedTransaction());

                    while (true) {
                        r = client.getState(c.getId());
                        System.out.println("-->? " + r);
                        Thread.currentThread().sleep(50);
                        if (!r.state.isPending())
                            break;
                    }
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            });
            thread.setName("thread register: " + i);
            threadList.add(thread);
            thread.start();
        }

        for (Thread thread : threadList)
            thread.join();

        long t2 = new Date().getTime();
        long multiTime = t2 - t1;
        System.out.println("time: " + multiTime + "ms");


//        r = client.getState(c.getId());
//        assertEquals(ItemState.UNDEFINED, r.state);
//        System.out.println(":: " + r);
//
//        LogPrinter.showDebug(true);
////        r = client.register(c.getLastSealedBinary());
//        System.out.println(r);
//
//        Client client = new Client(myKey, );
    }


    @Test
    public void localNetwork2() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 3; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = TestKeys.privateKey(3);

        //Client client = new Client(myKey, main.myInfo, null);

        final long CONTRACTS_PER_THREAD = 100;
        final long THREADS_COUNT = 4;

        class TestRunnable implements Runnable {

            public int threadNum = 0;
            List<Contract> contractList = new ArrayList<>();
            Map<HashId, Contract> contractHashesMap = new ConcurrentHashMap<>();
            Client client = null;

            public void prepareClient() {
                try {
                    client = new Client(myKey, main.myInfo, null);
                } catch (Exception e) {
                    System.out.println("prepareClient exception: " + e.toString());
                }
            }

            public void prepareContracts() throws Exception {
                contractList = new ArrayList<>();
                for (int iContract = 0; iContract < CONTRACTS_PER_THREAD; ++iContract) {
                    Contract testContract = new Contract(myKey);
                    for (int i = 0; i < 10; i++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        testContract.addNewItems(nc);
                    }
                    testContract.seal();
                    assertTrue(testContract.isOk());
                    contractList.add(testContract);
                    contractHashesMap.put(testContract.getId(), testContract);
                }
            }

            private void sendContractsToRegister() throws Exception {
                for (int i = 0; i < contractList.size(); ++i) {
                    Contract contract = contractList.get(i);
                    client.register(contract.getPackedTransaction());
                }
            }

            private void waitForContracts() throws Exception {
                while (contractHashesMap.size() > 0) {
                    Thread.currentThread().sleep(300);
                    for (HashId id : contractHashesMap.keySet()) {
                        ItemResult itemResult = client.getState(id);
                        if (!itemResult.state.isPending())
                            contractHashesMap.remove(id);
                    }
                }
            }

            @Override
            public void run() {
                try {
                    sendContractsToRegister();
                    waitForContracts();
                } catch (Exception e) {
                    System.out.println("runnable exception: " + e.toString());
                }
            }
        }

        System.out.println("singlethread test prepare...");
        TestRunnable runnableSingle = new TestRunnable();
        Thread threadSingle = new Thread(() -> {
            runnableSingle.threadNum = 0;
            runnableSingle.run();
        });
        runnableSingle.prepareClient();
        runnableSingle.prepareContracts();
        System.out.println("singlethread test start...");
        long t1 = new Date().getTime();
        threadSingle.start();
        threadSingle.join();
        long t2 = new Date().getTime();
        long dt = t2 - t1;
        long singleThreadTime = dt;
        System.out.println("singlethread test done!");

        System.out.println("multithread test prepare...");
        List<Thread> threadsList = new ArrayList<>();
        List<Thread> threadsPrepareList = new ArrayList<>();
        List<TestRunnable> runnableList = new ArrayList<>();
        for (int iThread = 0; iThread < THREADS_COUNT; ++iThread) {
            TestRunnable runnableMultithread = new TestRunnable();
            final int threadNum = iThread + 1;
            Thread threadMultiThread = new Thread(() -> {
                runnableMultithread.threadNum = threadNum;
                runnableMultithread.run();
            });
            Thread threadPrepareMultiThread = new Thread(() -> {
                try {
                    runnableMultithread.prepareContracts();
                } catch (Exception e) {
                    System.out.println("prepare exception: " + e.toString());
                }
            });
            runnableMultithread.prepareClient();
            threadsList.add(threadMultiThread);
            threadsPrepareList.add(threadPrepareMultiThread);
            runnableList.add(runnableMultithread);
        }
        for (Thread thread : threadsPrepareList)
            thread.start();
        for (Thread thread : threadsPrepareList)
            thread.join();
        Thread.sleep(500);
        System.out.println("multithread test start...");
        t1 = new Date().getTime();
        for (Thread thread : threadsList)
            thread.start();
        for (Thread thread : threadsList)
            thread.join();
        t2 = new Date().getTime();
        dt = t2 - t1;
        long multiThreadTime = dt;
        System.out.println("multithread test done!");

        Double tpsSingleThread = (double) CONTRACTS_PER_THREAD / (double) singleThreadTime * 1000.0;
        Double tpsMultiThread = (double) CONTRACTS_PER_THREAD * (double) THREADS_COUNT / (double) multiThreadTime * 1000.0;
        Double boostRate = tpsMultiThread / tpsSingleThread;

        System.out.println("\n === total ===");
        System.out.println("singleThread: " + (CONTRACTS_PER_THREAD) + " for " + singleThreadTime + "ms, tps=" + String.format("%.2f", tpsSingleThread));
        System.out.println("multiThread(N=" + THREADS_COUNT + "): " + (CONTRACTS_PER_THREAD * THREADS_COUNT) + " for " + multiThreadTime + "ms, tps=" + String.format("%.2f", tpsMultiThread));
        System.out.println("boostRate: " + String.format("%.2f", boostRate));
        System.out.println("\n");

        mm.forEach(x -> x.shutdown());
    }


    @Test
    public void localNetwork3() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = TestKeys.privateKey(3);

        Set<PrivateKey> fromPrivateKeys = new HashSet<>();
        fromPrivateKeys.add(myKey);

        //Client client = new Client(myKey, main.myInfo, null);

        final long CONTRACTS_PER_THREAD = 10;
        final long THREADS_COUNT = 4;

        LogPrinter.showDebug(true);

        class TestRunnable implements Runnable {

            public int threadNum = 0;
            List<Parcel> contractList = new ArrayList<>();
            Map<HashId, Parcel> contractHashesMap = new ConcurrentHashMap<>();
            Client client = null;

            public void prepareClient() {
                try {
                    client = new Client(myKey, main.myInfo, null);
                } catch (Exception e) {
                    System.out.println("prepareClient exception: " + e.toString());
                }
            }

            public void prepareContracts() throws Exception {
                contractList = new ArrayList<>();
                for (int iContract = 0; iContract < CONTRACTS_PER_THREAD; ++iContract) {
                    Contract testContract = new Contract(myKey);
                    for (int i = 0; i < 10; i++) {
                        Contract nc = new Contract(myKey);
//                        nc.seal();
                        testContract.addNewItems(nc);
                    }
                    testContract.seal();
                    assertTrue(testContract.isOk());
                    Parcel parcel = createParcelWithFreshTU(client, testContract);
                    contractList.add(parcel);
                    contractHashesMap.put(parcel.getId(), parcel);
                }
            }

            private void sendContractsToRegister() throws Exception {
                for (int i = 0; i < contractList.size(); ++i) {
                    Parcel parcel = contractList.get(i);
                    client.registerParcel(parcel.pack());
                }
            }

            private void waitForContracts() throws Exception {
                while (contractHashesMap.size() > 0) {
                    Thread.currentThread().sleep(100);
                    for (Parcel p : contractHashesMap.values()) {
                        ItemResult itemResult = client.getState(p.getPayloadContract().getId());
                        if (!itemResult.state.isPending())
                            contractHashesMap.remove(p.getId());
                    }
                }
            }

            @Override
            public void run() {
                try {
                    sendContractsToRegister();
                    waitForContracts();
                } catch (Exception e) {
                    System.out.println("runnable exception: " + e.toString());
                }
            }
        }

        System.out.println("singlethread test prepare...");
        TestRunnable runnableSingle = new TestRunnable();
        Thread threadSingle = new Thread(() -> {
            runnableSingle.threadNum = 0;
            runnableSingle.run();
        });
        runnableSingle.prepareClient();
        runnableSingle.prepareContracts();
        System.out.println("singlethread test start...");
        long t1 = new Date().getTime();
        threadSingle.start();
        threadSingle.join();
        long t2 = new Date().getTime();
        long dt = t2 - t1;
        long singleThreadTime = dt;
        System.out.println("singlethread test done!");

        System.out.println("multithread test prepare...");
        List<Thread> threadsList = new ArrayList<>();
        List<Thread> threadsPrepareList = new ArrayList<>();
        List<TestRunnable> runnableList = new ArrayList<>();
        for (int iThread = 0; iThread < THREADS_COUNT; ++iThread) {
            TestRunnable runnableMultithread = new TestRunnable();
            final int threadNum = iThread + 1;
            Thread threadMultiThread = new Thread(() -> {
                runnableMultithread.threadNum = threadNum;
                runnableMultithread.run();
            });
            Thread threadPrepareMultiThread = new Thread(() -> {
                try {
                    runnableMultithread.prepareContracts();
                } catch (Exception e) {
                    System.out.println("prepare exception: " + e.toString());
                }
            });
            runnableMultithread.prepareClient();
            threadsList.add(threadMultiThread);
            threadsPrepareList.add(threadPrepareMultiThread);
            runnableList.add(runnableMultithread);
        }
        for (Thread thread : threadsPrepareList)
            thread.start();
        for (Thread thread : threadsPrepareList)
            thread.join();
        Thread.sleep(500);
        System.out.println("multithread test start...");
        t1 = new Date().getTime();
        for (Thread thread : threadsList)
            thread.start();
        for (Thread thread : threadsList)
            thread.join();
        t2 = new Date().getTime();
        dt = t2 - t1;
        long multiThreadTime = dt;
        System.out.println("multithread test done!");

        Double tpsSingleThread = (double) CONTRACTS_PER_THREAD / (double) singleThreadTime * 1000.0;
        Double tpsMultiThread = (double) CONTRACTS_PER_THREAD * (double) THREADS_COUNT / (double) multiThreadTime * 1000.0;
        Double boostRate = tpsMultiThread / tpsSingleThread;

        System.out.println("\n === total ===");
        System.out.println("singleThread: " + (CONTRACTS_PER_THREAD) + " for " + singleThreadTime + "ms, tps=" + String.format("%.2f", tpsSingleThread));
        System.out.println("multiThread(N=" + THREADS_COUNT + "): " + (CONTRACTS_PER_THREAD * THREADS_COUNT) + " for " + multiThreadTime + "ms, tps=" + String.format("%.2f", tpsMultiThread));
        System.out.println("boostRate: " + String.format("%.2f", boostRate));
        System.out.println("\n");

        mm.forEach(x -> x.shutdown());
    }


    public synchronized Parcel createParcelWithFreshTU(Client client, Contract c) throws Exception {

        PrivateKey stepaPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/tu_key.private.unikey"));
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(stepaPrivateKey);
        Contract stepaTU = Contract.fromDslFile("./src/test_contracts/StepaTU.yml");
        stepaTU.addSignerKey(manufacturePrivateKey);
        stepaTU.seal();
        ItemResult itemResult = client.register(stepaTU.getPackedTransaction(), 5000);
//        node.registerItem(stepaTU);
//        ItemResult itemResult = node.waitItem(stepaTU.getId(), 18000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        return ContractsService.createParcel(c, stepaTU, 150, keys);
    }


    public static long idealConcurrentWork() {
        long s = 0l;
        for (int i = 0; i < 100000000; ++i)
            s += i;
        return s;
    }


    public void testSomeWork(Runnable someWork) throws Exception {
        final long THREADS_COUNT_MAX = Runtime.getRuntime().availableProcessors();

        System.out.println("warm up...");
        Thread thread0 = new Thread(someWork);
        thread0.start();
        thread0.join();

        long t1 = new Date().getTime();
        Thread thread1 = new Thread(someWork);
        thread1.start();
        thread1.join();
        long t2 = new Date().getTime();
        long singleTime = t2 - t1;
        System.out.println("single: " + singleTime + "ms");

        for (int THREADS_COUNT = 2; THREADS_COUNT <= THREADS_COUNT_MAX; ++THREADS_COUNT) {
            t1 = new Date().getTime();
            List<Thread> threadList = new ArrayList<>();
            for (int n = 0; n < THREADS_COUNT; ++n) {
                Thread thread = new Thread(someWork);
                threadList.add(thread);
                thread.start();
            }
            for (Thread thread : threadList)
                thread.join();
            t2 = new Date().getTime();
            long multiTime = t2 - t1;
            double boostRate = (double) THREADS_COUNT / (double) multiTime * (double) singleTime;
            System.out.println("multi(N=" + THREADS_COUNT + "): " + multiTime + "ms,   boostRate: x" + String.format("%.2f", boostRate));
        }
    }


    @Test
    public void testBossPack() throws Exception {
        byte[] br = new byte[200];
        new Random().nextBytes(br);
        Runnable r = () -> {
            try {
                Boss.Writer w = new Boss.Writer();
                for (int i = 0; i < 1000000; ++i) {
                    w.writeObject(br);
                    br[0]++;
                }
                w.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
        testSomeWork(() -> {
            r.run();
        });
        Thread.sleep(1500);
        testSomeWork(() -> {
            r.run();
        });
    }


    @Test
    public void testContractCheck() throws Exception {
        PrivateKey key = TestKeys.privateKey(3);
        testSomeWork(() ->  {
            try {
                Contract c = new Contract(key);
                for (int k = 0; k < 500; k++) {
                    Contract nc = new Contract(key);
                    nc.seal();
                    c.addNewItems(nc);
                }
                c.seal();
                c.check();
            } catch (Quantiser.QuantiserException e) {
                e.printStackTrace();
            }
        });
    }




    @Test
    public void testLedger() throws Exception {

        Properties properties = new Properties();

        File file = new File("./src/test_config_2/" + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        final PostgresLedger ledger_s = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        StateRecord record = ledger_s.findOrCreate(HashId.createRandom());

        System.out.println("--- find or create ---");
        testSomeWork(() ->  {
            for (int i = 0; i < 10000; ++i)
                ledger_s.findOrCreate(HashId.createRandom());
        });

        System.out.println("--- lock to create ---");
        testSomeWork(() ->  {
            for (int i = 0; i < 10000; ++i)
                record.createOutputLockRecord(HashId.createRandom());
        });

        System.out.println("--- lock to revoke ---");
        testSomeWork(() ->  {
            for (int i = 0; i < 10000; ++i)
                record.lockToRevoke(HashId.createRandom());
        });
    }



    @Test
    public void testIdealConcurrentWork() throws Exception {
        testSomeWork(() -> {
            for (int i = 0; i < 100; ++i)
                idealConcurrentWork();
        });
    }


    @Test
    public void testNewContractSeal() throws Exception {
        testSomeWork(() -> {
            for (int i = 0; i < 10; ++i) {
                PrivateKey myKey = null;
                try {
                    myKey = TestKeys.privateKey(3);
                } catch (Exception e) {
                }
                Contract testContract = new Contract(myKey);
                for (int iContract = 0; iContract < 10; ++iContract) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    testContract.addNewItems(nc);
                }
                testContract.seal();
            }
        });
    }


    @Test
    public void testHashId() throws Exception {
        testSomeWork(() -> {
            byte[] randBytes = Do.randomBytes(1*1024*1024);
            for (int i = 0; i < 100; ++i)
                HashId.of(randBytes);
        });
    }



    @Test
    public void registerContract500_seal() throws Exception {
        TestSpace ts = prepareTestSpace();
        Contract contract = createContract500(ts.myKey);
        ItemResult itemResult = ts.client.register(contract.getLastSealedBinary(), 10000);
        assertEquals(ItemState.DECLINED, itemResult.state);
        int i = 0;
        for (Approvable sub : contract.getNewItems()) {
            ItemResult subItemResult = ts.client.getState(sub);
            System.out.println("" + (i++) + " - " + subItemResult.state);
            assertEquals(ItemState.UNDEFINED, subItemResult.state);
        }
    }



    @Test
    public void registerContract500_pack() throws Exception {
        TestSpace ts = prepareTestSpace();
        Contract contract = createContract500(ts.myKey);
        ItemResult itemResult = ts.client.register(contract.getPackedTransaction(), 30000);
        assertEquals(ItemState.APPROVED, itemResult.state);
        Thread.sleep(5000);
        int i = 0;
        for (Approvable sub : contract.getNewItems()) {
            ItemResult subItemResult = ts.client.getState(sub);
            System.out.println("" + (i++) + " - " + subItemResult.state);
            assertEquals(ItemState.APPROVED, subItemResult.state);
        }
    }



    private TestSpace prepareTestSpace() throws Exception {
        TestSpace testSpace = new TestSpace();
        testSpace.nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            testSpace.nodes.add(createMain("node" + (i + 1), false));
        testSpace.node = testSpace.nodes.get(0);
        assertEquals("http://localhost:8080", testSpace.node.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", testSpace.node.myInfo.publicUrlString());
        testSpace.myKey = TestKeys.privateKey(3);
        testSpace.client = new Client(testSpace.myKey, testSpace.node.myInfo, null);
        return testSpace;
    }



    private Contract createContract500(PrivateKey key) {
        Contract contract = new Contract(key);
        for (int i = 0; i < 500; ++i) {
            Contract sub = new Contract(key);
            sub.seal();
            contract.addNewItems(sub);
        }
        contract.seal();
        return contract;
    }



    private class TestSpace {
        public List<Main> nodes = null;
        public Main node = null;
        PrivateKey myKey = null;
        Client client = null;
    }

}