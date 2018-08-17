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

    public JSApiSharedStorage getSharedStorage() {
        return new JSApiSharedStorage();
    }

    public JSApiOriginStorage getOriginStorage() {
        return new JSApiOriginStorage(this.currentContract.getOrigin());
    }

    public JSApiRevisionStorage getRevisionStorage() {
        return new JSApiRevisionStorage(this.currentContract.getId(), this.currentContract.getParent());
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
