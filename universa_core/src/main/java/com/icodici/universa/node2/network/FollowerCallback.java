package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Simple example of follower callback server. Use only to receive follower callbacks from Universa nodes.
 */
public class FollowerCallback {
    private ServerSocket serverSocket;
    private PrivateKey callbackKey;
    private int port = 8080;
    private Thread main;
    private Set<PublicKey> nodeKeys;

    public FollowerCallback(PrivateKey callbackKey) {
        this.callbackKey = callbackKey;
    }

    public FollowerCallback(PrivateKey callbackKey, int port) {
        this(callbackKey);
        this.port = port;
    }

    public void setNetworkNodeKeys(Set<PublicKey> keys) { nodeKeys = keys; }

    public void clearNetworkNodeKeys() { nodeKeys = null; }

    public void start() {
        main = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                System.out.println("Follower callback server started.");

                while (!Thread.interrupted()) {
                    FollowerCallbackThread callback = new FollowerCallbackThread(serverSocket.accept());

                    Thread thread = new Thread(callback);
                    thread.start();
                }

            } catch (IOException e) {
                System.err.println("Follower callback connection error: " + e.getMessage());
            }
        });
        main.start();
    }

    public void stop() {
        if (main.isAlive()) {
            main.interrupt();
            //serverSocket.close();
        }
    }

    public class FollowerCallbackThread implements Runnable {
        private Socket acceptedSocket;

        public FollowerCallbackThread(Socket socket) {
            acceptedSocket = socket;
        }

        // parsing constants
        final String startContentType = "Content-Type: multipart/form-data; boundary=";
        final String nodeUserAgent = "User-Agent: Universa Node";
        final String contentLength = "Content-Length: ";
        final String contentDisposition = "Content-Disposition: form-data; name=\"callbackData\"; filename=\"callbackData.boss\"";

        private byte[] parseRequest(BufferedReader in, InputStream inStream) {
            try {
                String contentType = in.readLine();
                String userAgent = in.readLine();
                String boundary;
                int length = 0;

                if (contentType.startsWith(startContentType) && userAgent.equals(nodeUserAgent))
                    boundary = contentType.substring(startContentType.length());
                else
                    return null;

                String line = in.readLine();
                while (line.length() > 0) {
                    if (line.startsWith(contentLength))
                        length = Integer.parseInt(line.substring(contentLength.length()));
                    line = in.readLine();
                }

                line = in.readLine();
                String disposition = in.readLine();
                if ((length == 0) || !line.endsWith(boundary) || !disposition.equals(contentDisposition))
                    return null;

                while (line.length() > 0)
                    line = in.readLine();

                int av = inStream.available();
                byte[] xdata = new byte[length];
                inStream.read(xdata);

                char[] chars = new char[length];
                int readed = in.read(chars, 0, length);

                String end = new String(Arrays.copyOfRange(chars, readed - boundary.length() - 8, readed));
                if (!end.contains(boundary))
                    return null;

                inStream.reset();


                inStream.read(xdata);

                byte[] data = new String(Arrays.copyOfRange(chars, 0, readed - boundary.length() - 8)).getBytes(StandardCharsets.UTF_8);
                return data;
            } catch (IOException e) {
                System.err.println("Follower callback parsing error: " + e.getMessage());
            }

            return null;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(acceptedSocket.getInputStream()));
                PrintWriter out = new PrintWriter(acceptedSocket.getOutputStream());
                BufferedOutputStream receiptOut = new BufferedOutputStream(acceptedSocket.getOutputStream());

                String request = in.readLine();

                StringTokenizer parse = new StringTokenizer(request);
                String method = parse.nextToken().toUpperCase();
                String resource = parse.nextToken();

                byte[] data;

                if (!method.equals("POST") || ((data = parseRequest(in, acceptedSocket.getInputStream())) == null)) {
                    out.println("HTTP/1.1 500 Error parsing follower callback");
                    out.flush();

                    in.close();
                    out.close();
                    receiptOut.close();
                    acceptedSocket.close();
                    return;
                }

                System.out.println("Follower callback received. Path: " + resource);

                Binder b = Boss.unpack(data);

                // check node key
                if (nodeKeys != null) {

                }

                // sign receipt

                out.println("HTTP/1.1 200 OK");
                out.flush();

                in.close();
                out.close();
                receiptOut.close();
                acceptedSocket.close();
            } catch (IOException e) {
                System.err.println("Follower callback running error: " + e.getMessage());
            }
        }
    }
}
