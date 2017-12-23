/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;

import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The key data, contains not only the key, but also some additional information, to be kept in the contract alongside
 * with a key. The key itself could be base64-encoded string with packed key, byte[] data with packed key or even an
 * instance of {@link PublicKey} or {@link PrivateKey}.
 * <p>
 * Keydata support equality: equal instances are these with equal keys.
 */
@BiType(name="KeyRecord")
public class KeyRecord extends Binder implements BiSerializable {

    private PublicKey publicKey;

    private KeyRecord() {}

    /**
     * Construct from a builder. Note that on successful construction "key" parameter will be updated with valid {@link
     * PublicKey} instance, same as {@link #getPublicKey()} returns.
     *
     * @param binder
     *
     * @throws IllegalArgumentException
     */
    public KeyRecord(Binder binder) {
        super(binder);
        setupKey();
    }

    private void setupKey() {
        try {
            Object x = getOrThrow("key");
            remove("key");
            if (x instanceof PublicKey) {
                publicKey = (PublicKey) x;
            } else if (x instanceof PrivateKey) {
                publicKey = ((PrivateKey) x).getPublicKey();
            } else if (x instanceof String) {
                publicKey = new PublicKey(Base64u.decodeCompactString((String) x));
            } else {
                if (x instanceof Bytes)
                    x = ((Bytes) x).toArray();
                if (x instanceof byte[]) {
                    publicKey = new PublicKey((byte[]) x);
                } else {
                    throw new IllegalArgumentException("unsupported key object: " + x.getClass().getName());
                }
            }
            put("key", publicKey);
        } catch (EncryptionError e) {
            throw new IllegalArgumentException("unsupported key, failed to construct", e);
        }
    }

    public KeyRecord(PublicKey key) {
        this.publicKey = key;
        put("key", key);
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        put("key", publicKey);
        this.publicKey = publicKey;
    }

    /**
     * Equality based on the keys. Instances are equal if keys are equal, other fields are not compared.
     *
     * @param o object to compare
     *
     * @return true if both are {@link KeyRecord} instances with equal keys, see {@link PublicKey#equals(Object)}
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof KeyRecord)
            return ((KeyRecord) o).publicKey.equals(publicKey);
        return false;
    }

    /**
     * hashcode based on the {@link PublicKey#hashCode()}
     *
     * @return has code of the public key
     */
    @Override
    public int hashCode() {
        return publicKey.hashCode();
    }

    public Binder serializeToBinder() {
        Binder data = new Binder();
        data.putAll(this);
        data.put("key", publicKey.pack());
        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        clear();
        putAll(data);
        deserializer.deserializeInPlace(this);
        setupKey();

//        System.out.println("D: " + this);
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder result = new Binder();

//        SortedSet<String> keys = new TreeSet<String>(this.keySet());
//        for (String key : keys) {
//            if(this.get(key) instanceof Map)
//            {
//                SortedSet<String> keys2 = new TreeSet<String>(((Map)this.get(key)).keySet());
//                Binder result2 = new Binder();
//                for (String key2 : keys2) {
//                    result2.put(key2, ((Map)this.get(key)).get(key2));
//                }
//                result.put(key, result2);
//            } else {
//                result.put(key, this.get(key));
//            }
//        }
        Object name = this.get("name");

        if(name != null) {
            if(name instanceof Map) {
                SortedSet<String> keys2 = new TreeSet<String>(((Map)name).keySet());
                Binder result2 = new Binder();
                for (String key2 : keys2) {
                    result2.put(key2, ((Map)name).get(key2));
                }
                result.put("name", result2);
            } else {
                result.put("name", name);
            }
        }
//        result.putAll(this);
        result.put("key", s.serialize(publicKey));
//        System.out.println("S: " + result);
        return result;
    }

    static {
        DefaultBiMapper.registerClass(KeyRecord.class);
    }
}
