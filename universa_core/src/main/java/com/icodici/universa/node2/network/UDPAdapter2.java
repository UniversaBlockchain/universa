package com.icodici.universa.node2.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.crypto.digest.Crc32;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class UDPAdapter2 extends DatagramAdapter {

    private DatagramSocket socket;
    private SocketListenThread socketListenThread;
    private ConcurrentHashMap<Integer, Session> sessionsByRemoteId = new ConcurrentHashMap<>();
    private String logLabel = "";
    private Integer nextBlockId = 1;


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
    }


    @Override
    public void send(NodeInfo destination, byte[] payload) throws InterruptedException {
        report(logLabel, () -> "send to "+destination.getNumber()+", isActive: "+socketListenThread.isActive.get(), VerboseLevel.DETAILED);

        if (!socketListenThread.isActive.get())
            return;

        Session session = getOrCreateSession(destination.getNumber(), destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort(), destination);
        if (session.state.get() == Session.STATE_HANDSHAKE) {
            // add payload to output queue
            // and process handshaking
            sendHello(session);
        } else {
            // send payload right now
            DatagramPacket dp = new DatagramPacket(payload, payload.length, destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort());
            try {
                socket.send(dp);
            } catch (IOException ioe) {
            }
        }
    }


    private void sendPacket(NodeInfo destination, Packet packet) throws InterruptedException {
        byte[] payload = packet.makeByteArray();
        DatagramPacket dp = new DatagramPacket(payload, payload.length, destination.getNodeAddress().getAddress(), destination.getNodeAddress().getPort());
        try {
            socket.send(dp);
        } catch (IOException e) {
            report(logLabel, ()->"sendPacket exception: " + e, VerboseLevel.BASE);
        }
    }


    @Override
    public void shutdown() {
        report(logLabel, ()->"shutting down...", VerboseLevel.BASE);
        socketListenThread.isActive.set(false);
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
     * If session for remote id is already created - returns it, otherwise creates new {@link Session}
     * @param remoteId is id of remote party.
     * @param address is {@link InetAddress} of remote party.
     * @param port is port number of remote party.
     * @return
     */
    protected Session getOrCreateSession(int remoteId, InetAddress address, int port, NodeInfo destination) {
        Session s = sessionsByRemoteId.computeIfAbsent(remoteId, (k)->{
            Session session = new Session(address, port, destination);
            report(logLabel, ()->"session created for nodeId "+remoteId, VerboseLevel.BASE);
            session.remoteNodeId = remoteId;
            session.sessionKey = sessionKey;
            return session;
        });
        report(logLabel, ()->">>local node: "+myNodeInfo.getNumber()+" remote node: "+s.remoteNodeId, VerboseLevel.DETAILED);
        report(logLabel, ()->">>local nonce: "+s.localNonce+" remote nonce: "+s.remoteNonce, VerboseLevel.DETAILED);
        report(logLabel, ()->">>state: "+s.state, VerboseLevel.DETAILED);
        report(logLabel, ()->">>session key: "+s.sessionKey.hashCode(), VerboseLevel.DETAILED);
        return s;
    }


    /**
     * This is first step of creation and installation of the session.
     * @param session is {@link Session} in which sending is.
     * @throws InterruptedException if something went wrong
     */
    private void sendHello(Session session) throws InterruptedException {
        report(logLabel, ()->"send hello to "+session.remoteNodeId, VerboseLevel.BASE);
        session.state.set(Session.HANDSHAKE_STEP_HELLO);
        Packet packet = new Packet(1, 1, myNodeInfo.getNumber(), session.remoteNodeId, 0, PacketTypes.HELLO, new byte[0]);
        sendPacket(session.remoteNodeInfo, packet);
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
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            setName("UDP-socket-listener-" + socketListenThreadNumber++);
            logLabel = myNodeInfo.getNumber() + "-" + getName() + ": ";

            isActive.set(true);
            while(isActive.get()) {
                try {
                    threadSocket.receive(receivedDatagram);
                } catch (SocketTimeoutException e) {
                    // received nothing
                } catch (IOException e) {
                    e.printStackTrace();
                }

                byte[] data = Arrays.copyOfRange(receivedDatagram.getData(), 0, receivedDatagram.getLength());
                Packet packet = new Packet();
                packet.parseFromByteArray(data);

                if (packet.type == PacketTypes.HELLO) {
                    report(logLabel, ()->"received hello from " + packet.senderNodeId, VerboseLevel.BASE);
                }

                receiver.accept(data);

            }

            socket.close();
            socket.disconnect();

            report(logLabel, ()->"SocketListenThread has finished", VerboseLevel.BASE);
        }

    }


    /**
     * Two remote parties should create valid session before start data's exchanging. This class implement that session
     * according with remote parties is handshaking and eexchanging.
     */
    private class Session {

        private PublicKey publicKey;
        private SymmetricKey sessionKey;
        private InetAddress address;
        private int port;
        private byte[] localNonce;
        private byte[] remoteNonce;
        private int remoteNodeId = -1;
        private NodeInfo remoteNodeInfo;

        private AtomicInteger state;
        private AtomicInteger handshakeStep;

        static public final int STATE_HANDSHAKE             = 1;
        static public final int STATE_EXCHANGING            = 2;
        static public final int HANDSHAKE_STEP_HELLO         = 1;
        static public final int HANDSHAKE_STEP_WELCOME       = 2;
        static public final int HANDSHAKE_STEP_KEY_REQ       = 3;
        static public final int HANDSHAKE_STEP_SESSION       = 4;

        Session(InetAddress address, int port, NodeInfo remoteNodeInfo) {
            this.remoteNodeInfo = remoteNodeInfo;
            this.address = address;
            this.port = port;
            localNonce = Do.randomBytes(64);
            state = new AtomicInteger(STATE_HANDSHAKE);
            handshakeStep = new AtomicInteger(HANDSHAKE_STEP_HELLO);
        }

        public void createSessionKey() throws EncryptionError {
            if (sessionKey == null) {
                sessionKey = new SymmetricKey();
            }
        }

        /**
         * Reconstruct key from got byte array. Calls when remote party sends key.
         * @param key is byte array with packed key.
         */
        public void reconstructSessionKey(byte[] key) throws EncryptionError {
            sessionKey = new SymmetricKey(key);
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
        static public final int SESSION =       6;
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
            Bytes byteArray = Boss.dump(data);
            return byteArray.toArray();
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
