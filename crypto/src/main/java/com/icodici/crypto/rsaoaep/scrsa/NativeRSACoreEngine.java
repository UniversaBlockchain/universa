package com.icodici.crypto.rsaoaep.scrsa;

// Copy of org.bouncycastle.crypto.engines.RSAEngine,
// then copy of com.squareup.crypto.rsa;

import com.squareup.jnagmp.GmpInteger;

import java.math.BigInteger;

import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.DataLengthException;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;

import static com.squareup.jnagmp.Gmp.modPowInsecure;
import static com.squareup.jnagmp.Gmp.modPowSecure;

/**
 * this does your basic RSA algorithm.
 * <p>
 * SQUARE: replacement for {@code RSACoreEngine}; this is <i>much</i> faster using jna-gmp.
 */
final class NativeRSACoreEngine {
    private RSAKeyParameters key;
    private boolean forEncryption;
    private boolean isPrivate;
    private boolean isSmallExponent;

    // cached components for private CRT key
    private GmpInteger p;
    private GmpInteger q;
    private GmpInteger dP;
    private GmpInteger dQ;
    private BigInteger qInv;

    // cached components for public key
    private GmpInteger exponent;
    private GmpInteger modulus;

    /**
     * initialise the RSA engine.
     *
     * @param forEncryption true if we are encrypting, false otherwise.
     * @param param         the necessary RSA key parameters.
     */
    public void init(
            boolean forEncryption,
            CipherParameters param) {
        if (param instanceof ParametersWithRandom) {
            ParametersWithRandom rParam = (ParametersWithRandom) param;

            key = (RSAKeyParameters) rParam.getParameters();
        } else {
            key = (RSAKeyParameters) param;
        }

        this.forEncryption = forEncryption;

        if (key instanceof RSAPrivateCrtKeyParameters) {
            isPrivate = true;
            //
            // we have the extra factors, use the Chinese Remainder Theorem - the author
            // wishes to express his thanks to Dirk Bonekaemper at rtsffm.com for
            // advice regarding the expression of this.
            //
            RSAPrivateCrtKeyParameters crtKey = (RSAPrivateCrtKeyParameters) key;

            p = new GmpInteger(crtKey.getP());
            q = new GmpInteger(crtKey.getQ());
            dP = new GmpInteger(crtKey.getDP());
            dQ = new GmpInteger(crtKey.getDQ());
            qInv = crtKey.getQInv();

            exponent = modulus = null;
        } else {
            isPrivate = false;
            exponent = new GmpInteger(key.getExponent());
            modulus = new GmpInteger(key.getModulus());
            isSmallExponent = exponent.bitLength() < 64;

            p = q = dP = dQ = null;
            qInv = null;
        }
    }

    /**
     * Return the maximum size for an input block to this engine.
     * For RSA this is always one byte less than the key size on
     * encryption, and the same length as the key size on decryption.
     *
     * @return maximum size for an input block.
     */
    public int getInputBlockSize() {
        int bitSize = key.getModulus().bitLength();

        if (forEncryption) {
            return (bitSize + 7) / 8 - 1;
        } else {
            return (bitSize + 7) / 8;
        }
    }

    /**
     * Return the maximum size for an output block to this engine.
     * For RSA this is always one byte less than the key size on
     * decryption, and the same length as the key size on encryption.
     *
     * @return maximum size for an output block.
     */
    public int getOutputBlockSize() {
        int bitSize = key.getModulus().bitLength();

        if (forEncryption) {
            return (bitSize + 7) / 8;
        } else {
            return (bitSize + 7) / 8 - 1;
        }
    }

    public BigInteger convertInput(
            byte[] in,
            int inOff,
            int inLen) {
        if (inLen > (getInputBlockSize() + 1)) {
            throw new DataLengthException("input too large for RSA cipher.");
        } else if (inLen == (getInputBlockSize() + 1) && !forEncryption) {
            throw new DataLengthException("input too large for RSA cipher.");
        }

        byte[] block;

        if (inOff != 0 || inLen != in.length) {
            block = new byte[inLen];

            System.arraycopy(in, inOff, block, 0, inLen);
        } else {
            block = in;
        }

        BigInteger res = new BigInteger(1, block);
        if (res.compareTo(key.getModulus()) >= 0) {
            throw new DataLengthException("input too large for RSA cipher.");
        }

        return res;
    }

    public byte[] convertOutput(
            BigInteger result) {
        byte[] output = result.toByteArray();

        if (forEncryption) {
            if (output[0] == 0 && output.length > getOutputBlockSize())        // have ended up with an extra zero byte, copy down.
            {
                byte[] tmp = new byte[output.length - 1];

                System.arraycopy(output, 1, tmp, 0, tmp.length);

                return tmp;
            }

            if (output.length < getOutputBlockSize())     // have ended up with less bytes than normal, lengthen
            {
                byte[] tmp = new byte[getOutputBlockSize()];

                System.arraycopy(output, 0, tmp, tmp.length - output.length, output.length);

                return tmp;
            }
        } else {
            if (output[0] == 0)        // have ended up with an extra zero byte, copy down.
            {
                byte[] tmp = new byte[output.length - 1];

                System.arraycopy(output, 1, tmp, 0, tmp.length);

                return tmp;
            }
        }

        return output;
    }

    public BigInteger processBlock(BigInteger input) {
        if (isPrivate) {
            BigInteger mP, mQ, h, m;

            // mP = ((input mod p) ^ dP)) mod p
            mP = modPowSecure(input.remainder(p), dP, p);

            // mQ = ((input mod q) ^ dQ)) mod q
            mQ = modPowSecure(input.remainder(q), dQ, q);

            // h = qInv * (mP - mQ) mod p
            h = mP.subtract(mQ);
            h = h.multiply(qInv);
            h = h.mod(p);               // mod (in Java) returns the positive residual

            // m = h * q + mQ
            m = h.multiply(q);
            m = m.add(mQ);

            return m;
        } else {
            if (isSmallExponent) {
                // Public key with reasonable (small) exponent, no need for secure.
                return modPowInsecure(input, exponent, modulus);
            } else {
                // Client mistakenly configured private key as public? Better be safe than sorry.
                return modPowSecure(input, exponent, modulus);
            }
        }
    }
}