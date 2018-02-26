/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.HMAC;
import net.sergeych.tools.ByteRingBuffer;
import net.sergeych.tools.Do;
import net.sergeych.tools.Hashable;

import java.io.*;
import java.util.Arrays;
import java.util.Map;

/**
 * Symmetric key: main interface to the symmetric cipher used in attesta.
 * This implementation uses AES256 in CRT mode with IV to
 * encrypt / decrypt. To change it, derive your class and simply override {@link #getCipher()}.
 * <p>
 * The cipher set and HMAC method maight be extended, using this class guaranteees compatibility.
 * <p>
 * Created by sergeych on 04/06/16.
 */
@SuppressWarnings("unused")
public class SymmetricKey extends AbstractKey implements Serializable, Hashable {

    public static SymmetricKey fromPassword(String password, int rounds) {
        return fromPassword(password, rounds, null);
    }

    public static SymmetricKey fromPassword(String password, int rounds, byte[] salt) {
        return new KeyInfo(KeyInfo.PRF.HMAC_SHA256, rounds, salt, null)
                .derivePassword(password);
    }

    /**
     * Exception raised by the {@link EtaDecryptingStream} when HMAC verification failed or other
     * situations of the kind.
     */
    public class AuthenticationFailed extends IOException {
        public AuthenticationFailed() {
        }

        public AuthenticationFailed(String message) {
            super(message);
        }
    }

    /**
     * Read and decrypt AE (EtA) stream. When reaches the end, checks the HMAC (based on SHA256)
     * and throws {@link AuthenticationFailed} if it does not match.
     */
    public class EtaDecryptingStream extends InputStream {
        private final InputStream inputStream;
        private final CTRTransformer transformer;
        private final ByteRingBuffer ring;
        private final HMAC hmac;
        private boolean readingFinished = false;

        EtaDecryptingStream(InputStream inputStream) throws IOException, EncryptionError {
            this.inputStream = inputStream;
            byte[] IV = new byte[getCipher().getBlockSize()];
            inputStream.read(IV);
            transformer = new CTRTransformer(getCipher(), IV);

            hmac = new HMAC(key);

            // We should have always block bytes in the buffer to finish:
            ring = new ByteRingBuffer(hmac.getLength() + 8);
            for (int i = 0; i < hmac.getLength(); i++)
                ring.put(inputStream.read());
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            if (b == null) {
                throw new NullPointerException();
            } else if (off < 0 || len < 0 || len > b.length - off) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }
            // here we protect with double end read: we wan't do this, because we call end() when read last byte
            if(!readingFinished) {
//                int c = read();
//                if (c == -1) {
//                    return -1;
//                }
//                b[off] = (byte) c;

                int c;
                int i = 0;
                // and here we need to throw custom exceptions
//            try {
                for (; i < len; i++) {
                    c = read();
                    if (c == -1) {
                        break;
                    }
                    b[off + i] = (byte) c;
                }
//            } catch (IOException ee) {
//            }
                return i;
            }
            // and here we return -1 code as expected by callers if read has ends
            return -1;
        }

        @Override
        public int read() throws IOException {
            int nextByte = inputStream.read();
            if (nextByte < 0) {
                readingFinished = true;
                end();
                return -1;
            } else {
                ring.put(nextByte);
                try {
                    int encrypted = ring.get();
                    hmac.update(encrypted);
                    return transformer.transformByte(encrypted);
                } catch (EncryptionError encryptionError) {
                    throw new IOException("failed to decrypt", encryptionError);
                }
            }
        }

        private void end() throws IOException {
            byte[] readHmac = ring.readAll();
            if (readHmac.length != hmac.getLength())
                throw new IOException("stream corrupted: bad hmac record size:" + readHmac.length);
            if (!Arrays.equals(readHmac, hmac.digest())) {
                throw new AuthenticationFailed("HMAC authentication failed, data corrupted");
            }
        }
    }

    /**
     * Encrypts an AE (EtA) stream using Sha256-based HMAC. When all the data are written, call
     * {@link #end()} or {@link #close()} explicitely to add HMAC record. The difference is, close()
     * closes the output stream too.
     */
    public class EtaEncryptingStream extends OutputStream {

        private final HMAC hmac;
        private boolean done = false;
        private OutputStream outputStream;
        private CTRTransformer transformer;

        EtaEncryptingStream(OutputStream outputStream) throws IOException, EncryptionError {
            this(outputStream, true);
        }

        EtaEncryptingStream(OutputStream outputStream, boolean encrypt) throws IOException,
                EncryptionError {
            int blockSize = 64; // for SHA256 at least
            this.outputStream = outputStream;

            hmac = new HMAC(key);

            if (encrypt) {
                transformer = new CTRTransformer(getCipher(), null);
                outputStream.write(transformer.getIV());
            }
        }

        /**
         * Finishes encryption and writes down HMAC record. Futhher writing will cause {@link
         * EOFException}. Does not close the underlying output stream!
         *
         * @throws IOException
         */
        public void end() throws IOException {
            if (done)
                return;
            done = true;
            outputStream.write(hmac.digest());
        }

        /**
         * Calls {@link #end()}, if wasn't called before, and closes undelying stream. Therefore,
         * it is not possible to use the undelying stream after calling it.
         *
         * @throws IOException
         */
        @Override
        public void close() throws IOException {
            if (!done)
                end();
            outputStream.close();
        }

        @Override
        public void write(int plain) throws IOException {
            if (done)
                throw new EOFException("can't write past the end()");
            try {
                int encrypted = transformer == null ? plain : transformer.transformByte(plain);
                hmac.update(encrypted);
                outputStream.write(encrypted);
            } catch (EncryptionError encryptionError) {
                throw new IOException("failed to encrypt", encryptionError);
            }
        }
    }

    private byte[] key;

    private BlockCipher cipher = null;

    /**
     * Create random symmetric key (AES256, CTR)
     */
    public SymmetricKey() {
        key = CTRTransformer.randomBytes(32);
        keyInfo = new KeyInfo(KeyInfo.Algorythm.AES256, null, 32);
    }

    public SymmetricKey(byte[] key) {
        setKey(key);
    }

    public SymmetricKey(byte[] key, KeyInfo keyInfo) {
        setKey(key);
        this.keyInfo = keyInfo;
    }

    public void setKey(byte[] key) {
        cipher = null;
        this.key = key;
    }

    public byte[] getKey() {
        return key;
    }

    public int getBitStrength() {
        return getSize() * 8;
    }

    public int getSize() {
        return key.length;
    }

    @Override
    public Map<String, Object> toHash() throws IllegalStateException {
        return Do.map("key", key);
    }

    @Override
    public void updateFromHash(Map<String, Object> hash) throws Error {
        setKey((byte[]) hash.get("key"));
    }

    protected BlockCipher getCipher() {
        if (cipher == null) {
            cipher = new AES256();
            cipher.initialize(BlockCipher.Direction.ENCRYPT, this);
        }
        return cipher;
    }

    public byte[] encrypt(byte[] plaintext) throws EncryptionError {
        return EncryptingStream.encrypt(getCipher(), plaintext);
    }

    public byte[] decrypt(byte[] ciphertext) throws EncryptionError {
        return DecryptingStream.decrypt(getCipher(), ciphertext);
    }

    public OutputStream encryptStream(OutputStream outputStream) throws IOException,
            EncryptionError {
        return new EncryptingStream(getCipher(), outputStream);
    }

    public InputStream decryptStream(InputStream inputStream) throws IOException, EncryptionError {
        return new DecryptingStream(getCipher(), inputStream);
    }

    /**
     * Encrypt some stream on hte fly using AE (EtA) with SHA256-based HMAC. Caller must call {@link
     * EtaEncryptingStream#end()} or {@link EtaEncryptingStream#close()} in order to properly finish
     * AE process. Note, to get the maximum security you should put some random sized random data in
     * the file. As a variant, write some number N and then N random bytes. Have N also random. When
     * reading, read N and skip N bytes first. Otherwise, use {@link net.sergeych.boss.Boss} streams
     * to write structured data and put some random bytes in the beginning then skip it.
     *
     * @param out
     *         stream where to put encrypted data.
     *
     * @return encrpyting stream
     *
     * @throws IOException
     * @throws EncryptionError
     */
    public EtaEncryptingStream etaEncryptStream(OutputStream out) throws IOException,
            EncryptionError {
        return new EtaEncryptingStream(out);
    }

    /**
     * Decrypt some stream on the fly using AE (EtA) and SHA256-based HMAC. Simply read the returned
     * to the end stream. When it reaches the end, HMAC record will be extracted and checked, and
     * the {@link AuthenticationFailed} will be thrown as need.
     *
     * @param in
     *         stream to decrypt
     *
     * @return stream with decrypted data.
     *
     * @throws IOException
     * @throws EncryptionError
     */
    public EtaDecryptingStream etaDecryptStream(InputStream in) throws IOException,
            EncryptionError {
        return new EtaDecryptingStream(in);
    }

    /**
     * Encrypt data suing AE (EtA) with HMAC based on SHA256. It is advisory ro add some
     * random-sized random data chunk (easiest is to add it to the beginning), or use {@link
     * net.sergeych.boss.Boss} to serialize structored data and add random chunk somewhere. Make the
     * hacker effor hopeless and silly ;) Keeping the right data size reveals some information on
     * the package contents, just don't.
     * <p>
     * It uses IV-based CTR encryption, see {@link #encrypt(byte[])} for details.
     *
     * @param data
     *         to encrypt
     *
     * @return encrypted data.
     *
     * @throws EncryptionError
     */
    public byte[] etaEncrypt(byte[] data) throws EncryptionError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            final EtaEncryptingStream s = etaEncryptStream(bos);
            s.write(data);
            s.end();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOError", e);
        }
    }

    /**
     * Rare case: HMAC-signing data without ebcrypting it. Right now we use it only
     * for testing, but if need can extends with verification of the plain data.
     *
     * @param data
     *         to sign
     *
     * @return data + HMAC (last 32 bytes)
     *
     * @throws EncryptionError
     */
    public byte[] etaSign(byte[] data) throws EncryptionError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            final EtaEncryptingStream s = new EtaEncryptingStream(bos, false);
            s.write(data);
            s.end();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOError", e);
        }
    }

    /**
     * Decrypt data using AE (EtA) with SHA256-based HMAC.
     *
     * @param data
     *         to decrypt
     *
     * @return decrypted data
     *
     * @throws EncryptionError
     * @throws AuthenticationFailed
     *         if the authentication record does not match the data.
     */
    public byte[] etaDecrypt(byte[] data) throws EncryptionError, AuthenticationFailed {
        try {
            return Do.read(etaDecryptStream(new ByteArrayInputStream(data)));
        } catch (AuthenticationFailed e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("unexpected IOError", e);
        }
    }

    public static byte[] xor(byte[] src, int value) {
        byte[] result = new byte[src.length];
        for (int i = 0; i < src.length; i++)
            result[i] = (byte) ((src[i] ^ value) & 0xFF);
        return result;
    }

    @Override
    public byte[] pack() {
        if (key == null)
            throw new IllegalStateException("key is not yet initialized, no keyInfo is available");
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SymmetricKey))
            return false;
        return Arrays.equals(key, ((SymmetricKey) obj).key);
    }

    @Override
    public int hashCode() {
        return key[0] + (key[1] << 8) + (key[2] << 16) + (key[3] << 24);
    }
}
