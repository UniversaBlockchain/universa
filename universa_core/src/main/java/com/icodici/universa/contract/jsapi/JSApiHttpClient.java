package com.icodici.universa.contract.jsapi;

import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import net.sergeych.utils.Bytes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JSApiHttpClient {

    JSApiUrlParser urlParser;

    public JSApiHttpClient(JSApiScriptParameters scriptParameters) {
        this.urlParser = new JSApiUrlParser();
        scriptParameters.domainMasks.forEach(mask -> this.urlParser.addUrlMask(mask));
        scriptParameters.ipMasks.forEach(mask -> this.urlParser.addIpMask(mask));
    }

    public List sendGetRequest(String strUrl, String respType) throws IOException {
        if (urlParser.isUrlAllowed(strUrl))
            return sendRequest("GET", strUrl, respType, null, null);
        throw new IllegalArgumentException("http access denied");
    }

    public List sendPostRequest(String strUrl, String respType, Map<String, Object> params, String contentType) throws IOException {
        if (urlParser.isUrlAllowed(strUrl)) {
            String header = "application/x-www-form-urlencoded";
            if (CONTENTTYPE_JSON.equals(contentType))
                header = "application/json";
            return sendRequest("POST", strUrl, respType, params, header);
        }
        throw new IllegalArgumentException("http access denied");
    }

    private boolean checkBoundary(String boundary, Map<String, Object> formParams, Map<String, byte[]> files) {
        for (String paramName : formParams.keySet()) {
            String paramValue = formParams.get(paramName).toString();
            if (paramValue.indexOf(boundary) != -1)
                return false;
        }
        for (String paramName : files.keySet()) {
            byte[] binData = files.get(paramName);
            String strData = new String(binData);
            if (strData.indexOf(boundary) != -1)
                return false;
        }
        return true;
    }

    private String generateBoundary(Map<String, Object> formParams, Map<String, byte[]> files) {
        int counter = 0;
        do {
            String boundary = Bytes.random(16).toHex(false);
            if (checkBoundary(boundary, formParams, files))
                return boundary;
        } while (++counter < 10);
        throw new IllegalArgumentException("failed to create http multipart boundary");
    }

    public List sendPostRequestMultipart(String strUrl, String respType, Map<String, Object> formParams, Map<String, byte[]> files) throws IOException {
        if (urlParser.isUrlAllowed(strUrl)) {
            String boundary = generateBoundary(formParams, files);
            URL url = new URL(strUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Universa JAVA API Client");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            connection.setDoOutput(true);
            DataOutputStream os = new DataOutputStream(connection.getOutputStream());
            for (String paramName : formParams.keySet()) {
                Object paramValue = formParams.get(paramName);
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=" + paramName + "\r\n");
                os.writeBytes("\r\n");
                os.writeBytes(paramValue.toString());
                os.writeBytes("\r\n");
            }
            int fileCounter = 0;
            for (String paramName : files.keySet()) {
                byte[] binData = files.get(paramName);
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=" + paramName + "; filename=file_" + fileCounter + "\r\n");
                os.writeBytes("Content-Type: application/octet-stream" + "\r\n");
                os.writeBytes("\r\n");
                os.write(binData);
                os.writeBytes("\r\n");
                fileCounter += 1;
            }
            os.writeBytes("--" + boundary + "--" + "\r\n");
            os.flush();
            os.close();

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
        throw new IllegalArgumentException("http access denied");
    }

    private List sendRequest(String method, String strUrl, String respType, Map<String, Object> params, String contentType) throws IOException {
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
