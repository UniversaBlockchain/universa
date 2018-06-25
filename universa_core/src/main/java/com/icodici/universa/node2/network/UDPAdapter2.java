package com.icodici.universa.node2.network;

import com.icodici.crypto.*;
import com.icodici.crypto.digest.Crc32;
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
import java.util.function.Function;

public class UDPAdapter2 extends DatagramAdapter {

    private DatagramSocket socket;
    private SocketListenThread socketListenThread;
    private ConcurrentHashMap<Integer, Session> sessionsByRemoteId = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, SessionReader> sessionReaders = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, SessionReader> sessionReaderCandidates = new ConcurrentHashMap<>();
    private String logLabel = "";
    private Integer nextBlockId = 1;
    private Timer timerHandshake = new Timer();

    private final static int HANDSHAKE_TIMEOUT_MILLIS = 1000;


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
    public UDPAdapter2(PrivateKey ownPrivateKey, SymmetricKey sessionKey, NodeInfo myNodeInfo, NetConfig netConfig) throws IOException {
        super(ownPrivateKey, sessionKey, myNodeInfo, netConfig);

        logLabel = "udp" + myNodeInfo.getNumber() + ": ";

        nextBlockId = new Random().nextInt(Integer.MAX_VALUE);

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

    }


    @Override
    public void send(NodeInfo destination, byte[] payload) throws InterruptedException {
        report(logLabel, () -> "send to "+destination.getNumber()+", isActive: "+socketListenThread.isActive.get(), VerboseLevel.DETAILED);

        if (!socketListenThread.isActive.get())
            return;

        Session session = getOrCreateSession(destination);
        if (session.state.get() == Session.STATE_HANDSHAKE) {
            session.addPayloadToOutputQueue(payload);
            restartHandshakeIfNeeded(session, Instant.now());
        } else {
            sendPayload(session, payload);
        }
    }


    private void sendPacket(NodeInfo destination, Packet packet) {
        byte[] payload = packet.makeByteArray();
        DatagramPacket dp = new DatagramPacket(payload, payload.length, destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort());
        try {
            report(logLabel, ()->"sendPacket datagram size: " + payload.length, VerboseLevel.DETAILED);
            socket.send(dp);
        } catch (IOException e) {
            callErrorCallbacks("sendPacket exception: " + e);
        }
    }


    private void sendPayload(Session session, byte[] payload) {
        try {
            byte[] payloadWithRandomChunk = new byte[payload.length + 2];
            System.arraycopy(payload, 0, payloadWithRandomChunk, 0, payload.length);
            System.arraycopy(Bytes.random(2).toArray(), 0, payloadWithRandomChunk, payload.length, 2);
            byte[] encryptedPayload = session.sessionKey.etaEncrypt(payloadWithRandomChunk);
            byte[] crc32 = new Crc32().digest(payload);
            Packet packet = new Packet(1, 1, myNodeInfo.getNumber(),
                    session.remoteNodeInfo.getNumber(), 0, PacketTypes.DATA, encryptedPayload);
            sendPacket(session.remoteNodeInfo, packet);
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendPayload) EncryptionError: " + e);
        }
    }


    @Override
    public void shutdown() {
        report(logLabel, ()->"shutting down...", VerboseLevel.BASE);
        socketListenThread.isActive.set(false);
        timerHandshake.cancel();
        timerHandshake.purge();
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
            sessionReader.remoteNodeInfo = destination;
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


    private void restartHandshakeIfNeeded(Session s, Instant now) {
        if (s.state.get() == Session.STATE_HANDSHAKE) {
            if (s.handshakeExpiresAt.isBefore(now)) {
                report(logLabel, ()->"handshaking with nodeId="+s.remoteNodeInfo.getNumber()+" is timed out, restart", VerboseLevel.BASE);
                sendHello(s);
                s.handshakeStep.set(Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME);
                s.handshakeExpiresAt = Instant.now().plusMillis(HANDSHAKE_TIMEOUT_MILLIS);
            }
        }
    }


    /**
     * This is first step of creation and installation of the session.
     * @param session is {@link Session} in which sending is.
     */
    private void sendHello(Session session) {
        report(logLabel, ()->"send hello to "+session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
        Packet packet = new Packet(1, 1, myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), 0, PacketTypes.HELLO, new byte[0]);
        sendPacket(session.remoteNodeInfo, packet);
    }


    /**
     * When someone send us {@link PacketTypes#HELLO} typed {@link UDPAdapter2.Packet},
     * we should respond with {@link PacketTypes#WELCOME}.
     * @param sessionReader is {@link UDPAdapter2.SessionReader} in which sending is.
     */
    private void sendWelcome(SessionReader sessionReader) {
        report(logLabel, ()->"send welcome to "+sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
        Packet packet = new Packet(1, 1, myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), 0, PacketTypes.WELCOME, sessionReader.localNonce);
        sendPacket(sessionReader.remoteNodeInfo, packet);
    }


    /**
     * We have sent {@link PacketTypes#HELLO} typed {@link Packet},
     * and have got {@link PacketTypes#WELCOME} typed {@link Packet} - it means we can continue handshake and send request for session's keys.
     * @param session is {@link Session} in which sending is.
     * @param payload is prepared in {@link SocketListenThread#onReceiveWelcome(Packet)}
     */
    private void sendKeyReq(Session session, byte[] payload) {
        report(logLabel, ()->"send key_req to "+session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
        Packet packet = new Packet(1, 1, myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), 0, PacketTypes.KEY_REQ, payload);
        sendPacket(session.remoteNodeInfo, packet);
    }


    /**
     * Someone who sent {@link PacketTypes#HELLO} typed {@link Packet},
     * send us new {@link PacketTypes#KEY_REQ} typed {@link Packet} - if all is ok we send session keys to.
     * From now we ready to data exchange.
     * @param sessionReader is {@link SessionReader} in which sending is.
     */
    private void sendSessionKey(SessionReader sessionReader) throws EncryptionError {
        report(logLabel, ()->"send session_key to "+sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);

        List data = Arrays.asList(sessionReader.sessionKey.getKey(), sessionReader.remoteNonce);
        byte[] packed = Boss.pack(data);
        PublicKey sessionPublicKey = new PublicKey(sessionReader.remoteNodeInfo.getPublicKey().pack());
        byte[] encrypted = sessionPublicKey.encrypt(packed);
        byte[] sign = ownPrivateKey.sign(encrypted, HashType.SHA512);

        Packet packet1 = new Packet(1, 1, myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), 0, PacketTypes.SESSION_PART1, encrypted);
        Packet packet2 = new Packet(1, 1, myNodeInfo.getNumber(),
                sessionReader.remoteNodeInfo.getNumber(), 0, PacketTypes.SESSION_PART2, sign);
        sendPacket(sessionReader.remoteNodeInfo, packet1);
        sendPacket(sessionReader.remoteNodeInfo, packet2);
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
                boolean isDatagramReceived = true;
                try {
                    threadSocket.receive(receivedDatagram);
                } catch (SocketTimeoutException e) {
                    report(logLabel, ()->"received nothing", VerboseLevel.BASE);
                    isDatagramReceived = false;
                } catch (IOException e) {
                    e.printStackTrace();
                    isDatagramReceived = false;
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
                            case PacketTypes.KEY_REQ:
                                onReceiveKeyReq(packet);
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


        private void onReceiveHello(Packet packet) {
            report(logLabel, ()->"received hello from " + packet.senderNodeId, VerboseLevel.BASE);
            SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
            if (sessionReader != null) {
                sessionReader.generateNewLocalNonce();
                sendWelcome(sessionReader);
            }
        }


        private void onReceiveWelcome(Packet packet) {
            report(logLabel, ()->"received welcome from " + packet.senderNodeId + ", received nonce is " + Base64.encodeString(packet.payload), VerboseLevel.BASE);
            try {
                Session session = getOrCreateSession(packet.senderNodeId);
                if (session != null) {
                    if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME)) {
                        session.remoteNonce = packet.payload;

                        // send key_req
                        List data = Arrays.asList(session.localNonce, session.remoteNonce);
                        byte[] packed = Boss.pack(data);
                        byte[] sign = ownPrivateKey.sign(packed, HashType.SHA512);
                        List payloadList = Arrays.asList(packed, sign);
                        sendKeyReq(session, Boss.dumpToArray(payloadList));

                        session.handshakeStep.set(Session.HANDSHAKE_STEP_WAIT_FOR_SESSION);
                        session.handshake_sessionPart1 = null;
                        session.handshake_sessionPart2 = null;
                    }
                }
            } catch (EncryptionError e) {
                callErrorCallbacks("(onReceiveWelcome) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }


        private void onReceiveKeyReq(Packet packet) {
            report(logLabel, ()->"received key_req from " + packet.senderNodeId, VerboseLevel.BASE);
            try {
                SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
                if (sessionReader != null) {
                    List payloadList = Boss.load(packet.payload);
                    byte[] packed = ((Bytes) payloadList.get(0)).toArray();
                    byte[] sign = ((Bytes) payloadList.get(1)).toArray();
                    List nonceList = Boss.load(packed);
                    byte[] packet_senderNonce = ((Bytes) nonceList.get(0)).toArray();
                    byte[] packet_remoteNonce = ((Bytes) nonceList.get(1)).toArray();
                    if (Arrays.equals(packet_remoteNonce, sessionReader.localNonce)) {
                        if (sessionReader.remoteNodeInfo.getPublicKey().verify(packed, sign, HashType.SHA512)) {
                            report(logLabel, ()->"key_req successfully verified", VerboseLevel.BASE);
                            sessionReader.remoteNonce = packet_senderNonce;
                            sessionReader.sessionKey = new SymmetricKey();
                            sendSessionKey(sessionReader);
                            acceptSessionReaderCandidate(packet.senderNodeId, sessionReader);
                        }
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
                if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_SESSION)) {
                    session.handshake_sessionPart1 = packet.payload;
                    onReceiveSession(session);
                }
            }
        }


        private void onReceiveSessionPart2(Packet packet) {
            report(logLabel, ()->"received session_part2 from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_SESSION)) {
                    session.handshake_sessionPart2 = packet.payload;
                    onReceiveSession(session);
                }
            }
        }


        private void onReceiveSession(Session session) {
            try {
                if ((session.handshake_sessionPart1 != null) && (session.handshake_sessionPart2 != null)) {
                    report(logLabel, ()->"received both parts of session from " + session.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
                    byte[] encrypted = session.handshake_sessionPart1;
                    byte[] sign = session.handshake_sessionPart2;
                    if (session.remoteNodeInfo.getPublicKey().verify(encrypted, sign, HashType.SHA512)) {
                        byte[] decryptedData = ownPrivateKey.decrypt(encrypted);
                        List data = Boss.load(decryptedData);
                        byte[] sessionKey = ((Bytes) data.get(0)).toArray();
                        byte[] nonce = ((Bytes) data.get(1)).toArray();
                        if (Arrays.equals(nonce, session.localNonce)) {
                            report(logLabel, () -> "session successfully verified", VerboseLevel.BASE);
                            session.reconstructSessionKey(sessionKey);
                            session.state.set(Session.STATE_EXCHANGING);
                        }
                    }
                }
            } catch (EncryptionError e) {
                callErrorCallbacks("(onReceiveSession) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }


        private void onReceiveData(Packet packet) {
            SessionReader sessionReader = getSessionReader(packet.senderNodeId);
            if (sessionReader != null) {
                if (sessionReader.sessionKey != null) {
                    try {
                        byte[] decrypted = sessionReader.sessionKey.etaDecrypt(packet.payload);
                        if (decrypted.length > 2) {
                            byte[] payload = new byte[decrypted.length - 2];
                            System.arraycopy(decrypted, 0, payload, 0, payload.length);
                            receiver.accept(decrypted);
                        } else {
                            callErrorCallbacks("(onReceiveData) decrypted payload too short");
                        }
                    } catch (EncryptionError e) {
                        callErrorCallbacks("(onReceiveData) EncryptionError: " + e);
                    } catch (SymmetricKey.AuthenticationFailed e) {
                        callErrorCallbacks("(onReceiveData) SymmetricKey.AuthenticationFailed: " + e);
                    }
                } else {
                    callErrorCallbacks("sessionReader.sessionKey is null");
                }
            } else {
                callErrorCallbacks("no sessionReader found for node " + packet.senderNodeId);
            }

        }

    }


    /**
     * Two remote parties should create valid session before start data's exchanging. This class implement that session
     * according with remote parties is handshaking and eexchanging.
     */
    private class Session {

        private SymmetricKey sessionKey;
        private byte[] localNonce;
        private byte[] remoteNonce;
        private NodeInfo remoteNodeInfo;
        private BlockingQueue<byte[]> outputQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

        private AtomicInteger state;
        private AtomicInteger handshakeStep;
        private Instant handshakeExpiresAt;
        private byte[] handshake_sessionPart1 = null;
        private byte[] handshake_sessionPart2 = null;

        static public final int STATE_HANDSHAKE             = 1;
        static public final int STATE_EXCHANGING            = 2;
        static public final int HANDSHAKE_STEP_INIT                  = 1;
        static public final int HANDSHAKE_STEP_WAIT_FOR_WELCOME      = 2;
        static public final int HANDSHAKE_STEP_WAIT_FOR_SESSION      = 3;

        Session(NodeInfo remoteNodeInfo) {
            this.remoteNodeInfo = remoteNodeInfo;
            localNonce = Do.randomBytes(64);
            state = new AtomicInteger(STATE_HANDSHAKE);
            handshakeStep = new AtomicInteger(HANDSHAKE_STEP_INIT);
            handshakeExpiresAt = Instant.now().plusMillis(-HANDSHAKE_TIMEOUT_MILLIS);
        }

        /**
         * Reconstruct key from got byte array. Calls when remote party sends key.
         * @param key is byte array with packed key.
         */
        public void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
        }

        private void addPayloadToOutputQueue(byte[] payload) {
            if (!outputQueue.offer(payload)) {
                outputQueue.poll();
                outputQueue.offer(payload);
            }
        }

    }


    private class SessionReader {
        private byte[] localNonce;
        private byte[] remoteNonce;
        private NodeInfo remoteNodeInfo;
        private SymmetricKey sessionKey = null;

        private void generateNewLocalNonce() {
            localNonce = Do.randomBytes(64);
        }
    }


    /**
     * Packet is atomary object for sending to socket. It has size that fit socket buffer size.
     * Think about packet as about low-level structure. Has type, link to block (by id), num of packets in
     * block at all, payload section and some other data.
     */
    public class PacketTypes
    {
        static public final int DATA =          0;
        static public final int ACK =           1;
        static public final int NACK =          2;
        static public final int HELLO =         3;
        static public final int WELCOME =       4;
        static public final int KEY_REQ =       5;
        static public final int SESSION_PART1 = 6;
        static public final int SESSION_PART2 = 7;
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

        /**
         * Pack packet.
         * @return packed packet.
         */
        public byte[] makeByteArray() {
            List data = Arrays.asList(brotherPacketsNum, packetId, senderNodeId, receiverNodeId, blockId, type, new Bytes(payload));
            return Boss.dumpToArray(data);
        }

        /**
         * Reconstruct packet from bytes array.
         * @param byteArray is bytes array for reconstruction.
         * @throws IOException if something went wrong.
         */
        public void parseFromByteArray(byte[] byteArray) {
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

}
