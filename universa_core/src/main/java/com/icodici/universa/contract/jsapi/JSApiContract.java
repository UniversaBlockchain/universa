package com.icodici.universa.contract.jsapi;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.jsapi.permissions.JSApiPermission;
import com.icodici.universa.contract.jsapi.roles.JSApiRole;
import com.icodici.universa.contract.jsapi.roles.JSApiRoleBuilder;
import com.icodici.universa.node2.Quantiser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Implements js-api part for working with contract.
 */
public class JSApiContract {
    private Contract currentContract;

    public JSApiContract(Contract c) {
        this.currentContract = c;
    }

    public String getId() {
        return this.currentContract.getId().toBase64String();
    }

    public int getRevision() {
        return this.currentContract.getState().getRevision();
    }

    public String getOrigin() {
        return this.currentContract.getOrigin().toBase64String();
    }

    public String getParent() {
        return this.currentContract.getParent() == null ? null : this.currentContract.getParent().toBase64String();
    }

    public long getCreatedAt() {
        return this.currentContract.getCreatedAt().toEpochSecond();
    }

    public String getStateDataField(String fieldPath) {
        return this.currentContract.getStateData().getStringOrThrow(fieldPath).toString();
    }

    public void setStateDataField(String fieldPath, String value) {
        this.currentContract.getStateData().set(fieldPath, value);
    }

    public void setStateDataField(String fieldPath, int value) {
        this.currentContract.getStateData().set(fieldPath, value);
    }

    public String getDefinitionDataField(String fieldPath) {
        return this.currentContract.getDefinition().getData().getStringOrThrow(fieldPath);
    }

    public String getTransactionalDataField(String fieldPath) {
        return this.currentContract.getTransactionalData().getStringOrThrow(fieldPath).toString();
    }

    public void setTransactionalDataField(String fieldPath, String value) {
        this.currentContract.getTransactionalData().set(fieldPath, value);
    }

    public JSApiRole getIssuer() {
        return JSApiRole.createJSApiRole(this.currentContract.getIssuer());
    }

    public JSApiRole getOwner() {
        return JSApiRole.createJSApiRole(this.currentContract.getOwner());
    }

    public JSApiRole getCreator() {
        return JSApiRole.createJSApiRole(this.currentContract.getCreator());
    }

    public void setOwner(List<String> addresses) throws KeyAddress.IllegalAddressException {
        List<KeyAddress> addressesList = new ArrayList<>();
        for (String s : addresses)
            addressesList.add(new KeyAddress(s));
        this.currentContract.setOwnerKeys(addressesList);
    }

    public void registerRole(JSApiRole role) {
        this.currentContract.registerRole(role.extractRole(new JSApiAccessor()));
    }

    public boolean isPermitted(String permissionName, PublicKey... keys) throws Quantiser.QuantiserException {
        HashSet<PublicKey> keysSet = new HashSet<>(Arrays.asList(keys));
        return this.currentContract.isPermitted(permissionName, keysSet);
    }

    public JSApiContract createRevision() {
        return new JSApiContract(this.currentContract.createRevision());
    }

    public void addPermission(JSApiPermission permission) {
        this.currentContract.addPermission(permission.extractPermission(new JSApiAccessor()));
    }

    public void addReference(JSApiReference reference) {
        this.currentContract.addReference(reference.extractReference(new JSApiAccessor()));
    }

    /**
     * Extracts instance of {@link Contract} from instance of {@link JSApiContract}.
     */
    public Contract extractContract(JSApiAccessor apiAccessor) throws IllegalArgumentException {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return currentContract;
    }
}
