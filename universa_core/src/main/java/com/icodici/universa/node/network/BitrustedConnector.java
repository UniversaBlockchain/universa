/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.*;
import net.sergeych.boss.Boss;
import net.sergeych.farcall.BossConnector;
import net.sergeych.farcall.Command;
import net.sergeych.farcall.Connector;
import net.sergeych.farcall.Farcall;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;

import com.icodici.universa.node.network.BitrustedConnector.Error;

public class BitrustedConnector implements Farcall.Target, Connector {

    private static final int MY_VERSION = 1;
    private static LogPrinter log = new LogPrinter("BRCN");
    private static ExecutorService pool = Executors.newCachedThreadPool();
    private final PrivateKey myKey;
    private final SymmetricKey mySessionKey = new SymmetricKey();
    private final byte[] myNonce = Do.randomBytes(32);
    private int handshakeTimeoutMillis = 500000;
    private Farcall connection;
    private PublicKey remoteKey;
    private SymmetricKey remoteSessionKey;
    private Predicate<byte[]> isTrustedKey;
    private AsyncEvent ready = new AsyncEvent();
    private boolean connected = false;
    private BlockingQueue<Binder> inputQueue = new LinkedBlockingQueue<>();

    /**
     * Create instance but does not start handshake.
     *
     * @param myKey
     * @param input
     * @param output
     * @throws IOException
     */
    public BitrustedConnector(PrivateKey myKey,
                              InputStream input,
                              OutputStream output)
            throws IOException {
        this.myKey = myKey;
        connection = new Farcall(new BossConnector(input, output));
        connection.asyncCommands(Executors.newSingleThreadExecutor());
    }

    @Override
    public void send(Map<String, Object> data) throws IOException {
        checkConnected();
        byte[] packed = mySessionKey.etaEncrypt(Boss.pack(data));
        if (!connection.isClosed())
            connection.sendParams("block", packed);
    }

    @Override
    public Map<String, Object> receive() throws IOException {
        try {
            return inputQueue.take();
        } catch (InterruptedException e) {
            throw new EOFException("input is interrupted/being closed");
        }
    }

    @Override
    public void close() {
        connected = false;
        connection.close();
    }

    private void checkConnected() {
        if (!connected)
            throw new IllegalStateException("connector is not connected");
    }

    public void connect(Predicate<byte[]> isTrustedKey) throws Error, TimeoutException, InterruptedException {
        Future<?> initDone = pool.submit(() -> {
            this.isTrustedKey = isTrustedKey;
            connection.start(this);
            Binder result = Binder.from(
                    connection.sendKeyParams(
                            "hello",
                            "protocol", "bitrusted",
                            "version", MY_VERSION,
                            "public_key", myKey.getPublicKey().pack(),
                            "session_key", mySessionKey.pack(),
                            "nonce", myNonce
                    ).waitSuccess()
            );
            // this is an error that
            // we can't process hello answer until other side will send us it's hello
            // we must be able to do it
            ready.await();
            processHelloAnswer(result);
            return true;
        });

        try {
            initDone.get(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
            connected = true;
        } catch (DeferredResult.Error | ExecutionException e) {
//            log.wtf("initialization failed", e);
//            e.printStackTrace();
            initDone.cancel(true);
            throw new Error("initialization failed", e.getCause());
        } catch (InterruptedException | TimeoutException e) {
            initDone.cancel(true);
            throw (e);
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void processHelloAnswer(Binder result) throws EncryptionError {
        byte[] data = result.getBinaryOrThrow("data");
        byte[] signature = result.getBinaryOrThrow("signature");
        if( remoteKey == null )
            throw new IllegalStateException("remote key must be set now");
        if (!remoteKey.verify(data, signature, HashType.SHA256))
            throw new EncryptionError("bad signature in hello answer");
        Binder answer = Boss.unpack(myKey.decrypt(data));
        if (!Arrays.equals(answer.getBinaryOrThrow("nonce"), myNonce))
            throw new EncryptionError("nonce mismatch");
        remoteSessionKey = new SymmetricKey(answer.getBinary("session_key"));
    }

    /**
     * The handshake and transport command processing
     *
     * @param command
     * @return
     * @throws Exception
     */
    @Override
    public Object onCommand(Command command) throws Exception {
//        log.d(toString()+" cmd "+command.getName());
        switch (command.getName()) {
            case "hello":
                return onHello(Binder.from(command.getKeyParams()));
            case "block":
                return decryptBlock(command);
        }
        return null;
    }

    private Object decryptBlock(Command command) throws EncryptionError {
        try {
            synchronized (remoteSessionKey) {
                Bytes ciphertext = command.getParam(0);
                if (ciphertext == null) {
                    throw new IllegalStateException("missing block data");
                }
                Binder plain = Boss.unpack(remoteSessionKey.etaDecrypt(ciphertext.toArray()));
                inputQueue.put(plain);
            }
        } catch (SymmetricKey.AuthenticationFailed authenticationFailed) {
            throw new EncryptionError("authentication failed on bitrusted block");
        } catch (InterruptedException e) {
            Thread.interrupted();
        } catch (Exception e) {
            log.wtf("failed to process block", e);
            e.printStackTrace();
        }
        return null;
    }

    private Object onHello(Binder params) throws IOException {
        // Checking protocol and version
        if (!params.getStringOrThrow("protocol").equals("bitrusted"))
            throw new IOException("unsupported protocol, needs bitrusted'");
        if (params.getIntOrThrow("version") > MY_VERSION)
            throw new IOException("unsupported protocol version, needs <= " + MY_VERSION);

        // checking parameters
        byte[] nonce = params.getBinaryOrThrow("nonce");

        byte[] packedKey = params.getBinaryOrThrow("public_key");
        if (isTrustedKey != null && !isTrustedKey.test(packedKey))
            throw new IllegalArgumentException("public key is not accepted");
        remoteKey = new PublicKey(packedKey);
        remoteSessionKey = new SymmetricKey(params.getBinaryOrThrow("session_key"));

        // We intentionally do not use capsule here to improve network speed
        byte[] result = Boss.pack(Binder.fromKeysValues(
                "session_key", mySessionKey.pack(),
                "nonce", nonce)
        );
        result = remoteKey.encrypt(result);
        byte[] signature = myKey.sign(result, HashType.SHA256);
        ready.fire(null);
//        log.d(toString() + " returning hello");
        return Binder.fromKeysValues("data", result, "signature", signature);
    }

    public int getHandshakeTimeoutMillis() {
        return handshakeTimeoutMillis;
    }

    public void setHandshakeTimeoutMillis(int handshakeTimeoutMillis) {
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
    }

    public SymmetricKey getMySessionKey() {
        return mySessionKey;
    }

    public SymmetricKey getRemoteSessionKey() {
        return remoteSessionKey;
    }

    class Error extends IOException {
        public Error() {
        }

        public Error(String message) {
            super(message);
        }

        public Error(String message, Throwable cause) {
            super(message, cause);
        }

        public Error(Throwable cause) {
            super(cause);
        }
    }


}
