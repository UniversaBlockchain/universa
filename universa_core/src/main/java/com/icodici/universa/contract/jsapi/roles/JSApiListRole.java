package com.icodici.universa.contract.jsapi.roles;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;

import java.util.*;

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
    public boolean isAllowedForKeys(PublicKey... keys) {
        HashSet<PublicKey> keySet = new HashSet<>(Arrays.asList(keys));
        return listRole.isAllowedForKeys(keySet);
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
