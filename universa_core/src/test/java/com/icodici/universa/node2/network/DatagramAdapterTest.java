///*
// * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
// *
// * Written by Sergey Chernov <real.sergeych@gmail.com>
// *
// */
//
//package com.icodici.universa.node2.network;
//
//import com.icodici.universa.node.network.TestKeys;
//import com.icodici.universa.node2.NodeInfo;
//import org.junit.Test;
//
//import java.util.ArrayList;
//
//import static org.junit.Assert.assertArrayEquals;
//import static org.junit.Assert.assertEquals;
//
//public class DatagramAdapterTest {
//    @Test
//    public void sendAndReceive() throws Exception {
//
//        NodeInfo node1 = new NodeInfo(TestKeys.publicKey(0),10, "test_node_10", "localhost", 16201, 16202);
//        NodeInfo node2 = new NodeInfo(TestKeys.publicKey(0),11, "test_node_11", "localhost", 16203, 16204);
//        NodeInfo node3 = new NodeInfo(TestKeys.publicKey(0),12, "test_node_12", "localhost", 16204, 16205);
//
//        DatagramAdapter d1 = null; // create implemented class with node1
//        DatagramAdapter d2 = null; // create implemented class with node1
//        DatagramAdapter d3 = null; // create implemented class with node1
//
//        byte[] payload1 = "test data set 1".getBytes();
//
//        ArrayList<byte[]> receviedFor2 = new ArrayList<>();
//
//
//
//        d2.receive(d->receviedFor2.add(d));
//
//        // send from adapter d1, to d2 as it is connected with node2 credentials:
//        d1.send(node2, payload1);
//
//        // wait until it is delivered
//        assertEquals(1, receviedFor2.size());
//        byte[] data = receviedFor2.get(0);
//
//        // receiver must s
//        assertArrayEquals(payload1, data);
//
//        // And test it for all interfaceces and big arrays of data
//    }
//
//
//    @Test
//    public void testReconnect() throws Exception {
//        // create pair of connected adapters
//        // ensure data are circulating between them in both directions
//        // delete one adapter (ensure the socket is closed)
//        // reopent it
//        // ensure connection is restored and data are transmitted
//    }
//}