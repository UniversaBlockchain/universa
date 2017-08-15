/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Hashable;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Generic private key interface.
 * <p>
 * Important: The Key can be in not-initialized state after creation, in which case the depending
 * methods must throw {@link IllegalStateException}. Non-initialized state is a transient state
 * between creation and construction from the hash or generation new key.
 * <p>
 * Created by sergeych on 15/04/16.
 */
@SuppressWarnings("unused")
public abstract class AbstractPrivateKey extends AbstractAsymmetricKey implements Hashable {

    /**
     * Generate a new key pair of the specified bit strength.
     * <p>
     * The private key instance should have empty constructor (non initialized instance).
     *
     * @param bitStrength bit strength of the key, e.g. 2048 ro RSA.
     * @param mgf1HashType hash function used for MGF1 (normally this is SHA1).
     */
    public abstract void generate(int bitStrength, HashType mgf1HashType);

    /**
     * True if the key is properly initialized. The key is initialized after generate
     * is called or loaded from the hash (see {@link Hashable}
     */
    public abstract boolean isInitialized();


    /**
     * @return bit strength of the key, e.g. 2048 for RSA.
     */
    public abstract int getBitStrength();

    /**
     * @return corresponding public key
     */
    abstract public AbstractPublicKey getPublicKey() throws IllegalStateException;

    /**
     * Check that this key instance is suitable for decryption too (some keys can provide
     * sign only functionality).
     *
     * @return true if this key can be used for decryption.
     */
    public abstract boolean canDecrypt();


    /**
     * Decrypt the ciphertext.
     *
     * @param ciphertext to decrypt
     * @return decrypted plaintext
     * @throws EncryptionError if any error prevents decryption (incorrect block data, block too short and like)
     */
    public abstract byte[] decrypt(byte[] ciphertext) throws EncryptionError;

    /**
     * Sign the source document represented by the input stream. The document can be way too large to
     * load the whole into memory, so implementations should calculate the signature by reading
     * portions of it from the stream.
     *
     * @param input document to sign
     * @param salt salt to use; may be null, then it is generated automatically with the maximum possible size.
     * @return digital signature of the document
     * @throws IOException if the document can't be read, is empty and like.
     */
    public abstract byte[] sign(InputStream input, HashType hashType, @Nullable byte[] salt) throws IOException;

    /**
     * Sign the source document represented by the input stream. The document can be way too large to
     * load the whole into memory, so implementations should calculate the signature by reading
     * portions of it from the stream.
     *
     * @param input document to sign
     * @return digital signature of the document
     * @throws IOException if the document can't be read, is empty and like.
     */
    public byte[] sign(InputStream input, HashType hashType) throws IOException {
        return sign(input, hashType, null);
    }

    /**
     * Digitally sign data in array.
     *
     * @param data to sign
     * @param salt salt to use; may be null, then it is generated automatically with the maximum possible size.
     * @return digital signature
     */
    public byte[] sign(byte[] data, HashType hashType, @Nullable byte[] salt) {
        try {
            return sign(new ByteArrayInputStream((data)), hashType, salt);
        } catch (IOException e) {
            throw new RuntimeException("Failed to sign", e);
        }
    }

    /**
     * Digitally sign data in array.
     *
     * @param data to sign
     * @return digital signature
     */
    public byte[] sign(byte[] data, HashType hashType) {
        try {
            return sign(new ByteArrayInputStream((data)), hashType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to sign", e);
        }
    }

    /**
     * Digitally sign a string encoded in UTF-8.
     *
     * @param data to sign
     * @param salt salt to use; may be null, then it is generated automatically with the maximum possible size.
     * @return digital signature
     */
    public byte[] sign(String data, HashType hashType, @Nullable byte[] salt) {
        return sign(data.getBytes(), hashType, salt);
    }

    /**
     * Digitally sign a string encoded in UTF-8.
     *
     * @param data to sign
     * @return digital signature
     */
    public byte[] sign(String data, HashType hashType) {
        return sign(data.getBytes(), hashType);
    }
}
