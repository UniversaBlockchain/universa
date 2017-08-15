/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration with various supported hash functions.
 *
 * Created by sergeych on 07/12/16.
 */
public enum HashType {
    SHA1,
    SHA256,
    SHA512;
    /* When adding any new value, make sure to add the line in {@link algorithmByType}! */

    private static Map algorithmByType = Collections.unmodifiableMap(new HashMap<HashType, Digest>() {{
        put(HashType.SHA1, new SHA1Digest());
        put(HashType.SHA256, new SHA256Digest());
        put(HashType.SHA512, new SHA512Digest());
    }});

    private static Map algorithmNameByType = Collections.unmodifiableMap(new HashMap<HashType, String>() {{
        for (Object key : algorithmByType.keySet()) {
            put((HashType) key, ((Digest)algorithmByType.get(key)).getAlgorithmName());
        }
    }});
    private static Map algorithmTypeByName = Collections.unmodifiableMap(new HashMap<String, HashType>() {{
        for (Object key : algorithmByType.keySet()) {
            put(((Digest)algorithmByType.get(key)).getAlgorithmName(), (HashType) key);
        }
    }});

    /**
     * Create a new {@link Digest} for this hash type.
     * */
    public Digest makeDigest() {
        Digest result = (Digest) this.algorithmByType.get(this);
        try {
            return result.getClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(String.format("Unsupported digest %s", this));
        }
    }

    /**
     * Get the name of the algorithm.
     * */
    public String getAlgorithmName() {
        return (String) algorithmNameByType.get(this);
    }

    /**
     * Given the digest/hash algorithm name, return a new instance of the appropriate {@link HashType}.
     * May return `null` if the digest `algorithmName` is not supported.
     */
    public static HashType getByAlgorithmName(String algorithmName) {
        HashType hashTypeClass = (HashType) algorithmTypeByName.get(algorithmName);
        if (hashTypeClass == null) {
            return null;
        } else {
            return hashTypeClass;
        }

    }
}
