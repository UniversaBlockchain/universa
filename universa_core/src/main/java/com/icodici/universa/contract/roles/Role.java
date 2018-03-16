/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.contract.Reference;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.icodici.universa.contract.roles.Role.RequiredMode.ALL_OF;
import static com.icodici.universa.contract.roles.Role.RequiredMode.ANY_OF;

/**
 * Base class for any role combination, e.g. single key, any key from a set, all keys from a set, minimum number of key
 * from a set and so on.
 * <p>
 * IMPORTANT, This class express "any_of" logic, e.g. if any of the presented keys is listed, then the role is allowed.
 */
@BiType(name = "Role")
public abstract class Role implements BiSerializable {

    enum RequiredMode {
        ALL_OF,
        ANY_OF
    }

    private String name;
    private Contract contract;
    private Set<String> requiredAllReferences = new HashSet<>();
    private Set<String> requiredAnyReferences = new HashSet<>();




    public void addAllRequiredReferences(Collection<Reference> references, RequiredMode requiredMode) {
        (requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences).addAll(references.stream().map(reference -> reference.getName()).collect(Collectors.toSet()));
    }

    public Set<String> getReferences(RequiredMode requiredMode) {
        return requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences;
    }

    public void addRequiredReference(Reference reference,RequiredMode requiredMode) {
        addRequiredReference(reference.getName(), requiredMode);
    }

    public void addRequiredReference(String refName,RequiredMode requiredMode) {
        (requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences).add(refName);
    }

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
            return otherRole.requiredAnyReferences.equals(requiredAnyReferences) && otherRole.requiredAllReferences.equals(requiredAllReferences) && otherRole.name.equals(name) && otherRole.getClass() == getClass();
        }
        return false;
    }

    /**
     * A role has exactly same set of keys as in the supplied role. It does not check the logik, only that list of all
     * keys is exactly same.
     * <p>
     * To check that the role can be performed by some other role, use {@link #isAllowedForKeys(Set)}.
     *
     * @param otherRole is {@link Role} for checking by keys
     * @return true if equals
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

    public abstract Set<AnonymousId> getAnonymousIds();

    public abstract Set<KeyAddress> getKeyAddresses();

    public Set<KeyRecord> getKeyRecords() {
        return Collections.emptySet();
    }

    /**
     * If the role is a link or like, get the target role. If it is not possible (no target role, for example), throws
     * {@link IllegalStateException}.
     *
     * @param <T> is type
     *
     * @return resolved role
     */
    public <T extends Role> @NonNull T resolve() {
        return (T) this;
    }

    /**
     * Testing only. For lne-key roles, return the keyrecord.
     *
     * @return found {@link KeyRecord}
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
        Binder required = data.getBinder("required");
        if(required != null) {
            if(required.containsKey(ALL_OF.name())) {
                requiredAllReferences.addAll(deserializer.deserialize(required.getArray(ALL_OF.name())));
            }

            if(required.containsKey(ANY_OF.name())) {
                requiredAnyReferences.addAll(deserializer.deserialize(required.getArray(ANY_OF.name())));
            }

        }
    }

    @Override
    public Binder serialize(BiSerializer s) {

        Binder b = Binder.fromKeysValues(
                "name", name
        );

        if(!requiredAnyReferences.isEmpty() || !requiredAllReferences.isEmpty()) {
            Binder required = new Binder();

            if(!requiredAllReferences.isEmpty()) {
                required.set(ALL_OF.name(),s.serialize(requiredAllReferences));
            }

            if(!requiredAllReferences.isEmpty()) {
                required.set(ANY_OF.name(),s.serialize(requiredAnyReferences));
            }

            b.set("required",required);
        }

        return b;
    }

    /**
     * Create alias role to this one using {@link RoleLink}. If this role has attached to some contract, this method
     * also registers new role in the same contract.
     *
     * @param roleName new role name
     *
     * @return linked {@link RoleLink}
     */
    public RoleLink linkAs(String roleName) {
        RoleLink newRole = new RoleLink(roleName, name);
        if (contract != null)
            contract.registerRole(newRole);
        return newRole;
    }

    /**
     * If this role has public keys, they will be replaced with AnonymousIds.
     */
    public abstract void anonymize();
}
