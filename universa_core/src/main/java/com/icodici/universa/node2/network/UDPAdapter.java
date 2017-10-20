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

import static java.util.Arrays.asList;

public class UDPAdapter extends DatagramAdapter {

    static private LogPrinter log = new LogPrinter("UDPA");

    private DatagramSocket socket;
    private DatagramPacket receivedDatagram;

    private SocketListenThread socketListenThread;

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

        byte[] buf = new byte[DatagramAdapter.MAX_PACKET_SIZE];
        receivedDatagram = new DatagramPacket(buf, buf.length);

        socketListenThread = new SocketListenThread();
        socketListenThread.start();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkUnsent();
            }
        }, RETRANSMIT_TIME, RETRANSMIT_TIME);
    }


    @Override
    public void send(NodeInfo destination, byte[] payload) throws EncryptionError, InterruptedException {
        System.out.println(getLabel() + "send to " + destination.getId());

        Session session = sessionsById.get(destination.getId());

        Block rawBlock = new Block(myNodeInfo.getId(), destination.getId(),
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.RAW_DATA,
                destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort(),
                payload);

        if (session != null && session.isValid()) {
            if(session.state == Session.EXCHANGING) {
                System.out.println(getLabel() + "session is ok");

                session.addBlockToWaitingQueue(rawBlock);
                sendAsDataBlock(rawBlock, session);
            } else {
                System.out.println(getLabel() + "session not valid yet");
                session.addBlockToWaitingQueue(rawBlock);
            }
        } else {
            System.out.println(getLabel() + "session not exist");
            session = createSession(destination.getId(),
                    destination.getNodeAddress().getAddress(),
                    destination.getNodeAddress().getPort());
            session.publicKey = destination.getPublicKey();
            session.remoteNodeId = destination.getId();
            session.addBlockToWaitingQueue(rawBlock);
            sendHello(session);
        }
    }


    @Override
    public void shutdown() {
        socketListenThread.shutdownThread();
        socket.close();
        socket.disconnect();
        sessionsById.clear();
    }


    public String getLabel()
    {
        return myNodeInfo.getId() + "-0: ";
    }


    protected void sendBlock(Block block, Session session) throws InterruptedException {

        block.splitByPackets(MAX_PACKET_SIZE);
//        List<DatagramPacket> outs = makeDatagramPacketsFromBlock(block, session.address, session.port);
        List<DatagramPacket> outs = new ArrayList(block.datagrams.values());

        block.sendAttempts++;
        try {
            if(testMode == TestModes.SHUFFLE_PACKETS || testMode == TestModes.LOST_AND_SHUFFLE_PACKETS) {
                Collections.shuffle(outs);
            }

            System.out.println(getLabel() + "sending packets num:  " + outs.size());
            for (DatagramPacket d : outs) {
                if(testMode == TestModes.LOST_PACKETS || testMode == TestModes.LOST_AND_SHUFFLE_PACKETS) {
                    if (new Random().nextBoolean()) {
                        String lst = getLabel() + " Lost packet in block: " + block.blockId;
                        System.out.println(lst);
                        continue;
                    }
                }
                socket.send(d);
            }
        } catch (IOException e) {
            System.out.println(getLabel() + "send block error, socket already closed");
//            e.printStackTrace();
        }
        if(block.type != PacketTypes.PACKET_ACK) {
            session.addBlockToSendingQueue(block);
        }
    }


    protected void sendAsDataBlock(Block rawDataBlock, Session session) throws EncryptionError, InterruptedException {
        System.out.println(getLabel() + "send data to " + session.remoteNodeId);
        byte[] encrypted = session.sessionKey.etaEncrypt(rawDataBlock.payload);

        Binder binder = Binder.fromKeysValues(
                "data", encrypted
        );

        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                rawDataBlock.blockId, PacketTypes.DATA,
                session.address, session.port,
                Boss.pack(binder));
        sendBlock(block, session);
    }


    protected void sendHello(Session session) throws EncryptionError, InterruptedException {
        System.out.println(getLabel() + "send hello to " + session.remoteNodeId);

        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.HELLO,
                session.address, session.port,
                myNodeInfo.getPublicKey().pack());
        sendBlock(block, session);
    }


    protected void sendWelcome(Session session) throws InterruptedException {
        System.out.println(getLabel() + "send welcome to " + session.remoteNodeId);

        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.WELCOME,
                session.address, session.port,
                session.localNonce);
        sendBlock(block, session);
    }


    protected void sendKeyRequest(Session session) throws InterruptedException {
        System.out.println(getLabel() + "send key request to " + session.remoteNodeId);

        List data = asList(session.localNonce, session.remoteNonce);
        try {
            byte[] packed = Boss.pack(data);
            byte[] signed = ownPrivateKey.sign(packed, HashType.SHA512);

            Binder binder = Binder.fromKeysValues(
                    "data", packed,
                    "signature", signed
            );

            Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                    new Random().nextInt(Integer.MAX_VALUE), PacketTypes.KEY_REQ,
                    session.address, session.port,
                    Boss.pack(binder));
            sendBlock(block, session);
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }


    protected void sendSessionKey(Session session) throws InterruptedException {
        System.out.println(getLabel() + "send session key to " + session.remoteNodeId);

        List data = asList(session.sessionKey.getKey(), session.remoteNonce);
        try {
            byte[] packed = Boss.pack(data);
            byte[] encrypted = session.publicKey.encrypt(packed);
            byte[] signed = ownPrivateKey.sign(encrypted, HashType.SHA512);

            Binder binder = Binder.fromKeysValues(
                    "data", encrypted,
                    "signature", signed
            );

            Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                    new Random().nextInt(Integer.MAX_VALUE), PacketTypes.SESSION,
                    session.address, session.port,
                    Boss.pack(binder));
            sendBlock(block, session);
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }


    protected void sendPacketAck(Session session, int blockId, int packetId) throws InterruptedException {
        System.out.println(getLabel() + "send packet_ack to " + session.remoteNodeId);

        List data = asList(blockId, packetId);
        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.PACKET_ACK,
                session.address, session.port,
                Boss.pack(data));
        sendBlock(block, session);
    }


    protected void sendAck(Session session, int blockId) throws InterruptedException {
        System.out.println(getLabel() + "send ack to " + session.remoteNodeId);

        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.ACK,
                session.address, session.port,
                Boss.pack(blockId));
        sendBlock(block, session);
    }


    protected void sendNack(Session session, int blockId) throws InterruptedException {
        System.out.println(getLabel() + "send nack to " + session.remoteNodeId);

        Block block = new Block(myNodeInfo.getId(), session.remoteNodeId,
                new Random().nextInt(Integer.MAX_VALUE), PacketTypes.NACK,
                session.address, session.port,
                Boss.pack(blockId));
        sendBlock(block, session);
    }


//    protected List<DatagramPacket> makeDatagramPacketsFromBlock(Block block, InetAddress address, int port) {
//
//        block.splitByPackets(MAX_PACKET_SIZE);
//
//        return datagramPackets;
//
//    }


    protected Session createSession(int remoteId, InetAddress address, int port) throws EncryptionError {

        Session session;

        session = new Session(address, port);
        System.out.println(getLabel() + "session created for nodeId " + remoteId);
        session.remoteNodeId = remoteId;
        session.sessionKey = sessionKey;
        sessionsById.put(remoteId, session);

        return session;

    }


    protected void checkUnsent() {
        List<Block> blocksToRemove;
        for(Session session : sessionsById.values()) {
            blocksToRemove = new ArrayList();
            for (Block block : session.sendingBlocksQueue) {
                if(!block.isDelivered()) {
                    System.out.println(getLabel() + "block: " + block.blockId + " type: " + block.type + " sendAttempts: " + block.sendAttempts + " not delivered");
                    try {
                        if(block.sendAttempts >= RETRANSMIT_MAX_ATTEMPTS) {
                            System.out.println(getLabel() + "block " + block.blockId + " type " + block.type + " will be removed");
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
                    session.removeBlockFromSendingQueue(rb);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


//    public byte[] serialize(Object obj) throws IOException {
//        try(ByteArrayOutputStream b = new ByteArrayOutputStream()){
//            try(ObjectOutputStream o = new ObjectOutputStream(b)){
//                o.writeObject(obj);
//            }
//            return b.toByteArray();
//        }
//    }
//
//    public Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
//        try(ByteArrayInputStream b = new ByteArrayInputStream(bytes)){
//            try(ObjectInputStream o = new ObjectInputStream(b)){
//                return o.readObject();
//            }
//        }
//    }


    class SocketListenThread extends Thread
    {
        private Boolean active = false;

        private HashMap<Integer, Block> waitingBlocks = new HashMap<>();

        private HashMap<Integer, Block> obtainedBlocks = new HashMap<>();

        @Override
        public void run()
        {
            setName(Integer.toString(new Random().nextInt(100)));
            System.out.println(getLabel() + " UDPAdapter listen socket at " + myNodeInfo.getNodeAddress().getAddress() + ":" + myNodeInfo.getNodeAddress().getPort());
            active = true;
            while(active) {
                try {
                    if(!socket.isClosed()) {
                        socket.receive(receivedDatagram);
                    }
                } catch (SocketException e) {
//                    e.printStackTrace();
                } catch (IOException e) {
//                    e.printStackTrace();
                }

                String rcvd = getLabel() + " Got data from address: " + receivedDatagram.getAddress() + ", port: " + receivedDatagram.getPort();
//                System.out.println(rcvd);


                byte[] data = Arrays.copyOfRange(receivedDatagram.getData(), 0, receivedDatagram.getLength());

                Packet packet = new Packet();
                Block waitingBlock = null;
                try {
                    packet.parseFromByteArray(data);

                    if (waitingBlocks.containsKey(packet.blockId)) {
                        waitingBlock = waitingBlocks.get(packet.blockId);
                    } else {
                        if (obtainedBlocks.containsKey(packet.blockId)) {
                            // Do nothing, cause we got and obtained this block already
                            System.out.println(getLabel() + " warning: repeated block given, with id " + packet.blockId);
                        } else {
                            waitingBlock = new Block(packet.senderNodeId, packet.receiverNodeId,
                                    packet.blockId, packet.type,
                                    receivedDatagram.getAddress(), receivedDatagram.getPort());
                            waitingBlocks.put(waitingBlock.blockId, waitingBlock);
                        }
//                        waitingBlock = new Block(packet.senderNodeId, packet.receiverNodeId, packet.blockId, packet.type);
//                        waitingBlocks.put(waitingBlock.blockId, waitingBlock);
                    }

                    if(waitingBlock != null) {
                        waitingBlock.addToPackets(packet);

                        if (waitingBlock.isSolid()) {
                            moveWaitingBlockToObtained(waitingBlock);
                            waitingBlock.reconstruct();
                            obtainSolidBlock(waitingBlock);
                        } else {
                            if(packet.type != PacketTypes.PACKET_ACK) {
                                Session session = sessionsById.get(packet.senderNodeId);
                                if (session == null) {
                                    session = createSession(packet.senderNodeId, receivedDatagram.getAddress(), receivedDatagram.getPort());
                                }
                                sendPacketAck(session, packet.blockId, packet.packetId);
                            }
                        }
                    }

                } catch (IllegalArgumentException e) {
//                    e.printStackTrace();
                } catch (InterruptedException e) {
//                    e.printStackTrace();
                } catch (IOException e) {
//                    e.printStackTrace();
                }
            }
        }


        public void shutdownThread()
        {
            active = false;
        }


        public String getLabel()
        {
            return myNodeInfo.getId() + "-" + getName() + ": ";
        }


        protected void obtainSolidBlock(Block block) throws EncryptionError, SymmetricKey.AuthenticationFailed, InterruptedException {
            Session session = null;
            Binder unbossedPayload;
            byte[] signedUnbossed;
            List ackList;
            int ackBlockId;
            int ackPacketId;

            switch (block.type) {

                case PacketTypes.HELLO:
                    System.out.println(getLabel() + " got hello from " + block.senderNodeId);
                    PublicKey key = new PublicKey(block.payload);
                    session = sessionsById.get(block.senderNodeId);
                    session.publicKey = key;
                    sendWelcome(session);
                    break;

                case PacketTypes.WELCOME:
                    System.out.println(getLabel() + " got welcome from " + block.senderNodeId);
                    session = sessionsById.get(block.senderNodeId);
                    session.remoteNonce = block.payload;
                    session.makeBlockDeliveredByType(PacketTypes.HELLO);
                    sendKeyRequest(session);
                    break;

                case PacketTypes.KEY_REQ:
                    System.out.println(getLabel() + " got key request from " + block.senderNodeId);
                    session = sessionsById.get(block.senderNodeId);
                    session.makeBlockDeliveredByType(PacketTypes.WELCOME);
                    unbossedPayload = Boss.load(block.payload);
                    signedUnbossed = unbossedPayload.getBinaryOrThrow("data");
                    try {
                        if (session.publicKey != null) {
                            if (session.publicKey.verify(signedUnbossed, unbossedPayload.getBinaryOrThrow("signature"), HashType.SHA512)) {

                                System.out.println(getLabel() + " successfully verified ");

                                List receivedData = Boss.load(signedUnbossed);
                                byte[] senderNonce = ((Bytes) receivedData.get(0)).toArray();
                                byte[] receiverNonce = ((Bytes) receivedData.get(1)).toArray();

                                // if remote nonce from received data equals with own nonce
                                // (means request sent after welcome from known and expected node)
                                if (Arrays.equals(receiverNonce, session.localNonce)) {
                                    session.remoteNonce = senderNonce;
                                    session.createSessionKey();
                                    System.out.println(getLabel() + " check session " + session.isValid());
                                    sendSessionKey(session);
//                                    session.makeBlockDeliveredByType(PacketTypes.SESSION);
                                } else {
                                    System.out.println(Errors.BAD_VALUE + " got nonce is not valid");
                                }
                            } else {
                                System.out.println(Errors.BAD_VALUE + " no public key");
                            }
                        }
                    } catch (EncryptionError e) {
                        System.out.println(Errors.BAD_VALUE + " sign has not verified, " + e.getMessage());
                    }
                    break;

                case PacketTypes.SESSION:
                    System.out.println(getLabel() + " got session from " + block.senderNodeId);
                    session = sessionsById.get(block.senderNodeId);
                    session.makeBlockDeliveredByType(PacketTypes.KEY_REQ);
                    unbossedPayload = Boss.load(block.payload);
                    signedUnbossed = unbossedPayload.getBinaryOrThrow("data");
                    try {
                        if (session.publicKey.verify(signedUnbossed, unbossedPayload.getBinaryOrThrow("signature"), HashType.SHA512)) {

                            System.out.println(getLabel() + " successfully verified ");

                            byte[] decryptedData = ownPrivateKey.decrypt(signedUnbossed);
                            List receivedData = Boss.load(decryptedData);
                            byte[] sessionKey = ((Bytes) receivedData.get(0)).toArray();
                            byte[] receiverNonce = ((Bytes) receivedData.get(1)).toArray();

                            // if remote nonce from received data equals with own nonce
                            // (means session sent as answer to key request from known and expected node)
                            if(Arrays.equals(receiverNonce, session.localNonce)) {
                                session.reconstructSessionKey(sessionKey);
                                System.out.println(getLabel() + " check session " + session.isValid());

                                // Tell remote nonce we got session and no need to resend it.
                                answerAckOrNack(session, block, receivedDatagram.getAddress(), receivedDatagram.getPort());

                                if(session != null && session.isValid() && session.state == Session.EXCHANGING) {
                                    System.out.println(getLabel() + " waiting blocks num " + session.waitingBlocksQueue.size());
                                    try {
                                        for (Block waitingBlock : session.waitingBlocksQueue) {
                                            System.out.println(getLabel() + " waitingBlock " + waitingBlock.blockId + " type " + waitingBlock.type);
                                            if (waitingBlock.type == PacketTypes.RAW_DATA) {
                                                sendAsDataBlock(waitingBlock, session);
                                            } else {
                                                sendBlock(waitingBlock, session);
                                            }
                                            session.removeBlockFromWaitingQueue(waitingBlock);
                                        }
                                    } catch (InterruptedException e) {
                                        System.out.println(Errors.BAD_VALUE + " send encryption error, " + e.getMessage());
                                    }
                                }
                            } else {
                                System.out.println(Errors.BAD_VALUE + " got nonce is not valid");
                            }
                        }
                    } catch (EncryptionError e) {
                        System.out.println(Errors.BAD_VALUE + " sign has not verified, " + e.getMessage());
                    }
                    break;

                case PacketTypes.DATA:
                    System.out.println(getLabel() + " got data from " + block.senderNodeId);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null && session.isValid() && session.state == Session.EXCHANGING) {
                        unbossedPayload = Boss.load(block.payload);
                        byte[] decrypted = session.sessionKey.etaDecrypt(unbossedPayload.getBinaryOrThrow("data"));

                        receiver.accept(decrypted);
                    }
                    answerAckOrNack(session, block, receivedDatagram.getAddress(), receivedDatagram.getPort());
                    break;

                case PacketTypes.ACK:
                    System.out.println(getLabel() + " got ack from " + block.senderNodeId);
                    ackBlockId = Boss.load(block.payload);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null && session.isValid()) {
                        session.makeBlockDelivered(ackBlockId);
                    }
                    break;

                case PacketTypes.NACK:
                    System.out.println(getLabel() + " got nack from " + block.senderNodeId);
                    ackBlockId = Boss.load(block.payload);
                    System.out.println(getLabel() + " blockId: " + ackBlockId);

                    session = sessionsById.get(block.senderNodeId);
                    if(session != null && session.isValid()) {
                        session.moveBlocksFromSendingToWaiting();
                        session.state = Session.HANDSHAKE;
                        session.sessionKey = sessionKey;
                        sendHello(session);
                    }
                    break;

                case PacketTypes.PACKET_ACK:
                    ackList = Boss.load(block.payload);
                    ackBlockId = (int) ackList.get(0);
                    ackPacketId = (int) ackList.get(1);
                    System.out.println(getLabel() + " got packet_ack from " + block.senderNodeId + " for block id " + ackBlockId);
                    session = sessionsById.get(block.senderNodeId);
                    if(session != null) {
                        System.out.println(getLabel() + " num packets was in queue: " + session.sendingPacketsQueue.size());
                        session.removePacketFromSendingQueue(ackBlockId, ackPacketId);
                        session.incremetWaitIndexForPacketsFromSendingQueue();
                        System.out.println(getLabel() + " num packets in queue: " + session.sendingPacketsQueue.size());
                    }
                    break;
            }
        }


        public void moveWaitingBlockToObtained(Block block) {
            waitingBlocks.remove(block.blockId);
            obtainedBlocks.put(block.blockId, block);
        }


        public void answerAckOrNack(Session session, Block block, InetAddress address, int port) throws InterruptedException, EncryptionError {
            if(session != null && session.isValid() && session.state == Session.EXCHANGING) {

                sendAck(session, block.blockId);
            } else {
                session = createSession(block.senderNodeId, address, port);
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

        public byte[] makeByteArray() {
            List data = asList(brotherPacketsNum, packetId, senderNodeId, receiverNodeId, blockId, type, new Bytes(payload));
            Bytes byteArray = Boss.dump(data);
            return byteArray.toArray();
        }

        public void parseFromByteArray(byte[] byteArray) throws IOException {

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
        private int sendAttempts;
        private InetAddress address;
        private int port;

        private ConcurrentHashMap<Integer, Packet> packets;
        private ConcurrentHashMap<Integer, DatagramPacket> datagrams;

        private Boolean delivered = false;

        public Block() {
            packets = new ConcurrentHashMap<>();
        }

        public Block(int senderNodeId, int receiverNodeId, int blockId, int type, InetAddress address, int port, byte[] payload) {
            this.senderNodeId = senderNodeId;
            this.receiverNodeId = receiverNodeId;
            this.blockId = blockId;
            this.type = type;
            this.address = address;
            this.port = port;
            this.payload = payload;

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

        public ConcurrentHashMap<Integer, Packet> splitByPackets(int packetSize) {
            packets = new ConcurrentHashMap<>();
            datagrams = new ConcurrentHashMap<>();

            List headerData = asList(0, 0, senderNodeId, receiverNodeId, blockId, type);
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
                packets.putIfAbsent(packetId, packet);

                blockByteArray = packet.makeByteArray();
                datagramPacket = new DatagramPacket(blockByteArray, blockByteArray.length, address, port);
                datagrams.putIfAbsent(packetId, datagramPacket);

                offset += copySize;
                packetId++;
            }

//            for (Packet packet : packets.values()) {
//            }

            return packets;
        }

        public void reconstruct() throws IOException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            for (Packet packet : packets.values()) {
                outputStream.write(packet.payload);
            }
            payload = outputStream.toByteArray();
        }

        public void addToPackets(Packet packet) {
            if(!packets.containsKey(packet.packetId)) {
                packets.putIfAbsent(packet.packetId, packet);
            }
        }

        public Boolean isSolid() {
            if(packets.size() > 0) {
                // packets.get(0) may be null because packets can received in random order
                if(packets.get(0) != null && packets.get(0).brotherPacketsNum == packets.size()) {
                    return true;
                }
            }

            return false;
        }

        public Boolean isDelivered() {
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
        private int remoteNodeId = 0;

        private int state;

        static public final int HANDSHAKE =      0;
        static public final int EXCHANGING =     1;

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

        public Boolean isValid() {
            if (localNonce == null) {
                return false;
            }
            if (remoteNonce == null) {
                return false;
            }
            if (sessionKey == null) {
                return false;
            }
            if (publicKey == null) {
                return false;
            }
            if (remoteNodeId == 0) {
                return false;
            }
//            if (state != EXCHANGING) {
//                return false;
//            }

            return true;
        }

        public void createSessionKey() throws EncryptionError {
            if (sessionKey == null) {
                sessionKey = new SymmetricKey();
            }
            state = EXCHANGING;
        }

        public void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
            state = EXCHANGING;
        }

        public void addBlockToWaitingQueue(Block block) throws InterruptedException {
            if(!waitingBlocksQueue.contains(block))
                waitingBlocksQueue.put(block);
        }

        public void addBlockToSendingQueue(Block block) throws InterruptedException {
            if(!sendingBlocksQueue.contains(block))
                sendingBlocksQueue.put(block);

            for (Packet p : block.packets.values()) {
                addPacketToSendingQueue(p);
            }
        }

        public void addPacketToSendingQueue(Packet packet) throws InterruptedException {
            if(!sendingPacketsQueue.contains(packet))
                sendingPacketsQueue.put(packet);
        }

        public void removeBlockFromWaitingQueue(Block block) throws InterruptedException {
            if(waitingBlocksQueue.contains(block))
                waitingBlocksQueue.remove(block);
        }

        public void removeBlockFromSendingQueue(Block block) throws InterruptedException {
            if(sendingBlocksQueue.contains(block))
                sendingBlocksQueue.remove(block);

            for (Packet p : block.packets.values()) {
                removePacketFromSendingQueue(p);
            }
        }

        public void removePacketFromSendingQueue(Packet packet) throws InterruptedException {
            if(sendingPacketsQueue.contains(packet))
                sendingPacketsQueue.remove(packet);
        }

        public void incremetWaitIndexForPacketsFromSendingQueue() throws InterruptedException {
            for (Packet p : sendingPacketsQueue) {
                p.sendWaitIndex++;
            }
//            System.out.println("BEFORE: " + sendingPacketsQueue.size());
//            sendingPacketsQueue.poll().sendWaitIndex ++;
//            sendingPacketsQueue.poll().sendWaitIndex ++;
//            sendingPacketsQueue.poll().sendWaitIndex ++;
//            System.out.println("AFTER: " + sendingPacketsQueue.size());
        }

        public void removePacketFromSendingQueue(int blockId, int packetId) throws InterruptedException {
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

        public void makeBlockDelivered(int blockId) throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.blockId == blockId) {
                    removeBlockFromSendingQueue(sendingBlock);
                    sendingBlock.delivered = true;
                }
            }
        }

        public void moveBlocksFromSendingToWaiting() throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                removeBlockFromSendingQueue(sendingBlock);
                addBlockToWaitingQueue(sendingBlock);
            }
        }

        public void makeBlockDeliveredByType(int type) throws InterruptedException {
            for (Block sendingBlock : sendingBlocksQueue) {
                if (sendingBlock.type == type) {
                    removeBlockFromSendingQueue(sendingBlock);
                    sendingBlock.delivered = true;
                }
            }
        }

    }
}
