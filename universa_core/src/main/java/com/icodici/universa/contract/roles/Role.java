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
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;
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

    public boolean containReference(String name) {
        if(requiredAllReferences.contains(name) || requiredAnyReferences.contains(name))
            return true;

        if(this instanceof RoleLink) {
            return ((RoleLink)this).getRole().containReference(name);
        }

        if(this instanceof ListRole) {
            return ((ListRole)this).getRoles().stream().anyMatch(r -> r.containReference(name));
        }

        return false;
    }


    /**
    /* Mode of combining references
     */
    public enum RequiredMode {
        /**
        /* In this mode, all references must be allowed for allows role
         */
        ALL_OF,
        /**
         * In this mode, at least one of the references must be allowed for allows role
         */
        ANY_OF
    }

    private String name;
    private Contract contract;
    private String comment = null;
    protected Set<String> requiredAllReferences = new HashSet<>();
    protected Set<String> requiredAnyReferences = new HashSet<>();

    /**
     * Adds required references to role. The references is added to the set corresponding
     * to the specified mode of combining references ({@link RequiredMode}).
     *
     * @param references is collection of added references
     * @param requiredMode is mode of combining references
     */
    public void addAllRequiredReferences(Collection<Reference> references, RequiredMode requiredMode) {
        (requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences).addAll(references.stream().map(reference -> reference.getName()).collect(Collectors.toSet()));
    }

    /**
     * Get set of references corresponding to the specified mode of combining references ({@link RequiredMode}).
     *
     * @param requiredMode is mode of combining references
     * @return set of references
     */
    public Set<String> getReferences(RequiredMode requiredMode) {
        return requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences;
    }

    /**
     * Adds required reference to role. The reference is added to the set corresponding
     * to the specified mode of combining references ({@link RequiredMode}).
     *
     * @param reference is added reference
     * @param requiredMode is mode of combining references
     */
    public void addRequiredReference(Reference reference,RequiredMode requiredMode) {
        addRequiredReference(reference.getName(), requiredMode);
    }

    /**
     * Adds required reference to role. The reference is added to the set corresponding
     * to the specified mode of combining references ({@link RequiredMode}).
     *
     * @param refName is name of reference in contract
     * @param requiredMode is mode of combining references
     */
    public void addRequiredReference(String refName,RequiredMode requiredMode) {
        (requiredMode == ALL_OF ? requiredAllReferences : requiredAnyReferences).add(refName);
    }

    protected Role() {
    }

    protected Role(String name, Contract contract) {
        this.name = name;
        this.contract = contract;
    }

    protected Role(String name) {
        this.name = name;
    }

    /**
     * Get name of role
     *
     * @return name of role
     */
    public String getName() {
        return name;
    }

    /**
     * Get comment of role
     *
     * @return comment of role (may be null)
     */
    public String getComment() {
        return comment;
    }

    /**
     * Set comment of role
     *
     * @param comment of role
     */
    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Check role is allowed to keys
     *
     * @param keys is set of keys
     * @return true if role is allowed to keys
     */
    public  boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        return isAllowedForReferences(contract == null ? new HashSet<>() : contract.getValidRoleReferences());
    }


    /**
     * Check role is allowed to keys and references
     *
     * @param keys is collection of keys
     * @param references this parameter is ignored
     * @return true if role is allowed to keys and references
     * @deprecated use isAllowedForKeys instead. References should be used withith reference contract context
     */
    @Deprecated
    public boolean isAllowedFor(Collection<? extends AbstractKey> keys, Collection<String> references) {
        return isAllowedForKeys(keys instanceof Set ? (Set<? extends AbstractKey>) keys : new HashSet<>(keys));
    }

    private boolean isAllowedForReferences(Collection<String> references) {
        if(requiredAllReferences.stream().anyMatch(ref -> references == null || !references.contains(ref))) {
            return false;
        }

        return requiredAnyReferences.isEmpty() ||
                requiredAnyReferences.stream().anyMatch(ref -> references != null && references.contains(ref));
    }

    /**
     * Check that the address matches role.
     *
     * @param keyAddress address for matching with role
     * @return true if match or false
     * @deprecated the only usable check allowance method is isAllowedForKeys
     */
    @Deprecated
    public boolean isMatchingKeyAddress(KeyAddress keyAddress) {
        for (KeyAddress ka : this.getKeyAddresses()) {
            if (keyAddress.isMatchingKeyAddress(ka))
                return true;
        }

        for (PublicKey pk : this.getKeys()) {
            if (keyAddress.isMatchingKey(pk))
                return true;
        }

        return false;
    }
    /**
     * @deprecated the only usable check allowance method is isAllowedForKeys
     */
    @Deprecated
    public  boolean isMatchingRole(Role role) {

        return false;
    }

    /**
     * Check validity of role
     *
     * @return true if role is valid
     */
    public abstract boolean isValid();

    /**
     * Get hash code of role name
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /**
     * Role equality is different: it only checks that it points to the same role.
     *
     * @param obj is object to be checked with
     * @return true if equals
     */
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Role) {
            return equalsIgnoreName((Role) obj) && ((name == null && ((Role) obj).name == null) || (name != null && ((Role) obj).name != null && name.equals(((Role) obj).name)));
        }
        return false;
    }

    public final boolean equalsIgnoreName(Role otherRole) {
        boolean refsEquals = otherRole.requiredAnyReferences.equals(requiredAnyReferences) && otherRole.requiredAllReferences.equals(requiredAllReferences);
        if(!refsEquals)
            return false;
        return equalsIgnoreNameAndRefs(otherRole);
    }

    protected abstract boolean equalsIgnoreNameAndRefs(Role otherRole);

    /**
     * A role has exactly same set of keys as in the supplied role. It does not check the logic,
     * only that list of all keys is exactly same.
     * <p>
     * To check that the role can be performed by some other role, use {@link #isAllowedForKeys(Set)}.
     *
     * @param otherRole is {@link Role} for checking by keys
     * @return true if equals
     * @deprecated the only usable check allowance method is isAllowedForKeys
     */
    @Deprecated
    public boolean equalKeys(@NonNull Role otherRole) {
        return otherRole.getKeys().equals(getKeys());
    }
    @Deprecated
    public boolean equalAddresses(Role otherRole) {
        return otherRole.getKeyAddresses().equals(getKeyAddresses());
    }
    @Deprecated
    public boolean equalAnonIds(Role otherRole) {
        return otherRole.getAnonymousIds().equals(getAnonymousIds());
    }

    /**
     * Get all addresses, used by this role. For public keys returns addresses too.
     * @deprecated the only usable check allowance method is isAllowedForKeys
     * @return list of strings with addresses
     */
    @Deprecated
    public List<String> getAllAddresses() {
        List<String> res = new ArrayList<>();
        getKeyAddresses().forEach(ka -> res.add(ka.toString()));
        getKeys().forEach(publicKey -> res.add(publicKey.getShortAddress().toString()));
        return res;
    }

    /**
     * Initializes role from dsl.
     *
     * @param serializedRole is {@link Binder} from dsl with data of role
     */
    public abstract void initWithDsl(Binder serializedRole);

    /**
     * Create new role from dsl.
     *
     * @param name is role name
     * @param serializedRole is {@link Binder} from dsl with data of role
     */
    static public Role fromDslBinder(String name, Binder serializedRole) {
        if (name == null)
            name = serializedRole.getStringOrThrow("name");
        Role result;
        String type =serializedRole.getString("type",null);
            if(type == null || type.equalsIgnoreCase("simple")) {
            result = new SimpleRole(name);
        } else if(type.equalsIgnoreCase("link")) {
            result = new RoleLink(name);
        } else if(type.equalsIgnoreCase("list")) {
            result = new ListRole(name);
        } else {
            throw new IllegalArgumentException("Unknown role type: " + type);
        }
        result.initWithDsl(serializedRole);
        if(serializedRole.containsKey("requires")) {
            Binder requires = serializedRole.getBinderOrThrow("requires");
            if(requires.containsKey("all_of")) {
                result.requiredAllReferences.addAll(requires.getListOrThrow("all_of"));
            }

            if(requires.containsKey("any_of")) {
                result.requiredAnyReferences.addAll(requires.getListOrThrow("any_of"));
            }
        }
        result.setComment(serializedRole.getString("comment", null));

        return result;
    }

    /**
     * Get set of all keys in sub-roles.
     * @deprecated getting plain keys from the role without understanding its internal structure is not allowed.
     * Use {@link #getSimpleAddress()} if you need to display single bearer of a role
     * @return set of public keys (see {@link PublicKey})
     */
    @Deprecated
    public abstract Set<PublicKey> getKeys();

    /**
     * Get set of all anonymous identifiers in sub-roles.
     * @deprecated getting plain keys from the role without understanding its internal structure is not allowed.
     * Use {@link #getSimpleAddress()} if you need to display single bearer of a role
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    @Deprecated
    public abstract Set<AnonymousId> getAnonymousIds();

    /**
     * Get set of all key addresses in sub-roles.
     * @deprecated getting plain keys from the role without understanding its internal structure is not allowed.
     * Use {@link #getSimpleAddress()} if you need to display single bearer of a role
     * @return set of key addresses (see {@link KeyAddress})
     */
    @Deprecated
    public abstract Set<KeyAddress> getKeyAddresses();

    /**
     * Get set of all key records in sub-roles.
     * @deprecated getting plain keys from the role without understanding its internal structure is not allowed.
     * Use {@link #getSimpleAddress()} if you need to display single bearer of a role
     * @return set of key records (see {@link KeyAddress})
     */
    @Deprecated
    public abstract Set<KeyRecord> getKeyRecords();

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
     * @deprecated the only usable check allowance method is isAllowedForKeys
     * @return found {@link KeyRecord}
     */
    @Deprecated
    public KeyRecord getKeyRecord() {
        Set<KeyRecord> kr = getKeyRecords();
        if (kr.size() > 1)
            throw new IllegalStateException("can't get key record as there are many of them");
        return kr.iterator().next();
    }

    /**
     * Set role contract.
     *
     * @param contract is role contract
     */
    public void setContract(Contract contract) {
        this.contract = contract;
    }

    /**
     * Get role contract.
     *
     * @return role contract
     */
    public Contract getContract() {
        return contract;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        name = data.getStringOrThrow("name");
        comment = data.getString("comment", null);
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

            if(!requiredAnyReferences.isEmpty()) {
                required.set(ANY_OF.name(),s.serialize(requiredAnyReferences));
            }

            b.set("required",required);
        }

        if (comment != null)
            b.set("comment", comment);

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
        return new RoleLink(roleName, contract, name);
    }


    /**
     * Get an address from the role, if it is just a single one.
     *
     * May be used to display a single bearer of a role in UIs. Returns  {@code null} if a single address cannot be decided
     * for the role (like, if there is no addresses/keys discoverable or if there is more than 1 address/key). If the role is bound
     * to a public key rather than an address, returns its short address.
     *
     * @apiNote: if the role uses any References, it affects the role bearing rules and effectively means the single address cannot
     * be discovered definitely; the method will return {@code null} in this case as well.
     *
     * @return role address or null
     */
    public final @Nullable KeyAddress getSimpleAddress() {
        return getSimpleAddress(false);
    }

    @Nullable KeyAddress getSimpleAddress(boolean ignoreRefs) {
        return null;
    }

    /**
     * If this role has public keys, they will be replaced with {@link AnonymousId}.
     */
    public abstract void anonymize();
}
