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
 * The actualy permission implementation must implement {@link #checkChanges(Contract, Contract, Map)}, see this method for
 * information on how to approve changes with the permission.
 */
public abstract class Permission implements BiSerializable, Comparable<Permission> {

    private String name;
    private Role role;

    /**
     * Get the permission id or null if is not yet set.
     */
    @Nullable
    public String getId() {
        return id;
    }

    /**
     * Set the permission id. Id is used to simplify detection of the permission changes. Each permission must have a unique per-contract
     * id set while serializing the contract. Once permission id is set, it must never bbe changed.
     *
     * @return permission id or null
     */
    public void setId(@NonNull String id) {
        if( this.id != null)
            throw new IllegalStateException("permission id is already set");
        this.id = id;
    }

    private String id;

    public Binder getParams() {
        return params;
    }

    private Binder params;

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

    public static Permission forName(String name, Role role, Binder params) {
        switch (name) {
            case "revoke":
                return new RevokePermission(role);
            case "change_owner":
                return new ChangeOwnerPermission(role);
            case "modify_data":
                return new ModifyDataPermission(role, params);
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

    public boolean isAllowedForKeys(PublicKey... keys) {
        Set<PublicKey> keySet = new HashSet<>();
        for (PublicKey k : keys)
            keySet.add(k);
        return role.isAllowedForKeys(keySet);
    }

    public boolean isAllowedForKeys(Collection<PublicKey> keys) {
        return keys instanceof Set ? role.isAllowedForKeys((Set) keys) : role.isAllowedForKeys(new HashSet<>(keys));
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
     *  @param contract     source (valid) contract
     * @param changed
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    public abstract void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + name + ":" + role + ">";
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Permission)
            return compareTo((Permission) obj) == 0;
        return super.equals(obj);
    }

    @Override
    public int compareTo(Permission o) {
        return name.compareTo(o.name);
    }

}
