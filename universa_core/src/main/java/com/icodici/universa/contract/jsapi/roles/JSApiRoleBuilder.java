package com.icodici.universa.contract.jsapi.roles;

import com.icodici.crypto.KeyAddress;

public class JSApiRoleBuilder {
    public JSApiSimpleRole createSimpleRole(String name, String... addresses) throws KeyAddress.IllegalAddressException {
        return new JSApiSimpleRole(name, addresses);
    }
}
