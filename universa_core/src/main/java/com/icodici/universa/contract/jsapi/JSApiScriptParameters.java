package com.icodici.universa.contract.jsapi;

import net.sergeych.tools.Binder;

public class JSApiScriptParameters {
    public boolean isCompressed = false;
    public int timeLimitMillis = 0;

    public Binder toBinder() {
        Binder binder = new Binder();
        binder.set("compression", (isCompressed ? JSApiCompressionEnum.ZIP : JSApiCompressionEnum.RAW).toString());
        binder.put("time_limit", timeLimitMillis);
        return binder;
    }

    public static JSApiScriptParameters fromBinder(Binder binder) {
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        JSApiCompressionEnum compression = JSApiCompressionEnum.valueOf(binder.getStringOrThrow("compression"));
        scriptParameters.isCompressed = (compression != JSApiCompressionEnum.RAW);
        scriptParameters.timeLimitMillis = binder.getIntOrThrow("time_limit");
        return scriptParameters;
    }
}
