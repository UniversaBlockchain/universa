package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;

import java.util.ArrayList;
import java.util.List;

public class JSApiListRole {
    private ListRole listRole;

    public JSApiListRole(String name, String mode, JSApiSimpleRole... roles) {
        List<Role> listRoles = new ArrayList<>();
        for (int i = 0; i < roles.length; ++i)
            listRoles.add(roles[i].extractRole(new JSApiAccessor()));
        listRole = new ListRole(name, modeStringToEnum(mode), listRoles);
    }

    public void setQuorum(int quorum) {
        listRole.setQuorum(quorum);
    }

    public List<String> getAllAddresses() {
        return listRole.getAllAddresses();
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
