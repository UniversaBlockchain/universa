/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Ut;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpClient {

    public class Answer {
        public final int code;
        public final Binder data;

        private Answer(int code, @Nonnull Binder data) {
            this.code = code;
            this.data = data;
        }
    }

    private String url;

    public HttpClient(String rootUrlString) {
        this.url = rootUrlString;
    }

    public Answer request(String path, Object... keysValues) throws IOException {
        return request(path, Binder.fromKeysValues(keysValues));
    }

    public Answer request(String path, Binder params) throws IOException {
        String charset = "UTF-8";

        byte[] data = Boss.pack(params);

        String boundary = "==boundary==" + Ut.randomString(48);

        String CRLF = "\r\n"; // Line separator required by multipart/form-data.

        URLConnection connection = new URL(url + "/"+ path).openConnection();
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (
                OutputStream output = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
        ) {
            // Send normal param.

            // Send binary file.
            writer.append("--" + boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"requestData\"; filename=\"requestData.boss\"").append(CRLF);
            writer.append("Content-Type: application/octet-stream").append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF).flush();
            output.write(data);
            output.flush(); // Important before continuing with writer!
            writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

            // End of multipart/form-data.
            writer.append("--" + boundary + "--").append(CRLF).flush();
        }

        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        int responseCode = httpConnection.getResponseCode();
        byte[] answer = Do.read(httpConnection.getInputStream());
        return new Answer(responseCode, Boss.unpack(answer));
    }
}
