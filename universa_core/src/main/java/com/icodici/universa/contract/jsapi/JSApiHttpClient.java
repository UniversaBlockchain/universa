package com.icodici.universa.contract.jsapi;

import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JSApiHttpClient {

    public List sendGetRequest(String strUrl, String respType) throws MalformedURLException, IOException {
        return sendRequest("GET", strUrl, respType, null, null);
    }

    public List sendPostRequest(String strUrl, String respType, Map<String, Object> params, String contentType) throws MalformedURLException, IOException {
        String header = "application/x-www-form-urlencoded";
        if ("json".equals(contentType))
            header = "application/json";
        return sendRequest("POST", strUrl, respType, params, header);
    }

    private List sendRequest(String method, String strUrl, String respType, Map<String, Object> params, String contentType) throws MalformedURLException, IOException {
        URL url = new URL(strUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
        connection.setRequestProperty("Connection", "close");
        if (contentType != null)
            connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestMethod(method);
        if (params != null) {
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            if ("application/json".equals(contentType))
                os.write(JsonTool.toJsonString(params).getBytes());
            else
                os.write(getFormParams(params).getBytes());
            os.flush();
            os.close();
        }
        if (200 != connection.getResponseCode())
            return null;
        Object resp;
        switch (respType) {
            case RESPTYPE_TEXT:
                resp = new String(Do.read(connection.getInputStream()));
                break;
            case RESPTYPE_JSON:
                resp = JsonTool.fromJson(new String(Do.read(connection.getInputStream())));
                break;
            default:
                resp = Do.read(connection.getInputStream());
                break;
        }
        return Arrays.asList(connection.getResponseCode(), resp);
    }

    private String getFormParams(Map<String, Object> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((k, v) -> {
            try {
                String sk = URLEncoder.encode(k, "UTF-8");
                String sv = URLEncoder.encode(v.toString(), "UTF-8");
                pairs.add(sk + "=" + v);
            } catch (UnsupportedEncodingException e) {
                System.err.println("JSApiHttpClient.getFormParams exception: " + e);
            }
        });
        return String.join("&", pairs);
    }

    public static final String RESPTYPE_TEXT = "text";
    public static final String RESPTYPE_JSON = "json";
    public static final String RESPTYPE_BIN = "bin";
    public static final String CONTENTTYPE_FORM = "form";
    public static final String CONTENTTYPE_JSON = "json";

}
