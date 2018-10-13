package com.icodici.universa.node2.network;

import com.icodici.crypto.PrivateKey;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

public class FollowerCallback {
    private PrivateKey callbackKey;
    private int port = 8080;
    private Thread main;

    public FollowerCallback(PrivateKey callbackKey) {
        this.callbackKey = callbackKey;
    }

    public FollowerCallback(PrivateKey callbackKey, int port) {
        this(callbackKey);
        this.port = port;
    }

    public void start() {
        main = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(port);
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
        if (main.isAlive())
            main.interrupt();
    }

    public class FollowerCallbackThread implements Runnable {
        private Socket acceptedSocket;

        public FollowerCallbackThread(Socket socket) {
            acceptedSocket = socket;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(acceptedSocket.getInputStream()));
                PrintWriter out = new PrintWriter(acceptedSocket.getOutputStream());
                BufferedOutputStream receiptOut = new BufferedOutputStream(acceptedSocket.getOutputStream());

                String line = in.readLine();

                StringTokenizer parse = new StringTokenizer(line);
                String method = parse.nextToken().toUpperCase();
                String resource = parse.nextToken();

                System.out.println("FOLLOWER CALLBACK!!!");
                System.out.println("Method: " + method);
                System.out.println("Resource: " + resource);

                // check nodes
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
