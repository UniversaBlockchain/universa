/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.*;
import com.icodici.universa.Errors;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;

import static java.util.Arrays.asList;

public class UDPAdapter extends DatagramAdapter {

    static private LogPrinter log = new LogPrinter("UDPA");

    private DatagramSocket socket;

    private SocketListenThread socketListenThread;

    private Object lock = new Object();

//    private ConcurrentHashMap<PublicKey, Session> sessionsByKey = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, Session> sessionsById = new ConcurrentHashMap<>();

    private Timer timer = new Timer();

    /**
     * Create an instance that listens for the incoming datagrams using the specified configurations. The adapter should
     * start serving incoming datagrams immediately upon creation.
     *
     * @param sessionKey
     * @param myNodeInfo
     */
    public UDPAdapter(PrivateKey ownPrivateKey, SymmetricKey sessionKey, NodeInfo myNodeInfo) throws IOException {
        super(ownPrivateKey, sessionKey, myNodeInfo);

        socket = new DatagramSocket(myNodeInfo.getNodeAddress().getPort());
        socket.setReuseAddress(true);

        socketListenThread = new SocketListenThread(socket);
        socketListenThread.start();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkUnsent();
            }
        }, RETRANSMIT_TIME, RETRANSMIT_TIME);
    }


    @Override
    public synchronized void send(NodeInfo destination, byte[] payload) throws EncryptionError, InterruptedException {
        report(getLabel(), "send to " + destination.getNumber(), VerboseLevel.BASE);

        Session session = sessionsById.get(destination.getNumber());

        Block rawBlock = new Block(myNodeInfo.getNumber(), destination.getNumber(),
                                   new Random().nextInt(Integer.MAX_VALUE), PacketTypes.RAW_DATA,
                                   destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort(),
                                   payload.clone());

        if (session != null) {
            if(session.isValid()) {
                if (session.state == Session.EXCHANGING || session.state == Session.SESSION) {
                    report(getLabel(), "session is ok", VerboseLevel.BASE);

                    session.addBlockToWaitingQueue(rawBlock);
                    sendAsDataBlock(rawBlock, session);
                } else {
                    report(getLabel(), "session is handshaking", VerboseLevel.BASE);
                    session.addBlockToWaitingQueue(rawBlock);
                }
            } else {
                if (session.state == Session.EXCHANGING || session.state == Session.SESSION) {
                    report(getLabel(), "session not valid for exchanging, recreate", VerboseLevel.BASE);
                    if(sessionsById.containsKey(session.remoteNodeId)) {
                        sessionsById.remove(session.remoteNodeId);
                    }
                    session = getOrCreateSession(destination.getNumber(),
                            destination.getNodeAddress().getAddress(),
                            destination.getNodeAddress().getPort());
                    session.publicKey = destination.getPublicKey();
                    session.remoteNodeId = destination.getNumber();
                    session.addBlockToWaitingQueue(rawBlock);
                    sendHello(session);
                } else {
                    report(getLabel(), "session not valid yet, but it is handshaking", VerboseLevel.BASE);
                    session.addBlockToWaitingQueue(rawBlock);
                }
            }
        } else {
            report(getLabel(), "session not exist", VerboseLevel.BASE);
            session = getOrCreateSession(destination.getNumber(),
                    destination.getNodeAddress().getAddress(),
                    destination.getNodeAddress().getPort());
            session.publicKey = destination.getPublicKey();
            session.remoteNodeId = destination.getNumber();
            session.addBlockToWaitingQueue(rawBlock);
            sendHello(session);
        }
    }


    @Override
    public void shutdown() {
        report(getLabel(), "shutdown");
        socketListenThread.shutdownThread();
        socket.close();
        socket.disconnect();
        closeSessions();
    }


    public void closeSessions() {
        report(getLabel(), "closeSessions");
        sessionsById.clear();
    }


    public void brakeSessions() {
        report(getLabel(), "brakeSessions");
        for (Session s : sessionsById.values()) {
            s.publicKey = new PublicKey();
        }
    }


    public String getLabel()
    {
        return myNodeInfo.getNumber() + "-0: ";
    }


    public void report(String label, String message, int level)
    {
        if(level <= verboseLevel)
            System.out.println(label + message);
    }


    public void report(String label, String message)
    {
        report(label, message, VerboseLevel.DETAILED);
    }


    protected synchronized void sendBlock(Block block, Session session) throws InterruptedException {

        if(!block.isValidToSend()) {
            block.prepareToSend(MAX_PACKET_SIZE);
        }

        List<DatagramPacket> outs = new ArrayList(block.datagrams.values());

        block.sendAttempts++;
        if(block.type != PacketTypes.PACKET_ACK &&
                block.type != PacketTypes.ACK &&
                block.type != PacketTypes.NACK) {
            session.addBlockToSendingQueue(block);
        }
        try {
            if(testMode == TestModes.SHUFFLE_PACKETS || testMode == TestModes.LOST_AND_SHUFFLE_PACKETS) {
                Collections.shuffle(outs);
            }

            report(getLabel(), "sending packets num:  " + outs.size());
            for (DatagramPacket d : outs) {
                if(testMode == TestModes.LOST_PACKETS || testMode == TestModes.LOST_AND_SHUFFLE_PACKETS) {
                    if (new Random().nextInt(100) < lostPacketsPercent) {
                        report(getLabel(), "Lost packet in block: " + block.blockId);
                        continue;
                    }
                }
                socket.send(d);
            }
        } catch (IOException e) {
            report(getLabel(), "send block error, socket already closed");
//            e.printStackTrace();
        }
    }


    protected synchronized void sendAsDataBlock(Block rawDataBlock, Session session) throws EncryptionError, InterruptedException {
        report(getLabel(), "send data to " + session.remoteNodeId, VerboseLevel.BASE);
        report(getLabel(), "sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);

        byte[] crc32Local = new Crc32().digest(rawDataBlock.payload);
        report(getLabel(), "sendAsDataBlock: Crc32 id is " + Arrays.equals(rawDataBlock.crc32, crc32Local));

        byte[] encrypted = session.sessionKey.etaEncrypt(rawDataBlock.payload.clone());

        Binder binder = Binder.fromKeysValues(
                "data", encrypted,
                "crc32", rawDataBlock.crc32
        );
        byte[] packedData = Boss.pack(binder);
        report(getLabel(), " data size: " + rawDataBlock.payload.length + " for " + session.remoteNodeId);
        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                rawDataBlock.blockId, PacketTypes.DATA,
                                session.address, session.port,
                                packedData);
        sendBlock(block, session);
    }


    protected synchronized void sendHello(Session session) throws EncryptionError, InterruptedException {
        report(getLabel(), "send hello to " + session.remoteNodeId, VerboseLevel.BASE);

        session.state = Session.HELLO;
        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.HELLO,
                                session.address, session.port,
                                myNodeInfo.getPublicKey().pack());
        sendBlock(block, session);
    }


    protected synchronized void sendWelcome(Session session) throws InterruptedException {
        report(getLabel(), "send welcome to " + session.remoteNodeId, VerboseLevel.BASE);

        session.state = Session.WELCOME;
        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.WELCOME,
                                session.address, session.port,
                                session.localNonce);
        sendBlock(block, session);
    }


    protected synchronized void sendKeyRequest(Session session) throws InterruptedException {
        report(getLabel(), "send key request to " + session.remoteNodeId, VerboseLevel.BASE);

        session.state = Session.KEY_REQ;
        List data = asList(session.localNonce, session.remoteNonce);
        try {
            byte[] packed = Boss.pack(data);
            byte[] signed = ownPrivateKey.sign(packed, HashType.SHA512);

            Binder binder = Binder.fromKeysValues(
                    "data", packed,
                    "signature", signed
            );

            Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                    new Random().nextInt(Integer.MAX_VALUE), PacketTypes.KEY_REQ,
                                    session.address, session.port,
                                    Boss.pack(binder));
            sendBlock(block, session);
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }


    protected synchronized void sendSessionKey(Session session) throws InterruptedException {
        report(getLabel(), "send session key to " + session.remoteNodeId, VerboseLevel.BASE);
        report(getLabel(), "sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);

        List data = asList(session.sessionKey.getKey(), session.remoteNonce);
        try {
            byte[] packed = Boss.pack(data);
            byte[] encrypted = session.publicKey.encrypt(packed);
            byte[] signed = ownPrivateKey.sign(encrypted, HashType.SHA512);

            Binder binder = Binder.fromKeysValues(
                    "data", encrypted,
                    "signature", signed
            );

            Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                    new Random().nextInt(Integer.MAX_VALUE), PacketTypes.SESSION,
                                    session.address, session.port,
                                    Boss.pack(binder));
            sendBlock(block, session);
            session.state = Session.SESSION;
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }


    protected synchronized void sendPacketAck(Session session, int blockId, int packetId) throws InterruptedException {
        report(getLabel(), "send packet_ack to " + session.remoteNodeId);

        List data = asList(blockId, packetId);
        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.PACKET_ACK,
                                session.address, session.port,
                                Boss.pack(data));
        sendBlock(block, session);
    }


    protected synchronized void sendAck(Session session, int blockId) throws InterruptedException {
        report(getLabel(), "send ack to " + session.remoteNodeId, VerboseLevel.BASE);

        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.ACK,
                                session.address, session.port,
                                Boss.pack(blockId));
        sendBlock(block, session);
    }


    protected synchronized void sendNack(Session session, int blockId) throws InterruptedException {
        report(getLabel(), "send nack to " + session.remoteNodeId, VerboseLevel.BASE);

        Block block = new Block(myNodeInfo.getNumber(), session.remoteNodeId,
                                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.NACK,
                                session.address, session.port,
                                Boss.pack(blockId));
        sendBlock(block, session);
    }


    protected synchronized Session getOrCreateSession(int remoteId, InetAddress address, int port) throws EncryptionError {

        if(sessionsById.containsKey(remoteId)) {
            Session s = sessionsById.get(remoteId);
            report(getLabel(), ">>Session was exist for node " + remoteId + " at the node " + myNodeInfo.getNumber(), VerboseLevel.BASE);
            report(getLabel(), ">>local node: " + myNodeInfo.getNumber() + " remote node: " + s.remoteNodeId, VerboseLevel.BASE);
            report(getLabel(), ">>local nonce: " + s.localNonce + " remote nonce: " + s.remoteNonce, VerboseLevel.BASE);
            report(getLabel(), ">>state: " + s.state, VerboseLevel.BASE);
            report(getLabel(), ">>session key: " + s.sessionKey.hashCode(), VerboseLevel.BASE);
//            report(getLabel(), ">>new local nonce: " + session.localNonce, VerboseLevel.BASE);

            return s;
        }

        Session session;

        session = new Session(address, port);
        report(getLabel(), "session created for nodeId " + remoteId);
        session.remoteNodeId = remoteId;
        session.sessionKey = sessionKey;
        report(getLabel(), "sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);
        sessionsById.putIfAbsent(remoteId, session);

        return session;

    }


    protected synchronized void checkUnsent() {
        List<Block> blocksToRemove;
        for(Session session : sessionsById.values()) {
            blocksToRemove = new ArrayList();
            for (Block block : session.sendingBlocksQueue) {
                if(!block.isDelivered()) {
                    report(getLabel(), "block: " + block.blockId + " type: " + block.type + " sendAttempts: " + block.sendAttempts + " not delivered", VerboseLevel.BASE);
                    try {
                        if(block.sendAttempts >= RETRANSMIT_MAX_ATTEMPTS) {
                            report(getLabel(), "block " + block.blockId + " type " + block.type + " will be removed", VerboseLevel.BASE);
                            blocksToRemove.add(block);
                        } else {
                            sendBlock(block, session);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            for(Block rb : blocksToRemove) {
                try {
//                    if(rb.type == PacketTypes.DATA && session.sendingBlocksQueue.contains(rb)) {
//                        System.err.println(getLabel() + "block " + rb.blockId + " type " + rb.type + " has not delivered and will be removed");
//                        callErrorCallbacks("block " + rb.blockId + " type " + rb.type + " has not delivered and will be removed");
//                    }
                    session.removeBlockFromSendingQueue(rb);
                    session.removeBlockFromWaitingQueue(rb.blockId);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    protected synchronized void checkUnsentPackets(Session session) {
        List<Block> blocksToResend = new ArrayList();
        List<Packet> packetsToResend = new ArrayList();
        List<DatagramPacket> datagramsToResend = new ArrayList();
//        for(Session session : sessionsById.values()) {
        for (Block block : session.sendingBlocksQueue) {
            if(!block.isDelivered()) {
                blocksToResend.add(block);
                for(Packet packet : block.packets.values()) {
                    if(packet.sendWaitIndex >= 3) {
//                            packetsToResend.add(packet);
                        datagramsToResend.add(block.datagrams.get(packet.packetId));
                        report(getLabel(), " packet will be resend, blockId: " + packet.blockId + " packetId: " + packet.packetId + " type: " + packet.type + " sendWaitIndex: " + packet.sendWaitIndex);
                    }
                }
            }
        }
//        }

        for(DatagramPacket datagram : datagramsToResend) {
            try {
                if(datagram != null) {
                    socket.send(datagram);
                    report(getLabel(), " datagram was resent");
                } else {
                    report(getLabel(), " datagram unexpected became null");
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }

    }


    protected synchronized void sendWaitingBlocks(Session session) throws EncryptionError {
        if (session != null && session.isValid() && (session.state == Session.EXCHANGING || session.state == Session.SESSION)) {
            report(getLabel(), " waiting blocks num " + session.waitingBlocksQueue.size());
            try {
                for (Block waitingBlock : session.waitingBlocksQueue) {
                    report(getLabel(), " waitingBlock " + waitingBlock.blockId + " type " + waitingBlock.type);
                    if (waitingBlock.type == PacketTypes.RAW_DATA) {
                        sendAsDataBlock(waitingBlock, session);
                    } else {
                        sendBlock(waitingBlock, session);
                    }
//                                            session.removeBlockFromWaitingQueue(waitingBlock);
                }
            } catch (InterruptedException e) {
                System.out.println(Errors.BAD_VALUE + " send encryption error, " + e.getMessage());
            }
        }
    }



    protected synchronized void callErrorCallbacks(String message) {
        for(Function<String, String> fn : errorCallbacks) {
            fn.apply(message);
        }
    }


    class SocketListenThread extends Thread
    {
        private Boolean active = false;

        private final DatagramSocket threadSocket;
        private DatagramPacket receivedDatagram;

        private ConcurrentHashMap<Integer, Block> waitingBlocks = new ConcurrentHashMap<>();

        private ConcurrentHashMap<Integer, Block> obtainedBlocks = new ConcurrentHashMap<>();

        public SocketListenThread(DatagramSocket socket){

            byte[] buf = new byte[DatagramAdapter.MAX_PACKET_SIZE];
            receivedDatagram = new DatagramPacket(buf, buf.length);
            this.threadSocket = socket;
        }

        @Override
        public void run()
        {
            setName(Integer.toString(new Random().nextInt(100)));
            report(getLabel(), " UDPAdapter listen socket at " + myNodeInfo.getNodeAddress().getAddress() + ":" + myNodeInfo.getNodeAddress().getPort());
            active = true;
            while(active) {
                try {
                    if(!threadSocket.isClosed()) {
                        if(active) {
                            threadSocket.receive(receivedDatagram);
                        }
                    }
                } catch (SocketException e) {
//                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if(active) {

                    byte[] data = Arrays.copyOfRange(receivedDatagram.getData(), 0, receivedDatagram.getLength());

                    Packet packet = new Packet();
                    Block waitingBlock = null;
                    try {
                        packet.parseFromByteArray(data);

                        report(getLabel(), " got packet with blockId: " + packet.blockId + " packetId: " + packet.packetId + " type: " + packet.type);

                        if (waitingBlocks.containsKey(packet.blockId)) {
                            waitingBlock = waitingBlocks.get(packet.blockId);
                        } else {
                            if (obtainedBlocks.containsKey(packet.blockId)) {
                                // Do nothing, cause we got and obtained this block already
                                report(getLabel(), " warning: repeated block given, with id " + packet.blockId);
                            } else {
                                waitingBlock = new Block(packet.senderNodeId, packet.receiverNodeId,
                                        packet.blockId, packet.type,
                                        receivedDatagram.getAddress(), receivedDatagram.getPort());
                                waitingBlocks.put(waitingBlock.blockId, waitingBlock);
                            }
//                        waitingBlock = new Block(packet.senderNodeId, packet.receiverNodeId, packet.blockId, packet.type);
//                        waitingBlocks.put(waitingBlock.blockId, waitingBlock);
                        }

                        if (waitingBlock != null) {
                            waitingBlock.addToPackets(packet);

                            if (waitingBlock.isSolid()) {
                                moveWaitingBlockToObtained(waitingBlock);
                                waitingBlock.reconstruct();
                                obtainSolidBlock(waitingBlock);
                            } else {
                                if (packet.type != PacketTypes.PACKET_ACK) {
//                                    Session session = sessionsById.get(packet.senderNodeId);
//                                    if (session == null) {
//                                        session = getOrCreateSession(packet.senderNodeId, receivedDatagram.getAddress(), receivedDatagram.getPort());
//                                    }
                                    Session session = getOrCreateSession(packet.senderNodeId, receivedDatagram.getAddress(), receivedDatagram.getPort());
                                    sendPacketAck(session, packet.blockId, packet.packetId);
                                    switch (packet.type) {
                                        case PacketTypes.HELLO:
                                            session.makeBlockDeliveredByType(PacketTypes.HELLO);
                                            break;
                                        case PacketTypes.WELCOME:
                                            session.makeBlockDeliveredByType(PacketTypes.HELLO);
                                            session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                                            break;
                                        case PacketTypes.KEY_REQ:
                                            session.makeBlockDeliveredByType(PacketTypes.HELLO);
                                            session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                                            session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                                            break;
                                        case PacketTypes.SESSION:
                                            session.makeBlockDeliveredByType(PacketTypes.HELLO);
                                            session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                                            session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                                            session.makeBlockDeliveredByType(PacketTypes.SESSION);
                                        case PacketTypes.DATA:
                                            if(session.isValid() && (session.state == Session.EXCHANGING || session.state == Session.SESSION)) {
                                                session.makeBlockDeliveredByType(PacketTypes.HELLO);
                                                session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                                                session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                                                session.makeBlockDeliveredByType(PacketTypes.SESSION);
                                            }
                                            break;
                                    }
                                }
                            }
                        }

                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (SymmetricKey.AuthenticationFailed e) {
                        callErrorCallbacks("SymmetricKey.AuthenticationFailed in node " + myNodeInfo.getNumber() + ": " + e.getMessage());
                        e.printStackTrace();
                    } catch (EncryptionError e) {
                        callErrorCallbacks(getLabel() + " EncryptionError in node " + myNodeInfo.getNumber() + ": " + e.getMessage());
                        for (Session s : sessionsById.values()) {
                            report(getLabel(), ">>---", VerboseLevel.BASE);
                            report(getLabel(), ">>local node: " + myNodeInfo.getNumber() + " remote node: " + s.remoteNodeId, VerboseLevel.BASE);
                            report(getLabel(), ">>local nonce: " + s.localNonce + " remote nonce: " + s.remoteNonce, VerboseLevel.BASE);
                            report(getLabel(), ">>state: " + s.state, VerboseLevel.BASE);
                            report(getLabel(), ">>session key: " + s.sessionKey.hashCode(), VerboseLevel.BASE);
                        }
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        callErrorCallbacks("IllegalStateException in node " + myNodeInfo.getNumber() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    report(getLabel(), "socket will be closed");
                    shutdownThread();
                }
            }
        }


        public synchronized void shutdownThread()
        {
            active = false;
            interrupt();
            threadSocket.close();
        }


        public synchronized String getLabel()
        {
            return myNodeInfo.getNumber() + "-" + getName() + ": ";
        }


        protected synchronized void obtainSolidBlock(Block block) throws SymmetricKey.AuthenticationFailed, EncryptionError, InterruptedException {
            Session session = null;
            Binder unbossedPayload;
            byte[] signedUnbossed;
            List ackList;
            int ackBlockId;
            int ackPacketId;

            switch (block.type) {

                case PacketTypes.HELLO:
                    report(getLabel(), "got hello from " + block.senderNodeId, VerboseLevel.BASE);
                    PublicKey key = new PublicKey(block.payload);
//                    session = sessionsById.get(block.senderNodeId);
//                    if (session == null) {
//                        session = getOrCreateSession(block.senderNodeId, block.address, block.port);
//                    }
                    session = getOrCreateSession(block.senderNodeId, block.address, block.port);
                    session.publicKey = key;
                    session.makeBlockDeliveredByType(PacketTypes.HELLO);
                    if(session.state == Session.HANDSHAKE ||
                            session.state == Session.EXCHANGING ||
                            session.state == Session.SESSION ||
                            // if current node sent hello or other handshake blocks choose who will continue handshake - whom id is greater
                            session.remoteNodeId > myNodeInfo.getNumber()) {

                        sendWelcome(session);
                    } else {
                        report(getLabel(), "node sent handshake too, to " + session.remoteNodeId + " state: " + session.state, VerboseLevel.BASE);
                    }
                    break;

                case PacketTypes.WELCOME:
                    report(getLabel(), "got welcome from " + block.senderNodeId, VerboseLevel.BASE);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null) {
                        session.remoteNonce = block.payload;
                        session.makeBlockDeliveredByType(PacketTypes.HELLO);
                        session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                        if (session.state == Session.HELLO ||
                                // if current node sent hello or other handshake blocks choose who will continue handshake - whom id is greater
                                session.remoteNodeId > myNodeInfo.getNumber()) {

                            sendKeyRequest(session);
                        } else {
                            report(getLabel(), "node sent handshake too, to " + session.remoteNodeId + " state: " + session.state, VerboseLevel.BASE);
                        }
                    }
                    break;

                case PacketTypes.KEY_REQ:
                    report(getLabel(), "got key request from " + block.senderNodeId, VerboseLevel.BASE);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null) {
                        session.makeBlockDeliveredByType(PacketTypes.HELLO);
                        session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                        session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                        unbossedPayload = Boss.load(block.payload);
                        signedUnbossed = unbossedPayload.getBinaryOrThrow("data");
                        if (session.publicKey != null) {
                            if (session.publicKey.verify(signedUnbossed, unbossedPayload.getBinaryOrThrow("signature"), HashType.SHA512)) {

                                report(getLabel(), "successfully verified ");

                                List receivedData = Boss.load(signedUnbossed);
                                byte[] senderNonce = ((Bytes) receivedData.get(0)).toArray();
                                byte[] receiverNonce = ((Bytes) receivedData.get(1)).toArray();

                                // if remote nonce from received data equals with own nonce
                                // (means request sent after welcome from known and expected node)
                                if (Arrays.equals(receiverNonce, session.localNonce)) {
                                    session.remoteNonce = senderNonce;

                                    if (session.state == Session.WELCOME ||
                                            // if current node sent hello or other handshake blocks choose who will continue handshake - whom id is greater
                                            session.remoteNodeId > myNodeInfo.getNumber()) {

                                        session.createSessionKey();
                                        report(getLabel(), " check session " + session.isValid());
                                        sendSessionKey(session);
                                    } else {
                                        report(getLabel(), "node sent handshake too, to " + session.remoteNodeId + " state: " + session.state, VerboseLevel.BASE);
                                    }
//                                    session.makeBlockDeliveredByType(PacketTypes.SESSION);
                                } else {
                                    throw new EncryptionError(Errors.BAD_VALUE + ": got nonce is not valid (not equals with current). Got nonce: " + receiverNonce + " from node " + block.senderNodeId + " with sender nonce " + senderNonce);
                                }
                            } else {
                                throw new EncryptionError(Errors.BAD_VALUE + ": sign has not verified. Got data have signed with key not match with known public key.");
                            }
                        } else {
                            throw new EncryptionError(Errors.BAD_VALUE + ": public key for current session is broken or does not exist");
                        }
                    }
                    break;

                case PacketTypes.SESSION:
                    report(getLabel(), "got session from " + block.senderNodeId, VerboseLevel.BASE);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null) {
                        session.makeBlockDeliveredByType(PacketTypes.HELLO);
                        session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                        session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                        session.makeBlockDeliveredByType(PacketTypes.SESSION);
                        unbossedPayload = Boss.load(block.payload);
                        signedUnbossed = unbossedPayload.getBinaryOrThrow("data");
                        if (session.publicKey != null) {
                            if (session.publicKey.verify(signedUnbossed, unbossedPayload.getBinaryOrThrow("signature"), HashType.SHA512)) {

                                report(getLabel(), " successfully verified ");

                                byte[] decryptedData = ownPrivateKey.decrypt(signedUnbossed);
                                List receivedData = Boss.load(decryptedData);
                                byte[] sessionKey = ((Bytes) receivedData.get(0)).toArray();
                                byte[] receiverNonce = ((Bytes) receivedData.get(1)).toArray();

                                // if remote nonce from received data equals with own nonce
                                // (means session sent as answer to key request from known and expected node)
                                if (Arrays.equals(receiverNonce, session.localNonce)) {
                                    session.reconstructSessionKey(sessionKey);
                                    report(getLabel(), " check session " + session.isValid());

                                    // Tell remote nonce we got session or send own and no need to resend it.
                                    answerAckOrNack(session, block, receivedDatagram.getAddress(), receivedDatagram.getPort());

                                    sendWaitingBlocks(session);
                                } else {
                                    throw new EncryptionError(Errors.BAD_VALUE + ": got nonce is not valid (not equals with current)");
                                }
                            } else {
                                throw new EncryptionError(Errors.BAD_VALUE + ": sign has not verified. Got data have signed with key not match with known public key.");
                            }
                        } else {
                            throw new EncryptionError(Errors.BAD_VALUE + ": public key for current session does not exist");
                        }
                    }
                    break;

                case PacketTypes.DATA:
                    report(getLabel(), "got data from " + block.senderNodeId, VerboseLevel.BASE);
                    session = sessionsById.get(block.senderNodeId);
                    try {
                        if(session != null && session.isValid() && (session.state == Session.EXCHANGING || session.state == Session.SESSION)) {

                            session.makeBlockDeliveredByType(PacketTypes.HELLO);
                            session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                            session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                            session.makeBlockDeliveredByType(PacketTypes.SESSION);

                            unbossedPayload = Boss.load(block.payload);
                            report(getLabel(), "sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);

                            byte[] decrypted = session.sessionKey.etaDecrypt(unbossedPayload.getBinaryOrThrow("data"));
                            byte[] crc32Remote = unbossedPayload.getBinaryOrThrow("crc32");
                            byte[] crc32Local = new Crc32().digest(decrypted);

                            if(Arrays.equals(crc32Remote, crc32Local)) {
                                report(getLabel(), "Crc32 id ok", VerboseLevel.BASE);
                                if(receiver != null) receiver.accept(decrypted);
                            } else {
                                report(getLabel(), "Crc32 Error, sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId, VerboseLevel.BASE);

                                sendNack(session, block.blockId);
                                throw new EncryptionError(Errors.BAD_VALUE +
                                        ": Crc32 Error, decrypted length " + decrypted.length +
                                        "\n sessionKey is " + session.sessionKey.hashCode() +
                                        "\n own sessionKey is " + sessionKey.hashCode() +
                                        "\n string: " + new String(decrypted) +
                                        "\n remote id: " + session.remoteNodeId +
                                        "\n reconstructered block id: " + block.blockId);
                            }

                        }
                        answerAckOrNack(session, block, receivedDatagram.getAddress(), receivedDatagram.getPort());
                    } catch (SymmetricKey.AuthenticationFailed e) {
                        report(getLabel(), "SymmetricKey.AuthenticationFailed, sessionKey is " + session.sessionKey.hashCode() + " for " + session.remoteNodeId, VerboseLevel.BASE);
                        sendNack(session, block.blockId);
                        throw e;
                    }
                    break;

                case PacketTypes.ACK:
                    report(getLabel(), "got ack from " + block.senderNodeId, VerboseLevel.BASE);
                    session = sessionsById.get(block.senderNodeId);
                    ackBlockId = Boss.load(block.payload);
                    report(getLabel(), " ackBlockId is: " + ackBlockId);
                    if(session != null) {
                        report(getLabel(), " num packets was in queue: " + session.sendingPacketsQueue.size());
                        session.makeBlockDelivered(ackBlockId);
                        session.removeBlockFromWaitingQueue(ackBlockId);
                        session.incremetWaitIndexForPacketsFromSendingQueue();
                        report(getLabel(), " num packets in queue: " + session.sendingPacketsQueue.size());
                        checkUnsentPackets(session);

                        if (session.state == Session.SESSION) {
                            session.state = Session.EXCHANGING;
                            sendWaitingBlocks(session);
                        }
                    }
                    break;

                case PacketTypes.NACK:
                    report(getLabel(), "got nack from " + block.senderNodeId, VerboseLevel.BASE);
                    ackBlockId = Boss.load(block.payload);
                    report(getLabel(), " blockId: " + ackBlockId);

                    session = sessionsById.get(block.senderNodeId);
                    if(session != null && session.isValid() && (session.state == Session.EXCHANGING || session.state == Session.SESSION)) {
                        session.moveBlocksFromSendingToWaiting();
                        session.removeDataBlocksFromWaiting();
                        session.state = Session.HANDSHAKE;
                        report(getLabel(), "sessionKey was " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);
                        session.sessionKey = sessionKey;
                        report(getLabel(), "sessionKey now " + session.sessionKey.hashCode() + " for " + session.remoteNodeId);
                        sendHello(session);
                    }
                    break;

                case PacketTypes.PACKET_ACK:
                    ackList = Boss.load(block.payload);
                    ackBlockId = (int) ackList.get(0);
                    ackPacketId = (int) ackList.get(1);
                    report(getLabel(), " got packet_ack from " + block.senderNodeId + " for block id " + ackBlockId + " for packet id " + ackPacketId);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null) {
                        report(getLabel(), " num packets was in queue: " + session.sendingPacketsQueue.size());
                        session.removePacketFromSendingQueue(ackBlockId, ackPacketId);
                        session.incremetWaitIndexForPacketsFromSendingQueue();
                        report(getLabel(), " num packets in queue: " + session.sendingPacketsQueue.size());
                        checkUnsentPackets(session);
                    }
                    break;
            }
        }


        public synchronized void moveWaitingBlockToObtained(Block block) {
            waitingBlocks.remove(block.blockId);
            obtainedBlocks.put(block.blockId, block);
        }


        public synchronized void answerAckOrNack(Session session, Block block, InetAddress address, int port) throws InterruptedException, EncryptionError {
            if(session != null && session.isValid() && (session.state == Session.EXCHANGING || session.state == Session.SESSION)) {
                sendAck(session, block.blockId);
            } else {
                if(session != null) {
                    if(sessionsById.containsKey(session.remoteNodeId)) {
                        sessionsById.remove(session.remoteNodeId);
                    }
                }
                session = getOrCreateSession(block.senderNodeId, address, port);
                // we remove block from obtained because it broken and will can be regiven with correct data
                obtainedBlocks.remove(block.blockId);
                sendNack(session, block.blockId);
            }
        }
    }


    public class PacketTypes
    {
        static public final int RAW_DATA =     -1;
        static public final int DATA =          0;
        static public final int ACK =           1;
        static public final int NACK =          2;
        static public final int HELLO =         3;
        static public final int WELCOME =       4;
        static public final int KEY_REQ =       5;
        static public final int SESSION =       6;
        static public final int PACKET_ACK =    7;
    }


    public class Packet {
        private int senderNodeId;
        private int receiverNodeId;
        private int blockId;
        // id of packet if parent block is splitted to blocks sequence
        private int packetId = 0;
        // Num of packets in parent sequence if parent block is splitted to blocks sequence
        private int brotherPacketsNum = 0;
        private int type;
        private byte[] payload;
        // How long packet wait in queue (in got other packets times)
        private int sendWaitIndex = 0;

        private Boolean delivered = false;

        public Packet() {
        }

        public Packet(int packetsNum, int packetId, int senderNodeId, int receiverNodeId, int blockId, int type, byte[] payload) {
            this.brotherPacketsNum = packetsNum;
            this.packetId = packetId;
            this.senderNodeId = senderNodeId;
            this.receiverNodeId = receiverNodeId;
            this.blockId = blockId;
            this.type = type;
            this.payload = payload;
        }

        public synchronized byte[] makeByteArray() {
            List data = asList(brotherPacketsNum, packetId, senderNodeId, receiverNodeId, blockId, type, new Bytes(payload));
            Bytes byteArray = Boss.dump(data);
            return byteArray.toArray();
        }

        public synchronized void parseFromByteArray(byte[] byteArray) throws IOException {

            List data = Boss.load(byteArray);
            brotherPacketsNum = (int) data.get(0);
            packetId = (int) data.get(1);
            senderNodeId = (int) data.get(2);
            receiverNodeId = (int) data.get(3);
            blockId = (int) data.get(4);
            type = (int) data.get(5);
            payload = ((Bytes) data.get(6)).toArray();
        }
    }


    public class Block
    {
        private int senderNodeId;
        private int receiverNodeId;
        private int blockId;
        private int type;
        private byte[] payload;
        private byte[] crc32;
        private int sendAttempts;
        private InetAddress address;
        private int port;

        private ConcurrentHashMap<Integer, Packet> packets;
        private ConcurrentHashMap<Integer, DatagramPacket> datagrams;

        private Boolean delivered = false;

        private Boolean validToSend = false;

        public Block() {
            packets = new ConcurrentHashMap<>();
            datagrams = new ConcurrentHashMap<>();
        }

        public Block(int senderNodeId, int receiverNodeId, int blockId, int type, InetAddress address, int port, byte[] payload) {
            this.senderNodeId = senderNodeId;
            this.receiverNodeId = receiverNodeId;
            this.blockId = blockId;
            this.type = type;
            this.address = address;
            this.port = port;
            this.payload = payload;
            this.crc32 = new Crc32().digest(payload);

            packets = new ConcurrentHashMap<>();
            datagrams = new ConcurrentHashMap<>();
        }

        public Block(int senderNodeId, int receiverNodeId, int blockId, int type, InetAddress address, int port) {
            this.senderNodeId = senderNodeId;
            this.receiverNodeId = receiverNodeId;
            this.blockId = blockId;
            this.type = type;
            this.address = address;
            this.port = port;

            packets = new ConcurrentHashMap<>();
            datagrams = new ConcurrentHashMap<>();
        }

        public synchronized void prepareToSend(int packetSize) {
            packets = new ConcurrentHashMap<>();
            datagrams = new ConcurrentHashMap<>();

            List headerData = asList(0, 0, senderNodeId, receiverNodeId, blockId, type);
            // TODO: resolve issue with magic digit
            int headerSize = Boss.dump(headerData).size() + 3; // 3 - Boss artefact

            byte[] blockByteArray;
            DatagramPacket datagramPacket;
            Packet packet;
            byte[] cutPayload;
            int offset = 0;
            int copySize = 0;
            int packetId = 0;
            int packetsNum = payload.length / (packetSize - headerSize) + 1;
            while(payload.length > offset) {
                copySize = packetSize - headerSize;
                if(offset + copySize >= payload.length) {
                    copySize = payload.length - offset;
                }
                cutPayload = Arrays.copyOfRange(payload, offset, offset + copySize);
                packet = new Packet(packetsNum, packetId, senderNodeId, receiverNodeId, blockId, type, cutPayload);
                packets.put(packetId, packet);

                blockByteArray = packet.makeByteArray();
                datagramPacket = new DatagramPacket(blockByteArray, blockByteArray.length, address, port);
                datagrams.put(packetId, datagramPacket);

                offset += copySize;
                packetId++;
            }

            validToSend = true;
        }

        public synchronized void reconstruct() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            for (Packet packet : packets.values()) {
                outputStream.write(packet.payload);
            }
            payload = outputStream.toByteArray();
        }

        public synchronized void addToPackets(Packet packet) {
            if(!packets.containsKey(packet.packetId)) {
                packets.put(packet.packetId, packet);
            }
            report(getLabel(), " addToPackets: " + packets.size() + " from " + packet.brotherPacketsNum + ", blockId: " + packet.blockId + " packetId: " + packet.packetId + " type: " + packet.type);
        }

        public synchronized void markPacketAsDelivered(Packet packet) throws InterruptedException {
            packet.delivered = true;
            if(datagrams.containsKey(packet.packetId)) {
                datagrams.remove(packet.packetId);
            }
        }

        public synchronized Boolean isSolid() {
            if(packets.size() > 0) {
                // packets.get(0) may be null because packets can received in random order
                if(packets.get(0) != null && packets.get(0).brotherPacketsNum == packets.size()) {
                    return true;
                }
            }

            return false;
        }

        public synchronized Boolean isValidToSend() {
            if(packets.size() == 0) {
                return false;
            }
//            if(datagrams.size() == 0) {
//                return false;
//            }

            return validToSend;
        }

        public synchronized Boolean isDelivered() {
            return delivered;
        }
    }


    private class Session {

        private PublicKey publicKey;
        private SymmetricKey sessionKey;
        private InetAddress address;
        private int port;
        private byte[] localNonce;
        private byte[] remoteNonce;
        private int remoteNodeId = -1;

        private int state;

        static public final int NOT_EXIST =         0;
        static public final int HANDSHAKE =         1;
        static public final int EXCHANGING =        2;
        static public final int HELLO =             3;
        static public final int WELCOME =           4;
        static public final int KEY_REQ =           5;
        static public final int SESSION =           6;

        /**
         * Queue where store not sent yet Blocks.
         */
        private BlockingQueue<Block> waitingBlocksQueue = new LinkedBlockingQueue<>();

        /**
         * Queue where store sending Blocks.
         */
        private BlockingQueue<Block> sendingBlocksQueue = new LinkedBlockingQueue<>();

        /**
         * Queue where store sending Packets.
         */
        private BlockingQueue<Packet> sendingPacketsQueue = new LinkedBlockingQueue<>();


        Session(InetAddress address, int port) throws EncryptionError {
            this.address = address;
            this.port = port;
            localNonce = Do.randomBytes(64);
            state = HANDSHAKE;
        }

        public synchronized Boolean isValid() {
            if (localNonce == null) {
                report(getLabel(), "session validness check: localNonce is null");
                return false;
            }
            if (remoteNonce == null) {
                report(getLabel(), "session validness check: remoteNonce is null");
                return false;
            }
            if (sessionKey == null) {
                report(getLabel(), "session validness check: sessionKey is null");
                return false;
            }
//            if (publicKey == null) {
//                return false;
//            }
            if (remoteNodeId < 0) {
                report(getLabel(), "session validness check: remoteNodeId is not defined");
                return false;
            }
//            if (state != EXCHANGING) {
//                return false;
//            }

            return true;
        }

        public synchronized void createSessionKey() throws EncryptionError {
            if (sessionKey == null) {
                sessionKey = new SymmetricKey();
            }
//            state = EXCHANGING;
        }

        public synchronized void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
            state = EXCHANGING;
        }

        public synchronized void addBlockToWaitingQueue(Block block) throws InterruptedException {
            if(!waitingBlocksQueue.contains(block))
                waitingBlocksQueue.put(block);
        }

        public synchronized void addBlockToSendingQueue(Block block) throws InterruptedException {
            if(!sendingBlocksQueue.contains(block))
                sendingBlocksQueue.put(block);

            for (Packet p : block.packets.values()) {
                addPacketToSendingQueue(p);
            }
        }

        public synchronized void addPacketToSendingQueue(Packet packet) throws InterruptedException {
            if(!sendingPacketsQueue.contains(packet))
                sendingPacketsQueue.put(packet);
        }

        public synchronized void removeBlockFromWaitingQueue(Block block) throws InterruptedException {
            if(waitingBlocksQueue.contains(block))
                waitingBlocksQueue.remove(block);
        }

        public synchronized void removeBlockFromWaitingQueue(int blockId) throws InterruptedException {
            for (Block block : waitingBlocksQueue) {
                if (block.blockId == blockId) {
                    removeBlockFromWaitingQueue(block);
                }
            }
        }

        public synchronized void removeBlockFromSendingQueue(Block block) throws InterruptedException {
            if(sendingBlocksQueue.contains(block))
                sendingBlocksQueue.remove(block);

            for (Packet p : block.packets.values()) {
                removePacketFromSendingQueue(p);
            }
        }

        public synchronized void removePacketFromSendingQueue(Packet packet) throws InterruptedException {
            if(sendingPacketsQueue.contains(packet))
                sendingPacketsQueue.remove(packet);

            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.blockId == packet.blockId) {
                    sendingBlock.markPacketAsDelivered(packet);
                    report(getLabel(), " markPacketAsDelivered, packets num: " + sendingBlock.packets.size() + " diagrams num: " + sendingBlock.datagrams.size());
                }
            }
            report(getLabel(), " remove packet from queue, blockId: " + packet.blockId + " packetId: " + packet.packetId + " type: " + packet.type + " sendWaitIndex: " + packet.sendWaitIndex + " delivered: " + packet.delivered);

        }

        public synchronized void incremetWaitIndexForPacketsFromSendingQueue() throws InterruptedException {
//            if(sendingPacketsQueue.peek() != null)
//                sendingPacketsQueue.peek().sendWaitIndex++;
//            Object[] sp = sendingPacketsQueue.toArray();
//            if(sp.length > 0) ((Packet) sp[0]).sendWaitIndex ++;
//            if(sp.length > 1) ((Packet) sp[1]).sendWaitIndex ++;
//            if(sp.length > 2) ((Packet) sp[2]).sendWaitIndex ++;

//            if(sp.length > 0) {
//                report(getLabel(), " incremet peek: " + sendingPacketsQueue.peek().blockId + " " + sendingPacketsQueue.peek().packetId);
//                report(getLabel(), " incremet array: " + ((Packet) sp[0]).blockId + " " + ((Packet) sp[0]).packetId);
//            }

            for (Packet p : sendingPacketsQueue) {
                p.sendWaitIndex++;
                report(getLabel(), " packet, blockId: " + p.blockId + " packetId: " + p.packetId + " type: " + p.type + " sendWaitIndex: " + p.sendWaitIndex);
            }
        }

        public synchronized void removePacketFromSendingQueue(int blockId, int packetId) throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.blockId == blockId) {
                    for (Packet p : sendingBlock.packets.values()) {
                        if(p.packetId == packetId) {
                            removePacketFromSendingQueue(p);
                        }
                    }
                }
            }
        }

        public synchronized void makeBlockDelivered(int blockId) throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.blockId == blockId) {
                    removeBlockFromSendingQueue(sendingBlock);
                    sendingBlock.delivered = true;
                    report(getLabel(), "block " + sendingBlock.blockId + " delivered");
                }
            }
        }

        public synchronized void moveBlocksFromSendingToWaiting() throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                sendingBlock.validToSend = false;
                removeBlockFromSendingQueue(sendingBlock);
                addBlockToWaitingQueue(sendingBlock);
            }
        }

        public synchronized void removeDataBlocksFromWaiting() throws InterruptedException {
            for (Block block : waitingBlocksQueue) {
                if(block.type == PacketTypes.DATA) {
                    removeBlockFromWaitingQueue(block);
                }
            }
        }

        public synchronized void makeBlockDeliveredByType(int type) throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.type == type) {
                    removeBlockFromSendingQueue(sendingBlock);
                    sendingBlock.delivered = true;
                }
            }
        }

        public synchronized Block getRawDataBlockFromWaitingQueue(int blockId) throws InterruptedException {
            for (Block block : waitingBlocksQueue) {
                if (block.blockId == blockId && block.type == PacketTypes.RAW_DATA) {
                    return block;
                }
            }

            return null;
        }

    }
}
