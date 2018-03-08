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
import com.icodici.crypto.KeyAddress;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.KeyRecord;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
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
    private final Set<AnonymousId> anonymousIds = new HashSet<>();
    private final Set<KeyAddress> keyAddresses = new HashSet<>();

    public SimpleRole(String name, @NonNull KeyRecord keyRecord) {
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
            AnonymousId anonId = null;
            if (x instanceof KeyRecord)
                kr = (KeyRecord) x;
            else if (x instanceof PublicKey)
                kr = new KeyRecord((PublicKey) x);
            else if (x instanceof AnonymousId)
                anonId = (AnonymousId) x;
            else if (x instanceof PrivateKey)
                kr = new KeyRecord(((PrivateKey) x).getPublicKey());
            else if (x instanceof KeyAddress)
                keyAddresses.add((KeyAddress) x);
            else
                throw new IllegalArgumentException("Cant create KeyRecord from " + x);
            if (anonId != null)
                anonymousIds.add(anonId);
            else
                keyRecords.put(kr.getPublicKey(), kr);
        });
    }

    public void addKeyRecord(KeyRecord keyRecord) {
        keyRecords.put(keyRecord.getPublicKey(), keyRecord);
    }

    /**
     * Testing only. For one-key roles, return the keyrecord.
     *
     * @return got {@link KeyRecord}
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

    @Override
    public Set<PublicKey> getKeys() {
        return keyRecords.keySet();
    }

    @Override
    public Set<AnonymousId> getAnonymousIds() {
        return anonymousIds;
    }

    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        // any will go logic
        return keys.stream().anyMatch(k -> {
            boolean anyMatch1 = anonymousIds.stream().anyMatch(anonId -> {
                try {
                    return k.matchAnonymousId(anonId.getBytes());
                } catch (IOException e) {
                    return false;
                }
            });
            boolean anyMatch2 = keyRecords.containsKey(k.getPublicKey());
            boolean anyMatch3 = keyAddresses.stream().anyMatch(address -> {
                try {
                    return k.isMatchingKeyAddress(address);
                } catch (IllegalArgumentException e) {
                    return false;
                }
            });
            return anyMatch1 || anyMatch2 || anyMatch3;
        });
    }

    public boolean isValid() {
        return !keyRecords.isEmpty() || !anonymousIds.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleRole) {
            boolean a = ((SimpleRole) obj).getName().equals(getName());
            boolean b = ((SimpleRole) obj).equalKeys(this);
            boolean c = ((SimpleRole) obj).anonymousIds.containsAll(this.anonymousIds);
            boolean d = ((SimpleRole) obj).keyAddresses.containsAll(this.keyAddresses);
            return a && b && c && d;
        }
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
     * @param name is new name for the role
     *
     * @return cloned {@link SimpleRole}
     */
    public SimpleRole cloneAs(String name) {
        SimpleRole r = new SimpleRole(name);
        keyRecords.values().forEach(kr -> r.addKeyRecord(kr));
        anonymousIds.forEach(aid -> r.anonymousIds.add(aid));
        keyAddresses.forEach(keyAddr -> r.keyAddresses.add(keyAddr));

        return r;
    }

    @Override
    public String toString() {
        return "SimpleRole<" + System.identityHashCode(this) + ":" + getName() + ":anyOf:" + keyRecords.keySet() + "|" + anonymousIds + ">";
    }

    public boolean isAllowedForKeys(SimpleRole anotherRole) {
        return isAllowedForKeys(anotherRole.keyRecords.keySet());
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        // role can have keys - this should actually be refactored to let role
        // hold other roles and so on.
        List keyList = data.getList("keys", null);
        if (keyList != null) {
            // TODO: the list of keys must be cleared before the condition is checked "if (keyList != null)". Otherwise, when the role is deserialized without the keys, the old role keys remain keyRecords.clear();
            keyList.forEach(kr -> {
                addKeyRecord(deserializer.deserialize(kr));
            });
        }
        List anonIdList = data.getList("anonIds", null);
        anonymousIds.clear();
        if (anonIdList != null) {
            for (Object aid : anonIdList) {
                anonymousIds.add( deserializer.deserialize(aid));
            }
        }
        List keyAddrList = data.getList("addresses", null);
        keyAddresses.clear();
        if (keyAddrList != null) {
            for (Object keyAddr :  keyAddrList) {
                keyAddresses.add( deserializer.deserialize(keyAddr));
            }
        }
    }

    @Override
    public Binder serialize(BiSerializer s) {
        return super.serialize(s).putAll(
                "keys", s.serialize(keyRecords.values()),
                "anonIds", s.serialize(anonymousIds),
                "addresses", s.serialize(keyAddresses)
        );
    }

    @Override
    public void anonymize() {
        for (PublicKey publicKey : keyRecords.keySet())
            anonymousIds.add(AnonymousId.fromBytes(publicKey.createAnonymousId()));
        keyRecords.clear();
    }

    static {
        DefaultBiMapper.registerClass(SimpleRole.class);
    }
}
