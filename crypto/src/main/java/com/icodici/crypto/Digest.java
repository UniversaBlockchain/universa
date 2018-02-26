/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstract base class for all MAC funtions (sha1, crc32 and so on). Provides utility interface to
 * it. Implementation must provide only #getLength(), #_update(byte[],int,int) and #_digest()
 * methods.
 * <p>
 * The class provides many utility methods, direct digest calculation, string and stream digest and
 * so on.
 * <p>
 * Created by sergeych on 07/01/16.
 */
public abstract class Digest {

    /**
     * Override to process sequence of bytes. Is called only if the #_digest() was not called. Inner
     * logic protects from update after digest is calculated.
     *
     * @param data
     *         source message
     * @param offset
     *         index to start processing from
     * @param size
     *         number of bytes to proces
     */
    protected abstract void _update(byte[] data, int offset, int size);

    /**
     * Override it to calculate and return digest of all processed data. It is called only once per
     * instance.
     *
     * @return digest
     */
    protected abstract byte[] _digest();

    /**
     * Override to provide digest length in bytes.
     */
    public abstract int getLength();

    /**
     * The processing chunk size, used in HMAC/PRF implementations.
     *
     * @return block size in bytes
     */
    protected int getChunkSize() {
        return 64;
    };

    private byte[] lastDigest = null;

    public void update(byte[] data, int offset, int length) {
        if (lastDigest == null)
            _update(data, offset, length);
        else
            throw new IllegalStateException("digest is already calculated");
    }

    /**
     * Calculate and return message digest or return last calculated digest. It is save and
     * effective to call #digest() multiple times. It is not allowed to call any of the update()
     * methods after digest calculation.
     *
     * @return message digest
     */
    public byte[] digest() {
        if (lastDigest == null)
            lastDigest = _digest();
        return lastDigest;
    }

    /**
     * Update digest using specified data. Can not be executed after any {@link #digest()} call.
     *
     * @param data
     *
     * @return self
     */
    public Digest update(byte[] data) {
        update(data, 0, data.length);
        return this;
    }

    /**
     * Update disgest with a single byte
     * @param signleByte
     * @return self
     */
    public Digest update(int signleByte) {
        byte[] d = { (byte)(signleByte & 0xFF)};
        update( d, 0, 1);
        return this;
    }

    /**
     * Update digest using specified string data, converted to the default encoding (e.g. UTF8) Can
     * not be executed after any {@link #digest()} call.
     *
     * @param data
     *         string
     *
     * @return self
     */
    public Digest update(String data) {
        return update(data.getBytes());
    }

    public byte[] digest(byte[] data) {
        update(data);
        return digest();
    }

    public String hexDigest() {
        return new Bytes(digest()).toHex(false);
    }

    public String hexDigest(byte[] data) {
        return new Bytes(digest(data)).toHex(false);
    }

    public String hexDigest(String data) {
        return new Bytes(digest(data)).toHex(false);
    }

    public String base64Digest(String data) {
        return new Bytes(digest(data)).toBase64();
    }

    public String base64Digest(byte[] data) {
        return new Bytes(digest(data)).toBase64();
    }

    public String base64Digest() {
        return new Bytes(digest()).toBase64();
    }

    public byte[] digest(String data) {
        update(data);
        return digest();
    }

    public byte[] digest(InputStream in) throws IOException {
        update(in);
        return digest();
    }

    private Digest update(InputStream in) throws IOException {
        byte[] buffer = new byte[0x10000];

        while (true) {
            int size = in.read(buffer);
            if (size < 0)
                break;
            if (size > 0)
                update(buffer, 0, size);
        }
        return this;
    }

    public String base64Digest(InputStream in) throws IOException {
        update(in);
        return base64Digest();
    }

    public String hexDigest(InputStream in) throws IOException {
        update(in);
        return hexDigest();
    }

}
