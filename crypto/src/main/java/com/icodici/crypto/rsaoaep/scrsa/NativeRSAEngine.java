package com.icodici.crypto.rsaoaep.scrsa;

// Copy of org.bouncycastle.crypto.engines.RSAEngine,
// then copy of com.squareup.crypto.rsa;

import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.engines.RSAEngine;

/**
 * this does your basic RSA algorithm.
 * <p>
 * SQUARE: replacement for {@link RSAEngine}; this is <i>much</i> faster using jna-gmp.
 */
public final class NativeRSAEngine extends RSAEngine
        implements AsymmetricBlockCipher {
    private NativeRSACoreEngine core;

    /**
     * initialise the RSA engine.
     *
     * @param forEncryption true if we are encrypting, false otherwise.
     * @param param         the necessary RSA key parameters.
     */
    public void init(
            boolean forEncryption,
            CipherParameters param) {
        if (core == null) {
            core = new NativeRSACoreEngine();
        }

        core.init(forEncryption, param);
    }

    /**
     * Return the maximum size for an input block to this engine.
     * For RSA this is always one byte less than the key size on
     * encryption, and the same length as the key size on decryption.
     *
     * @return maximum size for an input block.
     */
    public int getInputBlockSize() {
        return core.getInputBlockSize();
    }

    /**
     * Return the maximum size for an output block to this engine.
     * For RSA this is always one byte less than the key size on
     * decryption, and the same length as the key size on encryption.
     *
     * @return maximum size for an output block.
     */
    public int getOutputBlockSize() {
        return core.getOutputBlockSize();
    }

    /**
     * Process a single block using the basic RSA algorithm.
     *
     * @param in    the input array.
     * @param inOff the offset into the input buffer where the data starts.
     * @param inLen the length of the data to be processed.
     * @return the result of the RSA process.
     * @throws DataLengthException the input block is too large.
     */
    public byte[] processBlock(
            byte[] in,
            int inOff,
            int inLen) {
        if (core == null) {
            throw new IllegalStateException("RSA engine not initialised");
        }

        return core.convertOutput(core.processBlock(core.convertInput(in, inOff, inLen)));
    }
}