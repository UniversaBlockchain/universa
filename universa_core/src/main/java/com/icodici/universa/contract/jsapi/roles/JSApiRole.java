package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;

import java.util.List;

public abstract class JSApiRole {

    abstract List<String> getAllAddresses();

    abstract Role extractRole(JSApiAccessor apiAccessor);

    abstract boolean isAllowedForAddresses(String... addresses);

    public static JSApiRole createJSApiRole(Role r) {
        if (r instanceof SimpleRole)
            return new JSApiSimpleRole(new JSApiAccessor(), (SimpleRole)r);
        else if (r instanceof ListRole)
            return new JSApiListRole(new JSApiAccessor(), (ListRole)r);
        throw new IllegalArgumentException("unknown role type");
    }

}
