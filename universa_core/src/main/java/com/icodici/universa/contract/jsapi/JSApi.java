package com.icodici.universa.contract.jsapi;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermissionBuilder;
import com.icodici.universa.contract.jsapi.roles.JSApiRoleBuilder;
import com.icodici.universa.contract.jsapi.storage.JSApiOriginStorage;
import com.icodici.universa.contract.jsapi.storage.JSApiRevisionStorage;
import com.icodici.universa.contract.jsapi.storage.JSApiSharedStorage;
import net.sergeych.utils.Base64;

/**
 * Implements js-api, that provided to client's javascript.
 */
public class JSApi {

    private Contract currentContract;
    private JSApiExecOptions execOptions;
    private JSApiScriptParameters scriptParameters;

    public JSApi(Contract currentContract, JSApiExecOptions execOptions, JSApiScriptParameters scriptParameters) {
        this.currentContract = currentContract;
        this.execOptions = execOptions;
        this.scriptParameters = scriptParameters;
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
        if (scriptParameters.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS))
            return new JSApiSharedFolders(execOptions);
        throw new IllegalArgumentException("access denied: missing permission " + JSApiScriptParameters.ScriptPermissions.PERM_SHARED_FOLDERS.toString());
    }

    public JSApiSharedStorage getSharedStorage() {
        if (scriptParameters.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE))
            return new JSApiSharedStorage();
        throw new IllegalArgumentException("access denied: missing permission " + JSApiScriptParameters.ScriptPermissions.PERM_SHARED_STORAGE.toString());
    }

    public JSApiOriginStorage getOriginStorage() {
        if (scriptParameters.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE))
            return new JSApiOriginStorage(this.currentContract.getOrigin());
        throw new IllegalArgumentException("access denied: missing permission " + JSApiScriptParameters.ScriptPermissions.PERM_ORIGIN_STORAGE.toString());
    }

    public JSApiRevisionStorage getRevisionStorage() {
        if (scriptParameters.checkPermission(JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE))
            return new JSApiRevisionStorage(this.currentContract.getId(), this.currentContract.getParent());
        throw new IllegalArgumentException("access denied: missing permission " + JSApiScriptParameters.ScriptPermissions.PERM_REVISION_STORAGE.toString());
    }

    public byte[] string2bin(String s) {
        return s.getBytes();
    }

    public PublicKey bin2publicKey(byte[] binary) throws EncryptionError {
        return new PublicKey(binary);
    }

    public PublicKey base64toPublicKey(String s) throws EncryptionError {
        return new PublicKey(Base64.decodeLines(s));
    }

}
