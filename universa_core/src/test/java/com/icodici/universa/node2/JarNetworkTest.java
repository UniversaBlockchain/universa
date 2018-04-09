package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Client;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class JarNetworkTest {

    private static Client whiteClient = null;



    @BeforeClass
    public static void beforeClass() throws Exception {
        String nodeUrl = "http://node-1-pro.universa.io:8080";
        PrivateKey clientKey = TestKeys.privateKey(0);
        whiteClient = new Client(nodeUrl, clientKey, null, false);
    }



    @Test
    public void registerSimpleContractWhite() throws Exception {
        Contract whiteContract = new Contract(TestKeys.privateKey(0));
        whiteContract.seal();

        System.out.println("whiteClient.register(whiteContract)...");
        ItemResult itemResult = whiteClient.register(whiteContract.getPackedTransaction(), 5000);
        System.out.println("whiteClient.register(whiteContract)... done! itemResult: " + itemResult.state);

        itemResult = whiteClient.getState(whiteContract.getId());
        System.out.println("whiteClient.getState(whiteContract): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    @Test
    public void registerManySimpleContractsWhite() throws Exception {
        int CONTRACTS_PER_THREAD = 50;
        int THREADS_COUNT = 4;
        AtomicLong totalCounter = new AtomicLong(0);
        Runnable r = () -> {
            try {
                Client cln = createWhiteClient();
                int nodeNumber = cln.getNodeNumber();
                System.out.println("nodeNumber: " + nodeNumber);
                for (int i = 0; i < CONTRACTS_PER_THREAD; ++i) {
                    Contract whiteContract = new Contract(TestKeys.privateKey(nodeNumber-1));
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
            Thread t = new Thread(r);
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
            }
        });
        heartbeat.start();
        for (Thread t : threadList)
            t.join();
        heartbeat.interrupt();
    }



    private Client createWhiteClient() {
        try {
            int nodeNumber = ThreadLocalRandom.current().nextInt(1, 11);
            String nodeUrl = "http://node-" + nodeNumber + "-pro.universa.io:8080";
            PrivateKey clientKey = TestKeys.privateKey(nodeNumber-1);
            return new Client(nodeUrl, clientKey, null, false);
        } catch (Exception e) {
            System.out.println("createWhiteClient exception: " + e.toString());
            return null;
        }
    }

}
