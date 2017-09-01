/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.RevokePermission;
import net.sergeych.diff.Delta;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class Permission {

    private final String name;
    private final Role role;

    public Binder getParams() {
        return params;
    }

    private final Binder params;

    protected Permission(String name, Role role) {
        this.name = name;
        this.role = role;
        params = null;
    }

    protected Permission(String name, Role role,Binder params) {
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

    public Binder serializeToBinder() {
        Binder results = new Binder();
        if( params != null )
            results.putAll(params);
        results.put("name", name);
        results.put("role", role);
        return results;
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
     *
     * @param contract     source (valid) contract
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    public abstract void checkChanges(Contract contract, Map<String, Delta> stateChanges);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "<" + name + ":" + role + ">";
    }

    public Role getRole() {
        return role;
    }
}
