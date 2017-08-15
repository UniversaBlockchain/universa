/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Permission;
import com.icodici.universa.contract.Role;

public class RevokePermission extends Permission {
    public RevokePermission(Role role) {
        super("revoke", role);
    }
}
