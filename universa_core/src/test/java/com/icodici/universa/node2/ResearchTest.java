package com.icodici.universa.node2;



import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.Do;
import org.junit.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;


@Ignore("test using with research purposes only, should be omitted")
public class ResearchTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";

    private static List<Process> nodeStartProcessList = null;
    private static List<Thread> nodeStartThreadList = null;

    private static PrivateKey nodeClientKey = new PrivateKey(2048);
    private static Client nodeClient = null;



    public ResearchTest() {
        System.out.println("ResearchTest()...");
    }




    @BeforeClass
    public static void beforeClass() throws Exception {
        System.out.println("curdir: " + System.getProperty("user.dir"));
        String killCmd = "kill -9 `ps aux | grep java | grep universa_core_test.jar | awk '{print $2}'`";
        Process p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", killCmd});
        p.waitFor();
        startLocalNetwork();
        createNodeClient();
    }



    private static void createNodeClient() throws Exception {
        //String nodeUrl = "http://node-1-com.universa.io:8080";
        String nodeUrl = "http://localhost:8080";
        nodeClient = new Client(nodeUrl, nodeClientKey, null, false);
    }



    private static void reCreateNodeClient() throws Exception {
        //String nodeUrl = "http://node-1-com.universa.io:8080";
        String nodeUrl = "http://localhost:6002";
        nodeClient = new Client(nodeUrl, nodeClientKey, null, false);
    }



    private static void startLocalNetwork() throws Exception {
        System.out.println("startLocalNetwork...");
        nodeStartProcessList = new ArrayList<>();
        nodeStartThreadList = new ArrayList<>();
        for (int n = 1; n <= 3; ++n) {
            String curDir = System.getProperty("user.dir");
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar", curDir+"/../out/artifacts/universa_core_test_jar/universa_core_test.jar", "-c", curDir+"/../universa_core/src/test_node_config_v2/node"+n);
            startProcessAsync(processBuilder, false);
        }
        Thread.sleep(1000);
        System.out.println("startLocalNetwork... done!");
    }



    private static void startProcessAsync(ProcessBuilder processBuilder, boolean printOutput) throws Exception {
        Thread thread = new Thread(() -> {
            try {
                if (printOutput)
                    System.out.println("\n");
                System.out.println("startProcessAsync...");
                Process process = processBuilder.start();
                if (printOutput)
                    printInputStream(process.getInputStream());
                nodeStartProcessList.add(process);
            } catch (Exception e) {
                System.out.println("startProcessAsync exception: " + e.toString());
            }
        });
        thread.start();
        nodeStartThreadList.add(thread);
        if (printOutput)
            Thread.sleep(1000);
        else
            Thread.sleep(100);
    }



    private static void printInputStream(InputStream inputStream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = "";
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    }



    @AfterClass
    public static void afterClass() throws Exception {
        stopLocalNetwork();
    }



    private static void stopLocalNetwork() {
        System.out.println("stopLocalNetwork()...");
        for (Process process : nodeStartProcessList)
            process.destroy();
        for (Thread thread: nodeStartThreadList)
            thread.interrupt();
        // for kill from command line:
        // kill -9 `ps aux | grep java | grep universa_core_test.jar | awk '{print $2}'`
    }



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
        ItemResult itemResult = nodeClient.register(stepaCoins.getLastSealedBinary(), 5000);
        System.out.println("nodeClient.register(stepaCoins)... done! itemResult: " + itemResult.state);

        itemResult = nodeClient.getState(stepaCoins.getId());
        System.out.println("nodeClient.getState(stepaCoins): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    @Test
    public void registerSimpleContract_recreateNodeClient() throws Exception {
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
        ItemResult itemResult = nodeClient.register(stepaCoins.getLastSealedBinary(), 1000);
        System.out.println("nodeClient.register(stepaCoins)... done! itemResult: " + itemResult.state);

        reCreateNodeClient();

        itemResult = nodeClient.getState(stepaCoins.getId());
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



}
