/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Counter-mode encrypting stream that works with any {@link BlockCipher}, {@link AES256} is
 * strongly recommended.
 * <p>
 * Created by sergeych on 15.12.16.
 */
public class EncryptingStream extends OutputStream {
    private final CTRTransformer transformer;
    private final OutputStream outputStream;

    public EncryptingStream(Class<? extends BlockCipher> cipherClass, byte[] key, OutputStream
            outputStream) throws EncryptionError, IOException {
        this(CTRTransformer.makeCipher(cipherClass, key),outputStream);
    }

    public EncryptingStream(BlockCipher cipher, OutputStream
            outputStream) throws EncryptionError, IOException {
        transformer = new CTRTransformer(cipher, null);
        this.outputStream = outputStream;
        outputStream.write(transformer.getIV());
    }

    @Override
    public void write(int b) throws IOException {
        try {
            outputStream.write(transformer.transformByte(b));
        } catch (EncryptionError encryptionError) {
            throw new RuntimeException("can't encrypt data", encryptionError);
        }
    }

    static byte[] encrypt(Class<? extends BlockCipher> cipherClass, byte[] key, byte[] source)
            throws EncryptionError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            new EncryptingStream(cipherClass, key, bos).write(source);
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOException", e);
        }
        return bos.toByteArray();
    }

    static byte[] encrypt(BlockCipher cipher, byte[] source)
            throws EncryptionError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            new EncryptingStream(cipher, bos).write(source);
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOException", e);
        }
        return bos.toByteArray();
    }
}
