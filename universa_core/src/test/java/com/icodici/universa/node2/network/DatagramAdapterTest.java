/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DatagramAdapterTest {
    @Test
    public void sendAndReceive() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);
        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(2),12, "test_node_12", "localhost", 16204, 16205, 16303);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(2), new SymmetricKey(), node3); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d3.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        byte[] payload1 = "test data set 1".getBytes();

        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor2.size());
        byte[] data = receviedFor2.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);

        // And test it for all interfaceces and big arrays of data

        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }

    @Test
    public void sendBigData() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

        d1.seTestMode(DatagramAdapter.TestModes.SHUFFLE_PACKETS);
        d2.seTestMode(DatagramAdapter.TestModes.SHUFFLE_PACKETS);

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        byte[] payload1 = Do.randomBytes(1024 * 10);

        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor2.size());
        byte[] data = receviedFor2.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);

        // And test it for all interfaceces and big arrays of data

        d1.shutdown();
        d2.shutdown();
    }


    @Test
    public void sendTrippleAndReceive() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);
        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(2),12, "test_node_12", "localhost", 16204, 16205, 16303);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(2), new SymmetricKey(), node3); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d3.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        byte[] payload1 = "test data set 1".getBytes();
        byte[] payload2 = "test data set 2222".getBytes();
        byte[] payload3 = "test data set 333333333333333".getBytes();

        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                if(receviedFor2.size() >= 3) {
                    waitStatusQueue.put("DONE");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);
        d1.send(node2, payload2);
        d1.send(node2, payload3);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(3, receviedFor2.size());
        byte[] data1 = receviedFor2.get(0);
        byte[] data2 = receviedFor2.get(1);
        byte[] data3 = receviedFor2.get(2);

        // receiver must s
        assertArrayEquals(payload1, data1);
        assertArrayEquals(payload2, data2);
        assertArrayEquals(payload3, data3);

        // And test it for all interfaceces and big arrays of data

        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }


//    @Test
//    public void testEtaCrypt() throws Exception {
//
//        SymmetricKey symmetricKey1 = new SymmetricKey();
//        byte[] payload = "test data set 1".getBytes();
//        byte[] crc32 = new Crc32().digest(payload);
//
//        for (int i = 0; i < 100000000; i++) {
//            byte[] encrypted = symmetricKey1.etaEncrypt(payload);
//            byte[] decrypted = symmetricKey1.etaDecrypt(encrypted);
//            byte[] crc32Decrypted = new Crc32().digest(decrypted);
//            if(!Arrays.equals(crc32, crc32Decrypted)) {
//                System.out.println("Eta error: " + i + " data: " + new String(payload) + " decrypted: " + new String(decrypted));
//            }
//        }
//    }


    @Test
    public void sendEachOtherAndReceive() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);
        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(2),12, "test_node_12", "localhost", 16204, 16205, 16303);

        SymmetricKey symmetricKey1 = new SymmetricKey();
        SymmetricKey symmetricKey2 = new SymmetricKey();
        SymmetricKey symmetricKey3 = new SymmetricKey();
        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), symmetricKey1, node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), symmetricKey2, node2); // create implemented class with node1
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(2), symmetricKey3, node3); // create implemented class with node1

        System.out.println("symmetricKey1 is " + symmetricKey1.hashCode());
        System.out.println("symmetricKey2 is " + symmetricKey2.hashCode());
        System.out.println("symmetricKey3 is " + symmetricKey3.hashCode());
//        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
//        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
//        d3.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        d1.addErrorsCallback(m -> {
            System.out.println(m);
            return m;});
        d2.addErrorsCallback(m -> {
            System.out.println(m);
            return m;});
        d3.addErrorsCallback(m -> {
            System.out.println(m);
            return m;});

//        d1.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);
//        d2.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);
//        d2.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);

        byte[] payload1 = "test data set 1".getBytes();
        byte[] payload2 = "test data set 2".getBytes();
        byte[] payload3 = "test data set 3".getBytes();

        int attempts = 100;
        int numSends = 100;

        ArrayList<byte[]> receviedFor1 = new ArrayList<>();
        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        ArrayList<byte[]> receviedFor3 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueueFor1 = new ArrayBlockingQueue<String>(1, true);
        BlockingQueue<String> waitStatusQueueFor2 = new ArrayBlockingQueue<String>(1, true);
        BlockingQueue<String> waitStatusQueueFor3 = new ArrayBlockingQueue<String>(1, true);

        AsyncEvent<Void> ae = new AsyncEvent<>();

        d1.receive(d-> {
            receviedFor1.add(d);
            if((receviedFor1.size() + receviedFor2.size() + receviedFor3.size()) == attempts * numSends)
                ae.fire();
        });
        d2.receive(d-> {
            receviedFor2.add(d);
            if((receviedFor1.size() + receviedFor2.size() + receviedFor3.size()) == attempts * numSends)
                ae.fire();
        });
        d3.receive(d-> {
            receviedFor3.add(d);
            if((receviedFor1.size() + receviedFor2.size() + receviedFor3.size()) == attempts * numSends)
                ae.fire();
        });

        for (int i = 0; i < attempts; i++) {
            System.out.println("Send part: " + i);

            // send from adapter d1, to d2 as it is connected with node2 credentials:
            for (int j = 0; j < numSends; j++) {
                int rnd1 = new Random().nextInt(3);

                int rnd2 = 0;
                int rnd3 = 0;
                while(rnd2 == rnd3) {
                    rnd2 = new Random().nextInt(3);
                    rnd3 = new Random().nextInt(3);
                }
                byte[] payload;
                DatagramAdapter sender;
                NodeInfo receiverNode;
                if(rnd1 == 0)
                    payload = payload1;
                else if(rnd1 == 1)
                    payload = payload2;
                else
                    payload = payload3;

                if(rnd2 == 0)
                    sender = d1;
                else if(rnd2 == 1)
                    sender = d2;
                else
                    sender = d3;

                if(rnd3 == 0)
                    receiverNode = node1;
                else if(rnd3 == 1)
                    receiverNode = node2;
                else
                    receiverNode = node3;

                sender.send(receiverNode, payload);
//                d1.send(node2, payload);
//                d2.send(node3, payload);
//                d3.send(node1, payload);
//                d1.send(node3, payload);
//                d2.send(node1, payload);
//                d3.send(node2, payload);
            }
            Thread.sleep(new Random().nextInt(20));
        }

        try {
            ae.await(60000);
        } catch (TimeoutException e) {
            System.out.println("time is up");
        }

        System.out.println("receviedFor1 got: " + (receviedFor1.size()));
        System.out.println("receviedFor2 got: " + (receviedFor2.size()));
        System.out.println("receviedFor3 got: " + (receviedFor3.size()));
        System.out.println("all got: " + (receviedFor1.size() + receviedFor2.size() + receviedFor3.size()));
//        while (waitStatusQueueFor1.size() < attempts * numSends){
//        }

//        while (!((waitStatusQueueFor2.take()).equals("DONE"))){
//            // wait until it is delivered
//        }
//        System.out.println("wait queue 2 DONE");
//
//        while (!((waitStatusQueueFor3.take()).equals("DONE"))){
//            // wait until it is delivered
//        }
//        System.out.println("wait queue 3 DONE");
//
        assertEquals(numSends * attempts, receviedFor1.size() + receviedFor2.size() + receviedFor3.size());
//        assertEquals(numSends * attempts, receviedFor2.size());
//        assertEquals(numSends * attempts, receviedFor3.size());
//            byte[] data1 = receviedFor2.get(0);
//            byte[] data2 = receviedFor2.get(1);
//            byte[] data3 = receviedFor2.get(2);
//
//            // receiver must s
//            assertArrayEquals(payload1, data1);
//            assertArrayEquals(payload2, data2);
//            assertArrayEquals(payload3, data3);

        // And test it for all interfaceces and big arrays of data

        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }


    @Test
    public void sendTrippleMultiTimesAndReceive() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);
        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(2),12, "test_node_12", "localhost", 16204, 16205, 16303);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(2), new SymmetricKey(), node3); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d3.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

//        d1.seTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
//        d2.seTestMode(DatagramAdapter.TestModes.LOST_PACKETS);

        byte[] payload1 = "test data set 1".getBytes();
        byte[] payload2 = "test data set 2222".getBytes();
        byte[] payload3 = "test data set 333333333333333".getBytes();

        int attempts = 100;
        int numSends = 5;

        for (int i = 0; i < attempts; i++) {
            System.out.println("Send part: " + i);

            ArrayList<byte[]> receviedFor2 = new ArrayList<>();
            BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

            d2.receive(d-> {
                receviedFor2.add(d);
                try {
                    if(receviedFor2.size() >= numSends) {
                        waitStatusQueue.put("DONE");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    System.out.println("DONE error");
                }
            });


            // send from adapter d1, to d2 as it is connected with node2 credentials:
            for (int j = 0; j < numSends; j++) {
                int rnd = new Random().nextInt(3);
                if(i == 0)
                    d1.send(node2, payload1);
                else if(i == 1)
                    d1.send(node2, payload2);
                else
                    d1.send(node2, payload3);
            }

            while (!((waitStatusQueue.take()).equals("DONE"))){
                // wait until it is delivered
            }

            assertEquals(numSends, receviedFor2.size());
//            byte[] data1 = receviedFor2.get(0);
//            byte[] data2 = receviedFor2.get(1);
//            byte[] data3 = receviedFor2.get(2);
//
//            // receiver must s
//            assertArrayEquals(payload1, data1);
//            assertArrayEquals(payload2, data2);
//            assertArrayEquals(payload3, data3);

            // And test it for all interfaceces and big arrays of data
        }

        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }


    @Test
    public void reconnect() throws Exception {
        // create pair of connected adapters
        // ensure data are circulating between them in both directions
        // delete one adapter (ensure the socket is closed)
        // reopent it
        // ensure connection is restored and data are transmitted

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        byte[] payload1 = "test data set 1".getBytes();
        byte[] payload2 = "test data set 2".getBytes();

        ArrayList<byte[]> receviedFor1 = new ArrayList<>();
        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor2.size());
        byte[] data = receviedFor2.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);

        // send data back

        d1.receive(d-> {
            receviedFor1.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d2, to d1
        d2.send(node1, payload2);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor1.size());
        data = receviedFor1.get(0);

        // receiver must s
        assertArrayEquals(payload2, data);

        // test with close and reopen socket
        System.out.println("-------");
        System.out.println("close socket and reopen with new adapter");
        System.out.println("-------");

        d2.shutdown();

        // create new adapter with d2 credentials
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        ArrayList<byte[]> receviedFor3 = new ArrayList<>();

        d3.receive(d-> {
            receviedFor3.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d3
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor3.size());
        data = receviedFor3.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);


        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }


    @Test
    public void lostPackets() throws Exception {
        // create pair of connected adapters
        // and simulate lost paclets and packets received in random order

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        d1.seTestMode(DatagramAdapter.TestModes.LOST_PACKETS);
        d2.seTestMode(DatagramAdapter.TestModes.LOST_PACKETS);

        byte[] payload1 = "test data set 1".getBytes();
        byte[] payload2 = "test data set 2".getBytes();

        ArrayList<byte[]> receviedFor1 = new ArrayList<>();
        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor2.size());
        byte[] data = receviedFor2.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);

        // send data back

        d1.receive(d-> {
            receviedFor1.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d2, to d1
        d2.send(node1, payload2);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor1.size());
        data = receviedFor1.get(0);

        // receiver must s
        assertArrayEquals(payload2, data);

        d1.shutdown();
        d2.shutdown();
    }


    @Test
    public void shufflePackets() throws Exception {
        // create pair of connected adapters
        // and simulate packets received in random order

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        d1.seTestMode(DatagramAdapter.TestModes.SHUFFLE_PACKETS);
        d2.seTestMode(DatagramAdapter.TestModes.SHUFFLE_PACKETS);

        byte[] payload1 = "test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 test data set 1 ".getBytes();

        ArrayList<byte[]> receviedFor1 = new ArrayList<>();
        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                waitStatusQueue.put("DONE");
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials:
        d1.send(node2, payload1);

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(1, receviedFor2.size());
        byte[] data = receviedFor2.get(0);

        // receiver must s
        assertArrayEquals(payload1, data);

        d1.shutdown();
        d2.shutdown();
    }


//    @Test
    public void reconnectWithLostAndShuffle() throws Exception {
        // Tottaly hard test with reconnect, shuffled and lost packets and multiple send.

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

        d1.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        d2.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);

        d1.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);
        d2.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);

//        byte[] payload1 = "test data set with rnd: ".getBytes();

        String payloadtring;
        final int timesToSend = 1;

        ArrayList<byte[]> receviedFor1 = new ArrayList<>();
        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
        BlockingQueue<String> waitStatusQueue = new ArrayBlockingQueue<String>(1, true);

        d2.receive(d-> {
            receviedFor2.add(d);
            try {
                if(receviedFor2.size() >= timesToSend) {
                    waitStatusQueue.put("DONE");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d2 as it is connected with node2 credentials, ten times:
        for (int i = 0; i < timesToSend; i++) {
            payloadtring  = "test data set with rnd: " + Do.randomInt(10000);
            d1.send(node2, payloadtring.getBytes());
        }

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(timesToSend, receviedFor2.size());
//        byte[] data = receviedFor2.get(0);
//
//        // receiver must s
//        assertArrayEquals(payload1, data);

        // send data back

        d1.receive(d-> {
            receviedFor1.add(d);
            try {
                if(receviedFor1.size() >= timesToSend) {
                    waitStatusQueue.put("DONE");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d2, to d1, ten times:
        for (int i = 0; i < timesToSend; i++) {
            payloadtring  = "test data set with rnd: " + Do.randomInt(10000);
            d2.send(node1, payloadtring.getBytes());
        }

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(timesToSend, receviedFor1.size());
//        data = receviedFor1.get(0);
//
//        // receiver must s
//        assertArrayEquals(payload2, data);

        // test with close and reopen socket
        System.out.println("-------");
        System.out.println("close socket and reopen with new adapter");
        System.out.println("-------");

        d2.shutdown();

        // create new adapter with d2 credentials
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        ArrayList<byte[]> receviedFor3 = new ArrayList<>();

        d3.seTestMode(DatagramAdapter.TestModes.LOST_AND_SHUFFLE_PACKETS);

        d3.receive(d-> {
            receviedFor3.add(d);
            try {
                if(receviedFor3.size() >= timesToSend) {
                    waitStatusQueue.put("DONE");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("DONE error");
            }
        });


        // send from adapter d1, to d3
        for (int i = 0; i < timesToSend; i++) {
            payloadtring  = "test data set with rnd: " + Do.randomInt(10000);
            d1.send(node2, payloadtring.getBytes());
        }

        while (!((waitStatusQueue.take()).equals("DONE"))){
            // wait until it is delivered
        }

        assertEquals(timesToSend, receviedFor3.size());
//        data = receviedFor3.get(0);

        // receiver must s
//        assertArrayEquals(payload1, data);


        d1.shutdown();
        d2.shutdown();
        d3.shutdown();
    }
}