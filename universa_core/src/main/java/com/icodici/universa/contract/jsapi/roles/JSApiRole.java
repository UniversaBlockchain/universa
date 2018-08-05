package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;

import java.util.List;

public abstract class JSApiRole {

    abstract public List<String> getAllAddresses();

    abstract public Role extractRole(JSApiAccessor apiAccessor);

    abstract public boolean isAllowedForAddresses(String... addresses);

    public static JSApiRole createJSApiRole(Role r) {
        if (r instanceof SimpleRole)
            return new JSApiSimpleRole(new JSApiAccessor(), (SimpleRole)r);
        else if (r instanceof ListRole)
            return new JSApiListRole(new JSApiAccessor(), (ListRole)r);
        else if (r instanceof RoleLink)
            return new JSApiRoleLink(new JSApiAccessor(), (RoleLink) r);
        throw new IllegalArgumentException("unknown role type");
    }

}
