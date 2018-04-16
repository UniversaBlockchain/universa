package com.icodici.universa.node2;



import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import org.junit.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;


@Ignore("test using with research purposes only, should be omitted")
public class ResearchTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";

    private static PrivateKey nodeClientKey = new PrivateKey(2048);
    private static Client nodeClient = null;
    private static List<Client> clients = null;



    public ResearchTest() {
        System.out.println("ResearchTest()...");
    }




    @BeforeClass
    public static void beforeClass() throws Exception {
        createNodeClient();
    }



    @AfterClass
    public static void afterClass() throws Exception {

    }



    private static void createNodeClient() throws Exception {
//        List<Main> mm = new ArrayList<>();
//        for (int i = 0; i < 4; i++)
//            mm.add(createMain("node" + (i + 1), false));
//
//        Thread.sleep(2000);

        clients = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            int n = i+1;
            String nodeUrl = "http://node-"+n+"-pro.universa.io:8080";
            PrivateKey clientKey = TestKeys.privateKey(i);
            Client client = new Client(nodeUrl, clientKey, null, false);
            clients.add(client);
        }

        nodeClient = clients.get(0);
        Thread.sleep(100);
    }



//    private static void startProcessAsync(ProcessBuilder processBuilder, boolean printOutput) throws Exception {
//        Thread thread = new Thread(() -> {
//            try {
//                if (printOutput)
//                    System.out.println("\n");
//                System.out.println("startProcessAsync...");
//                Process process = processBuilder.start();
//                if (printOutput)
//                    printInputStream(process.getInputStream());
//                nodeStartProcessList.add(process);
//            } catch (Exception e) {
//                System.out.println("startProcessAsync exception: " + e.toString());
//            }
//        });
//        thread.start();
//        nodeStartThreadList.add(thread);
//        if (printOutput)
//            Thread.sleep(1000);
//        else
//            Thread.sleep(100);
//    }



//    private static void printInputStream(InputStream inputStream) throws Exception {
//        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//        String line = "";
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);
//        }
//    }




    @Before
    public void setUp() throws Exception {
    }



    @Test
    public void nodeVersion() throws Exception {
        System.out.println("nodeClient.getNodeNumber(): " + nodeClient.getNodeNumber());
        System.out.println("nodeClient.getSession(): " + nodeClient.getSession());
        System.out.println("nodeClient.ping(): " + nodeClient.ping());
        System.out.println("nodeClient.getState(random): " + nodeClient.getState(HashId.createRandom()));
        System.out.println("nodeClient.getNodes().size(): " + nodeClient.getNodes().size());
        System.out.println("nodeClient.getVersion(): " + nodeClient.getVersion());
        System.out.println("nodeClient.getPositiveConsensus(): " + nodeClient.getPositiveConsensus());
        System.out.println("nodeClient.getUrl(): " + nodeClient.getUrl());
//        PublicKey publicKey = new PublicKey(Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 C5 24 96 7D 75 B6 D3 01 AC 46 7F 64 85 56 43 B6 F7 02 B5 4A 8F FE C7 0D DE 85 4F 53 7A F4 D7 9D 85 BB AD A9 7F 1F 4C 8D CD 5C 99 09 D1 61 29 1E 67 35 80 E7 44 58 41 35 37 16 55 C2 E6 22 0D EF 0F 8B 9B A4 C6 3D 0C 56 7B EB 98 18 C8 0A 2C 26 C0 9B 23 17 3D 6B A9 BF 37 81 E5 21 0C B7 29 50 E6 69 75 DA 2C 05 42 46 A6 A8 E8 85 13 62 96 31 8C FF 50 68 56 F3 BF C4 2C F7 24 9A 9A 1A 9D 95 1A F0 E1 82 00 25 1F 14 60 0B 01 95 74 1B EA D0 FF CC 62 5B 78 64 18 79 8E 14 FD 24 7A 36 5A 09 91 8F 3B F5 C6 55 AC BE DA AD 15 D9 CC 3A 08 76 AB F8 3F 45 F4 5A 26 5D 80 38 6C 02 27 95 8D F3 38 B1 DD 1B C7 5D 51 3C E1 1D 05 8E 2A 6C E8 17 D7 88 5B AE D4 F6 B7 7D A8 84 74 E1 4F 65 B3 DC 06 2D 07 21 AA 51 BF 93 11 C7 7D 1E 09 B3 CE A6 C1 83 60 50 A5 B8 F5 F4 11 A6 98 A0 F9 2B 2B 8D").getData());
//        PublicKey publicKey = new PublicKey(Bytes.fromBase64("HggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjQ==").getData());
//        System.out.println("transactionUnitsIssuerKey address: " + publicKey.getShortAddress());
//        System.out.println("transactionUnitsIssuerKey base64: " + publicKey.packToBase64String());
    }



    @Test
    public void registerSimpleContract() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();

        System.out.println("nodeClient.register(stepaCoins)...");
        //ItemResult itemResult = nodeClient.register(stepaCoins.getLastSealedBinary(), 5000);
        ItemResult itemResult = nodeClient.register(stepaCoins.getPackedTransaction(), 5000);
        System.out.println("nodeClient.register(stepaCoins)... done! itemResult: " + itemResult.state);

        itemResult = nodeClient.getState(stepaCoins.getId());
        System.out.println("nodeClient.getState(stepaCoins): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    @Test
    public void registerSimpleContractWhite() throws Exception {
//        Contract whiteContract = new Contract();
//        whiteContract.setIssuerKeys(TestKeys.privateKey(0));
//        whiteContract.setOwnerKeys(TestKeys.privateKey(0));
//        whiteContract.setCreatorKeys(TestKeys.privateKey(0));
//        whiteContract.addSignerKey(TestKeys.privateKey(0));
//        whiteContract.seal();

        Contract whiteContract = new Contract(TestKeys.privateKey(0));
        whiteContract.seal();

        System.out.println("nodeClient.register(whiteContract)...");
        //ItemResult itemResult = nodeClient.register(whiteContract.getLastSealedBinary(), 5000);
        ItemResult itemResult = nodeClient.register(whiteContract.getPackedTransaction(), 5000);
        System.out.println("nodeClient.register(whiteContract)... done! itemResult: " + itemResult.state);

        itemResult = nodeClient.getState(whiteContract.getId());
        System.out.println("nodeClient.getState(whiteContract): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }


    interface RunnablePrm {
        void run(int threadIndex);
    }

    @Test
    public void registerManySimpleContractsWhite() throws Exception {
        int CONTRACTS_PER_THREAD = 1000;
        int THREADS_COUNT = 10; // max 10
        AtomicLong totalCounter = new AtomicLong(0);
        RunnablePrm r = (threadIndex) -> {
            try {
                Client cln = clients.get(threadIndex);
                for (int i = 0; i < CONTRACTS_PER_THREAD; ++i) {
                    Contract whiteContract = new Contract(TestKeys.privateKey(threadIndex));
                    whiteContract.seal();
                    ItemResult itemResult = cln.register(whiteContract.getPackedTransaction(), 15000);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                    totalCounter.incrementAndGet();
                }
            } catch (Exception e) {
                System.out.println("error: " + e.toString());
            }
        };
        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < THREADS_COUNT; ++i) {
            final int threadIndex = i;
            Thread t = new Thread(() -> r.run(threadIndex));
            t.start();
            threadList.add(t);
        }
        Thread heartbeat = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(1000);
                    System.out.println("totalCounter: " + totalCounter.get());
                }
            } catch (Exception e) {
                System.out.println("error: " + e.toString());
            }
        });
        heartbeat.start();
        for (Thread t : threadList)
            t.join();
        heartbeat.interrupt();
    }



    @Test
    public void registerSimpleContract_getStateOnly() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        Contract stepaCoins = Contract.fromDslFile(ROOT_PATH + "stepaCoins.yml");
        stepaCoins.addSignerKey(stepaPrivateKeys.iterator().next());
        stepaCoins.seal();

        ItemResult itemResult = nodeClient.getState(stepaCoins.getId());
        System.out.println("nodeClient.getState(stepaCoins): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    static Main createMain(String name, boolean nolog) throws InterruptedException {
        return createMain(name,"",nolog);
    }

    static Main createMain(String name, String postfix, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2" + postfix + "/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                //m.config.getKeysWhiteList().add(m.config.getTransactionUnitsIssuerKey());
                m.config.getKeysWhiteList().add(new PublicKey(Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 CC 3F CA 82 7D CD 9F 61 7F 9E 16 4E 56 CA 5A EC B1 30 88 76 27 AF A3 C6 20 3E 3E EA 2B 50 71 A8 39 79 8E A5 46 0A 60 FC C1 8C 4F 42 77 4B 22 91 10 5A 34 BE B1 34 AB C3 87 39 EF 29 A8 11 2F FA 6D B8 56 8D DD 45 3D 6D 4C E4 A8 58 FA 46 73 CC 57 62 3F 58 D2 37 9E 33 2C 55 CF 1D AE BE D5 CC DD 21 F3 4B B6 1A 3F 86 35 CA 18 B5 2E 75 6A D4 CA 50 F5 C9 6C F4 A2 FE 7F C0 09 0A EA 99 F2 67 82 73 66 71 A3 F0 A0 12 E7 B6 E2 FE F3 F9 4B C1 F5 EA BC 8E 20 89 33 70 56 EA 10 CF 5B 86 F1 62 9F 67 6D 98 9B D8 0B 8C AB 35 4A FC 33 F6 D5 1E 68 55 DC 5F 59 82 9D D3 EF 44 ED 57 C0 93 EF 89 D7 F2 6D 51 B3 B8 43 5F 39 89 CD 90 12 F8 60 A9 89 F0 E0 92 53 4F 17 A0 96 A5 E4 C7 A1 9E E6 26 84 27 E1 AA 8B 09 D0 FF E2 B5 86 E5 F7 DE F3 11 32 F0 B4 05 BA 70 B5 3A EF 28 70 8E 29 AF A1 C1").getData()));
                m.waitReady();
                mm.add(m);
            } catch (Exception e) {
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


}