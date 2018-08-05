package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import net.sergeych.tools.Binder;

import java.util.Map;

public class JSApiSplitJoinPermission extends JSApiPermission {

    private SplitJoinPermission splitJoinPermission;

    public JSApiSplitJoinPermission(JSApiRole role, Map<String, Object> params) {
        Binder paramsBinder = new Binder();
        params.forEach((k, v) -> paramsBinder.set(k, v));
        splitJoinPermission = new SplitJoinPermission(role.extractRole(new JSApiAccessor()), paramsBinder);
    }

    @Override
    public Permission extractPermission(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return splitJoinPermission;
    }

}
