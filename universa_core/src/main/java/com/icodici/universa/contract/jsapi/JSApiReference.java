package com.icodici.universa.contract.jsapi;

import com.icodici.universa.contract.Reference;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.tools.Binder;

import java.util.HashMap;

public class JSApiReference {

    private Reference reference;

    public JSApiReference(Reference reference) {
        this.reference = reference;
    }

    public void setConditions(ScriptObjectMirror conditions) {
        Object paramsMap = JSApiHelpers.jo2Object(conditions);
        if (paramsMap instanceof HashMap) {
            Binder paramsBinder = new Binder();
            ((HashMap)paramsMap).forEach((k, v) -> paramsBinder.set(k.toString(), v));
            this.reference.setConditions(paramsBinder);
        }
    }

    public Reference extractReference(JSApiAccessor apiAccessor) {
        JSApiAccessor.checkApiAccessor(apiAccessor);
        return reference;
    }
}
