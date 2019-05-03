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
import net.sergeych.utils.Base64u;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.security.Key;
import java.util.*;

/**
 * Base class for any role combination, e.g. single key, any key from a set, all keys from a set, minimum number of key
 * from a set and so on.
 * <p>
 * IMPORTANT, This class express "all_of" logic, e.g. if all of the presented keys are listed, then the role is allowed.
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
                throw new IllegalArgumentException("Cant create KeyRecord from " + x + ": "+(x.getClass().getName()));

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
    @Deprecated
    public Set<KeyRecord> getKeyRecords() {
        return new HashSet(keyRecords.values());
    }

    /**
     * Get set of all keys in role.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    @Override
    @Deprecated
    public Set<PublicKey> getKeys() {
        return keyRecords.keySet();
    }

    /**
     * Get set of all anonymous identifiers in role.
     *
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    @Override
    @Deprecated
    public Set<AnonymousId> getAnonymousIds() {
        return anonymousIds;
    }

    /**
     * Get set of all key addresses in role.
     *
     * @return set of key addresses (see {@link KeyAddress})
     */
    @Deprecated
    @Override
    public Set<KeyAddress> getKeyAddresses() {
        return keyAddresses;
    }


    /**
     * Get set of all key records in role.
     *
     * @return set of key records (see {@link KeyRecord})
     */
    public Set<KeyRecord> getSimpleKeyRecords() {
        return new HashSet(keyRecords.values());
    }

    /**
     * Get set of all keys in role.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    public Set<PublicKey> getSimpleKeys() {
        return keyRecords.keySet();
    }

    /**
     * Get set of all anonymous identifiers in role.
     *
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    public Set<AnonymousId> getSimpleAnonymousIds() {
        return anonymousIds;
    }

    /**
     * Get set of all key addresses in role.
     *
     * @return set of key addresses (see {@link KeyAddress})
     */
    public Set<KeyAddress> getSimpleKeyAddresses() {
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
        if(!super.isAllowedForKeys(keys)) {
            return false;
        }

        boolean allMatch1 = anonymousIds.stream().allMatch(anonId -> keys.stream().anyMatch(key -> {
            try {
                return key.matchAnonymousId(anonId.getBytes());
            } catch (IOException e) {
                return false;
            }
        }));
        boolean allMatch2 = keyRecords.values().stream().allMatch( kr -> keys.stream().anyMatch(k -> k.getPublicKey().equals(kr.getPublicKey())));
        boolean allMatch3 = keyAddresses.stream().allMatch(address -> keys.stream().anyMatch(key -> key.isMatchingKeyAddress(address)));
        return allMatch1 && allMatch2 && allMatch3;
    }

    /**
     * Check validity of role. Valid {@link SimpleRole} contains keys, addresses or anonymous identifiers.
     *
     * @return true if role is valid
     */
    public boolean isValid() {
        return !keyRecords.isEmpty() || !anonymousIds.isEmpty() || !keyAddresses.isEmpty() ||
                !requiredAllReferences.isEmpty() || !requiredAnyReferences.isEmpty();
    }


    @Override
    protected boolean equalsIgnoreNameAndRefs(Role role) {
        if(!(role instanceof SimpleRole))
            return false;

        if(!hasAllKeys(this,(SimpleRole)role))
            return false;

        if(!hasAllKeys((SimpleRole)role,this))
            return false;

        return true;
    }

    private boolean hasAllKeys(SimpleRole role1, SimpleRole role2) {
        if(!role1.keyRecords.keySet().stream().allMatch(k->
                role2.keyRecords.containsKey(k) ||
                        role2.keyAddresses.contains(k.getShortAddress()) ||
                        role2.keyAddresses.contains(k.getLongAddress()) ||
                        role2.anonymousIds.contains(new AnonymousId(k.createAnonymousId()))))
            return false;

        if(!role1.keyAddresses.stream().allMatch(ka->
                role2.keyAddresses.contains(ka) ||
                        role2.keyRecords.keySet().stream().anyMatch(key->ka.equals(key.getShortAddress()) || ka.equals(key.getLongAddress()))))
            return false;

        if(!role1.anonymousIds.stream().allMatch(anonymousId ->
                role2.anonymousIds.contains(anonymousId) ||
                        role2.keyRecords.keySet().stream().anyMatch(key->new AnonymousId(key.createAnonymousId()).equals(anonymousId))))
            return false;

        return true;
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


        try {
            if (serializedRole.containsKey("addresses")) {
                List<Binder> list = serializedRole.getListOrThrow("addresses");
                for (Object address : list) {
                    keyAddresses.add(new KeyAddress((String) ((Map)address).get("uaddress")));
                }
            } else if (serializedRole.containsKey("uaddress")) {
                keyAddresses.add(new KeyAddress(serializedRole.getString("uaddress")));
            } else {
                addressesFound = false;
            }
        }catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
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
