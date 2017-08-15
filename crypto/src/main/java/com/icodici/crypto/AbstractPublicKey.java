/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Hashable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Generic public key interface.
 * <p>
 * Important: The Key can be in not-initialized state after creation, in which case the depending
 * methods must throw {@link IllegalStateException}. Non-initialized state is a transient state
 * between creation and construction from the hash or generation new key.
 * <p>
 * Created by sergeych on 15/04/16.
 */
@SuppressWarnings("unused")
public abstract class AbstractPublicKey extends AbstractAsymmetricKey implements Hashable {

    /**
     * Check that this key instance is suitable for encryption too (some keys can provide
     * checkSignature only functionality).
     *
     * @return true if this key can encrypt.
     */
    public abstract boolean canEncrypt();

    /**
     * @return bit strength of the key, e.g. 2048 ro RSA.
     */
    public abstract int getBitStrength();

    /**
     * True if the key is properly initialized
     */
    public abstract boolean isInitialized();

    /**
     * Public-key encryption of the block.
     *
     * @param plaintext data to encrypt.
     * @throws EncryptionError if the key can't encrypt, data too large for the key or any other error that prevents
     *                         data to be encrypted.
     */
    public abstract byte[] encrypt(byte[] plaintext) throws EncryptionError;

    /**
     * Check the digital signature. As very large documents can be signed, we represent them as
     * streams. The document can be way too large to load the whole into memory, so implementations
     * should calculate the signature by reading portions of it from the stream.
     * <p>
     * The method must not throw exception if the signature is bad. e.g. improper, has wrong
     * structure and so on. Instead, it must return false.
     *
     * @param input     source data
     * @param signature signature to check
     *
     * @return true if the signature is valid, false if not.
     * @throws IOException failed to read the input stream (including empty stream, EOF at start)
     */
    public abstract boolean checkSignature(InputStream input,
                                           byte[] signature,
                                           HashType hashType,
                                           int saltLength)
            throws IOException, IllegalStateException;

    /**
     * Check the digital signature. As very large documents can be signed, we represent them as
     * streams. The document can be way too large to load the whole into memory, so implementations
     * should calculate the signature by reading portions of it from the stream.
     * <p>
     * The method must not throw exception if the signature is bad. e.g. improper, has wrong
     * structure and so on. Instead, it must return false.
     *
     * @param input     source data
     * @param signature signature to check
     *
     * @return true if the signature is valid, false if not.
     * @throws IOException failed to read the input stream (including empty stream, EOF at start)
     */
    public boolean checkSignature(InputStream input,
                                  byte[] signature,
                                  HashType hashType)
            throws IOException, IllegalStateException {
        return checkSignature(input, signature, hashType, MAX_SALT_LENGTH);
    }


    /**
     * Any encryption type has an unique tag. For RSAES-OAEP the tag is r1. For elliptic curve
     * variant used in bitcoin, "e1" and so on. The tag is not intended to be human readable, it is
     * used to properly select decryption method and not waste package space.
     *
     * @return encryption method tag
     */
    public abstract String algorithmTag();

    /**
     * @param input     to check the signature against.
     * @param signature signature to check
     * @param hashType  type of the hash function used to create the signature
     * @return true if the signature is correct.
     */
    public boolean checkSignature(byte[] input, byte[] signature, HashType hashType, int saltLength) {
        try {
            return checkSignature(new ByteArrayInputStream(input), signature, hashType, saltLength);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @param input     to check the signature against.
     * @param signature signature to check
     * @param hashType  type of the hash function used to create the signature
     * @return true if the signature is correct.
     */
    public boolean checkSignature(byte[] input, byte[] signature, HashType hashType) {
        try {
            return checkSignature(new ByteArrayInputStream(input), signature, hashType, MAX_SALT_LENGTH);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @param data      to check the signature against.
     * @param signature signature to check
     * @return true if the signature is correct.
     */
    public boolean checkSignature(String data, byte[] signature, HashType hashType) {
        return checkSignature(data.getBytes(), signature, hashType);
    }

    /**
     * Encrypt data of the string using UTF-8 encoding
     *
     * @param data to encrypt
     * @return encrypted data
     * @throws EncryptionError
     */
    public byte[] encrypt(String data) throws EncryptionError {
        return encrypt(data.getBytes());
    }
}
