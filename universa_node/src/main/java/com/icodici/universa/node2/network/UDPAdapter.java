/*
 * Copyright (c) 2018, iCodici S.n.C, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 * refactored by Leonid Novikov <flint.emerald@gmail.com>
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.*;
import com.icodici.crypto.digest.Crc32;
import com.icodici.universa.Errors;
import com.icodici.universa.node2.ConnectivityInfo;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.Node;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

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
    private Map<NodeInfo, ConnectivityInfo> connectivityMap;


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

        int dupleProtectionPeriod = 2 * RETRANSMIT_TIME_GROW_FACTOR * RETRANSMIT_TIME * RETRANSMIT_MAX_ATTEMPTS;
        timerProtectionFromDuple.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                clearProtectionFromDupleBuffers();
            }
        }, dupleProtectionPeriod, dupleProtectionPeriod);
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
            if (session.retransmitMap.size() > MAX_RETRANSMIT_QUEUE_SIZE)
                session.addPayloadToOutputQueue(destination, payload);
            else
                sendPayload(session, payload);
        }
    }


    /**
     * Method creates {@link DatagramPacket} from given {@link Packet} and sends it to address:port from destination.
     * @param destination instance of {@link NodeInfo} with net address for sending.
     * @param packet data to send. It's {@link Packet#makeByteArray()} should returns data with size less than {@link DatagramAdapter#MAX_PACKET_SIZE}
     */
    private void sendPacket(NodeInfo destination, Packet packet) {
        //create immutable snapshot
        Map<NodeInfo,ConnectivityInfo> connectivityMapInstant = this.connectivityMap != null ? new HashMap<>(this.connectivityMap) : new HashMap<>();

        Set<NodeInfo> unreachableByMe = connectivityMapInstant.containsKey(myNodeInfo) ? connectivityMapInstant.get(myNodeInfo).getUnreachableNodes() : new HashSet<>();

        //check if not ECHO packed and is being sent to an unreachable node
        if(packet.type != PacketTypes.ECHO && unreachableByMe.contains(destination)) {
            Map<NodeInfo,Integer> routeLengths = new HashMap<>();
            routeLengths.put(destination,0);
            Set<NodeInfo> uncheckedNodes = new HashSet<>(connectivityMapInstant.keySet());

            //we might not receive connectivity diagnostics of the destination, so it is not on connectivityMap yet
            //still need to add it to uncheckedNodes
            uncheckedNodes.add(destination);

            while(!routeLengths.containsKey(myNodeInfo) && !uncheckedNodes.isEmpty()) {

                //find unchecked node with minimum known route length
                NodeInfo x = uncheckedNodes.stream().min(Comparator.comparingInt(n->routeLengths.getOrDefault(n, connectivityMapInstant.size()))).get();
                Integer xRouteLength = routeLengths.get(x);



                //no unchecked elements with known route length exists
                if(xRouteLength == null) {
                    //NO ROUTE TO HOST
                    break;
                }

                //find neighbors with unknown route length and set it to xRouteLength+1 for them
                for(NodeInfo ni : connectivityMapInstant.keySet()) {
                    if(!routeLengths.containsKey(ni) && !connectivityMapInstant.get(ni).getUnreachableNodes().contains(x)) {
                        routeLengths.put(ni,xRouteLength+1);
                    }
                }

                //mark x checked
                uncheckedNodes.remove(x);

            }

            if(!routeLengths.containsKey(myNodeInfo)) {
                //NO ROUTE TO HOST
                return;
            }

            List<NodeInfo> route = new ArrayList<>();
            while(true) {
                NodeInfo x;
                if(route.isEmpty()) {
                    x = myNodeInfo;
                } else {
                    x = route.get(route.size()-1);
                }

                //find mininum route length node
                Set<NodeInfo> toFindIn = new HashSet(connectivityMapInstant.keySet());

                //... among ones reachable from x
                toFindIn.removeAll(connectivityMapInstant.get(x).getUnreachableNodes());
                //... among ones not on the route already
                toFindIn.removeAll(route);

                try {
                    //next route node found
                    NodeInfo next = toFindIn.stream().min(Comparator.comparingInt(n->routeLengths.getOrDefault(n, connectivityMapInstant.size()))).get();
                    route.add(next);

                    if(routeLengths.get(next) == 1) {
                        break;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

            NodeInfo proxy = route.get(0);

            if(proxy == null) {
                //host unreachable
                return;
            } else {
                NodeInfo finalProxy = proxy;
                NodeInfo finalDestination = destination;
                report(logLabel, ()->"asking " + finalProxy + " to retransmit message to: " + finalDestination, VerboseLevel.DETAILED);


                Packet original = packet;
                packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                        proxy.getNumber(), PacketTypes.RETRANSMIT, original.makeByteArray());
                destination = proxy;

            }
        }

        byte[] payload = packet.makeByteArray();
        InetSocketAddress destAddr = myNodeInfo.hasV6() ? destination.getNodeAddressV6() : destination.getNodeAddress();
        DatagramPacket dp = new DatagramPacket(payload, payload.length, destAddr.getAddress(), destAddr.getPort());
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


    /**
     * All packets data ({@link Packet#payload}) of type {@link PacketTypes#DATA}
     * must be encrypted with sessionKey ({@link SymmetricKey}).
     * This method implements encryption procedure for it.
     * @param sessionKey key for encryption
     * @param payload data to encrypt
     * @return encrypted data, ready for sending to network
     */
    private byte[] preparePayloadForSession(SymmetricKey sessionKey, byte[] payload) {
        try {
            byte[] payloadWithRandomChunk = new byte[payload.length + 2];
            System.arraycopy(payload, 0, payloadWithRandomChunk, 0, payload.length);
            System.arraycopy(Bytes.random(2).toArray(), 0, payloadWithRandomChunk, payload.length, 2);
            byte[] encryptedPayload = new SymmetricKey(sessionKey.getKey()).etaEncrypt(payloadWithRandomChunk);
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


    /**
     * Creates {@link Packet} of type {@link PacketTypes#DATA} and sends it to network, initiates retransmission.
     * It is normal data sending procedure when {@link Session} with remote node is already established.
     * @param session {@link Session} with remote node
     * @param payload data to send
     */
    private void sendPayload(Session session, byte[] payload) {
        byte[] dataToSend = preparePayloadForSession(session.sessionKey, payload);
        Packet packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                session.remoteNodeInfo.getNumber(), PacketTypes.DATA, dataToSend);
        sendPacket(session.remoteNodeInfo, packet);
        session.addPacketToRetransmitMap(packet.packetId, packet, payload);
    }


    /**
     * Generates next serial packetId from sequence [1..{@link Integer#MAX_VALUE}], with cycle.
     * Used for packet confirmations, in retransmission algorithm.
     * @return new packet id for sending
     */
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

    Map<Integer,AsyncEvent> pingWaiters = new ConcurrentHashMap<>();

    @Override
    public int pingNodeUDP(int number, int timeoutMillis) {
        Packet p = new Packet();
        p.type = PacketTypes.ECHO;
        p.packetId = getNextPacketId();
        p.payload = new byte[]{};
        p.receiverNodeId = 0;
        p.senderNodeId = 0;

        AsyncEvent event = new AsyncEvent();
        long ts = Instant.now().toEpochMilli();
        pingWaiters.put(p.packetId,event);
        sendPacket(netConfig.getInfo(number),p);

        try {
            event.await(timeoutMillis);
        } catch (TimeoutException|InterruptedException e) {
            pingWaiters.remove(p.packetId);
            return -1;
        }

        return (int) (Instant.now().toEpochMilli()-ts);
    }


    /**
     * Logging
     * @param label each row starts with this label, pass here logLabel
     * @param message lambda-message, like ()->"some text"
     * @param level from enum {@link DatagramAdapter.VerboseLevel}
     */
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
     * If session for remote node is already created - returns it, otherwise creates new {@link Session}
     * @param destination {@link NodeInfo} to remote node
     * @return session
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


    /**
     * @see UDPAdapter#getOrCreateSession
     * @param remoteId id of remote node, {@link NodeInfo} will be got from {@link UDPAdapter#netConfig}
     * @return session
     */
    private Session getOrCreateSession(int remoteId) {
        NodeInfo destination = netConfig.getInfo(remoteId);
        if (destination == null) {
            callErrorCallbacks("(getOrCreateSession) unknown nodeId has received: "+remoteId);
            return null;
        }
        return getOrCreateSession(destination);
    }


    /**
     * If sessionRader for remote node is already created - returns it, otherwise creates new {@link SessionReader}
     * @param remoteId id of remote node, {@link NodeInfo} will be got from {@link UDPAdapter#netConfig}
     * @return sessionReader
     */
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


    /**
     * When handshake completed, sessionReader moves from candidates list to working readers list.
     * @param sessionReader
     */
    private void acceptSessionReaderCandidate(SessionReader sessionReader) {
        sessionReaders.put(sessionReader.remoteNodeInfo.getNumber(), sessionReader);
        sessionReaderCandidates.remove(sessionReader.remoteNodeInfo.getNumber());
    }


    /**
     * Returns {@link SessionReader} from working readers list.
     * @param remoteId id of remote node
     * @return sessionReader
     */
    private SessionReader getSessionReader(int remoteId) {
        return sessionReaders.get(remoteId);
    }


    /**
     * Calls from {@link UDPAdapter#timerHandshake}
     */
    private void restartHandshakeIfNeeded() {
        Instant now = Instant.now();
        sessionsByRemoteId.forEach((k, s) -> restartHandshakeIfNeeded(s, now));
    }


    /**
     * Calls from {@link UDPAdapter#timerRetransmit}
     */
    private void pulseRetransmit() {
        sessionsByRemoteId.forEach((k, s)->s.pulseRetransmit());
        sessionReaders.forEach((k, sr) -> sr.pulseRetransmit());
        sessionReaderCandidates.forEach((k, sr) -> sr.pulseRetransmit());
        sessionsByRemoteId.forEach((k, s)->s.sendAllFromOutputQueue());
    }


    /**
     * Calls from {@link UDPAdapter#timerProtectionFromDuple}
     */
    private void clearProtectionFromDupleBuffers() {
        sessionReaders.forEach((k, sr)-> sr.clearProtectionFromDupleBuffers());
        sessionReaderCandidates.forEach((k, sr)-> sr.clearProtectionFromDupleBuffers());
        sessionsByRemoteId.forEach((k, s)-> s.clearProtectionFromDupleBuffers());
    }


    /**
     * Checks time of active handshake procedures and restarts them if time is up {@link UDPAdapter#HANDSHAKE_TIMEOUT_MILLIS}
     * @param s session
     * @param now pass here Instant.now()
     */
    private void restartHandshakeIfNeeded(Session s, Instant now) {
        if (s.state.get() == Session.STATE_HANDSHAKE) {
            if (s.handshakeExpiresAt.isBefore(now)) {
                report(logLabel, ()->"handshaking with nodeId="+s.remoteNodeInfo.getNumber()+" is timed out, restart", VerboseLevel.BASE);
                s.handshakeStep.set(Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME);
                s.handshakeExpiresAt = now.plusMillis(HANDSHAKE_TIMEOUT_MILLIS);
                sendHello(s);
            }
        }
    }


    /**
     * for debug
     */
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
     * When someone send us {@link PacketTypes#HELLO} typed {@link UDPAdapter.Packet},
     * we should respond with {@link PacketTypes#WELCOME}.
     * @param sessionReader is {@link UDPAdapter.SessionReader} in which sending is.
     */
    private void sendWelcome(SessionReader sessionReader) {
        try {
            report(logLabel, () -> "send welcome to " + sessionReader.remoteNodeInfo.getNumber(), VerboseLevel.BASE);
            byte[] data = sessionReader.localNonce;
            byte[] sign = new PrivateKey(ownPrivateKey.pack()).sign(data, HashType.SHA512);
            byte[] payload = Boss.dumpToArray(Arrays.asList(data, sign));
            Packet packet = new Packet(getNextPacketId(), myNodeInfo.getNumber(),
                    sessionReader.remoteNodeInfo.getNumber(), PacketTypes.WELCOME, payload);
            sendPacket(sessionReader.remoteNodeInfo, packet);
            sessionReader.removeHandshakePacketsFromRetransmitMap();
            sessionReader.addPacketToRetransmitMap(packet.packetId, packet, sessionReader.localNonce);
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendWelcome) EncryptionError: " + e);
        }
    }


    /**
     * We have sent {@link PacketTypes#HELLO} typed {@link Packet},
     * and have got {@link PacketTypes#WELCOME} typed {@link Packet} - it means we can continue handshake and send
     * request for session's keys. KEY_REQ's payload is more than 512 bytes, so used two parts here.
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
     * SESSION's payload is more than 512 bytes, so used two parts here.
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


    /**
     * ACK packets are used only for respond to DATA packets. Retransmission of handshake's packet types stops on each
     * next handshake step. But last step need to be ACK-ed. For this used {@link PacketTypes#SESSION_ACK} packet.
     * @param session is {@link Session} in which sending is.
     * @throws EncryptionError
     */
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
     * @param nodeNumber node id in which sending is
     * @param packetId is id of block we have got.
     */
    private void sendNack(Integer nodeNumber, Integer packetId) {
        try {
            NodeInfo destination = netConfig.getInfo(nodeNumber);
            if (destination != null) {
                report(logLabel, ()->"send nack to "+nodeNumber, VerboseLevel.DETAILED);
                byte[] randomSeed = Do.randomBytes(64);
                byte[] data = Boss.dumpToArray(Arrays.asList(packetId, randomSeed));
                byte[] sign  = new PrivateKey(ownPrivateKey.pack()).sign(data, HashType.SHA512);
                byte[] payload = Boss.dumpToArray(Arrays.asList(data, sign));
                Packet packet = new Packet(0, myNodeInfo.getNumber(),
                        nodeNumber, PacketTypes.NACK, payload);
                sendPacket(destination, packet);
            }
        } catch (EncryptionError e) {
            callErrorCallbacks("(sendNack) can't send NACK, EncryptionError: " + e);
        }
    }


    /** Uses for listen threads naming. (In local tests we can see many adapters running on one machine.) */
    private static int socketListenThreadNumber = 1;

    public void setConnectivityMap(Map<NodeInfo, ConnectivityInfo> connectivityMap) {
        this.connectivityMap = connectivityMap;
    }

    /**
     * This thread listen socket for packets and processes them by types.
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
                            case PacketTypes.RETRANSMIT:
                                onReceiveRetransmit(packet);
                                break;
                            case PacketTypes.ECHO:
                                AsyncEvent e = pingWaiters.remove(packet.packetId);
                                if( e != null) {
                                    e.fire();
                                } else {
                                    threadSocket.send(receivedDatagram);
                                }
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


        /**
         * We have received {@link PacketTypes#HELLO} packet. Should create localNonce and send it in reply.
         * @param packet received {@link Packet}
         */
        private void onReceiveHello(Packet packet) throws EncryptionError {
            report(logLabel, ()->"received hello from " + packet.senderNodeId, VerboseLevel.BASE);
            NodeInfo nodeInfo = netConfig.getInfo(packet.senderNodeId);
            if (nodeInfo != null) {
                SessionReader sessionReader = getOrCreateSessionReaderCandidate(packet.senderNodeId);
                if (sessionReader != null) {
                    sessionReader.protectFromDuples(packet.packetId, ()->{
                        if (sessionReader.nextLocalNonceGenerationTime.isBefore(Instant.now())) {
                            sessionReader.localNonce = Do.randomBytes(64);
                            sessionReader.nextLocalNonceGenerationTime = Instant.now().plusMillis(HANDSHAKE_TIMEOUT_MILLIS);
                        }
                        sessionReader.handshake_keyReqPart1 = null;
                        sessionReader.handshake_keyReqPart2 = null;
                        sendWelcome(sessionReader);
                    });
                }
            } else {
                throw new EncryptionError(Errors.BAD_VALUE + ": block got from unknown node " + packet.senderNodeId);
            }
        }


        /**
         * We have received {@link PacketTypes#WELCOME} packet. Now we should request session key.
         * @param packet received {@link Packet}
         */
        private void onReceiveWelcome(Packet packet) {
            report(logLabel, ()->"received welcome from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                session.protectFromDuples(packet.packetId, ()-> {
                    try {
                        if ((session.state.get() == Session.STATE_HANDSHAKE) && (session.handshakeStep.get() == Session.HANDSHAKE_STEP_WAIT_FOR_WELCOME)) {
                            List packetData = Boss.load(packet.payload);
                            byte[] remoteNonce = ((Bytes)packetData.get(0)).toArray();
                            byte[] packetSign = ((Bytes)packetData.get(1)).toArray();
                            if (new PublicKey(session.remoteNodeInfo.getPublicKey().pack()).verify(remoteNonce, packetSign, HashType.SHA512)) {
                                session.removeHandshakePacketsFromRetransmitMap();
                                session.remoteNonce = remoteNonce;

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
                        }
                    } catch (EncryptionError e) {
                        callErrorCallbacks("(onReceiveWelcome) EncryptionError in node " + myNodeInfo.getNumber() + ": " + e);
                    }
                });
            }
        }


        /**
         * We have received {@link PacketTypes#KEY_REQ_PART1} packet. Waiting for part2 or continue if it has got already.
         * @param packet received {@link Packet}
         */
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


        /**
         * We have received {@link PacketTypes#KEY_REQ_PART2} packet. Waiting for part1 or continue if it has got already.
         * @param packet received {@link Packet}
         */
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


        /**
         * Here we checks that both parts of KEY_REQ are received.
         * Now we should create sessionKey and send it to handshake's initiator.
         * sessionReader is ready for receiving packets from remote session now,
         * so remove it from candidates list by call {@link UDPAdapter#acceptSessionReaderCandidate(SessionReader)}
         * @param sessionReader in which we have got our parts of KEY_REQ
         */
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
                            acceptSessionReaderCandidate(sessionReader);
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


        /**
         * We have received {@link PacketTypes#SESSION_PART1} packet. Waiting for part2 or continue if it has got already.
         * @param packet received {@link Packet}
         */
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


        /**
         * We have received {@link PacketTypes#SESSION_PART2} packet. Waiting for part1 or continue if it has got already.
         * @param packet received {@link Packet}
         */
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


        /**
         * Here we checks that both parts of SESSION are received.
         * Now we should create sessionKey and send it to handshake's initiator.
         * Handshake has completed now, so change session's state to {@link Session#STATE_EXCHANGING}.
         * Also, reply with {@link PacketTypes#SESSION_ACK}
         * @param session in which we have got our parts of SESSION
         */
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


        /**
         * We have received {@link PacketTypes#DATA} packet. Need to check crc32, decrypt payload with sessionKey,
         * call our main consumer, and reply with ACK or NACK according to success or fail.
         * @param packet received {@link Packet}
         */
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
                                    sendNack(packet.senderNodeId, packet.packetId);
                                }
                            } catch (EncryptionError e) {
                                callErrorCallbacks("(onReceiveData) EncryptionError: " + e);
                                sendNack(packet.senderNodeId, packet.packetId);
                            } catch (SymmetricKey.AuthenticationFailed e) {
                                callErrorCallbacks("(onReceiveData) SymmetricKey.AuthenticationFailed: " + e);
                                sendNack(packet.senderNodeId, packet.packetId);
                            }
                        } else {
                            callErrorCallbacks("sessionReader.sessionKey is null");
                            sendNack(packet.senderNodeId, packet.packetId);
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


        /**
         * We have received {@link PacketTypes#ACK} packet. Need to stop retransmitting of ack-ed packet.
         * @param packet received {@link Packet}
         */
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


        /**
         * We have received {@link PacketTypes#NACK} packet. Means that session is broken, e.g. remote node was
         * rebooted. Need to restart handshake procedure immediately.
         * @param packet received {@link Packet}
         */
        private void onReceiveNack(Packet packet) throws EncryptionError, SymmetricKey.AuthenticationFailed {
            report(logLabel, ()->"received nack from " + packet.senderNodeId, VerboseLevel.BASE);
            Session session = getOrCreateSession(packet.senderNodeId);
            if (session != null) {
                if (session.state.get() == Session.STATE_EXCHANGING) {
                    List dataList = Boss.load(packet.payload);
                    byte[] data = ((Bytes)dataList.get(0)).toArray();
                    byte[] sign = ((Bytes)dataList.get(1)).toArray();
                    if (new PublicKey(session.remoteNodeInfo.getPublicKey().pack()).verify(data, sign, HashType.SHA512)) {
                        List nackPacketIdList = Boss.load(data);
                        Integer nackPacketId = (int)nackPacketIdList.get(0);
                        if (session.retransmitMap.containsKey(nackPacketId)) {
                            session.startHandshake();
                            restartHandshakeIfNeeded(session, Instant.now());
                        }
                    }
                }
            }
        }


        /**
         * We have received {@link PacketTypes#SESSION_ACK} packet.
         * Need to stop retransmitting of any handshake packets.
         * @param packet received {@link Packet}
         */
        private void onReceiveSessionAck(Packet packet) {
            report(logLabel, ()->"received session_ack from " + packet.senderNodeId, VerboseLevel.BASE);
            SessionReader sessionReader = getSessionReader(packet.senderNodeId);
            if (sessionReader != null) {
                sessionReader.removeHandshakePacketsFromRetransmitMap();
            }
        }
    }

    private void onReceiveRetransmit(Packet packet) {
        Packet packetToRetransmit = new Packet();
        packetToRetransmit.parseFromByteArray(packet.payload);
        report(logLabel, ()->"retransmitting message from " + netConfig.getInfo(packetToRetransmit.senderNodeId) + " to " + netConfig.getInfo(packetToRetransmit.receiverNodeId), VerboseLevel.DETAILED);
        sendPacket(netConfig.getInfo(packetToRetransmit.receiverNodeId),packetToRetransmit);

    }


    /**
     * Implements protection from duplication received packets.
     */
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
     * Implements packet retransmission algorithm.
     */
    private class Retransmitter extends DupleProtection {
        public ConcurrentHashMap<Integer,RetransmitItem> retransmitMap = new ConcurrentHashMap<>();
        public NodeInfo remoteNodeInfo;
        public SymmetricKey sessionKey;

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

        protected Integer getState() {
            return Session.STATE_HANDSHAKE;
        }

        public void pulseRetransmit() {
            if (getState() == Session.STATE_EXCHANGING) {
                retransmitMap.forEach((itkey, item)-> {
                    if (item.nextRetransmitTime.isBefore(Instant.now())) {
                        item.updateNextRetransmitTime();
                        if (item.type == PacketTypes.DATA) {
                            if (item.packet == null) {
                                byte[] dataToSend = preparePayloadForSession(sessionKey, item.sourcePayload);
                                item.packet = new Packet(item.packetId, myNodeInfo.getNumber(), item.receiverNodeId, item.type, dataToSend);
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


    /**
     * For data exchanging, two remote parties should create two valid sessions. Each one initiates handshake from
     * it's local {@link Session}, and remote creates {@link SessionReader} for responding.
     * Session uses for handshaking and for transmit {@link PacketTypes#DATA}.
     * SessionReader uses for handshaking and for receive {@link PacketTypes#DATA}
     */
    private class Session extends Retransmitter {

        private byte[] localNonce;
        private byte[] remoteNonce;
        private BlockingQueue<OutputQueueItem> outputQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

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
            this.remoteNodeInfo = remoteNodeInfo;
            localNonce = Do.randomBytes(64);
            state = new AtomicInteger(STATE_HANDSHAKE);
            handshakeStep = new AtomicInteger(HANDSHAKE_STEP_INIT);
            handshakeExpiresAt = Instant.now().minusMillis(HANDSHAKE_TIMEOUT_MILLIS);
        }

        /**
         * Reconstruct key from got byte array. Calls when we receive session key from remote party.
         * @param key is byte array with packed key.
         */
        public void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
        }

        @Override
        protected Integer getState() {
            return state.get();
        }

        /**
         * If we send some payload into session, but session state is
         * {@link Session#STATE_HANDSHAKE} - it accumulates in {@link Session#outputQueue}.
         * @param destination instance of {@link NodeInfo} with net address for sending.
         * @param payload data to send
         */
        public void addPayloadToOutputQueue(NodeInfo destination, byte[] payload) {
            OutputQueueItem outputQueueItem = new OutputQueueItem(destination, payload);
            if (!outputQueue.offer(outputQueueItem)) {
                outputQueue.poll();
                outputQueue.offer(outputQueueItem);
            }
        }

        /**
         * When handshake procedure completes, we should send all accumulated messages.
         */
        public void sendAllFromOutputQueue() {
            try {
                if (state.get() != Session.STATE_HANDSHAKE) {
                    OutputQueueItem queuedItem;
                    int maxOutputs = MAX_RETRANSMIT_QUEUE_SIZE - retransmitMap.size();
                    int i = 0;
                    while ((queuedItem = outputQueue.poll()) != null) {
                        send(queuedItem.destination, queuedItem.payload);
                        if (i++ > maxOutputs)
                            break;
                    }
                }
            } catch (InterruptedException e) {
                callErrorCallbacks("(sendAllFromOutputQueue) InterruptedException in node " + myNodeInfo.getNumber() + ": " + e);
            }
        }

        /**
         * Changes session's state to {@link Session#STATE_HANDSHAKE}.
         */
        public void startHandshake() {
            if (lastHandshakeRestartTime.plusMillis(HANDSHAKE_TIMEOUT_MILLIS).isBefore(Instant.now())) {
                retransmitMap.forEach((k, v) -> {
                    v.retransmitCounter = 0;
                    v.packet = null;
                    v.nextRetransmitTime = Instant.now();
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

    }


    /**
     * SessionReader uses for handshaking and for receive {@link PacketTypes#DATA}
     * @see Session
     */
    private class SessionReader extends Retransmitter {
        private byte[] localNonce;
        private Instant nextLocalNonceGenerationTime = Instant.now().minusMillis(HANDSHAKE_TIMEOUT_MILLIS);
        private byte[] remoteNonce;
        private byte[] handshake_keyReqPart1 = null;
        private byte[] handshake_keyReqPart2 = null;
    }


    /**
     * Packet is atomary object for sending to socket. It has size that fit socket buffer size.
     * Think about packet as about low-level structure. Has header and payload sections.
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
        static public final int ECHO           = 10;
        static public final int RETRANSMIT           = 11;
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
         * Pack header and payload to bytes array.
         * @return packed packet.
         */
        public byte[] makeByteArray() {
            List data = Arrays.asList(packetId, senderNodeId, receiverNodeId, type, new Bytes(payload));
            return Boss.dumpToArray(data);
        }

        /**
         * Reconstruct packet from bytes array.
         * @param byteArray is bytes array for reconstruction.
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


    /**
     * for debug and testing
     */
    public Packet createTestPacket(int packetId, int senderNodeId, int receiverNodeId, int type, byte[] payload) {
        return new Packet(packetId, senderNodeId, receiverNodeId, type, payload);
    }


    /**
     * Item for accumulating in {@link Session#outputQueue}
     */
    private class OutputQueueItem {
        public NodeInfo destination;
        public byte[] payload;
        public OutputQueueItem(NodeInfo destination, byte[] payload) {
            this.destination = destination;
            this.payload = payload;
        }
    }


    /**
     * Item for accumulating in {@link Retransmitter#retransmitMap}
     */
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
            int maxRetransmitDelay = RETRANSMIT_TIME_GROW_FACTOR*retransmitCounter + RETRANSMIT_MAX_ATTEMPTS;
            maxRetransmitDelay /= RETRANSMIT_MAX_ATTEMPTS;
            maxRetransmitDelay *= RETRANSMIT_TIME;
            maxRetransmitDelay += RETRANSMIT_TIME/2;
            nextRetransmitTime = Instant.now().plusMillis(new Random().nextInt(maxRetransmitDelay));
        }
    }

}
