package com.icodici.universa.client;

import com.icodici.universa.contract.jsapi.JSApiExecOptions;
import com.icodici.universa.contract.jsapi.JSApiHttpServer;
import com.icodici.universa.contract.jsapi.JSApiHttpServerRoutes;

import java.util.Scanner;

public class CliServices {

    private JSApiHttpServer jsApiHttpServer = null;

    public boolean isAnythingStarted() {
        return jsApiHttpServer != null;
    }

    public void startJsApiHttpServer(
            String routesFilePath,
            JSApiHttpServer.IContractChecker contractChecker,
            JSApiHttpServer.ISlot1Requestor slot1Requestor
    ) throws Exception {
        JSApiHttpServerRoutes routes = new JSApiHttpServerRoutes(routesFilePath);
        jsApiHttpServer = new JSApiHttpServer(routes, new JSApiExecOptions(), contractChecker, slot1Requestor);
    }

    public void stopJsApiHttpServer() {
        try {
            if (jsApiHttpServer != null) {
                jsApiHttpServer.stop();
                jsApiHttpServer = null;
            }
        } catch (Exception e) {
            System.err.println("unable to stop JSApiHttpServer");
        }
    }

    public void waitForUserInput() {
        System.out.println();
        if (jsApiHttpServer != null)
            System.out.println("JSApiHttpServer is running, listen on port " + jsApiHttpServer.getListenPort());

        Scanner scanner = new Scanner(System.in);
        String inputString;
        do {
            System.out.println("type 'exit' to stop uniclient services and exit: ");
            inputString = scanner.nextLine();
        } while (!inputString.equals("exit"));

        System.out.println("initiate graceful shutdown...");
        stopJsApiHttpServer();
        System.out.println("all services stopped, exit");
    }

}
