/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

import com.icodici.crypto.digest.*;
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
 * Hash-based identity v3.
 * <p>
 * v3 uses 3 orthogonal algorithms and concatenates its results to get slightly longer but much more strong hash.
 * <p>
 * The algorithms are:
 * <p>
 * 1) SHA-512/256, the strongest to the length extension attack SHA2 family variant
 * <p>
 * 2) SHA3-256, which is a different algorithm from sha2 family and is known to be very string
 * <p>
 * 3) ГОСТ Р 34.11-2012 "Stribog" which is a standard in Russian Federation make it eligible in this country. While this
 * hashing algorithm is suspected to be less strong than is stated, in conjunction with two more completely different
 * hashes it makes the result steel solid and bulletproof.
 * <p>
 * The overall compund hash, consisting of 3 concatenated hashes, requires an attacker to create collision on both 3 in
 * the same time which is way more complex task than finding collision on each of them, as, generally, collision on one
 * algorithm will not work with another.
 * <p>
 * The classic usage scenatio is packed data of {@link Approvable} documents.
 * <p>
 * History.
 * <p>
 * First, the {@link Syntex1} algorighm was used, giving additional protection against some attacks by combining SHA2
 * and CRC32 as protection against some future collision attacks.
 * <p>
 * Later, the SHA2-512 was used as its analyse shown very good results.
 * <p>
 * Finally, as Universa grows, more strength and more jurisdiction compliance were added with v3, having 3 independent
 * algorithms, 2 of them are recognized and recommended by NYST and one is required in Russian federation. We suppose
 * that these algorithms joined together are a very hard hash for the collision attack.
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
     * @param packedData
     *         data to initialize from
     */
    public void initWith(byte[] packedData) {
        if (digest == null)
            digest = new CompositeDigest().digest(packedData);
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
        return digest == null ? "null" : (Base64u.encodeCompactString(digest).substring(0, 8) + "…");
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
     * @param hash
     *         digest to construct with
     * @return instance with a given digest
     */
    static public HashId withDigest(byte[] hash) {
        HashId id = new HashId();
        id.digest = hash;
        return id;
    }

    public String toBase64String() {
        assert (digest != null);
        return Base64u.encodeCompactString(digest);
    }

    /**
     * Return new HashId calculating composite digest hash of the data (see {@link CompositeDigest} class)
     *
     * @param data for hashing
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
                        "composite3", serializer.serialize(((HashId) object).getDigest())
                );
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                return HashId.withDigest(binder.getBinaryOrThrow("composite3"));
            }

            @Override
            public String typeName() {
                return "HashId";
            }
        });
    }

    public static HashId withDigest(String encodedString) {
        return withDigest(Base64u.decodeCompactString(encodedString));
    }

    /**
     * Composite digest uses 3 orthogonal algorithms concatenating three different hashes to gether to get longer but
     * much more strong hash.
     * <p>
     * The algorithms are:
     * <p>
     * 1) SHA-512/256, the strongest to the length extension attack SHA2 family variant
     * <p>
     * 2) SHA3-256, which is a different algorithm from sha2 family and is known to be very string
     * <p>
     * 3) ГОСТ Р 34.11-2012 "Stribog" which is a standard in Russian Federation make it eligible in this country. While
     * this hashing algorithm is suspected to be less strong than is stated, in conjunction with two more completely
     * different hashes it makes the result steel solid and bulletproof.
     * <p>
     * The overall compund hash, consisting of 3 concatenated hashes, requires an attacker to create collision on both 3
     * in the same time which is way more complex task than finding collision on each of them, as, generally, collision
     * on one algorithm will not work with another.
     * <p>
     * The classic usage scenatio is packed data of {@link Approvable} documents.
     * <p>
     * Created by sergeych on 16/07/2017.
     */
    public static class CompositeDigest extends Digest {

        private Sha512_256 sha2Digest = new Sha512_256();
        private Sha3_256 sha3Digest = new Sha3_256();
        private Gost3411_2012_256 gostDigest = new Gost3411_2012_256();

        @Override
        protected void _update(byte[] data, int offset, int size) {
            sha2Digest.update(data, offset, size);
            sha3Digest.update(data, offset, size);
            gostDigest.update(data, offset, size);
        }

        @Override
        protected byte[] _digest() {
            int length = getLength();
            byte[] data = new byte[getLength()];
            int pos = 0;

            byte[] d = sha2Digest.digest();
            System.arraycopy(d, 0, data, pos, d.length);
            pos += d.length;

            d = sha3Digest.digest();
            System.arraycopy(d, 0, data, pos, d.length);
            pos += d.length;

            d = gostDigest.digest();
            System.arraycopy(d, 0, data, pos, d.length);

            return data;
        }

        @Override
        public int getLength() {
            return sha2Digest.getLength() + sha3Digest.getLength() + gostDigest.getLength();
        }
    }
}

