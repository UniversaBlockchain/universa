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
import java.security.Key;
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

    /**
     * Create new {@link SimpleRole} and add one {@link KeyRecord} associated with role.
     *
     * @param name is name of role
     * @param keyRecord is {@link KeyRecord} associated with role
     */
    public SimpleRole(String name, @NonNull KeyRecord keyRecord) {
        super(name);
        keyRecords.put(keyRecord.getPublicKey(), keyRecord);
    }

    private SimpleRole() {}

    /**
     * Create new empty {@link SimpleRole}. Keys or records to role may be added with {@link #addKeyRecord(KeyRecord)}.
     *
     * @param name is name of role
     */
    public SimpleRole(String name) {
        super(name);
    }

    /**
     * Create new {@link SimpleRole}. Records are initialized from the collection and can have the following types:
     * {@link PublicKey}, {@link PrivateKey}, {@link KeyRecord}, {@link KeyAddress}, {@link AnonymousId}.
     *
     * @param name is name of role
     * @param records is collection of records to initialize role
     */
    public SimpleRole(String name, @NonNull Collection records) {
        super(name);
        initWithRecords(records);
    }

    private void initWithRecords(@NonNull Collection records) {
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
            else if (kr != null)
                keyRecords.put(kr.getPublicKey(), kr);
        });
    }

    /**
     * Adds {@link KeyRecord} to role.
     *
     * @param keyRecord is {@link KeyRecord}
     */
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

    /**
     * Get set of all key records in role.
     *
     * @return set of key records (see {@link KeyRecord})
     */
    public Set<KeyRecord> getKeyRecords() {
        return new HashSet(keyRecords.values());
    }

    /**
     * Get set of all keys in role.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    @Override
    public Set<PublicKey> getKeys() {
        return keyRecords.keySet();
    }

    /**
     * Get set of all anonymous identifiers in role.
     *
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    @Override
    public Set<AnonymousId> getAnonymousIds() {
        return anonymousIds;
    }

    /**
     * Get set of all key addresses in role.
     *
     * @return set of key addresses (see {@link KeyAddress})
     */
    @Override
    public Set<KeyAddress> getKeyAddresses() {
        return keyAddresses;
    }

    /**
     * Check role is allowed to keys
     *
     * @param keys is set of keys
     * @return true if role is allowed to keys
     */
    @Override
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

    /**
     * Check validity of role. Valid {@link SimpleRole} contains keys, addresses or anonymous identifiers.
     *
     * @return true if role is valid
     */
    public boolean isValid() {
        return !keyRecords.isEmpty() || !anonymousIds.isEmpty() || !keyAddresses.isEmpty();
    }

    /**
     * Role equality is different: it only checks that it points to the same role.
     *
     * @param obj is object to be checked with
     * @return true if equals
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleRole) {
            boolean a = ((SimpleRole) obj).getName().equals(getName());
            boolean b = ((SimpleRole) obj).equalKeys(this);
            boolean c = ((SimpleRole) obj).anonymousIds.containsAll(this.anonymousIds);
            boolean d = this.anonymousIds.containsAll(((SimpleRole) obj).anonymousIds);

            if (!(a && b && c && d))
                return false;

            boolean e = true;
            for (KeyAddress ka1: this.getKeyAddresses()) {
                e = false;
                for (KeyAddress ka2: ((SimpleRole) obj).getKeyAddresses()) {
                    if (ka1.equals(ka2)) {
                        e = true;
                        break;
                    }
                }

                if (!e)
                    break;
            }

            if (!e)
                return false;

            e = true;
            for (KeyAddress ka1: ((SimpleRole) obj).getKeyAddresses()) {
                e = false;
                for (KeyAddress ka2: this.getKeyAddresses()) {
                    if (ka1.equals(ka2)) {
                        e = true;
                        break;
                    }
                }

                if (!e)
                    break;
            }

            return e;
        }

        return false;
    }

    /**
     * Initializes role from dsl.
     *
     * @param serializedRole is {@link Binder} from dsl with data of role
     */
    @Override
    public void initWithDsl(Binder serializedRole) {
        boolean keysFound = true;
        boolean addressesFound = true;
        boolean anonIdsFound = true;

        if(serializedRole.containsKey("keys")) {
            List<Binder> list = serializedRole.getListOrThrow("keys");
            for(Object keyRecord : list) {
                addKeyRecord(new KeyRecord(Binder.of(keyRecord)));
            }
        } else if(serializedRole.containsKey("key")) {
            addKeyRecord(new KeyRecord(serializedRole));
        } else {
            keysFound = false;
        }



        if(serializedRole.containsKey("addresses")) {
            List<Binder> list = serializedRole.getListOrThrow("addresses");
            for(Object address : list) {
                keyAddresses.add(new KeyAddress(Binder.of(address)));
            }
        } else if(serializedRole.containsKey("uaddress")) {
            keyAddresses.add(new KeyAddress(serializedRole));
        } else {
            addressesFound = false;
        }

        if(serializedRole.containsKey("anonIds")) {
            List<Binder> list = serializedRole.getListOrThrow("anonIds");
            for(Object anonId : list) {
                anonymousIds.add(new AnonymousId(Binder.of(anonId)));
            }
        } else if(serializedRole.containsKey("anonymousId")) {
            anonymousIds.add(new AnonymousId(serializedRole));
        } else {
            anonIdsFound = false;
        }

        if(!addressesFound && !anonIdsFound && !keysFound) {
            //TODO: ?????? "binders" were in old code
            initWithRecords(serializedRole.getListOrThrow("binders"));
        }
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

    /**
     * Get role as string.
     *
     * @return string with data of role
     */
    @Override
    public String toString() {
        return "SimpleRole<" + System.identityHashCode(this) + ":" + getName() + ":anyOf:" + keyRecords.keySet() + "|" +
                anonymousIds +  ":requiredAll:" + requiredAllReferences + ":requiredAny:" + requiredAnyReferences + ">";
    }

    /**
     * Check role is allowed to keys from other {@link SimpleRole}
     *
     * @param anotherRole is other {@link SimpleRole} with keys
     * @return true if role is allowed to keys from other {@link SimpleRole}
     */
    public boolean isAllowedForKeys(SimpleRole anotherRole) {
        return isAllowedForKeys(anotherRole.keyRecords.keySet());
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        // role can have keys - this should actually be refactored to let role
        // hold other roles and so on.
        List keyList = data.getList("keys", null);
        keyRecords.clear();
        if (keyList != null) {
            keyList.forEach(kr -> {
                addKeyRecord(deserializer.deserialize(kr));
            });
        }
        List anonIdList = data.getList("anonIds", null);
        anonymousIds.clear();
        if (anonIdList != null) {
            for (Object aid : anonIdList) {
                AnonymousId anonymousId = deserializer.deserialize(aid);
                anonymousIds.add(anonymousId);
            }
        }
        List keyAddrList = data.getList("addresses", null);
        keyAddresses.clear();
        if (keyAddrList != null) {
            for (Object keyAddr :  keyAddrList) {
                KeyAddress ka = deserializer.deserialize(keyAddr);
                keyAddresses.add(ka);
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

    /**
     * If this role has public keys, they will be replaced with {@link AnonymousId}.
     */
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
