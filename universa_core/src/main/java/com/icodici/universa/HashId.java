/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

import com.icodici.crypto.Sha512;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Base64u;

import java.util.Arrays;

/**
 * Hash-based identity. This implementation uses SHA512, but it could be easily changed to adopt some other type. This
 * implementation is intended to use with packed data, such as {@link Approvable} documents.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class HashId implements Comparable<HashId> {

    public HashId(byte[] packedData) {
        initWith(packedData);
    }

    private HashId() {
    }

    @Override
    public boolean equals(Object obj) {
        // Faster than compareTo:
        if (obj instanceof HashId)
            return Arrays.equals(digest, ((HashId) obj).digest);
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(digest);
    }

    /**
     * Setup with binary data. Note that it is not allowed to update initialized instance, {@link IllegalStateException}
     * will be thrown otherwise.
     *
     * @param packedData data to initialize from
     */
    public void initWith(byte[] packedData) {
        if (digest == null)
            digest = new Sha512().digest(packedData);
        else
            throw new IllegalStateException("HashId is already initialized");
    }

    public byte[] getDigest() {
        return digest;
    }

    protected byte[] digest;

    @Override
    public int compareTo(HashId other) {
        if (digest.length != other.digest.length)
            throw new IllegalStateException("different digest size");

        for (int i = 0; i < digest.length; i++) {
            // We need unsigned bytes for proper comparing:
            int my = digest[i] & 0xFF;
            int his = other.digest[i] & 0xFF;
            if (my < his) return -1;
            if (my > his) return +1;
        }
        return 0;
    }

    /**
     * convert to "compact" base64u string, e.g. without '+' and without trailing '=' characters. See {@link Base64u},
     * {@link Base64#encodeCompactString(byte[])}. Use Base64u#decodeCompactString to restore the digest.
     *
     * @return base64u-encoded digest without trailing equality signs
     */
    @Override
    public String toString() {
        return digest == null ? "null" : (Base64u.encodeCompactString(digest).substring(0,8)+"…");
    }

    /**
     * Create random new hashId. Mainly for testing purposes. To create random bytes, use {@link Do#randomBytes(int)}
     * instead.
     *
     * @return instance with random digest
     */
    public static HashId createRandom() {
        return HashId.withDigest(Do.randomBytes(64));
    }

    /**
     * Create instance from a saved digest (obtained before with {@link #getDigest()}.
     *
     * @param hash digest to construct with
     * @return instance with a given digest
     */
    static public HashId withDigest(byte[] hash) {
        HashId id = new HashId();
        id.digest = hash;
        return id;
    }

    public String toBase64String() {
        assert(digest != null);
        return Base64u.encodeCompactString(digest);
    }

    /**
     * Return new HashId calculating SHA512 hash of the data
     *
     * @param data
     * @return HashId instance corresponding to the data parameter, using default hash algorithm
     */
    public static HashId of(byte[] data) {
        return new HashId(data);
    }

    static {
        DefaultBiMapper.registerAdapter(HashId.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                return Binder.fromKeysValues(
                        "sha512", ((HashId)object).getDigest()
                );
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                return HashId.withDigest(binder.getBinaryOrThrow("sha512"));
            }

            @Override
            public String typeName() {
                return "HashId";
            }
        });
    }
}
