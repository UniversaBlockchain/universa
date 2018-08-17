package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.permissions.RevokePermission;

public class JSApiRevokePermission extends JSApiPermission {

    private RevokePermission revokePermission;

    public JSApiRevokePermission(JSApiRole role) {
        revokePermission = new RevokePermission(role.extractRole(new JSApiAccessor()));
    }

    @Override
    public Permission extractPermission(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return revokePermission;
    }

}
