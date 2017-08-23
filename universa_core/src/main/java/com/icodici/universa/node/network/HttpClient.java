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
import net.sergeych.utils.LogPrinter;
import net.sergeych.utils.Ut;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;

public class HttpClient {

    static private LogPrinter log = new LogPrinter("HTCL");
    private String connectMessage;
    private String nodeId;
    private PrivateKey privateKey;
    private SymmetricKey sessionKey;
    private long sessionId;
    private String url;
    public HttpClient(String nodeId, String rootUrlString) {
        this.url = rootUrlString;
        this.nodeId = nodeId;
    }

    public String getConnectMessage() {
        return connectMessage;
    }

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
        byte[] key = Boss.unpack(
                privateKey.decrypt(
                        params.getBinaryOrThrow("encrypted_token")
                )
        )
                .getBinaryOrThrow("sk");
        sessionKey = new SymmetricKey(key);

        Binder result = command("hello");
        this.connectMessage = result.getStringOrThrow("message");
        if( !result.getStringOrThrow("status").equals("OK"))
            throw new ConnectionFailedException(""+result);
    }

    /**
     * Ping remote side to ensure it is connected
     * @return true is remote side answers properly. false generally meand we have to recconnect.
     */
    public boolean ping() {
        try {
            return command("sping").getStringOrThrow("sping").equals("spong");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Binder command(String name, Binder params) throws IOException {
        if (sessionKey == null)
            throw new IllegalStateException("session key is not yet setlled");
        Binder call = Binder.fromKeysValues(
                "command", name,
                "params", params
        );
        for (int i = 0; i < 10; i++) {
            try {
                Answer a = requestOrThrow("command",
                                          "command", "command",
                                          "params", sessionKey.encrypt(Boss.pack(call)),
                                          "session_id", sessionId
                );
                return Boss.unpack(
                        sessionKey.decrypt(a.data.getBinaryOrThrow("result"))
                );
            } catch (IOException e) {
                e.printStackTrace();
                log.d("error executing command " + name + ": " + e);
            }
            log.d("repeating command " + name + ", attempt " + (i + 1));
        }
        throw new IOException("Failed to execute command " + name);
    }

    public Binder command(String name, Object... keysValues) throws IOException {
        return command(name, Binder.fromKeysValues(keysValues));
    }

    private Answer requestOrThrow(String connect, Object... params) throws IOException {
        Answer answer = request(connect, params);
        if (answer.code >= 400 || answer.data.containsKey("errors"))
            throw new EndpointException(answer);
        return answer;
    }

    public String getUrl() {
        return url;
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

    public class ClientException extends IOException {
        public ClientException(String message) {
            super(message);
        }

        public ClientException(String message, Throwable cause) {
            super(message, cause);
        }

        public ClientException(Throwable cause) {
            super(cause);
        }

        public HttpClient getClient() { return HttpClient.this; }
    }

    public class ConnectionFailedException extends ClientException {

        public ConnectionFailedException() {
            super("connection failed");
        }

        public ConnectionFailedException(String reason) {
            super(reason);
        }
    }

    public class EndpointException extends ClientException {
        private final Answer answer;

        public EndpointException(Answer answer) {
            super("Client::EndpointException " + answer);
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
                return "" + code + " error: " + data.getListOrThrow("errors");
            }
            return "" + code + ": " + data;
        }

        public boolean isOk() {
            return code == 200;
        }
    }
}
