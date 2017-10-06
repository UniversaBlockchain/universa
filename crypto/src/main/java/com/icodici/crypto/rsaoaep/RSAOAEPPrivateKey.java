/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.AbstractPrivateKey;
import com.icodici.crypto.AbstractPublicKey;
import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.HashType;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Hashable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongycastle.crypto.*;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.spongycastle.crypto.signers.PSSSigner;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The Private Key for RSA asymmetric algorithm used together with OAEP padding.
 * All key information is private, until increasing its visibility is really needed.
 * Default hash and MGF1 hash are SHA-256.
 * <p>
 * Created by amyodov on 16.04.16.
 */
public class RSAOAEPPrivateKey extends AbstractPrivateKey {

    /** The default hash algorithm for OAEP. */
    public static final HashType DEFAULT_OAEP_HASH = HashType.SHA1;

    /** The default MGF1 hash algorithm. */
    public static final HashType DEFAULT_MGF1_HASH = HashType.SHA1;

    /**
     * Normally the public exponent is one of {3, 5, 17, 257 or 65537}
     */
    private static final byte[] DEFAULT_PUBLIC_EXPONENT = Hex.decode("010001");  /* 65537*/
    private static final int DEFAULT_RSA_CERTAINTY = 20;

    /**
     * Inner state of private key.
     */
    class State {
        final @NonNull AsymmetricBlockCipher decryptor;
        final @NonNull RSAPrivateCrtKeyParameters keyParameters;
        final @NonNull RSAOAEPPublicKey publicKey;
        final @NonNull HashType oaepHashType;
        final @NonNull HashType mgf1HashType;
        final @NonNull
        SecureRandom rng;

        State(AsymmetricBlockCipher decryptor,
              RSAPrivateCrtKeyParameters keyParameters, RSAOAEPPublicKey publicKey,
              HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
            this.decryptor = decryptor;
            this.keyParameters = keyParameters;
            this.publicKey = publicKey;
            this.oaepHashType = oaepHashType;
            this.mgf1HashType = mgf1HashType;
            this.rng = rng;
        }
    }

    /**
     * The whole class either fully initialized, or not.
     * <p>
     * Package-visible for unit tests.
     */
    @Nullable State state;


    /**
     * Empty constructor.
     * <p>
     */
    public RSAOAEPPrivateKey() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("RSAOAEPPrivateKey#%s", System.identityHashCode(this));
    }

    /**
     * Hidden (package-private) constructor, for internal/unittest usage.
     */
    RSAOAEPPrivateKey(byte[] e, byte[] p, byte[] q, HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {
        assert e != null;
        assert p != null;
        assert q != null;
        assert oaepHashType != null;
        assert mgf1HashType != null;

        init(e, p, q, oaepHashType, mgf1HashType, rng);
    }

    /**
     * Hidden (package-private) initializer, for internal/unittest usage.
     */
    void init(byte[] e, byte[] p, byte[] q, HashType oaepHashType, HashType mgf1HashType, SecureRandom rng) {

        final RSAKeyPair keyPair = RSAKeyPair.fromExponents(e, p, q);

        final RSAPrivateCrtKeyParameters privParameters = new RSAPrivateCrtKeyParameters(
                BigIntegers.fromUnsignedByteArray(keyPair.n),
                BigIntegers.fromUnsignedByteArray(keyPair.e), BigIntegers.fromUnsignedByteArray(keyPair.d),
                BigIntegers.fromUnsignedByteArray(keyPair.p), BigIntegers.fromUnsignedByteArray(keyPair.q),
                BigIntegers.fromUnsignedByteArray(keyPair.dP), BigIntegers.fromUnsignedByteArray(keyPair.dQ),
                BigIntegers.fromUnsignedByteArray(keyPair.qInv));

        final AsymmetricBlockCipher decryptor = makeDecryptor(mgf1HashType);

        // Private key goes together with its public key.
        final RSAOAEPPublicKey publicKey = new RSAOAEPPublicKey();
        publicKey.init(keyPair.n, keyPair.e, oaepHashType, mgf1HashType, rng);

        state = new RSAOAEPPrivateKey.State(decryptor, privParameters, publicKey, oaepHashType, mgf1HashType, rng);
        resetDecryptor();
    }

    /**
     * Create the proper decryptor engine.
     */
    private AsymmetricBlockCipher makeDecryptor(HashType mgf1HashType) {
        final Digest dummyDigest = new SHA1Digest(); // Only to satisfy interface.

        return new OAEPEncoding(RSAOAEPEngine.make(), dummyDigest, mgf1HashType.makeDigest(), new byte[0]);
    }

    /**
     * Reset the decrypting RSA engine, restarting any decryption flow from the beginning.
     */
    void resetDecryptor() {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            state.decryptor.init(false, new ParametersWithRandom(state.keyParameters, state.rng));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generate(int bitStrength, HashType mgf1HashType) {
        generate(bitStrength, DEFAULT_PUBLIC_EXPONENT, DEFAULT_RSA_CERTAINTY, DEFAULT_OAEP_HASH, mgf1HashType);
    }

    /**
     * Generate a new key pair, with all options specified.
     *
     * @param bitStrength bit strength of the key, e.g. 2048
     * @param e           RSA public exponent
     * @param certainty   RSA key generation certainty
     * @param mgf1HashType The type of the hash(digest) function used for OAEP MGF1 hash generation.
     */
    public void generate(int bitStrength, byte[] e, int certainty, HashType oaepHashType, HashType mgf1HashType) {
        final RSAKeyPairGenerator keyGen = new RSAKeyPairGenerator();
        keyGen.init(new RSAKeyGenerationParameters(
                BigIntegers.fromUnsignedByteArray(e), new SecureRandom(), bitStrength, certainty));
        final AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
        final RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keyPair.getPrivate();

        if (mgf1HashType == null) {
            mgf1HashType = DEFAULT_MGF1_HASH;
        }

        // Don't worry we are passing thread-unsafe hash and mgf1Hash Digest instances:
        // init() will clone them anyway.
        init(e, BigIntegers.asUnsignedByteArray(privateKey.getP()), BigIntegers.asUnsignedByteArray(privateKey.getQ()), oaepHashType, mgf1HashType, new SecureRandom());
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
    public AbstractPublicKey getPublicKey() throws IllegalStateException {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            return state.publicKey;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canDecrypt() {
        return isInitialized();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] decrypt(byte[] ciphertext) throws EncryptionError {
        if (state == null) {
            throw new IllegalStateException();
        } else {
            try {
                return state.decryptor.processBlock(ciphertext, 0, ciphertext.length);
            } catch (InvalidCipherTextException e) {
                throw new EncryptionError("decrypt failed", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Signature is created using RSA-PSS as described in PKCS# 1 v 2.1.
     */
    @Override
    public byte[] sign(InputStream input, HashType hashType, @Nullable byte[] salt) throws IllegalStateException, IOException {

        if (state == null) {
            throw new IllegalStateException();
        } else {
            final Digest primaryDigest = hashType.makeDigest();

            final PSSSigner signer;
            if (salt == null) {
                // Use maximum possible salt
                signer = new PSSSigner(
                        RSAOAEPEngine.make(),
                        primaryDigest, state.mgf1HashType.makeDigest(),
                        getMaxSaltLength(getBitStrength(), primaryDigest.getDigestSize()));
            } else {
                // Use some specific salt
                signer = new PSSSigner(
                        RSAOAEPEngine.make(),
                        primaryDigest, state.mgf1HashType.makeDigest(),
                        salt);
            }

            signer.init(true, new ParametersWithRandom(state.keyParameters, state.rng));

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
                        signer.update(buffer, 0, howManyBytesRead);
                    }
                }
            }

            try {
                return signer.generateSignature();
            } catch (CryptoException e) {
                throw new IOException(String.format("Cannot sign data: %s", e.toString()));
            }
        }
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
                put("e", BigIntegers.asUnsignedByteArray(state.keyParameters.getPublicExponent()));
                put("p", BigIntegers.asUnsignedByteArray(state.keyParameters.getP()));
                put("q", BigIntegers.asUnsignedByteArray(state.keyParameters.getQ()));

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
            byte[] e = (byte[]) hash.get("e");
            if (e == null) {
                throw new Error("e is not available");
            }

            byte[] p = (byte[]) hash.get("p");
            if (p == null) {
                throw new Error("p is not available");
            }

            byte[] q = (byte[]) hash.get("q");
            if (q == null) {
                throw new Error("q is not available");
            }

            // Optional fields.

            final String mgf1HashName = (String) hash.getOrDefault("mgf1Hash", DEFAULT_MGF1_HASH.getAlgorithmName());
            final HashType mgf1HashType = HashType.getByAlgorithmName(mgf1HashName);
            if (mgf1HashType == null) {
                throw new Error(String.format("MGF1 Hash %s is not available", mgf1HashName));
            }

            // Reuse previous rng if already defined (good for unit tests).
            final SecureRandom rng = (this.state == null)? new SecureRandom(): this.state.rng;
            this.init(e, p, q, DEFAULT_OAEP_HASH, mgf1HashType, rng);

        } catch (Exception e) {
            this.state = null;
            throw new Error(String.format("Incorrect data for private key: %s", e.toString()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] pack() {
        @NonNull final Map<String, Object> params = toHash();
        return Boss.dumpToArray(new Object[]{
                0,
                params.get("e"),
                params.get("p"),
                params.get("q")
        });
    }

    /**
     * Given a private key, get the maximum size of the block, considering some digest will be used.
     * See {@link OAEPEncoding::getInputBlockSize} for details.
     */
    int getMaxBlockSize() {
        final int
                keySize = getBitStrength(),
                digestSize = state.oaepHashType.makeDigest().getDigestSize(),
                maxBlockSize = keySize / 8 - 2 - 2 * digestSize;
        return maxBlockSize;
    }
}
