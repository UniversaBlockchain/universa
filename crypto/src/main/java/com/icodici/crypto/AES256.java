/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.engines.AESFastEngine;
import org.spongycastle.crypto.params.KeyParameter;

/**
 * AES256 block cipher implementation.
 */
public class AES256 implements BlockCipher {

    private org.spongycastle.crypto.@NonNull BlockCipher aesEngine;

    @Nullable
    private byte[] key;

    /**
     * Constructor.
     */
    public AES256() {
        aesEngine = new AESFastEngine();
    }

    /**
     * @return block size in bytes
     */
    @Override
    public int getBlockSize() {
        return 16;
    }

    @Override
    public int getKeySize() {
        return 32;
    }

    /**
     * Encryption method tag, AES256 for AES 256 (Rijndael 256/128) and so on
     *
     * @return
     */
    @Override
    public String getTag() {
        return "AES256";
    }

    @Override
    public void initialize(Direction direction, SymmetricKey key) {
        this.key = key.getKey();
        if (initialized()) {
            CipherParameters params = new KeyParameter(this.key);
            aesEngine.init(direction == Direction.ENCRYPT, params);
        }
    }

    /**
     * Whether the engine has been initialized properly.
     */
    protected boolean initialized() {
        return this.key != null && this.key.length == getKeySize();
    }

    /**
     * Encrypt/decrypt source block and return processed block
     *
     * @param block source block
     * @return transformed source block
     * @throws EncryptionError if key or block has wrong size
     */
    @Override
    public byte[] transformBlock(byte[] block) throws EncryptionError {
        if (!initialized()) {
            throw new EncryptionError("Not initialized with proper key");
        } else {
            byte[] buf = new byte[getBlockSize()];
            aesEngine.processBlock(block, 0, buf, 0);
            return buf;
        }
    }
}
