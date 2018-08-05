package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiHelpers;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import jdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.HashMap;

public class JSApiPermissionBuilder {

    public JSApiSplitJoinPermission createSplitJoinPermission(JSApiRole role, ScriptObjectMirror params) {
        Object paramsMap = JSApiHelpers.jo2Object(params);
        if (paramsMap instanceof HashMap)
            return new JSApiSplitJoinPermission(role, (HashMap)paramsMap);
        throw new IllegalArgumentException("createSplitJoinPermission error: wrong params");
    }

    public JSApiChangeNumberPermission createChangeNumberPermission(JSApiRole role, ScriptObjectMirror params) {
        Object paramsMap = JSApiHelpers.jo2Object(params);
        if (paramsMap instanceof HashMap)
            return new JSApiChangeNumberPermission(role, (HashMap)paramsMap);
        throw new IllegalArgumentException("createSplitJoinPermission error: wrong params");
    }

    public JSApiChangeOwnerPermission createChangeOwnerPermission(JSApiRole role) {
        return new JSApiChangeOwnerPermission(role);
    }

}
