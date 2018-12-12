package com.icodici.universa.contract.jsapi.roles;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;

import java.util.Arrays;
import java.util.HashSet;
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
    public Role extractRole(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return roleLink;
    }

    @Override
    public boolean isAllowedForKeys(PublicKey... keys) {
        HashSet<PublicKey> keySet = new HashSet<>(Arrays.asList(keys));
        return roleLink.isAllowedForKeys(keySet);
    }

}
