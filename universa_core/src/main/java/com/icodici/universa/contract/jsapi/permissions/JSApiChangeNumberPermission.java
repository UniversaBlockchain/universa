package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.permissions.ChangeNumberPermission;
import net.sergeych.tools.Binder;

import java.util.Map;

public class JSApiChangeNumberPermission extends JSApiPermission {

    private ChangeNumberPermission changeNumberPermission;

    public JSApiChangeNumberPermission(JSApiRole role, Map<String, Object> params) {
        Binder paramsBinder = new Binder();
        params.forEach((k, v) -> paramsBinder.set(k, v));
        changeNumberPermission = new ChangeNumberPermission(role.extractRole(new JSApiAccessor()), paramsBinder);
    }

    @Override
    public ChangeNumberPermission extractPermission(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return changeNumberPermission;
    }

}
