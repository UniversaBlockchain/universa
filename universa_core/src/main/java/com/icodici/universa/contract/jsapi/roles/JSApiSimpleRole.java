package com.icodici.universa.contract.jsapi.roles;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;

import java.util.ArrayList;
import java.util.List;

public class JSApiSimpleRole extends JSApiRole {
    private SimpleRole simpleRole;

    public JSApiSimpleRole(String name, String... addresses) throws KeyAddress.IllegalAddressException {
        List<KeyAddress> keyAddresses = new ArrayList<>();
        for (int i = 0; i < addresses.length; ++i) {
            keyAddresses.add(new KeyAddress(addresses[i]));
        }
        simpleRole = new SimpleRole(name, keyAddresses);
    }

    @Override
    public List<String> getAllAddresses() {
        return simpleRole.getAllAddresses();
    }

    @Override
    public Role extractRole(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return simpleRole;
    }
}
