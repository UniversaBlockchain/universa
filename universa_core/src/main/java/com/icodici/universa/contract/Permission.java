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

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class Permission {

    private final String name;
    private final Role role;

    protected Permission(String name, Role role) {
        this.name = name;
        this.role = role;
    }

    public static Permission forName(String name, Role role, Binder params) {
        switch (name) {
            case "revoke":
                return new RevokePermission(role);
            case "change_owner":
                return new ChangeOwnerPermission(role);
        }
        throw new IllegalArgumentException("unknown permission: "+name);
    }

    public boolean isAllowedForKeys(PublicKey... keys) {
        Set<PublicKey> keySet = new HashSet<>();
        for(PublicKey k: keys)
            keySet.add(k);
        return role.isAllowedForKeys(keySet);
    }

    public boolean isAllowedForKeys(Collection<PublicKey> keys) {
        return keys instanceof Set ? role.isAllowedForKeys((Set) keys) : role.isAllowedForKeys(new HashSet<>(keys));
    }

    public Binder serializeToBinder() {
        return Binder.fromKeysValues("role", role);
    }

    /**
     * Process changes of the contract. Implementation should check and remove all allowed changes from
     * the stateChanges parameter, and add errors to the contract using {@link Contract#addError(Errors, String)}
     * for all relevant but inappropriate changes.
     *
     * @param contract source (valid) contract
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    public void checkChanges(Contract contract, Map<String, Delta> stateChanges) {}

    @Override
    public String toString() {
        return getClass().getSimpleName()+"<"+name+":"+role+">";
    }

    public Role getRole() {
        return role;
    }
}
