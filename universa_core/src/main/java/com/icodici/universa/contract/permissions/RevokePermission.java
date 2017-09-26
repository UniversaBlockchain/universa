/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Permission;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiType;
import net.sergeych.diff.Delta;

import java.util.Map;

@BiType(name="RevokePermission")
public class RevokePermission extends Permission {
    public RevokePermission(Role role) {
        super("revoke", role);
    }

    @Override
    public void checkChanges(Contract contract, Map<String, Delta> stateChanges) {
        // this permission checks no changes, it's about the whole contract
    }

    static {
        DefaultBiMapper.registerClass(RevokePermission.class);
    }
}
