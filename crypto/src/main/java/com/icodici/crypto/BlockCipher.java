/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

/**
 * Interface to all block ciphers
 * <p>
 * Created by sergeych on 04/06/16.
 */
@SuppressWarnings("unused")
public interface BlockCipher {
    enum Direction {
        ENCRYPT,
        DECRYPT
    }

    /**
     * @return block size in bytes
     */
    int getBlockSize();

    int getKeySize();

    /**
     * Encryption method tag, AES256 for AES 256 (Rijndael 256/128) and so on
     *
     * @return
     */
    String getTag();

    void initialize(Direction direction, SymmetricKey key);

    /**
     * Encrypt/decrypt source block and return processed block
     *
     * @param block
     *         source block
     *
     * @return transformed source block
     *
     * @throws EncryptionError
     *         if key or block has wrong size
     */
    byte[] transformBlock(byte[] block) throws EncryptionError;
}
