package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;

import java.util.List;

public class JSApiRoleLink extends JSApiRole {

    private RoleLink roleLink;

    public JSApiRoleLink(JSApiAccessor apiAccessor, RoleLink roleLink) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        this.roleLink = roleLink;
    }

    public JSApiRoleLink(String newRoleName, String existingRoleName) {
        roleLink = new RoleLink(newRoleName, existingRoleName);
    }

    @Override
    public List<String> getAllAddresses() {
        return roleLink.getAllAddresses();
    }

    @Override
    public Role extractRole(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return roleLink;
    }

    @Override
    public boolean isAllowedForAddresses(String... addresses) {
        Role r = roleLink.resolve();
        if (r != null) {
            JSApiRole jsApiRole = JSApiRole.createJSApiRole(r);
            return jsApiRole.isAllowedForAddresses(addresses);
        }
        return false;
    }

}
