/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.diff.Delta;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract permission for the Universa contract. The permission implements the right of a {@link
 * com.icodici.universa.contract.roles.Role} player (e.g. Universa party, set of keys used in signing the contract) to
 * perform some change over the contract state. The real permissions are all superclasses of it.
 * <p>
 * The actually permission implementation must implement {@link #checkChanges(Contract, Contract, Map,Set,Collection,Collection)}, see this method for
 * information on how to approve changes with the permission.
 */
public abstract class Permission implements BiSerializable, Comparable<Permission> {

    private String name;
    private Role role;

    /**
     * Get the permission id or null if is not yet set.
     *
     * @return identifier
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * Set the permission id. Id is used to simplify detection of the permission changes. Each permission must have a unique per-contract
     * id set while serializing the contract. Once permission id is set, it must never bbe changed.
     *
     * @param id is identifier
     */
    public void setId(@NonNull String id) {
        if( this.id != null && !this.id.equals(id) )
            throw new IllegalStateException("permission id is already set");
        this.id = id;
    }

    private String id;

    /**
     * Get params of permission or null if is not yet set.
     *
     * @return params of permission
     */
    public Binder getParams() {
        return params;
    }

    protected Binder params;

    protected Permission() {
    }

    protected Permission(String name, Role role) {
        this.name = name;
        this.role = role;
        params = null;
    }

    protected Permission(String name, Role role, Binder params) {
        this.name = name;
        this.role = role;
        this.params = params;
    }

    /**
     * Create new permission of a specific type by type name
     *
     * @param name is specific type name
     * @param role allows to permission
     * @param params is parameters of permission, set of parameters depends on the type of permission
     * @return permission of a specific type
     */
    public static Permission forName(String name, Role role, Binder params) {
        switch (name) {
            case "revoke":
                return new RevokePermission(role);
            case "change_owner":
                return new ChangeOwnerPermission(role);
            case "modify_data":
                return new ModifyDataPermission(role, params);
            case "decrement_permission":
                return new ChangeNumberPermission(role, params);
            default:
                try {
                    String className = "com.icodici.universa.contract.permissions." +
                            Do.snakeToCamelCase(name) +
                            "Permission";
                    Class<Permission> cls = (Class<Permission>) Permission.class.getClassLoader().loadClass(className);
                    return cls.getConstructor(Role.class, Binder.class)
                            .newInstance(role, Binder.from(params));
                } catch (ClassCastException | ClassNotFoundException e) {
                    throw new IllegalArgumentException("unknown permission: " + name);
                } catch (Exception e) {
                    throw new IllegalArgumentException("can't construct permission: " + name, e);
                }
        }
    }

    /**
     * Check permission is allowed to keys
     *
     * @param keys is public keys
     * @return true if permission is allowed to keys
     */
    public boolean isAllowedForKeys(PublicKey... keys) {
        Set<PublicKey> keySet = new HashSet<>();
        for (PublicKey k : keys)
            keySet.add(k);
        return isAllowedFor(keySet, null);
    }

    /**
     * Check permission is allowed to keys
     *
     * @param keys is collection of public keys
     * @return true if permission is allowed to keys
     */
    public boolean isAllowedForKeys(Collection<PublicKey> keys) {
//        return keys instanceof Set ? role.isAllowedForKeys((Set) keys) : role.isAllowedForKeys(new HashSet<>(keys));
        return isAllowedFor(keys, null);
    }

    /**
     * Check permission is allowed to keys and references
     *
     * @param keys is collection of public keys
     * @param references is collection of references names
     * @return true if permission is allowed to keys and references
     */
    public boolean isAllowedFor(Collection<PublicKey> keys, Collection<String> references) {
        return role.isAllowedFor(keys, references);
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder results = new Binder();
        if (params != null)
            results.putAll(params);
        results.put("name", name);
        results.put("role", serializer.serialize(role));
        return results;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        name = data.getStringOrThrow("name");
        role = deserializer.deserialize(data.get("role"));
        params = data;
    }

    /**
     * Process changes of the contract. Implementation should check and remove all allowed changes from the stateChanges
     * parameter, and add errors to the contract using {@link Contract#addError(Errors, String)} for all relevant but
     * inappropriate changes.
     * <p>
     * <b>IMPORTANT NOTE</b>. Implementations usually should not add errors to the contract unless the permission can be
     * used <i>only once in any contract</i>, such as change_owher or revoke. In all other cases, when the permission
     * could be specified several times for different roles and with different parameter, implementation should do
     * nothing on the error and let others porceed. Unprocessed changes will cause error if no permission will clear
     * it.
     *  @param contract source (valid) contract
     * @param changed is contract for checking
     * @param stateChanges map of changes, see {@link Delta} for details
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     * @param checkingReferences are used to check other contracts permissions
     */
    public abstract void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences);

    /**
     * Get permission as string.
     *
     * @return string with data of permission
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + name + ":" + role + ">";
    }

    /**
     * Get role allows to permission.
     *
     * @return allowed role
     */
    public Role getRole() {
        return role;
    }

    /**
     * Get name of permission.
     *
     * @return name of permission
     */
    public String getName() {
        return name;
    }

    /**
     * Compares permission with obj.
     *
     * @param obj is compared object
     * @return true if permission is equals obj
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Permission)
            return compareTo((Permission) obj) == 0;
        return super.equals(obj);
    }

    /**
     * Compares permissions by name.
     *
     * @param o is compared permission
     * @return result of comparison
     */
    @Override
    public int compareTo(Permission o) {
        return name.compareTo(o.name);
    }

}
