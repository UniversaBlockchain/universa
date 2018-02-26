/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import java.util.Arrays;

/**
 * Synthetic digest composed from sha256 and crc32 in a way that makes collision attack more hard in
 * the case of successful attack on plain sha256.
 * <p>
 * The Syntex family digests have size encoding. This version, syntex1, has the digest size of 36
 * bytes. More hard variants syntex2 and 3 are respectively longer
 * <p>
 * syntex1 pseudo code:
 * <pre>
 *      size_string = decimal_string(size(source))
 *      s1 = sha256(source &amp; size_string)
 *      s2 = crc32(source &amp; size_string &amp; s1)
 *      syntex1 = s1 &amp; s2
 * </pre>
 * The idea is, if there will be a way to effectively create documents with the same sha256 digest,
 * the additional requirement of either fit or respect source file size will present additional
 * level of the difficulty, and the necessity of match also crc32 will make attack on sha256 not
 * less effective on syntex code, while keeping its length enough short. As crc32 is calculate over
 * the sha256 hash too it does not increase vulnerability comparing to the sha256.
 * <p>
 * Created by sergeych on 14/02/16.
 */
public class Syntex1 extends Digest {

    private Crc32 crc = new Crc32();
    private long length = 0;
    private Sha256 sha = new Sha256();
    private int digestLength = 0;

    @Override
    protected void _update(byte[] data, int offset, int size) {
        length += size;
        sha.update(data, offset, size);
        crc.update(data, offset, size);
    }

    @Override
    protected byte[] _digest() {
        byte[] strLength = new Long(length).toString().getBytes();

        sha.update(strLength);
        crc.update(strLength);

        byte[] s1 = sha.digest();
        byte[] s2 = crc.update(s1).digest();

        byte[] syntex = Arrays.copyOf(s1, 36);
        System.arraycopy(s2, 0, syntex, 32, 4);

        return syntex;
    }

    @Override
    public int getLength() {
        return 36;
    }
}
