package com.icodici.universa.contract.jsapi;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermissionBuilder;
import com.icodici.universa.contract.jsapi.roles.JSApiRoleBuilder;

/**
 * Implements js-api, that provided to client's javascript.
 */
public class JSApi {

    private Contract currentContract;
    private JSApiExecOptions execOptions;

    public JSApi(Contract currentContract, JSApiExecOptions execOptions) {
        this.currentContract = currentContract;
        this.execOptions = execOptions;
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

    public JSApiSharedFolders getSharedFolders() {
        return new JSApiSharedFolders(execOptions);
    }

    public byte[] string2bin(String s) {
        return s.getBytes();
    }

}
