/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.digest.Sha1;
import com.icodici.crypto.digest.Sha256;
import com.icodici.crypto.digest.Sha512;
import net.sergeych.boss.Boss;
import net.sergeych.utils.Base64;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

/**
 * The object used to search suitable keys in the registry, distinct keys without compromising them,
 * generate right keys from passwords and so on.
 * <p>
 * The KeyInfo is always included in the Capsule when used to encrypt data, so it has
 * binary-effective packed representation, {@link #pack()}, which can be used as a hashtag in the
 * keyring, for example, wrapping it in the {@link net.sergeych.utils.Bytes} instance make possible
 * to use it as a Map key and effectively find matching keys.
 * <p>
 * With symmetric keys special care should be taken to have a correct {@link #tag} value, as it is
 * not automatically generated, unlike with RSA keys, where it is 5 first bytes of Sha256() of the
 * packed public key, which is almost always enough to identify it.
 * <p>
 * For password-generated keys all necessary information is kept to recreate key by the password
 * which could be done easily with {@link #derivePassword(String)}.
 * <p>
 * Created by sergeych on 19.12.16.
 */
public class KeyInfo {

    public byte[] getSalt() {
        return salt;
    }

    private byte[] salt;
    private byte[] tag = null;
    private int rounds = 0;
    private Algorythm algorythm;
    private PRF prf = PRF.None;
    private int keyLength;

    /**
     * Check that this key CAN DECRYPT other key. The direction is important! It works with
     * public/private and symmetric keys.
     *
     * @param otherInfo
     *         other key's info
     *
     * @return true if the decription is is possible, otherwise impossible.
     */
    public boolean matchType(KeyInfo otherInfo) {
        if (keyLength != otherInfo.keyLength)
            return false;
        if (otherInfo.algorythm == algorythm && algorythm != Algorythm.RSAPublic )
            return true;
        if (algorythm == Algorythm.RSAPrivate && otherInfo.algorythm == Algorythm.RSAPublic)
            return true;
        return false;
    }

    /**
     * See {@link #matchType(KeyInfo)} for details.
     * @param otherKey
     * @return true if itherKey type matches this one's.
     */
    public boolean matchType(AbstractKey otherKey) {
        return matchType(otherKey.info());
    }

    /**
     * Return true if other key's tag is same or somehow match this key tag. It means that the
     * [robability of the successful decrryption is higher if this methods returns true.
     * @param keyInfo other key info
     * @return true if tags are matching/
     */
    public boolean matchTag(KeyInfo keyInfo) {
        return tag != null && keyInfo.tag != null &&
                Arrays.equals(tag, keyInfo.tag);
    }

    public void setTag(byte[] tag) {
        this.tag = tag;
    }

    public String getBase64Tag() {
        return Base64.encodeCompactString(getTag());
    }

    public enum Algorythm {
        UNKNOWN, RSAPublic, RSAPrivate, AES256
    }

    /**
     * Pseudo-random function to use with PBKDF2 to generate key from the password. It will
     * be used with HMAC algorythm,
     */
    public enum PRF {
        None,
        HMAC_SHA1,
        HMAC_SHA256,
        HMAC_SHA512
    }

    public boolean isPassword() {
        return prf != PRF.None;
    }

    public KeyInfo(Algorythm algorythm, byte[] tag, int keyLength) {
        this.algorythm = algorythm;
        this.tag = tag;
        this.keyLength = keyLength;
        checkSanity();
    }

    /**
     * Construct info for the algorythm with fixed key length, e.g. {@link Algorythm#AES256}.
     *
     * @param algorythm
     *         should be one with fixed key length
     * @param tag
     *         optional tag or null
     */
    public KeyInfo(Algorythm algorythm, byte[] tag) {
        this.algorythm = algorythm;
        this.tag = tag;
        this.keyLength = keyLength;
        if (algorythm == Algorythm.RSAPrivate || algorythm == Algorythm.RSAPublic)
            checkSanity();
        else
            throw new IllegalArgumentException("this algorythm requires block size");
    }

    /**
     * Construct PBKRF-based password key information.
     *
     * @param PRF
     *         hashing method PRF used in key derivation, see {@link PRF}.
     * @param rounds
     * @param salt
     * @param tag
     */
    public KeyInfo(PRF PRF, int rounds, byte[] salt, byte[] tag) {
        this.algorythm = Algorythm.AES256;
        this.tag = tag;
        this.prf = PRF;
        this.rounds = rounds;
        this.salt = salt;
        checkSanity();
    }

    private void checkSanity() {
        switch (algorythm) {
            case RSAPrivate: case RSAPublic:
                if (isPassword())
                    throw new IllegalArgumentException("RSA keys can't be password-derived");
                break;
            case AES256:
                keyLength = 32;
        }
        if (isPassword()) {
            if (rounds < 100)
                throw new IllegalArgumentException("should be more than 1000 rounds for PRF");
            if (keyLength < 16)
                throw new IllegalArgumentException("key should be at least 16 bytes for PRF");
            if (salt == null)
                salt = "attesta".getBytes();
        }
    }

    public KeyInfo(byte[] packedInfo) throws IOException {
        Boss.Reader r = new Boss.Reader(packedInfo);
        algorythm = Algorythm.values()[r.readInt()];
        tag = r.readBinary();
        prf = PRF.values()[r.readInt()];
        keyLength = r.readInt();
        if (isPassword()) {
            if (r.readInt() != 0)
                throw new IllegalArgumentException("unknown PBKDF type");
            rounds = r.readInt();
        }
        try {
            salt = r.readBinary();
        } catch (EOFException ignore) {
            salt = null;
        }

        checkSanity();
    }

    public byte[] getTag() {
        if (tag == null)
            return new byte[] {};
        return tag;
    }

    public int getRounds() {
        return rounds;
    }

    public Algorythm getAlgorythm() {
        return algorythm;
    }

    public PRF getPRF() {
        return prf;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public byte[] pack() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Boss.Writer w = new Boss.Writer(baos);
        try {
            w.write(algorythm.ordinal(), tag, prf.ordinal(), keyLength);
            if (isPassword()) {
                w.write(0, rounds);
            }
            w.write(salt);
            w.close();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("unexpected IO exception", e);
        }
    }

    public SymmetricKey derivePassword(String password) {
        if (!isPassword())
            throw new IllegalStateException("not the PRF keyInfo");
        Class<? extends Digest> cls;
        switch (prf) {
            case HMAC_SHA1:
                cls = Sha1.class;
                break;
            case HMAC_SHA256:
                cls = Sha256.class;
                break;
            case HMAC_SHA512:
                cls = Sha512.class;
                break;
            default:
                throw new IllegalArgumentException("unknown hash scheme for pbkdf2");

        }
        byte[] key = PBKDF2.derive(cls, password, salt, rounds, keyLength);
        return new SymmetricKey(key, this);
    }

    public AbstractKey unpackKey(byte[] data) throws EncryptionError {
        switch (algorythm) {
            case RSAPublic:
                return new PublicKey(data, this);
            case RSAPrivate:
                return new PrivateKey(data, this);
            case AES256:
                return new SymmetricKey(data, this);
        }
        throw new EncryptionError("can't unpack key: " + this);
    }

    @Override
    public String toString() {
        return String.format("Key(%s,%s,%s)", algorythm, prf, tag == null ? "null" : Base64.encodeCompactString(tag));
    }
}
