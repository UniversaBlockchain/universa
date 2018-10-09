package com.icodici.universa.contract.jsapi;

import com.icodici.universa.node.network.BasicHTTPService;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import net.sergeych.tools.JsonTool;

public class JSApiHttpResponse {
    private BasicHTTPService.Response response;

    public JSApiHttpResponse(BasicHTTPService.Response response) {
        this.response = response;
    }

    public void setResponseCode(int code) {
        response.setResponseCode(code);
    }

    public void setBodyAsPlainText(String answer) {
        response.setBody(answer);
    }

    public void setBodyAsJson(ScriptObjectMirror bodyAsJson) {
        response.setBody(JsonTool.toJsonString(bodyAsJson));
    }
}
