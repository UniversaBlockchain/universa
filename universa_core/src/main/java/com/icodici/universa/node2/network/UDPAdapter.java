package com.icodici.universa.node2.network;

import com.icodici.crypto.*;
import com.icodici.crypto.digest.Crc32;
import com.icodici.universa.Errors;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class UDPAdapter extends DatagramAdapter {

    private DatagramSocket socket;
    private SocketListenThread socketListenThread;
    private ConcurrentHashMap<Integer, Session> sessionsByRemoteId = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, SessionReader> sessionReaders = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, SessionReader> sessionReaderCandidates = new ConcurrentHashMap<>();
    private String logLabel = "";
    private Integer nextPacketId = 1;
    private Timer timerHandshake = new Timer();
    private Timer timerRetransmit = new Timer();
    private Timer timerProtectionFromDuple = new Timer();

    private final static int HANDSHAKE_TIMEOUT_MILLIS = 10000;


    /**
     * Create an instance that listens for the incoming datagrams using the specified configurations. The adapter should
     * start serving incoming datagrams immediately upon creation.
     *
     * @param ownPrivateKey is {@link PrivateKey} for signing requests
     * @param sessionKey is {@link SymmetricKey} with session
     * @param myNodeInfo is {@link NodeInfo} object described node this UDPAdapter work with
     * @param netConfig is {@link NetConfig} where all nodes data is stored
     * @throws IOException if something went wrong
     */
    public UDPAdapter(PrivateKey ownPrivateKey, SymmetricKey sessionKey, NodeInfo myNodeInfo, NetConfig netConfig) throws IOException {
        super(ownPrivateKey, sessionKey, myNodeInfo, netConfig);

        logLabel = "udp" + myNodeInfo.getNumber() + ": ";

        nextPacketId = new Random().nextInt(Integer.MAX_VALUE)+1;

        socket = new DatagramSocket(myNodeInfo.getNodeAddress().getPort());
        socket.setReuseAddress(true);

        socketListenThread = new SocketListenThread(socket);
        socketListenThread.start();

        timerHandshake.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                restartHandshakeIfNeeded();
            }
        }, RETRANSMIT_TIME, RETRANSMIT_TIME);

        timerRetransmit.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pulseRetransmit();
            }
        }, RETRANSMIT_TIME, RETRANSMIT_TIME);

        timerProtectionFromDuple.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clearProtectionFromDupleBuffers();
            }
        }, 6*RETRANSMIT_TIME*RETRANSMIT_MAX_ATTEMPTS, 6*RETRANSMIT_TIME*RETRANSMIT_MAX_ATTEMPTS);
    }


    @Override
    synchronized public void send(NodeInfo destination, byte[] payload) throws InterruptedException {
        report(logLabel, () -> "send to "+destination.getNumber()+", isActive: "+socketListenThread.isActive.get(), VerboseLevel.DETAILED);

        if (!socketListenThread.isActive.get())
            return;

        Session session = getOrCreateSession(destination);
        if (session.state.get() == Session.STATE_HANDSHAKE) {
            session.addPayloadToOutputQueue(destination, payload);
        } else {
            sendPayload(session, payload);
        }
    }


    private void sendPacket(NodeInfo destination, Packet packet) {
        byte[] payload = packet.makeByteArray();
        DatagramPacket dp = new DatagramPacket(payload, payload.length, destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort());
        try {
            report(logLabel, ()->"sendPacket datagram size: " + payload.length, VerboseLevel.DETAILED);
            if ((testMode == TestModes.LOST_PACKETS || testMode == TestModes.LOST_AND_SHUFFLE_PACKETS)
                && (new Random().nextInt(100) < lostPacketsPercent))
                report(logLabel, ()->"test mode: skip socket.send", VerboseLevel.BASE);
            else
                socket.send(dp);
        } catch (Exception e) {
            callErrorCallbacks("sendPacket exception: " + e);
        }
    }


    private byte[] preparePayloadForSession(Session session, byte[] payload) {
        try {
            byte[] payloadWithRandomChunk = new byte[payload.length + 2];
            System.arraycopy(payload, 0, payloadWithRandomChunk, 0, payload.length);
            System.arraycopy(Bytes.random(2).toArray(), 0, payloadWithRandomChunk, payload.length, 2);
            byte[] encryptedPayload = new SymmetricKey(session.sessionKey.getKey()).etaEncrypt(payloadWithRandomChunk);
            byte[] crc32 = new Crc32().digest(encryptedPayload);
            byte[] dataToSend = new byte[encryptedPayload.length + crc32.length];
            System.arraycopy(encryptedPayload, 0, dataToSend, 0, encryptedPayload.length);
            System.arraycopy(crc32, 0, dataToSend, encryptedPayload.length, crc32.length);
            return dataToSend;
        } catch (EncryptionError e) {
            callErrorCallbacks("(preparePayloadForSession) EncryptionError: " + e);
            return payload;
        }
    }


    private void sendPayload(Session session, byte[] payload) {
        byte[] dataToSend = preparePayloadForSession(session, payload);
        Packet packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), PacketTypes.DATA, dataToSend);
        sendPacket(session.remoteNodeInfo, packet);
        session.addPacketToRetransmitMap(packet.packetId, packet, payload);
    }


    synchronized private Integer getNextPacketId() {
        Integer res = nextPacketId;
        if (nextPacketId == Integer.MAX_VALUE)
            nextPacketId = 1;
        else
            ++nextPacketId;
        return res;
    }


    @Override
    public void shutdown() {
        report(logLabel, ()->"shutting down...", VerboseLevel.BASE);
        socketListenThread.isActive.set(false);
        socket.close();
        timerHandshake.cancel();
        timerHandshake.purge();
        timerRetransmit.cancel();
        timerRetransmit.purge();
        timerProtectionFromDuple.cancel();
        timerProtectionFromDuple.purge();
        try {
            socketListenThread.join();
        } catch (InterruptedException e) {
            report(logLabel, ()->"shutting down... InterruptedException: "+e, VerboseLevel.BASE);
        }
        report(logLabel, ()->"shutting down... done", VerboseLevel.BASE);
    }


    public void report(String label, Callable<String> message, int level)
    {
        if(level <= verboseLevel) {
            try {
                System.out.println(label + message.call());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * Use {@link DatagramAdapter#addErrorsCallback(Function)} to add callback to catch errors. This method call this backs.
     * @param message
     */
    private void callErrorCallbacks(String message) {
        report(logLabel, ()->"error: " + message, VerboseLevel.BASE);
        for(Function<String, String> fn : errorCallbacks) {
            fn.apply(message);
        }
    }


    /**
     * If session for remote id is already created - returns it, otherwise creates new {@link Session}
     * @return
     */
    private Session getOrCreateSession(NodeInfo destination) {
        Session s = sessionsByRemoteId.computeIfAbsent(destination.getNumber(), (k)->{
            Session session = new Session(destination);
            report(logLabel, ()->"session created for nodeId "+destination.getNumber(), VerboseLevel.BASE);
            session.sessionKey = sessionKey;
            return session;
        });
        report(logLabel, ()->">>local node: "+myNodeInfo.getNumber()+" remote node: "+s.remoteNodeInfo.getNumber(), VerboseLevel.DETAILED);
        report(logLabel, ()->">>local nonce: "+s.localNonce+" remote nonce: "+s.remoteNonce, VerboseLevel.DETAILED);
        report(logLabel, ()->">>state: "+s.state, VerboseLevel.DETAILED);
        report(logLabel, ()->">>session key: "+s.sessionKey.hashCode(), VerboseLevel.DETAILED);
        return s;
    }


    private Session getOrCreateSession(int remoteId) {
        NodeInfo destination = netConfig.getInfo(remoteId);
        if (destination == null) {
            callErrorCallbacks("(getOrCreateSession) unknown nodeId has received: "+remoteId);
            return null;
        }
        return getOrCreateSession(destination);
    }


    private SessionReader getOrCreateSessionReaderCandidate(int remoteId) {
        NodeInfo destination = netConfig.getInfo(remoteId);
        if (destination == null) {
            callErrorCallbacks("(getOrCreateSessionReaderCandidate) unknown nodeId has received: "+remoteId);
            return null;
        }
        SessionReader sr = sessionReaderCandidates.computeIfAbsent(destination.getNumber(), (k)-> {
            SessionReader sessionReader = new SessionReader();
            sessionReader.remoteNodeInfo = Boss.load(Boss.dumpToArray(destination));
            report(logLabel, ()->"sessionReader created for nodeId "+destination.getNumber(), VerboseLevel.BASE);
            return sessionReader;
        });
        return sr;
    }


    private void acceptSessionReaderCandidate(int remoteId, SessionReader sessionReader) {
        sessionReaders.put(remoteId, sessionReader);
        sessionReaderCandidates.remove(remoteId);
    }


    private SessionReader getSessionReader(int remoteId) {
        return sessionReaders.get(remoteId);
    }


    private void restartHandshakeIfNeeded() {
        Instant now = Instant.now();
        sessionsByRemoteId.forEach((k, s) -> restartHandshakeIfNeeded(s, now));
    }


    private void pulseRetransmit() {
        sessionsByRemoteId.forEach((k, s)->s.pulseRetransmit());
        sessionReaders.forEach((k, sr) -> sr.pulseRetransmit());
        sessionReaderCandidates.forEach((k, sr) -> sr.pulseRetransmit());
    }


    private void clearProtectionFromDupleBuffers() {
        sessionReaders.forEach((k, sr)-> sr.clearProtectionFromDupleBuffers());
        sessionReaderCandidates.forEach((k, sr)-> sr.clearProtectionFromDupleBuffers());
        sessionsByRemoteId.forEach((k, s)-> s.clearProtectionFromDupleBuffers());
    }


    private void restartHandshakeIfNeeded(Session s, Instant now) {
        if (s.state.get() == Session.STATE_HANDSHAKE) {
            if (s.handshakeExpiresAt.isBefore(now)) {
                report(logLabel, ()->"handshaking with nodeId="+s.remoteNodeInfo.getNumber()+" is timed out, restart", VerboseLevel.BASE);
                s.handshakeStep.set(Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME);
                s.handshakeExpiresAt = Instant.now().plusMillis(HANDSHAKE_TIMEOUT_MILLIS);
                sendHello(s);
            }
        }
    }


    public void printInternalState() {
        System.out.println("\nprintInternalState "+logLabel);
        System.out.println("  inputQueue.size(): " + inputQueue.size());
        sessionsByRemoteId.forEach((k, s)->{
            System.out.println("  session with node="+k+":");
            System.out.println("    outputQueue.size(): " + s.outputQueue.size());
            System.out.println("    retransmitMap.size(): " + s.retransmitMap.size());
            System.out.println("    protectionFromDuple0.size(): " + s.protectionFromDuple0.size());
            System.out.println("    protectionFromDuple1.size(): " + s.protectionFromDuple1.size());
        });
        sessionReaders.forEach((k, sr)-> {
            System.out.println("  sessionReader with node="+k+":");
            System.out.println("    retransmitMap.size(): " + sr.retransmitMap.size());
            System.out.println("    protectionFromDuple0.size(): " + sr.protectionFromDuple0.size());
            System.out.println("    protectionFromDuple1.size(): " + sr.protectionFromDuple1.size());
        });
        sessionReaderCandidates.forEach((k, sr)-> {
            System.out.println("  sessionReaderCandidates with node="+k+":");
            System.out.println("    retransmitMap.size(): " + sr.retransmitMap.size());
            System.out.println("    protectionFromDuple0.size(): " + sr.protectionFromDuple0.size());
            System.out.println("    protectionFromDuple1.size(): " + sr.protectionFromDuple1.size());
        });
    }


    /**
     * This is first step of creation and installation of the session.
     * @param session is {@link Session} in which sending is.
     */
    private void sendHello(Session session) {
        try {
            report(logLabel, () -> "send hello to " + session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
            byte[] helloNonce = Do.randomBytes(64);
            Packet packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                    session.remoteNodeInfo.getNumber(), PacketTypes.HELLO, new PublicKey(session.remoteNodeInfo.getPublicKey().pack()).encrypt(helloNonce));
            sendPacket(session.remoteNodeInfo, packet);
            session.addPacketToRetransmitMap(packet.packetId, packet, helloNonce);
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendHello) EncryptionError: " + e);
        }
    }


    /**
     * When someone send us {@link PacketTypes#HELLO} typed {@link UDPAdapter2.Packet},
     * we should respond with {@link PacketTypes#WELCOME}.
     * @param sessionReader is {@link UDPAdapter.SessionReader} in which sending is.
     */
    private void sendWelcome(SessionReader sessionReader) {
        try {
            report(logLabel, () -> "send welcome to " + sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
            Packet packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                    sessionReader.remoteNodeInfo.getNumber(), PacketTypes.WELCOME, new PublicKey(sessionReader.remoteNodeInfo.getPublicKey().pack()).encrypt(sessionReader.localNonce));
            sendPacket(sessionReader.remoteNodeInfo, packet);
            sessionReader.addPacketToRetransmitMap(packet.packetId, packet, sessionReader.localNonce);
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendWelcome) EncryptionError: " + e);
        }
    }


    /**
     * We have sent {@link PacketTypes#HELLO} typed {@link Packet},
     * and have got {@link PacketTypes#WELCOME} typed {@link Packet} - it means we can continue handshake and send request for session's keys.
     * @param session is {@link Session} in which sending is.
     * @param payloadPart1 is prepared in {@link SocketListenThread#onReceiveWelcome(Packet)}
     * @param payloadPart2 is prepared in {@link SocketListenThread#onReceiveWelcome(Packet)}
     */
    private void sendKeyReq(Session session, byte[] payloadPart1, byte[] payloadPart2) throws EncryptionError {
        report(logLabel, ()->"send key_req to "+session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
        Packet packet1 = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), PacketTypes.KEY_REQ_PART1, payloadPart1);
        Packet packet2 = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), PacketTypes.KEY_REQ_PART2, payloadPart2);
        sendPacket(session.remoteNodeInfo, packet1);
        sendPacket(session.remoteNodeInfo, packet2);
        session.addPacketToRetransmitMap(packet1.packetId, packet1, payloadPart1);
        session.addPacketToRetransmitMap(packet2.packetId, packet2, payloadPart2);
    }


    /**
     * Someone who sent {@link PacketTypes#HELLO} typed {@link Packet},
     * send us new KEY_REQ typed {@link Packet} - if all is ok we send session keys to.
     * From now we ready to data exchange.
     * @param sessionReader is {@link SessionReader} in which sending is.
     */
    private void sendSessionKey(SessionReader sessionReader) throws EncryptionError {
        report(logLabel, ()->"send session_key to "+sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);

        List data = Arrays.asList(sessionReader.sessionKey.getKey(), sessionReader.remoteNonce);
        byte[] packed = Boss.pack(data);
        byte[] encrypted = new PublicKey(sessionReader.remoteNodeInfo.getPublicKey().pack()).encrypt(packed);
        byte[] sign = new PrivateKey(ownPrivateKey.pack()).sign(encrypted, HashType.SHA512);

        Packet packet1 = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), PacketTypes.SESSION_PART1, encrypted);
        Packet packet2 = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), PacketTypes.SESSION_PART2, sign);
        sendPacket(sessionReader.remoteNodeInfo, packet1);
        sendPacket(sessionReader.remoteNodeInfo, packet2);
        sessionReader.addPacketToRetransmitMap(packet1.packetId, packet1, encrypted);
        sessionReader.addPacketToRetransmitMap(packet2.packetId, packet2, sign);
    }


    /**
     * Each adapter will try to send blocks until have got special {@link Packet} with type {@link PacketTypes#ACK},
     * that means receiver have got block. So when we got packet and all is ok - call this method.
     * @param sessionReader is {@link SessionReader} in which sending is.
     * @param packetId is id of packet we have got.
     */
    private void sendAck(SessionReader sessionReader, Integer packetId) throws EncryptionError {
        report(logLabel, ()->"send ack to "+sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.DETAILED);
        Packet packet = new Packet(0, myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), PacketTypes.ACK, new SymmetricKey(sessionReader.sessionKey.getKey()).etaEncrypt(Boss.pack(packetId)));
        sendPacket(sessionReader.remoteNodeInfo, packet);
    }


    private void sendSessionAck(Session session) throws EncryptionError {
        report(logLabel, ()->"send session_ack to "+session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
        Packet packet = new Packet(0, myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), PacketTypes.SESSION_ACK, new SymmetricKey(session.sessionKey.getKey()).etaEncrypt(Do.randomBytes(32)));
        sendPacket(session.remoteNodeInfo, packet);
    }


    /**
     * Each adapter will try to send blocks until have got special {@link Packet} with type {@link PacketTypes#ACK},
     * that means receiver have got block. So when we got block, but something went wrong - call this method. Note that
     * for success blocks needs to call {@link UDPAdapter#sendAck(SessionReader, Integer)}
     * @param sessionReader is {@link SessionReader} in which sending is.
     * @param packetId is id of block we have got.
     */
    private void sendNack(SessionReader sessionReader, Integer packetId) {
        report(logLabel, ()->"send nack to "+sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.DETAILED);
        try {
            Packet packet = new Packet(0, myNodeInfo.getNumber(),
                    sessionReader.remoteNodeInfo.getNumber(), PacketTypes.NACK, new PublicKey(sessionReader.remoteNodeInfo.getPublicKey().pack()).encrypt(Boss.pack(packetId)));
            sendPacket(sessionReader.remoteNodeInfo, packet);
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendNack) can't send NACK, EncryptionError: " + e);
        }
    }


    private void sendNack(Integer nodeNumber, Integer packetId) {
        try {
            NodeInfo destination = netConfig.getInfo(nodeNumber);
            if (destination != null) {
                report(logLabel, ()->"send nack to "+nodeNumber, VerboseLevel.DETAILED);
                Packet packet = new Packet(0, myNodeInfo.getNumber(),
                        nodeNumber, PacketTypes.NACK, new PublicKey(destination.getPublicKey().pack()).encrypt(Boss.pack(packetId)));
                sendPacket(destination, packet);
            }
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendNack) can't send NACK, EncryptionError: " + e);
        }
    }


    private static int socketListenThreadNumber = 1;

    /**
     * This thread listen socket for packets. From packets it construct blocks. And send anwer for blocks by creating
     * and sending oter blocks.
     */
    private class SocketListenThread extends Thread {

        private AtomicBoolean isActive = new AtomicBoolean(false);
        private final DatagramSocket threadSocket;
        private DatagramPacket receivedDatagram;
        private String logLabel = "";

        public SocketListenThread(DatagramSocket socket){
            byte[] buf = new byte[DatagramAdapter.MAX_PACKET_SIZE];
            receivedDatagram = new DatagramPacket(buf, buf.length);
            threadSocket = socket;
            try {
                threadSocket.setSoTimeout(500);
            } catch (SocketException e) {
                report(logLabel, ()->"setSoTimeout failed, exception: " + e, VerboseLevel.BASE);
            }
        }

        @Override
        public void run() {
            setName("UDP-socket-listener-" + socketListenThreadNumber++);
            logLabel = myNodeInfo.getNumber() + "-" + getName() + ": ";

            isActive.set(true);
            while(isActive.get()) {
                boolean isDatagramReceived = false;
                try {
                    threadSocket.receive(receivedDatagram);
                    isDatagramReceived = true;
                } catch (SocketException e) {
                    report(logLabel, ()->"received SocketException: " + e, VerboseLevel.BASE);
                } catch (SocketTimeoutException e) {
                    report(logLabel, ()->"received nothing", VerboseLevel.BASE);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (isDatagramReceived) {
                    try {
                        byte[] data = Arrays.copyOfRange(receivedDatagram.getData(), 0, receivedDatagram.getLength());
                        Packet packet = new Packet();
                        packet.parseFromByteArray(data);

                        switch (packet.type) {
                            case PacketTypes.HELLO:
                                onReceiveHello(packet);
                                break;
                            case PacketTypes.WELCOME:
                                onReceiveWelcome(packet);
                                break;
                            case PacketTypes.KEY_REQ_PART1:
                                onReceiveKeyReqPart1(packet);
                                break;
                            case PacketTypes.KEY_REQ_PART2:
                                onReceiveKeyReqPart2(packet);
                                break;
                            case PacketTypes.SESSION_PART1:
                                onReceiveSessionPart1(packet);
                                break;
                            case PacketTypes.SESSION_PART2:
                                onReceiveSessionPart2(packet);
                                break;
                            case PacketTypes.DATA:
                                onReceiveData(packet);
                                break;
                            case PacketTypes.ACK:
                                onReceiveAck(packet);
                                break;
                            case PacketTypes.NACK:
                                onReceiveNack(packet);
                                break;
                            case PacketTypes.SESSION_ACK:
                                onReceiveSessionAck(packet);
                                break;
                            default:
                                report(logLabel, () -> "received unknown packet type: " + packet.type, VerboseLevel.BASE);
                                break;
                        }
                    } catch (Exception e) {
                        callErrorCallbacks("SocketListenThread exception: " + e);
                    }
                }
            }

            socket.close();
            socket.disconnect();

            report(logLabel, ()->"SocketListenThread has finished", VerboseLevel.BASE);
        }


        private void onReceiveHello(Packet packet) throws EncryptionError {
            report(logLabel, ()->"received hello from " + packet.senderNodeId, VerboseLevel.BASE);
            NodeInfo nodeInfo = netConfig.getInfo(packet.senderNodeId);
            if (nodeInfo != null) {
                SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
                if (sessionReader != null) {
                    sessionReader.protectFromDuples(packet.packetId, ()->{
                        sessionReader.generateNewLocalNonce();
                        sessionReader.handshake_keyReqPart1 = null;
                        sessionReader.handshake_keyReqPart2 = null;
                        sendWelcome(sessionReader);
                    });
                }
            } else {
                throw new EncryptionError(Errors.BAD_VALUE + ": block got from unknown node " + packet.senderNodeId);
            }
        }


        private void onReceiveWelcome(Packet packet) {
            report(logLabel, ()->"received welcome from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                session.protectFromDuples(packet.packetId, ()-> {
                    try {
                        if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME)) {
                            session.removeHandshakePacketsFromRetransmitMap();
                            session.remoteNonce = new PrivateKey(ownPrivateKey.pack()).decrypt(packet.payload);

                            // send key_req
                            session.localNonce = Do.randomBytes(64);
                            List data = Arrays.asList(session.localNonce, session.remoteNonce);
                            byte[] packed = Boss.pack(data);
                            byte[] encrypted = new PublicKey(session.remoteNodeInfo.getPublicKey().pack()).encrypt(packed);
                            byte[] sign = new PrivateKey(ownPrivateKey.pack()).sign(encrypted, HashType.SHA512);

                            session.handshakeStep.set(Session.HANDSHAKE_STEP_WAIT_FOR_SESSION);
                            session.handshake_sessionPart1 = null;
                            session.handshake_sessionPart2 = null;
                            sendKeyReq(session, encrypted, sign);
                        }
                    } catch (EncryptionError e) {
                        callErrorCallbacks("(onReceiveWelcome) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
                    }
                });
            }
        }


        private void onReceiveKeyReqPart1(Packet packet) {
            report(logLabel, ()->"received key_req_part1 from " + packet.senderNodeId + " (packetId="+packet.packetId+")", VerboseLevel.BASE);
            SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
            if (sessionReader != null) {
                sessionReader.protectFromDuples(packet.packetId, ()->{
                    sessionReader.removeHandshakePacketsFromRetransmitMap();
                    sessionReader.handshake_keyReqPart1 = packet.payload;
                    onReceiveKeyReq(sessionReader);
                });
            }
        }


        private void onReceiveKeyReqPart2(Packet packet) {
            report(logLabel, ()->"received key_req_part2 from " + packet.senderNodeId + " (packetId="+packet.packetId+")", VerboseLevel.BASE);
            SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
            if (sessionReader != null) {
                sessionReader.protectFromDuples(packet.packetId, ()->{
                    sessionReader.removeHandshakePacketsFromRetransmitMap();
                    sessionReader.handshake_keyReqPart2 = packet.payload;
                    onReceiveKeyReq(sessionReader);
                });
            }
        }


        private void onReceiveKeyReq(SessionReader sessionReader) {
            try {
                if ((sessionReader.handshake_keyReqPart1 != null) && (sessionReader.handshake_keyReqPart2 != null)) {
                    report(logLabel, ()->"received both parts of key_req from " + sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
                    byte[] encrypted = sessionReader.handshake_keyReqPart1;
                    byte[] packed = new PrivateKey(ownPrivateKey.pack()).decrypt(encrypted);
                    byte[] sign = sessionReader.handshake_keyReqPart2;
                    List nonceList = Boss.load(packed);
                    byte[] packet_senderNonce = ((Bytes) nonceList.get(0)).toArray();
                    byte[] packet_remoteNonce = ((Bytes) nonceList.get(1)).toArray();
                    if (Arrays.equals(packet_remoteNonce, sessionReader.localNonce)) {
                        if (new PublicKey(sessionReader.remoteNodeInfo.getPublicKey().pack()).verify(encrypted, sign, HashType.SHA512)) {
                            report(logLabel, ()->"key_req successfully verified", VerboseLevel.BASE);
                            sessionReader.remoteNonce = packet_senderNonce;
                            sessionReader.sessionKey = new SymmetricKey();
                            acceptSessionReaderCandidate(sessionReader.remoteNodeInfo.getNumber(), sessionReader);
                            sendSessionKey(sessionReader);
                        } else {
                            callErrorCallbacks("(onReceiveKeyReq) verify fails");
                        }
                    } else {
                        report(logLabel, ()->"(onReceiveKeyReq) remoteNonce mismatch", VerboseLevel.BASE);
                    }
                }
            } catch (EncryptionError e) {
                callErrorCallbacks("(onReceiveKeyReq) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }


        private void onReceiveSessionPart1(Packet packet) {
            report(logLabel, ()->"received session_part1 from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                session.protectFromDuples(packet.packetId, ()->{
                    session.removeHandshakePacketsFromRetransmitMap();
                    if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_SESSION)) {
                        session.handshake_sessionPart1 = packet.payload;
                        onReceiveSession(session);
                    }
                });
            }
        }


        private void onReceiveSessionPart2(Packet packet) {
            report(logLabel, ()->"received session_part2 from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                session.protectFromDuples(packet.packetId, ()->{
                    session.removeHandshakePacketsFromRetransmitMap();
                    if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_SESSION)) {
                        session.handshake_sessionPart2 = packet.payload;
                        onReceiveSession(session);
                    }
                });
            }
        }


        private void onReceiveSession(Session session) {
            try {
                if ((session.handshake_sessionPart1 != null) && (session.handshake_sessionPart2 != null)) {
                    report(logLabel, ()->"received both parts of session from " + session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
                    byte[] encrypted = session.handshake_sessionPart1;
                    byte[] sign = session.handshake_sessionPart2;
                    if (new PublicKey(session.remoteNodeInfo.getPublicKey().pack()).verify(encrypted, sign, HashType.SHA512)) {
                        byte[] decryptedData = new PrivateKey(ownPrivateKey.pack()).decrypt(encrypted);
                        List data = Boss.load(decryptedData);
                        byte[] sessionKey = ((Bytes) data.get(0)).toArray();
                        byte[] nonce = ((Bytes) data.get(1)).toArray();
                        if (Arrays.equals(nonce, session.localNonce)) {
                            report(logLabel, () -> "session successfully verified", VerboseLevel.BASE);
                            sendSessionAck(session);
                            session.reconstructSessionKey(sessionKey);
                            session.state.set(Session.STATE_EXCHANGING);
                            session.sendAllFromOutputQueue();
                            session.pulseRetransmit();
                        }
                    }
                }
            } catch (EncryptionError e) {
                callErrorCallbacks("(onReceiveSession) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }


        private void onReceiveData(Packet packet) {
            if (packet.payload.length > 4) {
                byte[] encryptedPayload = new byte[packet.payload.length - 4];
                byte[] crc32 = new byte[4];
                System.arraycopy(packet.payload, 0, encryptedPayload, 0, packet.payload.length-4);
                System.arraycopy(packet.payload, packet.payload.length-4, crc32, 0, 4);
                byte[] calcCrc32 = new Crc32().digest(encryptedPayload);
                if (Arrays.equals(crc32, calcCrc32)) {
                    SessionReader sessionReader = getSessionReader(packet.senderNodeId);
                    if (sessionReader != null) {
                        if (sessionReader.sessionKey != null) {
                            try {
                                byte[] decrypted = new SymmetricKey(sessionReader.sessionKey.getKey()).etaDecrypt(encryptedPayload);
                                if (decrypted.length > 2) {
                                    byte[] payload = new byte[decrypted.length - 2];
                                    System.arraycopy(decrypted, 0, payload, 0, payload.length);
                                    sendAck(sessionReader, packet.packetId);
                                    sessionReader.protectFromDuples(packet.packetId, ()->receiver.accept(payload));
                                } else {
                                    callErrorCallbacks("(onReceiveData) decrypted payload too short");
                                    sendNack(sessionReader, packet.packetId);
                                }
                            } catch (EncryptionError e) {
                                callErrorCallbacks("(onReceiveData) EncryptionError: " + e);
                                sendNack(sessionReader, packet.packetId);
                            } catch (SymmetricKey.AuthenticationFailed e) {
                                callErrorCallbacks("(onReceiveData) SymmetricKey.AuthenticationFailed: " + e);
                                sendNack(sessionReader, packet.packetId);
                            }
                        } else {
                            callErrorCallbacks("sessionReader.sessionKey is null");
                            sendNack(sessionReader, packet.packetId);
                        }
                    } else {
                        callErrorCallbacks("no sessionReader found for node " + packet.senderNodeId);
                        sendNack(packet.senderNodeId, packet.packetId);
                    }
                } else{
                    callErrorCallbacks("(onReceiveBlock) crc32 mismatch");
                }
            } else {
                callErrorCallbacks("(onReceiveBlock) packetPayload too short");
            }
        }


        private void onReceiveAck(Packet packet) throws EncryptionError, SymmetricKey.AuthenticationFailed {
            report(logLabel, ()->"received ack from " + packet.senderNodeId, VerboseLevel.DETAILED);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                if (session.state.get() == Session.STATE_EXCHANGING) {
                    Integer ackPacketId = Boss.load(new SymmetricKey(session.sessionKey.getKey()).etaDecrypt(packet.payload));
                    session.removePacketFromRetransmitMap(ackPacketId);
                }
            }
        }


        private void onReceiveNack(Packet packet) throws EncryptionError, SymmetricKey.AuthenticationFailed {
            report(logLabel, ()->"received nack from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                if (session.state.get() == Session.STATE_EXCHANGING) {
                    Integer nackPacketId = Boss.load(new PrivateKey(ownPrivateKey.pack()).decrypt(packet.payload));
                    if (session.retransmitMap.containsKey(nackPacketId)) {
                        session.startHandshake();
                        restartHandshakeIfNeeded(session, Instant.now());
                    }
                }
            }
        }


        private void onReceiveSessionAck(Packet packet) {
            report(logLabel, ()->"received session_ack from " + packet.senderNodeId, VerboseLevel.BASE);
            SessionReader sessionReader = getSessionReader(packet.senderNodeId);
            if (sessionReader != null) {
                sessionReader.removeHandshakePacketsFromRetransmitMap();
            }
        }
    }


    private class DupleProtection {
        public Set<Integer> protectionFromDuple0 = new HashSet<>();
        public Set<Integer> protectionFromDuple1 = new HashSet<>();
        public void protectFromDuples(Integer packetId, Runnable runnable) {
            if (!protectionFromDuple0.contains(packetId) && !protectionFromDuple1.contains(packetId)) {
                runnable.run();
                protectionFromDuple0.add(packetId);
            }
        }
        public void clearProtectionFromDupleBuffers() {
            protectionFromDuple1.clear();
            Set<Integer> tmp = protectionFromDuple1;
            protectionFromDuple1 = protectionFromDuple0;
            protectionFromDuple0 = tmp;
        }
    }


    /**
     * Two remote parties should create valid session before start data's exchanging. This class implement that session
     * according with remote parties is handshaking and eexchanging.
     */
    private class Session extends DupleProtection {

        private SymmetricKey sessionKey;
        private byte[] localNonce;
        private byte[] remoteNonce;
        private NodeInfo remoteNodeInfo;
        private BlockingQueue<OutputQueueItem> outputQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        private ConcurrentHashMap<Integer,RetransmitItem> retransmitMap = new ConcurrentHashMap<>();

        private AtomicInteger state;
        private AtomicInteger handshakeStep;
        private Instant handshakeExpiresAt;
        private byte[] handshake_sessionPart1 = null;
        private byte[] handshake_sessionPart2 = null;
        private Instant lastHandshakeRestartTime = Instant.now();

        static public final int STATE_HANDSHAKE             = 1;
        static public final int STATE_EXCHANGING            = 2;
        static public final int HANDSHAKE_STEP_INIT                  = 1;
        static public final int HANDSHAKE_STEP_WAIT_FOR_WELCOME      = 2;
        static public final int HANDSHAKE_STEP_WAIT_FOR_SESSION      = 3;

        Session(NodeInfo remoteNodeInfo) {
            this.remoteNodeInfo = Boss.load(Boss.dumpToArray(remoteNodeInfo));
            localNonce = Do.randomBytes(64);
            state = new AtomicInteger(STATE_HANDSHAKE);
            handshakeStep = new AtomicInteger(HANDSHAKE_STEP_INIT);
            handshakeExpiresAt = Instant.now().minusMillis(HANDSHAKE_TIMEOUT_MILLIS);
        }

        /**
         * Reconstruct key from got byte array. Calls when remote party sends key.
         * @param key is byte array with packed key.
         */
        public void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
        }

        public void addPayloadToOutputQueue(NodeInfo destination, byte[] payload) {
            OutputQueueItem outputQueueItem = new OutputQueueItem(destination, payload);
            if (!outputQueue.offer(outputQueueItem)) {
                outputQueue.poll();
                outputQueue.offer(outputQueueItem);
            }
        }

        public void sendAllFromOutputQueue() {
            try {
                OutputQueueItem queuedItem;
                while ((queuedItem = outputQueue.poll()) != null) {
                    send(queuedItem.destination, queuedItem.payload);
                }
            } catch (InterruptedException e) {
                callErrorCallbacks("(sendAllFromOutputQueue) InterruptedException in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }

        public void addPacketToRetransmitMap(Integer packetId, Packet packet, byte[] sourcePayload) {
            retransmitMap.put(packetId, new RetransmitItem(packet, sourcePayload));
        }

        public void removePacketFromRetransmitMap(Integer packetId) {
            retransmitMap.remove(packetId);
        }

        public void removeHandshakePacketsFromRetransmitMap() {
            retransmitMap.forEach((k, v)-> {
                if (v.type != PacketTypes.DATA)
                    retransmitMap.remove(k);
            });
        }

        public void startHandshake() {
            if (lastHandshakeRestartTime.plusMillis(RETRANSMIT_TIME).isBefore(Instant.now())) {
                retransmitMap.forEach((k, v) -> {
                    v.retransmitCounter = 0;
                    v.packet = null;
                });
                removeHandshakePacketsFromRetransmitMap();
                handshakeStep.set(HANDSHAKE_STEP_INIT);
                handshakeExpiresAt = Instant.now().minusMillis(HANDSHAKE_TIMEOUT_MILLIS);
                state.set(STATE_HANDSHAKE);
                lastHandshakeRestartTime = Instant.now();
            } else {
                callErrorCallbacks("(startHandshake) too short time after previous startHandshake");
            }
        }

        public void pulseRetransmit() {
            if (state.get() == Session.STATE_EXCHANGING) {
                retransmitMap.forEach((itkey, item)-> {
                    if (item.nextRetransmitTime.isBefore(Instant.now())) {
                        item.updateNextRetransmitTime();
                        if (item.type == PacketTypes.DATA) {
                            if (item.packet == null) {
                                byte[] dataToSend = preparePayloadForSession(this, item.sourcePayload);
                                item.packet = createTestPacket(item.packetId, myNodeInfo.getNumber(), item.receiverNodeId, item.type, dataToSend);
                            }
                            sendPacket(remoteNodeInfo, item.packet);
                            if (item.retransmitCounter++ >= RETRANSMIT_MAX_ATTEMPTS)
                                retransmitMap.remove(itkey);
                        }
                    }
                });
            } else {
                retransmitMap.forEach((itkey, item)-> {
                    if (item.nextRetransmitTime.isBefore(Instant.now())) {
                        item.updateNextRetransmitTime();
                        if (item.type != PacketTypes.DATA) {
                            if (item.packet != null) {
                                sendPacket(remoteNodeInfo, item.packet);
                                if (item.retransmitCounter++ >= RETRANSMIT_MAX_ATTEMPTS)
                                    retransmitMap.remove(itkey);
                            } else {
                                retransmitMap.remove(itkey);
                            }
                        }
                    }
                });
            }
        }
    }


    private class SessionReader extends DupleProtection {
        private byte[] localNonce;
        private byte[] remoteNonce;
        private NodeInfo remoteNodeInfo;
        private SymmetricKey sessionKey = null;
        private byte[] handshake_keyReqPart1 = null;
        private byte[] handshake_keyReqPart2 = null;
        private ConcurrentHashMap<Integer,RetransmitItem> retransmitMap = new ConcurrentHashMap<>();

        private void generateNewLocalNonce() {
            localNonce = Do.randomBytes(64);
        }

        public void addPacketToRetransmitMap(Integer packetId, Packet packet, byte[] sourcePayload) {
            retransmitMap.put(packetId, new RetransmitItem(packet, sourcePayload));
        }

        public void removeHandshakePacketsFromRetransmitMap() {
            retransmitMap.forEach((k, v)-> {
                if (v.type != PacketTypes.DATA)
                    retransmitMap.remove(k);
            });
        }

        public void pulseRetransmit() {
            retransmitMap.forEach((itkey, item)->{
                if (item.nextRetransmitTime.isBefore(Instant.now())) {
                    item.updateNextRetransmitTime();
                    sendPacket(remoteNodeInfo, item.packet);
                    if (item.retransmitCounter++ >= RETRANSMIT_MAX_ATTEMPTS)
                        retransmitMap.remove(itkey);
                }
            });
        }
    }


    /**
     * Packet is atomary object for sending to socket. It has size that fit socket buffer size.
     * Think about packet as about low-level structure. Has type, link to block (by id), num of packets in
     * block at all, payload section and some other data.
     */
    public class PacketTypes
    {
        static public final int DATA           = 0;
        static public final int ACK            = 1;
        static public final int NACK           = 2;
        static public final int HELLO          = 3;
        static public final int WELCOME        = 4;
        static public final int KEY_REQ_PART1  = 5;
        static public final int KEY_REQ_PART2  = 6;
        static public final int SESSION_PART1  = 7;
        static public final int SESSION_PART2  = 8;
        static public final int SESSION_ACK    = 9;
    }


    public class Packet {
        private int senderNodeId;
        private int receiverNodeId;
        private int packetId = 0;
        private int type;
        private byte[] payload;

        public Packet() {
        }

        public Packet(int packetId, int senderNodeId, int receiverNodeId, int type, byte[] payload) {
            this.packetId = packetId;
            this.senderNodeId = senderNodeId;
            this.receiverNodeId = receiverNodeId;
            this.type = type;
            this.payload = payload;
        }

        /**
         * Pack packet.
         * @return packed packet.
         */
        public byte[] makeByteArray() {
            List data = Arrays.asList(packetId, senderNodeId, receiverNodeId, type, new Bytes(payload));
            return Boss.dumpToArray(data);
        }

        /**
         * Reconstruct packet from bytes array.
         * @param byteArray is bytes array for reconstruction.
         * @throws IOException if something went wrong.
         */
        public void parseFromByteArray(byte[] byteArray) {
            List data = Boss.load(byteArray);
            packetId = (int) data.get(0);
            senderNodeId = (int) data.get(1);
            receiverNodeId = (int) data.get(2);
            type = (int) data.get(3);
            payload = ((Bytes) data.get(4)).toArray();
        }
    }


    public Packet createTestPacket(int packetId, int senderNodeId, int receiverNodeId, int type, byte[] payload) {
        return new Packet(packetId, senderNodeId, receiverNodeId, type, payload);
    }


    private class OutputQueueItem {
        public NodeInfo destination;
        public byte[] payload;
        public OutputQueueItem(NodeInfo destination, byte[] payload) {
            this.destination = destination;
            this.payload = payload;
        }
    }


    private class RetransmitItem {
        public Packet packet;
        public int retransmitCounter;
        public byte[] sourcePayload;
        public int receiverNodeId;
        public int packetId = 0;
        public int type;
        public Instant nextRetransmitTime;
        public RetransmitItem(Packet packet, byte[] sourcePayload) {
            this.packet = packet;
            this.sourcePayload = sourcePayload;
            this.retransmitCounter = 0;
            this.receiverNodeId = packet.receiverNodeId;
            this.packetId = packet.packetId;
            this.type = packet.type;
            updateNextRetransmitTime();
        }
        public void updateNextRetransmitTime() {
            nextRetransmitTime = Instant.now().plusMillis(new Random().nextInt(RETRANSMIT_TIME*(4*retransmitCounter+RETRANSMIT_MAX_ATTEMPTS)/RETRANSMIT_MAX_ATTEMPTS));
        }
    }

}
