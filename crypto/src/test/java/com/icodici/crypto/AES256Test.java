/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.AES256;
import com.icodici.crypto.BlockCipher;
import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.SymmetricKey;
import org.spongycastle.util.encoders.Hex;

import static org.junit.Assert.assertArrayEquals;

/**
 * Test AES256 implementation.
 */
public class AES256Test {

    public static final byte[] key1 = Hex.decode("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    public static final byte[] plaintext1 = Hex.decode("00112233445566778899aabbccddeeff");
    public static final byte[] ciphertext1 = Hex.decode("8ea2b7ca516745bfeafc49904b496089");

    /**
     * Test {@link AES256} wrapper for decrypting.
     * */
    @org.junit.Test
    public void encrypt() throws EncryptionError {
        AES256 engine = new AES256();
        engine.initialize(BlockCipher.Direction.ENCRYPT, new SymmetricKey(key1));
        byte[] result = engine.transformBlock(plaintext1);
        assertArrayEquals(ciphertext1, result);

    }
    /**
     * Test {@link AES256} wrapper for decrypting.
     * */
    @org.junit.Test
    public void decrypt() throws EncryptionError {
        AES256 engine = new AES256();
        engine.initialize(BlockCipher.Direction.DECRYPT, new SymmetricKey(key1));
        byte[] result = engine.transformBlock(ciphertext1);
        assertArrayEquals(plaintext1, result);
    }
}
