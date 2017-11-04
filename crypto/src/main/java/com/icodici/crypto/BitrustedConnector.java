/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.crypto;

import net.sergeych.boss.Boss;
import net.sergeych.farcall.BossConnector;
import net.sergeych.farcall.Command;
import net.sergeych.farcall.Connector;
import net.sergeych.farcall.Farcall;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Predicate;

public class BitrustedConnector implements Farcall.Target, Connector {

    private static final int MY_VERSION = 2;
    private static LogPrinter log = new LogPrinter("BRCN");
    private static ExecutorService pool = Executors.newCachedThreadPool();

    private final PrivateKey myKey;
    private final SymmetricKey mySessionKey = new SymmetricKey();
    private final byte[] myNonce = Do.randomBytes(32);
    private int handshakeTimeoutMillis = 2000;
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
     *
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
        else
            throw new EOFException("connection closed");
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

    public BitrustedConnector connect(Predicate<byte[]> isTrustedKey) throws Error, TimeoutException, InterruptedException {
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
            try {
                processHelloAnswer(result);
            }
            catch(EncryptionError e) {
                log.e("Encryption error my k: "+myKey+" remote k: "+remoteKey);
                return false;
            }
            ready.await();
            return true;
        });

        try {
            initDone.get(handshakeTimeoutMillis, TimeUnit.MILLISECONDS);
            connected = true;
        } catch (InterruptedException | TimeoutException e) {
            initDone.cancel(true);
            throw (e);
        } catch (Exception e) {
//            log.wtf("initialization failed", e);
//            e.printStackTrace();
            initDone.cancel(true);
            throw new Error("initialization failed", e.getCause());
        }
        return this;
    }

    public boolean isConnected() {
        return connected;
    }

    private void processHelloAnswer(Binder result) throws EncryptionError {
        byte[] data = result.getBinaryOrThrow("data");
        byte[] signature = result.getBinaryOrThrow("signature");
        setRemoteKey(result.getBinaryOrThrow("public_key"));
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
     *
     * @return
     *
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
        setRemoteKey(params.getBinaryOrThrow("public_key"));

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
        return Binder.fromKeysValues(
                "data", result,
                "signature", signature,
                "public_key", myKey.getPublicKey().pack()
        );
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

    /**
     * Unpack the remote public key, check if it is trusted, if the {@link #remoteKey} is null, saves new value,
     * otherwise checks that it is the same key, or throws {@link IllegalArgumentException}.
     * <p>
     * Set the remote key and accurately check that it was not spoofed, e.g. only one key could be set event several
     * times. Because of the p2p nature of the connection we have no guarantee which way the remote key will arrive
     * first (in the "hello" command from the remote or from the hello answer from the remote), this method allows to
     * set the remote key in right mode.
     *
     * @param packedKey packed remoteKey
     *
     * @throws IllegalArgumentException if the {@link #remoteKey} is already set to a different key.
     */
    public void setRemoteKey(@NonNull byte[] packedKey) throws EncryptionError {
        if (isTrustedKey != null && !isTrustedKey.test(packedKey))
            throw new IllegalArgumentException("public key is not accepted");
        PublicKey rk = new PublicKey(packedKey);
        synchronized (ready) {
            if (remoteKey == null)
                remoteKey = rk;
            else if (!rk.equals(remoteKey))
                throw new IllegalArgumentException("remote key already set to a different value");
        }
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
