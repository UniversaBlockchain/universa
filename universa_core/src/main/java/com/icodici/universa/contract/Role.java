/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for any role combination, e.g. single key, any key from a set, all keys from a set, minimum number of key
 * from a set and so on.
 * <p>
 * IMPORTANT, This class express "any_of" logic, e.g. if any pf the presented keys is listed, then the role is allowed.
 */
public class Role {
    private final String name;
    private final Map<PublicKey, KeyRecord> keyRecords = new HashMap<>();
    private Contract contract;

    public Role(String name, KeyRecord keyRecord) {
        this.name = name;
        keyRecords.put(keyRecord.getPublicKey(), keyRecord);
    }

    public Role(String name) {
        this.name = name;
    }


    public Role(String name, @NonNull Collection records) {
        this.name = name;
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

    public String getName() {
        return name;
    }

    /**
     * Testing only. For lne-key roles, return the keyrecord.
     *
     * @return
     */
    @Deprecated
    public KeyRecord getKeyRecord() {
        return keyRecords.values().iterator().next();
    }

    public Collection<KeyRecord> getKeyRecords() {
        return keyRecords.values();
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
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Role)
            return ((Role) obj).name.equals(name) && ((Role) obj).equalKeys(this);
        return false;
    }

    public boolean equalKeys(Object obj) {
        if (obj instanceof Role)
            return ((Role) obj).getKeys().equals(getKeys());
        return false;
    }

    static Role fromBinder(String name, Binder serializedRole) {
        if (name == null)
            name = serializedRole.getStringOrThrow("name");
        if (serializedRole.containsKey("key")) {
            // Single-key role
            return new Role(name, new KeyRecord(serializedRole));
        }
        Role r = new Role(serializedRole.getString("name", name));
        for (Object x : (Object[]) serializedRole.getOrThrow("keys")) {
            r.addKeyRecord(new KeyRecord(Binder.from(x)));
        }
        return r;
    }

    static {
        Boss.registerAdapter(Role.class, new Boss.Adapter() {
            @Override
            public Binder serialize(Object roleObject) {
                Role r = (Role) roleObject;
                return Binder.fromKeysValues(
                        "name", r.name,
                        "keys", r.keyRecords.values()
                );
            }

            @Override
            public Object deserialize(Binder binder) {
                return Role.fromBinder(null, binder);
            }

            @Override
            public String typeName() {
                return "role";
            }
        });
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
    public Role cloneAs(String name) {
        Role r = new Role(name);
        keyRecords.values().forEach(kr -> r.addKeyRecord(kr));
        return r;
    }

    @Override
    public String toString() {
        return "Role<" + System.identityHashCode(this) + ":" + name + ":anyOf:" + keyRecords.keySet() + ">";
    }

    public boolean isAllowedForKeys(Role anotherRole) {
        return isAllowedForKeys(anotherRole.keyRecords.keySet());
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public Contract getContract() {
        return contract;
    }
}
