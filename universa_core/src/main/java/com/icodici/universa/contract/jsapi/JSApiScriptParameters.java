package com.icodici.universa.contract.jsapi;

import net.sergeych.tools.Binder;

import java.util.ArrayList;
import java.util.List;

public class JSApiScriptParameters {

    public boolean isCompressed = false;
    public int timeLimitMillis = 0;
    public Binder permissions = new Binder();
    public List<String> domainMasks = new ArrayList<>();
    public List<String> ipMasks = new ArrayList<>();

    public Binder toBinder() {
        Binder binder = new Binder();
        binder.set("compression", (isCompressed ? JSApiCompressionEnum.ZIP : JSApiCompressionEnum.RAW).toString());
        binder.put("time_limit", timeLimitMillis);
        binder.put("permissions", permissions);
        binder.put("domainMasks", domainMasks);
        binder.put("ipMasks", ipMasks);
        return binder;
    }

    public static JSApiScriptParameters fromBinder(Binder binder) {
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        JSApiCompressionEnum compression = JSApiCompressionEnum.valueOf(binder.getStringOrThrow("compression"));
        scriptParameters.isCompressed = (compression != JSApiCompressionEnum.RAW);
        scriptParameters.timeLimitMillis = binder.getIntOrThrow("time_limit");
        scriptParameters.permissions = binder.getBinderOrThrow("permissions");
        scriptParameters.domainMasks = binder.getListOrThrow("domainMasks");
        scriptParameters.ipMasks = binder.getListOrThrow("ipMasks");
        return scriptParameters;
    }

    public void setPermission(ScriptPermissions permission, Boolean state) {
        permissions.set(permission.toString(), state.toString());
    }

    public boolean checkPermission(ScriptPermissions permission) {
        try {
            if (Boolean.TRUE.toString().equals(permissions.getStringOrThrow(permission.toString())))
                return true;
            return false;
        } catch (IllegalArgumentException e) {
            return getDefaultPermissionState(permission);
        }
    }

    private boolean getDefaultPermissionState(ScriptPermissions permission) {
        switch (permission) {
            case PERM_SHARED_FOLDERS:
                return true;
            case PERM_SHARED_STORAGE:
                return false;
            case PERM_ORIGIN_STORAGE:
                return false;
            case PERM_REVISION_STORAGE:
                return false;
            case PERM_HTTP_CLIENT:
                return false;
        }
        return false;
    }

    public enum ScriptPermissions {
        PERM_SHARED_FOLDERS("shared_folders"),
        PERM_SHARED_STORAGE("shared_storage"),
        PERM_ORIGIN_STORAGE("origin_storage"),
        PERM_REVISION_STORAGE("revision_storage"),
        PERM_HTTP_CLIENT("http_client");
        private final String text;
        ScriptPermissions(final String text){this.text=text;}
        @Override public String toString(){return text;}
    }

}
