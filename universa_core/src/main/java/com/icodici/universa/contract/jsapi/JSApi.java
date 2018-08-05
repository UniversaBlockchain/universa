package com.icodici.universa.contract.jsapi;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermissionBuilder;
import com.icodici.universa.contract.jsapi.roles.JSApiRoleBuilder;

/**
 * Implements js-api, that provided to client's javascript.
 */
public class JSApi {

    private Contract currentContract;

    public JSApi(Contract currentContract) {
        this.currentContract = currentContract;
    }

    public JSApiContract getCurrentContract() {
        return new JSApiContract(this.currentContract);
    }

    public JSApiRoleBuilder getRoleBuilder() {
        return new JSApiRoleBuilder();
    }

    public JSApiPermissionBuilder getPermissionBuilder() {
        return new JSApiPermissionBuilder();
    }

}
