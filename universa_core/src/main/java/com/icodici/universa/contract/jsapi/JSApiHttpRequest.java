package com.icodici.universa.contract.jsapi;

import com.icodici.universa.node.network.BasicHTTPService;

import java.util.HashMap;
import java.util.Map;

public class JSApiHttpRequest {
    private BasicHTTPService.Request request;

    public JSApiHttpRequest(BasicHTTPService.Request request) {
        this.request = request;
    }

    public Map getParams() {
        Map jsObj = new HashMap();
        request.getParams().forEach((k, v) -> {
            if (v instanceof BasicHTTPService.FileUpload)
                jsObj.put(k, ((BasicHTTPService.FileUpload)v).getBytes());
            else
                jsObj.put(k, v);
        });
        return jsObj;
    }
}
