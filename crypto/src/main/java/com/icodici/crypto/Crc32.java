/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

/**
 * Created by sergeych on 07/01/16.
 */
public class Crc32 extends Digest {

    CRC32 crc = new CRC32();

    @Override
    protected void _update(byte[] data, int offset, int size) {
        crc.update(data, offset, size);
    }

    @Override
    protected byte[] _digest() {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int)crc.getValue()).array();
    }

    @Override
    public int getLength() {
        return 32;
    }
}
