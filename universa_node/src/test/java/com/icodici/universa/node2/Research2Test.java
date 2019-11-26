package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import com.icodici.universa.node2.network.DatagramAdapter;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.FileTool;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Research2Test {

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

    public synchronized Parcel createParcelWithFreshU(Client client, Contract c, Collection<PrivateKey> keys) throws Exception {
        Set<PublicKey> ownerKeys = new HashSet();
        keys.stream().forEach(key->ownerKeys.add(key.getPublicKey()));
        Contract stepaU = InnerContractsService.createFreshU(100000000, ownerKeys);
        stepaU.check();
        //stepaU.setIsU(true);
        stepaU.traceErrors();

        PrivateKey clientPrivateKey = client.getSession().getPrivateKey();
        PrivateKey newPrivateKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));
        client.getSession().setPrivateKey(newPrivateKey);
        client.restart();

        Thread.sleep(8000);

        ItemResult itemResult = client.register(stepaU.getPackedTransaction(), 5000);
//        node.registerItem(stepaU);
//        ItemResult itemResult = node.waitItem(stepaU.getId(), 18000);

        client.getSession().setPrivateKey(clientPrivateKey);
        client.restart();

        Thread.sleep(8000);

        assertEquals(ItemState.APPROVED, itemResult.state);
        Set<PrivateKey> keySet = new HashSet<>();
        keySet.addAll(keys);
        return ContractsService.createParcel(c, stepaU, 150, keySet);
    }

    @After
    public void tearDown() throws Exception {
        LogPrinter.showDebug(false);
    }

    @Ignore
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

    @Ignore
    @Test
    public void localNetwork2() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        mm.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Main main = mm.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));

        //Client client = new Client(myKey, main.myInfo, null);

        final long CONTRACTS_PER_THREAD = 60;
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
                        if (itemResult.state == ItemState.APPROVED)
                            contractHashesMap.remove(id);
                        else
                            break;
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

    @Ignore
    @Test
    public void localNetwork3() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());
        PrivateKey myKey = new PrivateKey(Do.read("./src/test_contracts/keys/u_key.private.unikey"));

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
                    Parcel parcel = createParcelWithFreshU(client, testContract,Do.listOf(myKey));
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

    public static long idealConcurrentWork() {
        long s = 0l;
        for (int i = 0; i < 100000000; ++i)
            s += i;
        return s;
    }

    @Ignore
    @Test //
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

    @Ignore
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

    @Ignore
    @Test
    public void testIdealConcurrentWork() throws Exception {
        testSomeWork(() -> {
            for (int i = 0; i < 100; ++i)
                idealConcurrentWork();
        });
    }

    @Ignore
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

    @Ignore
    @Test
    public void testHashId() throws Exception {
        testSomeWork(() -> {
            byte[] randBytes = Do.randomBytes(1*1024*1024);
            for (int i = 0; i < 100; ++i)
                HashId.of(randBytes);
        });
    }

    @Test
    public void testFiles() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        String newFilename = FileTool.writeFileContentsWithRenaming(System.getProperty("java.io.tmpdir")+"/testFile_2.file", contract.getPackedTransaction());
        System.out.println("write done, new filename=" + newFilename);
    }


    @Ignore
    @Test
    public void researchParcel() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(0);

        Client client = new Client(myKey, main.myInfo, null);

        // wait for sanitation
        //Thread.sleep(9000);


        System.out.println("\n\n\n\n\n\n\n\n === start ===\n");

        mm.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Contract uContract = InnerContractsService.createFreshU(9000, new HashSet<>(Arrays.asList(myKey.getPublicKey())));
        uContract.seal();
        ItemResult itemResult = client.register(uContract.getPackedTransaction(), 5000);
        System.out.println("register uContract: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract u2 = uContract.createRevision(new HashSet<>(Arrays.asList(myKey)));
        u2.getStateData().set("transaction_units", "8100");
        u2.seal();
        itemResult = client.register(u2.getPackedTransaction(), 5000);
        System.out.println("register u2: " + itemResult);
        System.out.println("uContract state: " + client.getState(uContract.getId()));

        mm.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(false));

        Contract contractPayload = new Contract(myKey);
        contractPayload.seal();
        itemResult = client.register(uContract.getPackedTransaction(), 5000);
        System.out.println("register contractPayload (no parcel): " + itemResult);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(contractPayload, u2, 100, new HashSet<>(Arrays.asList(myKey)));
        itemResult = client.registerParcelWithState(parcel.pack(), 5000);
        System.out.println("register parcel: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        System.out.println("payment state: " + client.getState(parcel.getPaymentContract().getId()));
        System.out.println("payload state: " + client.getState(parcel.getPayloadContract().getId()));
        System.out.println("uContract state: " + client.getState(uContract.getId()));
        System.out.println("u2 state: " + client.getState(u2.getId()));



        System.out.println("\n === done ===\n\n\n\n\n\n\n\n\n");



        mm.forEach(x -> x.shutdown());
    }


    @Ignore
    @Test
    public void researchPaidOperation() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        PrivateKey myKey = TestKeys.privateKey(0);

        System.out.println("wait for sanitation...");
        Thread.sleep(9000);

        Client client = new Client(myKey, main.myInfo, null);
        System.out.println("\n\n\n\n\n\n\n\n === start ===\n");

        // create and register U contract
        mm.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(true));
        Contract uContract = InnerContractsService.createFreshU(9000, new HashSet<>(Arrays.asList(myKey.getPublicKey())));
        uContract.seal();
        ItemResult itemResult = client.register(uContract.getPackedTransaction(), 5000);
        System.out.println("register uContract: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        mm.forEach(m -> m.config.setIsFreeRegistrationsAllowedFromYaml(false));

        Contract payment = uContract.createRevision(myKey);
        payment.getStateData().set("transaction_units", 8900);
        payment.seal();

        Contract testPayload = new Contract(myKey);
        testPayload.seal();
        Parcel testParcel = new Parcel(testPayload.getTransactionPack(), payment.getTransactionPack());
        PaidOperation paidOperation = new PaidOperation(payment.getTransactionPack(), "debug_paid_operation", new Binder());
        //mm.forEach(m -> {m.node.verboseLevel = DatagramAdapter.VerboseLevel.BASE;});
        //mm.get(3).node.verboseLevel = DatagramAdapter.VerboseLevel.BASE;
        System.out.println("\nregister... paymentId="+paidOperation.getPaymentContract().getId() + ", operationId=" + paidOperation.getId());
        client.command("approvePaidOperation", "packedItem", paidOperation.pack());
        //client.registerParcel(testParcel.pack());

        System.out.println("sleep...");
        Thread.sleep(4000);
        itemResult = client.getState(payment.getId());
        System.out.println("payment state: " + itemResult);

        System.out.println("\n === done ===\n\n\n\n\n\n\n\n\n");
        mm.forEach(x -> x.shutdown());
    }

}
