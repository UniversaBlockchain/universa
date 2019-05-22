/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Adapter to the Universa Node UDP protocol v2.
 *
 * Protocol description: https://docs.google.com/document/d/1vlRQu5pcqWlOUT3yzBEKTq95jM8XlRutP-tR-aoUUiI/edit?usp=sharing
 *
 *
 */

public abstract class DatagramAdapter {

    /**
     * the queue where to put incoming data
     */
    BlockingQueue<byte[]> inputQueue = new LinkedBlockingQueue<>();

    /**
     * Maximum packet size in bytes. Adapter should try to send several blocks together as long as the overall encoded
     * packet sie is no more than MAX_PACKET_SIZE with all extra data attached.
     */
    static public final int MAX_PACKET_SIZE = 512;

    /**
     * Max number of attempts to retransmit a block, defaults to 10
     */
    static public final int RETRANSMIT_MAX_ATTEMPTS = 20;

    /**
     * Maxmimum number of data blocks in the sending queue after which oldest
     * items are discarded and overflow flag is set. Defaults to 50.
     */
    static public final int MAX_QUEUE_SIZE = 50000;

    /**
     * Maximum number of data blocks in the retransmit queue after which new
     * sending blocks are delayed in output queue.
     */
    static public final int MAX_RETRANSMIT_QUEUE_SIZE = 5000;

    /**
     * Time between attempts to retransmit a DATA block, in milliseconds
     */
    static public final int RETRANSMIT_TIME = 250;

    /**
     * Each next retransmit delayed little bit more than previous. This factor uses in calculation of delay.
     */
    static public final int RETRANSMIT_TIME_GROW_FACTOR = 4;

    /**
     * Time limit for handshaking procedure. If handshake is not complete for this time, it will be restarted.
     */
    static public final int HANDSHAKE_TIMEOUT_MILLIS = 10000;

    protected NodeInfo myNodeInfo;
    protected NetConfig netConfig;
    protected Consumer<byte[]> receiver = null;
    protected final SymmetricKey sessionKey;
    protected final PrivateKey ownPrivateKey;

    protected int testMode = TestModes.NONE;
    protected int verboseLevel = VerboseLevel.NOTHING;
    protected int lostPacketsPercent = 50;

    protected List<Function<String, String>> errorCallbacks = new ArrayList<>();

    /**
     * Create an instance that listens for the incoming datagrams using the specified configurations. The adapter should
     * start serving incoming datagrams immediately upon creation.
     *
     * @param ownPrivateKey is {@link PrivateKey} for signing requests
     * @param sessionKey is {@link SymmetricKey} with session
     * @param myNodeInfo is {@link NodeInfo} object described node this UDPAdapter work with
     * @param netConfig is {@link NetConfig} where all nodes data is stored
     */
    public DatagramAdapter(PrivateKey ownPrivateKey, SymmetricKey sessionKey, NodeInfo myNodeInfo, NetConfig netConfig) {
        this.myNodeInfo = myNodeInfo;
        this.netConfig = netConfig;
        this.sessionKey = sessionKey;
        this.ownPrivateKey = ownPrivateKey;
    }

    public abstract void send(NodeInfo destination, byte[] payload) throws EncryptionError, InterruptedException;


    /**
     * Close socket and stop threads/
     */
    public abstract void shutdown();

    /**
     * Add callback for errors.
     *
     * @param fn is callback for errors
     */
    public void addErrorsCallback(Function<String, String> fn) {
        errorCallbacks.add(fn);
    }

    public void receive(Consumer<byte[]> receiver) {
        byte[] payload;
        // first set the receiver so the queue won't be grow
        // the order does not matter anyway
        this.receiver = receiver;
        // now let's drain the buffer
        while((payload = inputQueue.poll()) != null ) {
            receiver.accept(payload);
        }
    }

    public void setTestMode(int testMode) {
        this.testMode = testMode;
    }



    public int getVerboseLevel() {
        return verboseLevel;
    }

    public void setVerboseLevel(int level) {
        this.verboseLevel = level;
    }

    public void setLostPacketsPercentInTestMode(int percent) {
        this.lostPacketsPercent = percent;
    }

    public abstract int pingNodeUDP(int number, int timeoutMillis);


    public class TestModes
    {
        static public final int NONE =                      0;
        static public final int LOST_PACKETS =              1;
        static public final int SHUFFLE_PACKETS =           2;
        static public final int LOST_AND_SHUFFLE_PACKETS =  3;
    }


    public static class VerboseLevel
    {
        static public final int NOTHING =           0;
        static public final int BASE =              1;
        static public final int DETAILED =          2;

        public static String intToString(int level) {
            if(level == NOTHING)
                return "nothing";
            if(level == BASE)
                return "base";
            if(level == DETAILED)
                return "detail";
            throw new IllegalArgumentException("Unknown level " + level);
        }

        public static int stringToInt(String level) {
            if(level.equals("nothing"))
                return NOTHING;
            if(level.equals("base"))
                return BASE;
            if(level.equals("detail"))
                return DETAILED;

            throw new IllegalArgumentException("Unknown mode " + level);
        }
    }
}
