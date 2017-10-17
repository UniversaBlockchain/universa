/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/17/17.
 */

package com.icodici.universa.node.network.server;

import com.icodici.crypto.*;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.exception.ClientError;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Session {

    private PublicKey publicKey;
    private SymmetricKey sessionKey;
    private byte[] serverNonce;
    private byte[] encryptedAnswer;
    private final long sessionId;


    private List<ErrorRecord> errors = Collections.synchronizedList(new ArrayList<>());


    public Session(PublicKey key, long sessionId) throws EncryptionError {
        this.publicKey = key;
        this.sessionId = sessionId;
    }

    private void createSessionKey() throws EncryptionError {
        if (sessionKey == null) {
            sessionKey = new SymmetricKey();
            Binder data = Binder.fromKeysValues(
                    "sk", sessionKey.pack()
            );
            encryptedAnswer = publicKey.encrypt(Boss.pack(data));
        }
    }

    Binder connect() {
        if (serverNonce == null)
            serverNonce = Do.randomBytes(48);
        return Binder.fromKeysValues(
                "server_nonce", serverNonce,
                "session_id", sessionId
        );
    }

    public Binder getToken(Binder data, PrivateKey privateKey) {
        // Check the answer is properly signed
        byte[] signedAnswer = data.getBinaryOrThrow("data");
        try {
            if (publicKey.verify(signedAnswer, data.getBinaryOrThrow("signature"), HashType.SHA512)) {
                Binder params = Boss.unpack(signedAnswer);
                // now we can check the results
                if (!Arrays.equals(params.getBinaryOrThrow("server_nonce"), serverNonce))
                    addError(Errors.BAD_VALUE, "server_nonce", "does not match");
                else {
                    // Nonce is ok, we can return session token
                    createSessionKey();
                    Binder result = Binder.fromKeysValues(
                            "client_nonce", params.getBinaryOrThrow("client_nonce"),
                            "encrypted_token", encryptedAnswer
                    );
                    byte[] packed = Boss.pack(result);
                    return Binder.fromKeysValues(
                            "data", packed,
                            "signature", privateKey.sign(packed, HashType.SHA512)
                    );
                }
            }
        } catch (Exception e) {
            addError(Errors.BAD_VALUE, "signed_data", "wrong or tampered data block:" + e.getMessage());
        }
        return null;
    }

    public Binder answer(Binder result) {
        if (result == null)
            result = new Binder();
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    private void addError(Errors code, String object, String message) {
        errors.add(new ErrorRecord(code, object, message));
    }

    public Binder command(Binder params) throws ClientError, EncryptionError {
        // decrypt params and execute command
        Binder result = null;
        try {
            result = Binder.fromKeysValues(
                    "result",
                    executeAuthenticatedCommand(
                            Boss.unpack(
                                    sessionKey.decrypt(params.getBinaryOrThrow("params"))
                            )
                    )
            );
        } catch (Exception e) {
            ErrorRecord r = (e instanceof ClientError) ? ((ClientError) e).getErrorRecord() :
                    new ErrorRecord(Errors.COMMAND_FAILED, "", e.getMessage());
            result = Binder.fromKeysValues(
                    "error", r
            );
        }
        // encrypt and return result
        return Binder.fromKeysValues(
                "result",
                sessionKey.encrypt(Boss.pack(result))
        );
    }

    private Binder executeAuthenticatedCommand(Binder params) throws ClientError {
        String cmd = params.getStringOrThrow("command");
        try {
            switch (cmd) {
                case "hello":
                    return Binder.fromKeysValues(
                            "status", "OK",
                            "message", "welcome to the Universa"
                    );
                case "sping":
                    return Binder.fromKeysValues("sping", "spong");

                case "test_error":
                    throw new IllegalAccessException("sample error");
            }
//            } catch (ClientError e) {
//                throw e;
        } catch (Exception e) {
            throw new ClientError(Errors.COMMAND_FAILED, cmd, e.getMessage());
        }
        throw new ClientError(Errors.UNKNOWN_COMMAND, "command", "unknown: " + cmd);
    }

    public long getSessionId() {
        return sessionId;
    }

    public SymmetricKey getSessionKey() {
        return sessionKey;
    }

    public Session setSessionKey(SymmetricKey sessionKey) {
        this.sessionKey = sessionKey;
        return this;
    }

    public Session clearErrors() {
        this.errors.clear();
        return this;
    }

    public Session addError(ErrorRecord error) {
        this.errors.add(error);
        return this;
    }

    public List<ErrorRecord> getErrors() {
        return errors;
    }

    public Session setErrors(List<ErrorRecord> errors) {
        this.errors = errors;
        return this;
    }
}
