package com.icodici.universa.contract.jsapi;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node.network.microhttpd.MicroHTTPDService;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JSApiHttpServer {

    private BasicHTTPService service;
    private JSApiHttpServerRoutes routes;
    private JSApiExecOptions execOptions;
    private IContractChecker contractChecker;
    private ISlot1Requestor slot1Requestor;
    private ConcurrentHashMap<String, JSApiEnvironment> endpoints;
    private ScheduledThreadPoolExecutor contractCheckerExecutor = new ScheduledThreadPoolExecutor(1);
    private static int SERVICE_PERIOD_SECONDS = 600;

    public interface IContractChecker {
        Boolean isApproved(HashId hashId);
    }

    public interface ISlot1Requestor {
        byte[] queryContract(HashId slotId, HashId originId);
    }

    public JSApiHttpServer(JSApiHttpServerRoutes routes, JSApiExecOptions execOptions, IContractChecker contractChecker, ISlot1Requestor slot1Requestor) throws Exception {
        this.routes = routes;
        this.execOptions = execOptions;
        this.contractChecker = contractChecker;
        this.slot1Requestor = slot1Requestor;
        service = new MicroHTTPDService();

        initEndpoints();
        checkAllContracts();

        service.on("/", (request, response) -> {
            JSApiEnvironment environment = endpoints.get(request.getPath());
            if (environment == null) {
                response.setResponseCode(404);
                return;
            }
            synchronized (environment) {
                try {
                    environment.callEvent(
                            environment.getHandlerMethodName(),
                            false,
                            new JSApiHttpRequest(request),
                            new JSApiHttpResponse(response)
                    );
                } catch (InterruptedException e) {
                    System.err.println("JSApiHttpServer error on "+request.getPath()+": "+e);
                    response.setResponseCode(500);
                    response.setBody("JSApiHttpServer error on "+request.getPath()+": "+e);
                }
            }
        });

        contractCheckerExecutor.scheduleWithFixedDelay(()->checkAllContracts(), 0, SERVICE_PERIOD_SECONDS, TimeUnit.SECONDS);

        service.start(routes.getPortToListen(), 32);
    }

    public void stop() throws Exception {
        service.close();
    }

    public int getListenPort() {
        return routes.getPortToListen();
    }

    private void initEndpoints() throws Exception {
        endpoints = new ConcurrentHashMap<>();
        ConcurrentLinkedQueue<Exception> exceptions = new ConcurrentLinkedQueue<>();
        routes.forEach((endpoint, route) -> {
            try {
                JSApiEnvironment apiEnvironment = JSApiEnvironment.execJSByName(
                        route.contract.getDefinition().getData().getBinder(Contract.JSAPI_SCRIPT_FIELD, null),
                        route.contract.getState().getData().getBinder(Contract.JSAPI_SCRIPT_FIELD, null),
                        execOptions,
                        route.scriptName,
                        route.contract,
                        route.jsParams == null ? new String[0] : route.jsParams
                );
                apiEnvironment.setHandlerMethodName(route.handlerMethodName);
                apiEnvironment.setSlotId(route.slotId);
                endpoints.put(endpoint, apiEnvironment);
            } catch (Exception e) {
                exceptions.add(e);
            }
        });
        if (!exceptions.isEmpty()) {
            throw exceptions.iterator().next();
        }
    }

    public void checkAllContracts() {
        //final AtomicBoolean needToReload = new AtomicBoolean(false);
        endpoints.forEach((endpoint, env) -> {
            HashId slotId = env.getSlotId();
            HashId originId = env.getCurrentContract().getOrigin();
            byte[] contractBinFromSlot1 = slot1Requestor.queryContract(slotId, originId);
            if (contractBinFromSlot1 != null) {
                try {
                    Contract contractFromSlot1 = Contract.fromPackedTransaction(contractBinFromSlot1);
                    if (contractFromSlot1.getRevision() > env.getCurrentContract().getRevision()) {
                        System.err.println("JSApiHttpServer warning: contract origin="+originId+" changed in slot1, endpoint: " + endpoint);
                        env.updateThisEnvironmentByName(contractFromSlot1, execOptions);
                    }
                } catch (IOException e) {
                    System.err.println("JSApiHttpServer error: unable to unpack latest contract origin=" + originId + " from slot1, update it, endpoint: " + endpoint + ", err: " + e);
                } catch (Exception e) {
                    System.err.println("JSApiHttpServer error while update JSApiEnvironment: " + e);
                    e.printStackTrace();
                }
            }
            HashId id = env.getCurrentContract().getId();
            if (!contractChecker.isApproved(id)) {
                System.err.println("JSApiHttpServer warning: contract id="+id+" is not approved, disabled " + endpoint);
                endpoints.remove(endpoint);
            }
        });
    }

}
