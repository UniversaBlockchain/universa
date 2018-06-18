/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiType;
import net.sergeych.diff.Delta;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Permission allows to revoke contract.
 */

@BiType(name="RevokePermission")
public class RevokePermission extends Permission {

    private RevokePermission() {}

    /**
     * Create new permission for revoke contract.
     *
     * @param role allows to permission
     */
    public RevokePermission(Role role) {
        super("revoke", role);
    }

    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences) {
        // this permission checks no changes, it's about the whole contract
    }

    static {
        DefaultBiMapper.registerClass(RevokePermission.class);
    }
}
