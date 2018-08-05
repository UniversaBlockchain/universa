package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import net.sergeych.tools.Binder;

import java.util.List;
import java.util.Map;

public class JSApiModifyDataPermission extends JSApiPermission {

    public ModifyDataPermission modifyDataPermission;

    public JSApiModifyDataPermission(JSApiRole role, Map<String, List<String>> params) {
        Binder paramsBinder = new Binder();
        params.forEach((k, v) -> paramsBinder.set(k, v));
        modifyDataPermission = new ModifyDataPermission(role.extractRole(new JSApiAccessor()), Binder.of("fields", paramsBinder));
    }

    @Override
    public Permission extractPermission(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return modifyDataPermission;
    }

}
