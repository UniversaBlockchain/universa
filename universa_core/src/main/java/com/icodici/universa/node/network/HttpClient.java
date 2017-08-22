/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import com.icodici.crypto.HashType;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.SymmetricKey;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Ut;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

public class HttpClient {

    public class EndpointException extends IOException {
        private final Answer answer;

        public EndpointException(Answer answer) {
            this.answer = answer;
        }

        public Answer getAnswer() {
            return answer;
        }

        public int getCode() {
            return answer.code;
        }

        public Binder getData() {
            return answer.data;
        }

        @Override
        public String toString() {
            return "Client::EndpointException " + answer;
        }
    }

    private String nodeId;
    private PrivateKey privateKey;
    private SymmetricKey sessionKey;
    private long sessionId;

    /**
     * Authenticte self to the remote party. Blocks until the handshake is done.
     */
    public void start(PrivateKey privateKey, PublicKey nodePublicKey) throws IOException {
        if (this.privateKey != null)
            throw new IllegalStateException("Private key is already set and the session is started");
        this.privateKey = privateKey;
        Answer a = requestOrThrow("connect", "client_key", privateKey.getPublicKey().pack());
        sessionId = a.data.getLongOrThrow("session_id");
        byte[] server_nonce = a.data.getBinaryOrThrow("server_nonce");
        byte[] client_nonce = Do.randomBytes(47);
        byte[] data = Boss.pack(Binder.fromKeysValues(
                "client_nonce", client_nonce,
                "server_nonce", server_nonce
        ));
        a = requestOrThrow("get_token",
                           "signature", privateKey.sign(data, HashType.SHA512),
                           "data", data,
                           "session_id", sessionId
        );
        data = a.data.getBinaryOrThrow("data");
        if (!nodePublicKey.verify(data, a.data.getBinaryOrThrow("signature"), HashType.SHA512))
            throw new IOException("node signature failed");
        Binder params = Boss.unpack(data);
        if (!Arrays.equals(client_nonce, params.getBinaryOrThrow("client_nonce")))
            throw new IOException("client nonce mismatch");
        sessionKey = new SymmetricKey(params.getBinary("session_key"));

//        Binder result = command("hello");
//        System.out.println(result);
    }

//    public Binder command(String name,Binder params) {
//
//    }

    public Binder command(String name, Object... keysValues) {
        return command(name, Binder.fromKeysValues(keysValues));
    }

    private Answer requestOrThrow(String connect, Object... params) throws IOException {
        Answer answer = request(connect, params);
        if (answer.code >= 400 || answer.data.containsKey("errors"))
            throw new EndpointException(answer);
        return answer;
    }

    public class Answer {
        public final int code;
        public final Binder data;

        private Answer(int code, @Nonnull Binder data) {
            this.code = code;
            this.data = data;
        }

        @Override
        public String toString() {
            if (data.containsKey("errors")) {
                return "" + code + " errors: " + data.getOrCreateList("errors");
            }
            return "" + code + ": " + data;
        }

        public boolean isOk() {
            return code == 200;
        }
    }

    public String getUrl() {
        return url;
    }

    private String url;

    public HttpClient(String nodeId, String rootUrlString) {
        this.url = rootUrlString;
        this.nodeId = nodeId;
    }

    public Answer request(String path, Object... keysValues) throws IOException {
        return request(path, Binder.fromKeysValues(keysValues));
    }


    public Answer request(String path, Binder params) throws IOException {
        String charset = "UTF-8";

        byte[] data = Boss.pack(params);

        String boundary = "==boundary==" + Ut.randomString(48);

        String CRLF = "\r\n"; // Line separator required by multipart/form-data.

        URLConnection connection = new URL(url + "/" + path).openConnection();
        connection.setDoOutput(true);

        connection.setConnectTimeout(2000);
        connection.setReadTimeout(5000);
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
        return new Answer(responseCode, Binder.from(Boss.load(answer)));
    }

    @Override
    public String toString() {
        return "Node<" + nodeId + ":" + getUrl() + ">";
    }
}
