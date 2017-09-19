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
     * Hidden (package-private) constructor, for internal/unittest usage.
     */
    public RSAOAEPPublicKey(byte[] n, byte[] e, HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
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

        return new OAEPEncoding(RSAOAEPEngine.make(), dummyDigest, mgf1HashType.makeDigest(), new byte[0]);
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
     * Check that this key instance is suitable for encryption too (some keys can provide
     * checkSignature only functionality).
     *
     * @return true if this key can encrypt.
     */
    @Override
    public boolean canEncrypt() {
        return isInitialized();
    }

    /**
     * @return bit strength of the key, e.g. 2048 ro RSA.
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
     * True if the key is properly initialized
     */
    @Override
    public boolean isInitialized() {
        return state != null;
    }

    /**
     * Public-key encryption of the block.
     *
     * @param plaintext data to encrypt.
     * @throws EncryptionError if the key can't encrypt, data too large for the key or any other error that prevents
     *                         data to be encrypted.
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
     * Check the digital signature. As very large documents can be signed, we represent them as
     * streams. The document can be way too large to load the whole into memory, so implementations
     * should calculate the signature by reading portions of it from the stream.
     * <p>
     * The method must not throw exception if the signature is bad. e.g. improper, has wrong
     * structure and so on. Instead, it must return false.
     *
     * @param input     source data
     * @param signature signature to check
     * @return true if the signature is valid, false if not.
     * @throws IOException failed to read the input stream (including empty stream, EOF at start)
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
                    RSAOAEPEngine.make(),
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
     * Any encryption type has an unique tag. For RSAES-OAEP the tag is r1. For elliptic curve
     * variant used in bitcoin, "e1" and so on. The tag is not intended to be human readable, it is
     * used to properly select decryption method and not waste package space.
     *
     * @return encryption method tag
     */
    @NonNull
    @Override
    public String algorithmTag() {
        return "r1";
    }

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
     * Load object state from the hash
     *
     * @param hash with data
     * @throws Error if the data are not suitable to load the object state
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
