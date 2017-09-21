/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.NodeStarter;
import com.icodici.universa.node.TestItem;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Average;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import net.sergeych.utils.Base64;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

public class NetConfigTest {

    public static final int TEST_NODES = 10;

    //    @Test
    @Test
    public void kesyRoundtop() throws Exception {
        Files.list(Paths.get("src/test_config_2/tmp"))
                .filter(Files::isRegularFile)
                .forEach(fn -> {
                    if (!fn.toString().endsWith(".private.unikey"))
                        return;
                    PrivateKey prk = null;
                    try {
                        prk = PrivateKey.fromPath(fn);

                        for (int j = 0; j < 20; j++) {
                            byte[] plain = Boss.pack(
                                    Binder.fromKeysValues(
                                            "session_key", new SymmetricKey().pack(),
                                            "nonce", Do.randomBytes(32))
                            );
                            PublicKey puk = new PublicKey(prk.getPublicKey().pack());
                            byte[] encrypted = puk.encrypt(plain);
                            try {
                                byte[] decrypted = prk.decrypt(encrypted);
                                assertArrayEquals(plain, decrypted);
                            } catch (EncryptionError e) {
                                System.out.println("RSA decryption error in key " + fn);
                                System.out.println("Private key: " + Base64.encodeString(prk.pack()));
                                System.out.println("Public key, restored: " + Base64.encodeString(puk.pack()));
                                System.out.println("plaintext: " + Base64.encodeString(plain));
                                System.out.println("ciphertext: " + Base64.encodeString(encrypted));
                                fail();
                            }
                        }
//                        System.out.println("passed: "+fn+" : "+prk);
                    } catch (IOException e) {
                        fail("Failed to load " + fn);
                    }
                });
    }

    @Test
    public void createNode() throws Exception {
        NodeStarter.main(new String[]{"-c", "src/test_config_2", "-i", "node3", "-p", "15510", "--test"});
        HttpClient client = new HttpClient("test", "http://127.0.0.1:15510");
        HttpClient.Answer answer = client.request("network");
        assertTrue(answer.isOk());
        Binder n4 = answer.data.getBinderOrThrow("node4");
        assertEquals(2086, n4.getIntOrThrow("port"));
        assertEquals("127.0.0.1", n4.getStringOrThrow("ip"));
        NodeStarter.shutdown();
        Thread.currentThread().sleep(200);
    }


    @Test
    public void buildNetwork() throws Exception {
        try (TestV1Network tn = new TestV1Network("src/test_config_2", 18730)) {
            assertEquals(TEST_NODES, tn.get(0).getAllNodes().size());
            assertEquals(TEST_NODES, tn.get(1).getAllNodes().size());
        }
    }

    @Test
    public void runNetwork() throws Exception {

        Average a = new Average();
        try (TestV1Network tn = new TestV1Network("src/test_config_2", 18730)) {

            NetworkV1 n1 = tn.get(0);
            assertEquals(TEST_NODES, n1.getAllNodes().size());
            assertEquals(TEST_NODES, tn.get(1).getAllNodes().size());

            n1.setRequeryPause(Duration.ofMillis(1000));
            ArrayList<Long> times = new ArrayList<>();
            for (int n = 0; n < 5; n++) {
                TestItem item1 = new TestItem(true);
                long t = StopWatch.measure(() -> {
                    ItemResult itemResult = n1.getLocalNode().registerItemAndWait(item1);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                });
                // The first cycle is before JIT pass, ignoring it
                System.out.println(t);
                if (n > 1)
                    a.update(t);
            }
            System.out.println("Average transaction time: " + a);
            System.out.println("variation:                " + a.variation());
            assertThat(a.variation(), is(lessThan(0.3)));
        }
    }
}