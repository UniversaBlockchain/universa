/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.rsaoaep.RSAOAEPPublicKey;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Hashable;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.Ut;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by sergeych on 02/12/16.
 */
public class PublicKey extends AbstractKey {
    private final RSAOAEPPublicKey publicKey;
    private byte[] cachedHint;

    public PublicKey(AbstractPublicKey publicKey) {
        this.publicKey = (RSAOAEPPublicKey) publicKey;
        setupInfo(pack());
    }

    public PublicKey() {
        this.publicKey = new RSAOAEPPublicKey();
    }

    public PublicKey(final byte[] bytes) throws EncryptionError {
        this.publicKey = new RSAOAEPPublicKey();
        unpack(bytes);
    }

    public PublicKey(final byte[] bytes, KeyInfo info) throws EncryptionError {
        this.publicKey = new RSAOAEPPublicKey();
        unpack(bytes, info);
    }

    public void unpack(byte[] bytes) throws EncryptionError {
        unpack(bytes, null);
    }

    @Override
    public boolean isPublic() {
        return true;
    }

    public void unpack(byte[] bytes, KeyInfo info) throws EncryptionError {
        List parts = Boss.load(bytes);
        if ((Integer) parts.get(0) != 1)
            throw new EncryptionError("invalid packed public key");
        try {
            // e, n
            Binder pp = new Binder("e", ((Bytes) parts.get(1)).toArray(),
                                   "n", ((Bytes) parts.get(2)).toArray());
            setComponents(pp);
        } catch (Exception error) {
            error.printStackTrace();
            throw new EncryptionError("failed to parse public key", error);
        }
        if (info == null)
            setupInfo(bytes);
        else
            keyInfo = info;
    }

    private void setupInfo(byte[] bytes) {
        keyInfo = new KeyInfo(KeyInfo.Algorythm.RSAPublic,
                              Arrays.copyOf(new Sha256().digest(bytes), 5),
                              publicKey.getBitStrength() / 8);
    }

    private void setComponents(Binder pp) throws Hashable.Error {
        publicKey.updateFromHash(pp);
        cachedHint = null;
    }

    public byte[] encrypt(String plainText) throws EncryptionError {
        return encrypt(plainText.getBytes(Ut.utf8));
    }

    public byte[] encrypt(byte[] bytes) throws EncryptionError {
        return publicKey.encrypt(bytes);
    }

    public byte[] pack() {
        Map<String, Object> params = publicKey.toHash();
        return Boss.dumpToArray(new Object[]{
                1,
                params.get("e"),
                params.get("n")
        });
    }

    public boolean verify(InputStream source, byte[] signature, HashType hashType) throws
            IOException {
        return publicKey.checkSignature(source, signature, hashType);
    }

    /**
     * Keys equality check. Only public keys are equal to each other. Right now private keys can't
     * be equal to the public even if the latter is its part.
     *
     * @param obj
     *         key to compare with. Should be PublicKey instaance.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PublicKey) {
            PublicKey k = (PublicKey) obj;
            Map<String, Object> a = publicKey
                    .toHash();
            Map<String, Object> b = k.publicKey
                    .toHash();
            return Arrays.equals((byte[]) a.get("e"), (byte[]) b.get("e")) &&
                    Arrays.equals((byte[]) a.get("n"), (byte[]) b.get("n"));
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        Map<String, Object> a = publicKey
                .toHash();
        byte[] key = (byte[]) a.get("n");
        return key[0] + (key[1] << 8) + (key[2] << 16) + (key[3] << 24);
    }

    private byte[] _fingerprint;

    @Override
    public byte[] fingerprint() {
        synchronized (publicKey) {
            if( _fingerprint == null ) {
                _fingerprint = new byte[33];
                _fingerprint[0] = (byte) FINGERPRINT_SHA512;
                Map<String, Object> a = publicKey.toHash();
                System.arraycopy(
                        new Sha256().update((byte[])a.get("e")).update((byte[])a.get("n")).digest(),
                        0,
                        _fingerprint,
                        1,
                        32);
            }
            return _fingerprint;
        }
    }

    static {
        DefaultBiMapper.registerAdapter(PublicKey.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                return Binder.of("packed",
                                 serializer.serialize(((PublicKey) object).pack()));
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                try {
                    return new PublicKey(binder.getBinaryOrThrow("packed"));
                } catch (EncryptionError encryptionError) {
                    return null;
                }
            }

            @Override
            public String typeName() {
                return "RSAPublicKey";
            }
        });
    }
}
