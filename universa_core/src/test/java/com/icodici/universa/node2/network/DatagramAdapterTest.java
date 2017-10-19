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
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
    public void sendTrippleAndReceive() throws Exception {

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);
        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(2),12, "test_node_12", "localhost", 16204, 16205, 16303);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1
        DatagramAdapter d3 = new UDPAdapter(TestKeys.privateKey(2), new SymmetricKey(), node3); // create implemented class with node1

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


    @Test
    public void testReconnect() throws Exception {
        // create pair of connected adapters
        // ensure data are circulating between them in both directions
        // delete one adapter (ensure the socket is closed)
        // reopent it
        // ensure connection is restored and data are transmitted

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

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
    public void testLostPackets() throws Exception {
        // create pair of connected adapters
        // and simulate lost paclets and packets received in random order

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

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
    public void testShufflePackets() throws Exception {
        // create pair of connected adapters
        // and simulate packets received in random order

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

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


    @Test
    public void testReconnectWithLostAndShuffle() throws Exception {
        // Tottaly hard test with reconnect, shuffled and lost packets and multiple send.

        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202, 16301);
        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(1),11, "test_node_11", "localhost", 16203, 16204, 16302);

        DatagramAdapter d1 = new UDPAdapter(TestKeys.privateKey(0), new SymmetricKey(), node1); // create implemented class with node1
        DatagramAdapter d2 = new UDPAdapter(TestKeys.privateKey(1), new SymmetricKey(), node2); // create implemented class with node1

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