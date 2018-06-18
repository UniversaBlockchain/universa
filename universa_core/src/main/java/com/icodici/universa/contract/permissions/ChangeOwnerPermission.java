/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiType;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Permission allows to change and remove owner role of contract.
 */

@BiType(name="ChangeOwnerPermission")
public class ChangeOwnerPermission extends Permission {

    /**
     * Create new permission for change owner role.
     *
     * @param role allows to permission
     */
    public ChangeOwnerPermission(Role role) {
        super("change_owner", role);
    }

    private ChangeOwnerPermission() {}

    /**
     * Check and remove change of state.owner, if any.
     *  @param contract valid contract state
     * @param changed is contract for checking
     * @param stateChanges changes in its state section
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     * @param checkingReferences are used to check other contracts permissions
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences) {
        Object x = stateChanges.get("owner");
        if( x != null ) {
            stateChanges.remove("owner");
            if( !(x instanceof MapDelta) )
                contract.addError(Errors.BAD_VALUE, "state.owner", "improper change");
            else {
                Delta<Role, Role> ci = (Delta<Role, Role>) x;
                if( !(ci.newValue() instanceof Role) )
                    contract.addError(Errors.BAD_VALUE, "state.owner", "improper change (new value not a role)");
            }
        }
    }

    static {
        DefaultBiMapper.registerClass(ChangeOwnerPermission.class);
    }
}
