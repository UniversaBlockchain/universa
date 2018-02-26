/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.AbstractPublicKey;
import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.HashType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.InvalidCipherTextException;
import org.spongycastle.crypto.Signer;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.signers.PSSSigner;
import org.spongycastle.util.BigIntegers;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The Public Key for RSA asymmetric algorithm used together with OAEP padding.
 * All key information is private, until increasing its visibility is really needed.
 * Default hash and MGF1 hash are SHA-256.
 * <p>
 * Created by amyodov on 16.04.16.
 */
public class RSAOAEPPublicKey extends AbstractPublicKey {

    /**
     * The default hash algorithm for OAEP.
     */
    public static final HashType DEFAULT_OAEP_HASH = HashType.SHA1;

    /**
     * The default MGF1 hash algorithm.
     */
    public static final HashType DEFAULT_MGF1_HASH = HashType.SHA1;

    /**
     * Inner state of public key.
     */
    class State {
        final @NonNull AsymmetricBlockCipher encryptor;
        final @NonNull RSAKeyParameters keyParameters;
        final @NonNull HashType oaepHashType;
        final @NonNull HashType mgf1HashType;
        final @NonNull SecureRandom rng;

        State(AsymmetricBlockCipher encryptor, RSAKeyParameters keyParameters,
              HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
            this.encryptor = encryptor;
            this.keyParameters = keyParameters;
            this.oaepHashType = oaepHashType;
            this.mgf1HashType = mgf1HashType;
            this.rng = rng;
        }
    }

    /**
     * The whole class either fully initialized, or not.
     */
    @Nullable State state;


    /**
     * Empty constructor.
     * <p>
     */
    public RSAOAEPPublicKey() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("RSAOAEPPublicKey#%s", System.identityHashCode(this));
    }

    /**
     * Hidden (package-private) constructor, for internal/unittest usage.
     */
    RSAOAEPPublicKey(byte[] n, byte[] e, HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
        init(n, e, oaepHashType, mgf1HashType, rng);
    }

    /**
     * Hidden (package-private) initializer, for internal/unittest usage.
     */
    void init(byte[] n, byte[] e, HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
        final RSAKeyParameters pubParameters = new RSAKeyParameters(
                false, BigIntegers.fromUnsignedByteArray(n), BigIntegers.fromUnsignedByteArray(e));

        state = new State(makeEncryptor(mgf1HashType), pubParameters, oaepHashType, mgf1HashType, rng);
        resetEncryptor();
    }

    /**
     * Create the proper encryptor engine.
     */
    private AsymmetricBlockCipher makeEncryptor(HashType mgf1HashType) {
        final Digest dummyDigest = new SHA1Digest(); // Only to satisfy interface.

        return new OAEPEncoding(RSAEngineFactory.make(), dummyDigest, mgf1HashType.makeDigest(), new byte[0]);
    }

    /**
     * Reset the encrypting RSA engine, restarting any encryption flow from the beginning.
     */
    void resetEncryptor() {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            state.encryptor.init(true, new ParametersWithRandom(state.keyParameters, state.rng));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canEncrypt() {
        return isInitialized();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getBitStrength() throws IllegalStateException {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            return state.keyParameters.getModulus().bitLength();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized() {
        return state != null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] encrypt(byte[] plaintext) throws EncryptionError, IllegalStateException {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            try {
                return state.encryptor.processBlock(plaintext, 0, plaintext.length);
            } catch (InvalidCipherTextException e) {
                throw new EncryptionError(String.format("Cannot encode: %s", e.toString()));
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public boolean checkSignature(InputStream input, byte[] signature, HashType hashType, int saltLength) throws
            IllegalStateException, IOException {

        if (state == null) {
            throw new IllegalStateException();
        } else {
            final Digest primaryDigest = hashType.makeDigest();

            if (saltLength == MAX_SALT_LENGTH) {
                saltLength = getMaxSaltLength(getBitStrength(), primaryDigest.getDigestSize());
            }
            if (saltLength < 0) {
                throw new RuntimeException(String.format("Incorrect salt length %s", saltLength));
            }

            final Signer signatureChecker = new PSSSigner(
                    RSAEngineFactory.make(),
                    primaryDigest, state.mgf1HashType.makeDigest(),
                    saltLength);
            signatureChecker.init(false, new ParametersWithRandom(state.keyParameters, state.rng));

            boolean done = false;
            while (!done) {
                int availableBytes = input.available();
                if (availableBytes <= 0) {
                    done = true;
                } else {
                    byte[] buffer = new byte[availableBytes];
                    int howManyBytesRead = input.read(buffer);
                    if (howManyBytesRead <= 0) {
                        done = true;
                    } else {
                        signatureChecker.update(buffer, 0, howManyBytesRead);
                    }
                }
            }

            return signatureChecker.verifySignature(signature);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String algorithmTag() {
        return "r1";
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Map<String, Object> toHash() throws IllegalStateException {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            return Collections.unmodifiableMap(new HashMap<String, Object>() {{
                put("n", BigIntegers.asUnsignedByteArray(state.keyParameters.getModulus()));
                put("e", BigIntegers.asUnsignedByteArray(state.keyParameters.getExponent()));

                // Optional fields.

                if (!state.mgf1HashType.equals(DEFAULT_MGF1_HASH)) {
                    put("mgf1Hash", state.mgf1HashType.getAlgorithmName());
                }
            }});
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateFromHash(Map<String, Object> hash) throws Error {
        if (hash == null) {
            throw new Error("hash is null");
        }
        try {
            byte[] n = (byte[]) hash.get("n");
            if (n == null) {
                throw new Error("n is not available");
            }

            byte[] e = (byte[]) hash.get("e");
            if (e == null) {
                throw new Error("e is not available");
            }

            // Optional fields.

            final String mgf1HashName = (String) hash.getOrDefault("mgf1Hash", DEFAULT_MGF1_HASH.getAlgorithmName());
            final HashType mgf1HashType = HashType.getByAlgorithmName(mgf1HashName);
            if (mgf1HashType == null) {
                throw new Error(String.format("MGF1 Hash %s is not available", mgf1HashName));
            }

            // Reuse previous rng if already defined (good for unit tests).
            final SecureRandom rng = (this.state == null) ? new SecureRandom() : this.state.rng;
            this.init(n, e, DEFAULT_OAEP_HASH, mgf1HashType, rng);

        } catch (Exception e) {
            this.state = null;
            throw new Error(String.format("Incorrect data for public key: %s", e.toString()));
        }
    }
}
