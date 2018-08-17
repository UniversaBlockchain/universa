package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.Permission;

public class JSApiChangeOwnerPermission extends JSApiPermission {

    private ChangeOwnerPermission changeOwnerPermission;

    public JSApiChangeOwnerPermission(JSApiRole role) {
        changeOwnerPermission = new ChangeOwnerPermission(role.extractRole(new JSApiAccessor()));
    }

    @Override
    public Permission extractPermission(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return changeOwnerPermission;
    }

}
