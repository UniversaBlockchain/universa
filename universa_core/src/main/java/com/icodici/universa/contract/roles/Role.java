/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collections;
import java.util.Set;

/**
 * Base class for any role combination, e.g. single key, any key from a set, all keys from a set, minimum number of key
 * from a set and so on.
 * <p>
 * IMPORTANT, This class express "any_of" logic, e.g. if any of the presented keys is listed, then the role is allowed.
 */
@BiType(name = "Role")
public abstract class Role implements BiSerializable {

    private String name;
    private Contract contract;

    protected Role() {
    }

    protected Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract boolean isAllowedForKeys(Set<? extends AbstractKey> keys);

    public abstract boolean isValid();

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Role) {
            Role otherRole = (Role) obj;
            return otherRole.name.equals(name) && otherRole.getClass() == getClass();
        }
        return false;
    }

    /**
     * A role has exactly same set of keys as in the supplied role. It does not check the logik, only that list of all
     * keys is exactly same.
     * <p>
     * To check that the role can be performed by some other role, use {@link #isAllowedForKeys(Set)}.
     *
     * @return
     */
    public boolean equalKeys(@NonNull Role otherRole) {
        return otherRole.getKeys().equals(getKeys());
    }


    static public Role fromDslBinder(String name, Binder serializedRole) {
        if (name == null)
            name = serializedRole.getStringOrThrow("name");
        if (serializedRole.containsKey("key")) {
            // Single-key role
            return new SimpleRole(name, new KeyRecord(serializedRole));
        }
        return new SimpleRole(name, serializedRole.getListOrThrow("binders"));
    }

    public abstract Set<PublicKey> getKeys();

    public abstract Set<byte[]> getAnonymousIds();

    public Set<KeyRecord> getKeyRecords() {
        return Collections.emptySet();
    }

    /**
     * If the role is a link or like, get the target role. If it is not possible (no target role, for example), throws
     * {@link IllegalStateException}.
     *
     * @param <T>
     *
     * @return
     */
    public <T extends Role> @NonNull T resolve() {
        return (T) this;
    }

    /**
     * Testing only. For lne-key roles, return the keyrecord.
     *
     * @return
     */
    @Deprecated
    public KeyRecord getKeyRecord() {
        Set<KeyRecord> kr = getKeyRecords();
        if (kr.size() > 1)
            throw new IllegalStateException("can't get key record as there are many of them");
        return kr.iterator().next();
    }

    public void setContract(Contract contract) {
        this.contract = contract;
    }

    public Contract getContract() {
        return contract;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        name = data.getStringOrThrow("name");
        contract = deserializer.getContext();
    }

    @Override
    public Binder serialize(BiSerializer s) {
        return Binder.fromKeysValues(
                "name", name
        );
    }

    /**
     * Create alias role to this one using {@link RoleLink}. If this role has attached to some contract, this method
     * also registers new role in the same contract.
     *
     * @param roleName new role name
     *
     * @return
     */
    public RoleLink linkAs(String roleName) {
        RoleLink newRole = new RoleLink(roleName, name);
        if (contract != null)
            contract.registerRole(newRole);
        return newRole;
    }
}
