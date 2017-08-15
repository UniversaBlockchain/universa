/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Do;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counter-mode decryptor stream working with any {@link BlockCipher}, {@link AES256} is
 * strongly recommended.
 * <p>
 * Created by sergeych on 15.12.16.
 */
public class DecryptingStream extends InputStream {

    private final InputStream inputStream;
    private final CTRTransformer transformer;

    public DecryptingStream(BlockCipher cipher, InputStream
            inputStream) throws EncryptionError, IOException {
        this.inputStream = inputStream;
        byte[] iv = new byte[cipher.getBlockSize()];
        inputStream.read(iv);
        transformer = new CTRTransformer(cipher, iv);
    }

    public DecryptingStream(Class<? extends BlockCipher> cipherClass, byte[] key, InputStream
            inputStream) throws EncryptionError, IOException {
        this(CTRTransformer.makeCipher(cipherClass, key), inputStream);
    }

    @Override
    public int read() throws IOException {
        try {
            final int i = inputStream.read();
            return i >= 0 ? transformer.transformByte(i) : -1;
        } catch (EncryptionError encryptionError) {
            throw new IOException("decryption failed", encryptionError);
        }
    }

    static public byte[] decrypt(Class<? extends BlockCipher> cipherClass, byte[] key, byte[]
            encryptedData) throws EncryptionError {
        ByteArrayInputStream bis = new ByteArrayInputStream(encryptedData);
        try {
            return Do.read(new DecryptingStream(cipherClass, key, bis));
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOException", e);
        }
    }

    static public byte[] decrypt(BlockCipher cipher, byte[]
            encryptedData) throws EncryptionError {
        ByteArrayInputStream bis = new ByteArrayInputStream(encryptedData);
        try {
        return Do.read(new DecryptingStream(cipher, bis));
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOException", e);
        }
    }
}
