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
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * A symlink-like role delegate. It uses a named role in the context of a bound {@link Contract} (with {@link
 * Role#setContract(Contract)} call), then it delegates all actual work to the target role from the contract roles.
 * <p>
 * This is used to assign roles to roles, and to create special roles for permissions, etc.
 */
@BiType(name = "RoleLink")
public class RoleLink extends Role {

    private String roleName;

    private RoleLink() {
    }

    /**
     * Create empty link role. To be initialized from dsl later
     *
     * @param name new role name
     * @param contract is contract role belongs to
     */
    public RoleLink(String name,Contract contract) {
        super(name,contract);
    }

    /**
     * Create empty link role. To be initialized from dsl later
     *
     * @param name new role name
     * @deprecated use {@link #RoleLink(String, Contract)}
     */
    @Deprecated
    public RoleLink(String name) {
        super(name);
    }

    /**
     * Create a link to a named role. Note that such links can be created ahead of time, e.g. when there is no bound
     * contract or the target role does not yet exist. Just be sure to bind the contract with {@link
     * #setContract(Contract)} before using the instance.
     *
     * @param name new role name
     * @param contract is contract role belongs to
     * @param roleName existing role name
     */
    public RoleLink(String name, Contract contract, String roleName) {
        super(name, contract);
        if (name.equals(roleName))
            throw new IllegalArgumentException("RoleLink: name and target name are equals: " + name);
        this.roleName = roleName;
    }

    /**
     * Create a link to a named role. Note that such links can be created ahead of time, e.g. when there is no bound
     * contract or the target role does not yet exist. Just be sure to bind the contract with {@link
     * #setContract(Contract)} before using the instance.
     *
     * @param name new role name
     * @param roleName existing role name
     * @deprecated use {@link #RoleLink(String, Contract, String)}
     */
    @Deprecated
    public RoleLink(String name, String roleName) {
        super(name);
        if (name.equals(roleName))
            throw new IllegalArgumentException("RoleLink: name and target name are equals: " + name);
        this.roleName = roleName;
    }

    /**
     * Return the resolved role taken from a bound contract. A resolved role may be a {@link RoleLink} itself, or null
     * (if a link is incorrect).
     *
     * @return {@link Role}
     */
    @Nullable
    public Role getRole() {
        return getContract().getRole(roleName);
    }

    /**
     * Return the resolved role. A resolved role may be a {@link RoleLink} itself, or null (if a link is incorrect).
     *
     * @return {@link Role}
     */
    @Override
    public <T extends Role> T resolve() {
        return resolve(false);
    }

    public <T extends Role> T resolve(boolean ignoreRefs) {
        int maxDepth = 40;
        for (Role r = this; maxDepth > 0; maxDepth--) {
            if (r instanceof RoleLink && (ignoreRefs || maxDepth == 40 || (
                r.getReferences(Role.RequiredMode.ALL_OF).isEmpty() &&
                r.getReferences(Role.RequiredMode.ANY_OF).isEmpty()))) {

                r = ((RoleLink) r).getRole();
                if (r == null)
                    return null;
            } else
                return (T) r;
        }
        return null;
//        throw new IllegalStateException("RoleLink depth exceeded, possible circular references");
    }

    private void badOperation() {
        throw new RuntimeException("operation not supported for RoleLink instance");
    }

    /**
     * Get set of all key records in linked role.
     *
     * @return set of key records (see {@link KeyRecord})
     */
    @Override
    @Deprecated
    public Set<KeyRecord> getKeyRecords() {
        return resolve().getKeyRecords();
    }

    /**
     * Get set of all anonymous identifiers in linked role.
     *
     * @return set of anonymous identifiers (see {@link AnonymousId})
     */
    @Override
    @Deprecated
    public Set<AnonymousId> getAnonymousIds() {
        final Role role = resolve();
        return (role == null) ? null : role.getAnonymousIds();
    }

    /**
     * Get set of all key addresses in linked role.
     *
     * @return set of key addresses (see {@link KeyAddress})
     */
    @Override
    @Deprecated
    public Set<KeyAddress> getKeyAddresses() {
        final Role role = resolve();
        return (role == null) ? null : role.getKeyAddresses();
    }

    /**
     * Get set of all keys in linked role.
     *
     * @return set of public keys (see {@link PublicKey})
     */
    @Override
    @Deprecated
    public Set<PublicKey> getKeys() {
        final Role role = resolve();
        return (role == null) ? null : role.getKeys();
    }

    /**
     * Check role is allowed to keys
     *
     * @param keys is set of keys
     * @return true if role is allowed to keys
     */
    @Override
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        if(!super.isAllowedForKeys(keys))
            return false;

        final Role role = resolve();
        return (role == null) ? false : role.isAllowedForKeys(keys);
    }

    /**
     * Check validity of role
     *
     * @return true if role is valid
     */
    @Override
    public boolean isValid() {
        final Role role = resolve();
        return (role == null) ? false : role.isValid();
    }

    /**
     * A role has exactly same set of keys as in the supplied role. It does not check the logic,
     * only that list of all keys is exactly same.
     * <p>
     * To check that the role can be performed by some other role, use {@link #isAllowedForKeys(Set)}.
     *
     * @param otherRole is {@link Role} for checking by keys
     * @return true if equals
     */
    @Override
    @Deprecated
    public boolean equalKeys(Role otherRole) {
        final Role role = getRole();
        return (role == null) ? false : role.equalKeys(otherRole);
    }

    /**
     * Initializes linked role from dsl.
     *
     * @param serializedRole is {@link Binder} from dsl with data of linked role
     */
    @Override
    public void initWithDsl(Binder serializedRole) {
        roleName = serializedRole.getStringOrThrow("target");
        if (getName().equals(roleName))
            throw new IllegalArgumentException("RoleLink: name and target name are equals: " + roleName);

    }

    /**
     * Get role as string.
     *
     * @return string with data of role
     */
    @Override
    public String toString() {
        if (getContract() != null) {
            final Role role = getRole();
            return "RoleLink<" + getName() + "->" + roleName + ":" + ((role == null) ? "null" : role.toString()) + ">";
        } else {
            return "RoleLink<" + getName() + "->" + roleName + ":" + "not connected>";
        }
    }



    @Override
    protected boolean equalsIgnoreNameAndRefs(Role otherRole) {
        if (otherRole instanceof RoleLink) {
            return ((RoleLink) otherRole).roleName.equals(roleName);
        }
        return false;
    }

    @Override
    public Binder serialize(BiSerializer s) {
        return super.serialize(s).putAll(
                "name", getName(),
                "target_name", roleName
        );
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        roleName = data.getStringOrThrow("target_name");
    }

    /**
     * If this role has public keys, they will be replaced with {@link AnonymousId}.
     */
    @Override
    public void anonymize() {
        final Role role = resolve();
        if (role != null)
            role.anonymize();
    }

    @Override
    @Nullable KeyAddress getSimpleAddress(boolean ignoreRefs) {
        if(!ignoreRefs  && (requiredAnyReferences.size() > 0 || requiredAllReferences.size() > 0))
            return null;

        Role r = resolve(ignoreRefs);
        if (r != null)
            return r.getSimpleAddress(ignoreRefs);

        return null;
    }

    static {
        DefaultBiMapper.registerClass(RoleLink.class);
    }
}
