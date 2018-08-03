/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.digest.Crc32;
import com.icodici.crypto.digest.Digest;
import com.icodici.crypto.rsaoaep.RSAOAEPPrivateKey;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.Ut;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Basic private key used in the system. At the moment it is long RSA key ad probably more strong
 * or at least the same than widely used elliptic curves.
 * <p>
 * Created by sergeych on 02/12/16.
 */
public class PrivateKey extends AbstractKey {

    private final RSAOAEPPrivateKey privateKey = new RSAOAEPPrivateKey();

    public PrivateKey(byte[] packedBinaryKey, KeyInfo info) throws EncryptionError {
        this(packedBinaryKey);
        // the tag is changed. so we can't use it
//        this.keyInfo = info;
    }

    public PrivateKey(byte[] packedBinaryKey) throws EncryptionError {
        List parts = Boss.load(packedBinaryKey);
        if ((Integer) parts.get(0) == 0) {
            // e, p, q: private key
            try {
                Binder pp = new Binder("e", ((Bytes) parts.get(1)).toArray(),
                                       "p", ((Bytes) parts.get(2)).toArray(),
                                       "q", ((Bytes) parts.get(3)).toArray());
                privateKey.updateFromHash(pp);
            } catch (Exception error) {
                error.printStackTrace();
                throw new EncryptionError("failed to parse private key", error);
            }
        } else if ((Integer) parts.get(0) == 1) {
            throw new PasswordProtectedException("key is password protected");
        } else {
            throw new EncryptionError("Bad or unknown private key type");
        }
    }

    public PrivateKey(int bitStrength) {
        privateKey.generate(bitStrength, HashType.SHA1);
    }

    /**
     * Used to load stored keys, see {@link net.sergeych.tools.Bindable} in {@link
     * AbstractKey}.
     *
     * Never use it directly.
     */
    public PrivateKey() {
    }


    @Override
    public byte[] decrypt(final byte[] encrypted) throws EncryptionError {
        return privateKey.decrypt(encrypted);
    }

    private PublicKey cachedPublicKey;

    public PublicKey getPublicKey() {
        if( cachedPublicKey == null )
           cachedPublicKey = new PublicKey(privateKey.getPublicKey());
        return cachedPublicKey;
    }

    public byte[] pack() {
        @NonNull final Map<String, Object> params = privateKey.toHash();
        return Boss.dumpToArray(new Object[]{
                0,
                params.get("e"),
                params.get("p"),
                params.get("q")
        });
    }

    @Override
    public byte[] sign(InputStream input, HashType hashType) throws EncryptionError, IOException {
        return privateKey.sign(input, hashType);
    }

    @Override
    public KeyInfo info() {
        if (keyInfo == null) {
            KeyInfo i = getPublicKey().info();
            keyInfo = new KeyInfo(KeyInfo.Algorythm.RSAPrivate, i.getTag(), privateKey
                    .getBitStrength() / 8);
        }
        return super.info();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PrivateKey))
            return false;
        Map<String, Object> a = privateKey
                .toHash();
        Map<String, Object> b = ((PrivateKey) obj).privateKey
                .toHash();
        return Arrays.equals((byte[]) a.get("e"), (byte[]) b.get("e")) &&
                Arrays.equals((byte[]) a.get("p"), (byte[]) b.get("p")) &&
                Arrays.equals((byte[]) a.get("q"), (byte[]) b.get("q"));
    }

    @Override
    public int hashCode() {
        Map<String, Object> a = privateKey
                .toHash();
        byte[] key = (byte[]) a.get("p");
        return key[0] + (key[1] << 8) + (key[2] << 16) + (key[3] << 24);
    }

    @Override
    public byte[] fingerprint() {
        return getPublicKey().fingerprint();
    }

    public static PrivateKey fromPath(Path path) throws IOException {
        return new PrivateKey(Do.read(path.toAbsolutePath().toString()));
    }

    @Override
    public Digest updateDigestWithKeyComponents(Digest digest) {
        return getPublicKey().updateDigestWithKeyComponents(digest);
    }

    static {
        DefaultBiMapper.registerAdapter(PrivateKey.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                return Binder.fromKeysValues("packed", ((PrivateKey) object).pack());
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                try {
                    return new PrivateKey(binder.getBinaryOrThrow("packed"));
                } catch (EncryptionError encryptionError) {
                    return null;
                }
            }

            @Override
            public String typeName() {
                return "RSAPrivateKey";
            }
        });
    }

    private static int getKDFRounds() {
        return Ut.isJUnitTest() ? 250 : 100000;
    }

    public byte[] packWithPassword(String password) throws EncryptionError {
        byte[] packedKey = pack();
        byte[] salt = getClass().getCanonicalName().getBytes();
        int rounds = getKDFRounds();
        KeyInfo.PRF function = KeyInfo.PRF.HMAC_SHA256;
        SymmetricKey key = new KeyInfo(function, rounds, salt, null)
                .derivePassword(password);

        byte[] packedEncryptedKey = key.encrypt(packedKey);

        return Boss.dumpToArray(new Object[]{
                1,
                rounds,
                salt,
                function.name(),
                packedEncryptedKey,
                new Crc32().update(packedKey).digest()
        });
    }

    public static PrivateKey unpackWithPassword(byte[] packedBinary, String password) throws EncryptionError {
        List params = Boss.load(packedBinary);
        if((Integer) params.get(0) == 0) {
            return new PrivateKey(packedBinary);
        } else if((Integer) params.get(0) == 1) {
            try {
                int rounds = (int) params.get(1);
                Bytes salt = (Bytes) params.get(2);
                String function = (String) params.get(3);
                Bytes packedEncryptedKey = (Bytes) params.get(4);
                Bytes digest = (Bytes) params.get(5);
                SymmetricKey key = new KeyInfo(KeyInfo.PRF.valueOf(function), rounds, salt.getData(), null)
                        .derivePassword(password);
                byte[] packedKey = key.decrypt(packedEncryptedKey.getData());
                byte[] resDigest = new Crc32().update(packedKey).digest();

                if(!digest.equals(new Bytes(resDigest))) {
                    throw new PasswordProtectedException("wrong password");
                }

                return new PrivateKey(packedKey);
            } catch (Exception e) {
                if(e instanceof PasswordProtectedException)
                    throw e;

                throw new EncryptionError("failed to parse password protected private key",e);
            }

        } else {
            throw new EncryptionError("Bad or unknown private key type");
        }
    }

    public static class PasswordProtectedException extends EncryptionError {

        public PasswordProtectedException(String message) {
            super(message);
        }
    }
}