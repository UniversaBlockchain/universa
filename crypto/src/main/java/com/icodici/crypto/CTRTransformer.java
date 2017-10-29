/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Basic counter mode (CTR) transformer to use in cryptostreams or separately.
 *
 * Created by sergeych on 14.12.16.
 */
class CTRTransformer {
    static private final SecureRandom rng;
    private final BlockCipher cipher;
    private final byte[] nonce;
    private int counter;
    private int index = 0;
    private final int blockSize;
    private byte[] source;
    private final byte[] counterBytes;

    static public byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        rng.nextBytes(bytes);
        return bytes;
    }

    static public byte[] randomBytes(int minLength,int maxLength) {
        int length = rng.nextInt(maxLength - minLength);
        byte[] bytes = new byte[length];
        rng.nextBytes(bytes);
        return bytes;
    }

    static {
        try {
            rng = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Can't create suitable PRNG");
        }
    }

    public byte[] getIV() {
        return nonce;
    }

    /**
     * Create CTR basic transformer
     *
     * @param cipher
     *         properly initialized cipher with key and and direction that must always be set {@link
     *         BlockCipher.Direction#ENCRYPT}
     * @param iv
     *         null to generate new random IV, get it with {@link #getIV()} and store somewhere with
     *         encrypted data.
     *
     * @throws EncryptionError
     */
    public CTRTransformer(BlockCipher cipher, byte[] iv) throws EncryptionError {
        this.cipher = cipher;
        blockSize = cipher.getBlockSize();
        nonce = iv == null ? randomBytes(blockSize) : iv;

        counter = 0;
        source = new byte[blockSize];
        counterBytes = new byte[4];

        prepareBlock();
    }

    private void prepareBlock() throws EncryptionError {
        System.arraycopy(nonce, 0, source, 0, blockSize);
        counterBytes[0] = (byte) (counter >> 24);
        counterBytes[1] = (byte) (counter >> 16);
        counterBytes[2] = (byte) (counter >> 8);
        counterBytes[3] = (byte) counter;
        applyXor(source, blockSize - 4, counterBytes);
        synchronized (cipher) {
            source = cipher.transformBlock(source);
        }
        counter++;
        index = 0;
    }

    private byte nextByte() throws EncryptionError {
        if (index >= blockSize)
            prepareBlock();
        return source[index++];
    }


//    /**
//     * Transforms in place data block
//     * @param source
//     * @throws EncryptionError
//     */
//    public void transform(byte[] source, int offset, int length) throws EncryptionError {
//        for (int i = 0; i < source.length; i++)
//            source[i] ^= nextByte();
//    }

    /**
     * Transform next byte
     * @param source
     * @return transformed byte
     * @throws EncryptionError
     */
    public int transformByte(int source) throws EncryptionError {
        return (source ^ nextByte()) & 0xFF;
    }

    static public void applyXor(byte[] source, int offset, byte[] mask) {
        int end = offset + mask.length;
        if (end > source.length)
            throw new IllegalArgumentException("source is too short for this offset and mask");
        int sourceIndex = offset;
        int maskIndex = 0;
        do {
            source[sourceIndex++] ^= mask[maskIndex++];
        }
        while (sourceIndex < end);
    }

    /**
     * Helps to create block cipher with a given class and key, by properly initializing it.
     * @param cipherClass cipher class to instantiate
     * @param key
     * @return properly initialized encpytor
     * @throws EncryptionError
     */
    public static BlockCipher makeCipher(Class<? extends BlockCipher> cipherClass, byte[] key) throws
            EncryptionError {
        try {
            BlockCipher cipher = cipherClass.newInstance();
            cipher.initialize(BlockCipher.Direction.DECRYPT, new SymmetricKey(key));
            return cipher;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new EncryptionError("failed to instantiate BlockCipher class " + cipherClass
                    .getName(), e);
        }

    }

    public static int nextRandom(int max) {
        return max <= 0 ? rng.nextInt() : rng.nextInt(max);
    }
}
