package com.icodici.universa.node2;



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

import static org.junit.Assert.assertEquals;


@Ignore("test using with research purposes only, should be omitted")
public class ResearchTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";

    private static PrivateKey nodeClientKey = new PrivateKey(2048);
    private static Client nodeClient = null;



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

        //String nodeUrl = "http://node-1-pro.universa.io:8080";
        String nodeUrl = "http://localhost:8080";
        nodeClientKey = TestKeys.privateKey(0);
        //System.out.println("test key: " + nodeClientKey.getPublicKey().packToBase64String());
        nodeClient = new Client(nodeUrl, nodeClientKey, null, false);
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

        System.out.println("nodeClient.register(stepaCoins)...");
        //ItemResult itemResult = nodeClient.register(whiteContract.getLastSealedBinary(), 5000);
        ItemResult itemResult = nodeClient.register(whiteContract.getPackedTransaction(), 5000);
        System.out.println("nodeClient.register(stepaCoins)... done! itemResult: " + itemResult.state);

        itemResult = nodeClient.getState(whiteContract.getId());
        System.out.println("nodeClient.getState(stepaCoins): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
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
                m.config.setTransactionUnitsIssuerKeyData(Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 B9 C7 CB 1B BA 3C 30 80 D0 8B 29 54 95 61 41 39 9E C6 BB 15 56 78 B8 72 DC 97 58 9F 83 8E A0 B7 98 9E BB A9 1D 45 A1 6F 27 2F 61 E0 26 78 D4 9D A9 C2 2F 29 CB B6 F7 9F 97 60 F3 03 ED 5C 58 27 27 63 3B D3 32 B5 82 6A FB 54 EA 26 14 E9 17 B6 4C 5D 60 F7 49 FB E3 2F 26 52 16 04 A6 5E 6E 78 D1 78 85 4D CD 7B 71 EB 2B FE 31 39 E9 E0 24 4F 58 3A 1D AE 1B DA 41 CA 8C 42 2B 19 35 4B 11 2E 45 02 AD AA A2 55 45 33 39 A9 FD D1 F3 1F FA FE 54 4C 2E EE F1 75 C9 B4 1A 27 5C E9 C0 42 4D 08 AD 3E A2 88 99 A3 A2 9F 70 9E 93 A3 DF 1C 75 E0 19 AB 1F E0 82 4D FF 24 DA 5D B4 22 A0 3C A7 79 61 41 FD B7 02 5C F9 74 6F 2C FE 9A DD 36 44 98 A2 37 67 15 28 E9 81 AC 40 CE EF 05 AA 9E 36 8F 56 DA 97 10 E4 10 6A 32 46 16 D0 3B 6F EF 80 41 F3 CC DA 14 74 D1 BF 63 AC 28 E0 F1 04 69 63 F7"));
                m.config.getKeysWhiteList().add(m.config.getTransactionUnitsIssuerKey());
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