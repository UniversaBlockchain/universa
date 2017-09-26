/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.KeyRecord;
import com.sun.istack.internal.NotNull;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.*;

/**
 * Base class for any role combination, e.g. single key, any key from a set, all keys from a set, minimum number of key
 * from a set and so on.
 * <p>
 * IMPORTANT, This class express "any_of" logic, e.g. if any of the presented keys is listed, then the role is allowed.
 */
@BiType(name = "SimpleRole")
public class SimpleRole extends Role {

    private final Map<PublicKey, KeyRecord> keyRecords = new HashMap<>();

    public SimpleRole(String name, @NotNull KeyRecord keyRecord) {
        super(name);
        keyRecords.put(keyRecord.getPublicKey(), keyRecord);
    }

    private SimpleRole() {}

    public SimpleRole(String name) {
        super(name);
    }


    public SimpleRole(String name, @NonNull Collection records) {
        super(name);
        records.forEach(x -> {
            KeyRecord kr = null;
            if (x instanceof KeyRecord)
                kr = (KeyRecord) x;
            else if (x instanceof PublicKey)
                kr = new KeyRecord((PublicKey) x);
            else if (x instanceof PrivateKey)
                kr = new KeyRecord(((PrivateKey) x).getPublicKey());
            else
                throw new IllegalArgumentException("Cant create KeyRecord from " + x);
            keyRecords.put(kr.getPublicKey(), kr);
        });
    }

    public void addKeyRecord(KeyRecord keyRecord) {
        keyRecords.put(keyRecord.getPublicKey(), keyRecord);
    }

    /**
     * Testing only. For one-key roles, return the keyrecord.
     *
     * @return
     */
    @Deprecated
    public KeyRecord getKeyRecord() {
        if (keyRecords.size() > 1)
            throw new IllegalStateException("Can't use with non-single key role");
        return keyRecords.values().iterator().next();
    }

    public Set<KeyRecord> getKeyRecords() {
        return new HashSet(keyRecords.values());
    }

    public Set<PublicKey> getKeys() {
        return keyRecords.keySet();
    }

    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        // any will go logic
        return keys.stream().anyMatch(k -> keyRecords.containsKey(k.getPublicKey()));
    }

    public boolean isValid() {
        return !keyRecords.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleRole)
            return ((SimpleRole) obj).getName().equals(getName()) && ((SimpleRole) obj).equalKeys(this);
        return false;
    }

    static {
        DefaultBiMapper.registerClass(SimpleRole.class);
    }

    /**
     * Clone the role with a different names, using the same (not copied) key records, in the new copy of the container.
     * So, it is safe to edit cloned keyRecords, while keys itself are not copied and are packed with Boss effeciently.
     * More or less ;)
     *
     * @param name
     *
     * @return
     */
    public SimpleRole cloneAs(String name) {
        SimpleRole r = new SimpleRole(name);
        keyRecords.values().forEach(kr -> r.addKeyRecord(kr));
        return r;
    }

    @Override
    public String toString() {
        return "SimpleRole<" + System.identityHashCode(this) + ":" + getName() + ":anyOf:" + keyRecords.keySet() + ">";
    }

    public boolean isAllowedForKeys(SimpleRole anotherRole) {
        return isAllowedForKeys(anotherRole.keyRecords.keySet());
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        // role can have keys - this should actually be refactored to let role
        // hold other roles and so on.
        List<Binder> keyList = data.getList("keys", null);
        if (keyList != null) {
            keyRecords.clear();
            keyList.forEach(kr -> {
                addKeyRecord(deserializer.deserialize(kr));
            });

        }
    }

    @Override
    public Binder serialize(BiSerializer s) {
        return super.serialize(s).putAll(
                        "keys", s.serialize(keyRecords.values())
                );
    }

    static {
        DefaultBiMapper.registerClass(SimpleRole.class);
    }
}
