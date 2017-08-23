/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Permission;
import com.icodici.universa.contract.Role;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;

import java.util.Map;

public class ChangeOwnerPermission extends Permission {
    public ChangeOwnerPermission(Role role) {
        super("change_owner", role);
    }

    /**
     * Check and remove change of state.owner, if any.
     *
     * @param contract, valid contracr state
     * @param stateChanges changes in its state section
     */
    @Override
    public void checkChanges(Contract contract, Map<String, Delta> stateChanges) {
        Object x = stateChanges.get("owner");
        if( x != null ) {
            stateChanges.remove("owner");
            if( !(x instanceof ChangedItem) )
                contract.addError(Errors.BAD_VALUE, "state.owner", "improper change");
            else {
                ChangedItem<Role, Role> ci = (ChangedItem<Role, Role>) x;
                if( !(ci.newValue() instanceof Role) )
                    contract.addError(Errors.BAD_VALUE, "state.owner", "improper change");
            }
        }
        super.checkChanges(contract, stateChanges);
    }
}
