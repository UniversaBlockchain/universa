package com.icodici.universa.contract.jsapi;

import com.icodici.universa.node.network.BasicHTTPService;

import java.util.HashMap;
import java.util.Map;

public class JSApiHttpRequest {
    private BasicHTTPService.Request request;

    public JSApiHttpRequest(BasicHTTPService.Request request) {
        this.request = request;
    }

    public Map parseForm() {
        Map jsObj = new HashMap();
        request.getParams().forEach((k, v) -> jsObj.put(k, v));
        return jsObj;
    }
}
