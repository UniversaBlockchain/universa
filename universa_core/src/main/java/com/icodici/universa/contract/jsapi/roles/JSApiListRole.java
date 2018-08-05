package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;

import java.util.ArrayList;
import java.util.List;

public class JSApiListRole extends JSApiRole {

    private ListRole listRole;

    public JSApiListRole(JSApiAccessor apiAccessor, ListRole listRole) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        this.listRole = listRole;
    }

    public JSApiListRole(String name, String mode, JSApiRole... roles) {
        List<Role> listRoles = new ArrayList<>();
        for (int i = 0; i < roles.length; ++i)
            listRoles.add(roles[i].extractRole(new JSApiAccessor()));
        listRole = new ListRole(name, modeStringToEnum(mode), listRoles);
    }

    public void setQuorum(int quorum) {
        listRole.setQuorum(quorum);
    }

    @Override
    public List<String> getAllAddresses() {
        return listRole.getAllAddresses();
    }

    @Override
    public Role extractRole(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return listRole;
    }

    @Override
    public boolean isAllowedForAddresses(String... addresses) {
        if (listRole.getMode() == ListRole.Mode.ALL)
            return listRole.getRoles().stream().allMatch(r -> JSApiRole.createJSApiRole(r).isAllowedForAddresses(addresses));
        if (listRole.getMode() == ListRole.Mode.ANY)
            return listRole.getRoles().stream().anyMatch(r -> JSApiRole.createJSApiRole(r).isAllowedForAddresses(addresses));
        if (listRole.getMode() == ListRole.Mode.QUORUM) {
            int counter = listRole.getQuorum();
            boolean result = counter == 0;
            for (Role role : listRole.getRoles()) {
                if (result)
                    break;
                if (role != null && JSApiRole.createJSApiRole(role).isAllowedForAddresses(addresses) && --counter ==0) {
                    result = true;
                    break;
                }
            }
            return result;
        }
        return false;
    }

    private static ListRole.Mode modeStringToEnum(String mode) {
        switch (mode) {
            case "all":
                return ListRole.Mode.ALL;
            case "any":
                return ListRole.Mode.ANY;
            case "quorum":
                return ListRole.Mode.QUORUM;
        }
        throw new IllegalArgumentException("unknown mode in JSApiListRole: " + mode);
    }

}
