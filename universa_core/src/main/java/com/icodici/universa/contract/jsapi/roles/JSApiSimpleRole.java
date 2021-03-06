package com.icodici.universa.contract.jsapi.roles;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class JSApiSimpleRole extends JSApiRole {

    private SimpleRole simpleRole;

    public JSApiSimpleRole(JSApiAccessor apiAccessor, SimpleRole simpleRole) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        this.simpleRole = simpleRole;
    }

    public JSApiSimpleRole(String name, String... addresses) throws KeyAddress.IllegalAddressException {
        List<KeyAddress> keyAddresses = new ArrayList<>();
        for (int i = 0; i < addresses.length; ++i) {
            keyAddresses.add(new KeyAddress(addresses[i]));
        }
        simpleRole = new SimpleRole(name, keyAddresses);
    }

    @Override
    public Role extractRole(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return simpleRole;
    }

    @Override
    public boolean isAllowedForKeys(PublicKey... keys) {
        HashSet<PublicKey> keySet = new HashSet<>(Arrays.asList(keys));
        return simpleRole.isAllowedForKeys(keySet);
    }

}
