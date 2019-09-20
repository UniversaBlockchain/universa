package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UBotSessionsProTest {

    @Test
    public void createSession() throws Exception {

        Client client = new Client("universa.pro", null, TestKeys.privateKey(1));

        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.seal();
        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId().toBase64String());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));
        requestContract.seal();
        requestContract.getTransactionPack().addReferencedItem(executableContract);

        System.out.println(client.command("ubotCreateSession","packedRequest",requestContract.getPackedTransaction()));

        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();


        for(int i = 0; i < client.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                    Thread.sleep(500);
                    if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                        if(readyCounter.incrementAndGet() == client.size()) {
                            readyEvent.fire();
                        }
                        break;
                    }
                }
            });
        }

        readyEvent.await();

    }

    @Test
    public void createSessionConcurrentRequests() throws Exception {
        Client client = new Client("universa.pro", null, TestKeys.privateKey(1));
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.seal();
        System.out.println("EID = " + executableContract.getId());


        List<Contract> requestContracts = new ArrayList<>();
        for(int i = 0; i < client.size();i++) {
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id",executableContract.getId().toBase64String());
            requestContract.getStateData().put("method_name","getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));
            requestContract.seal();
            requestContract.getTransactionPack().addReferencedItem(executableContract);
            requestContracts.add(requestContract);
        }
        for(int i = 0; i < client.size();i++) {
            int finalI = i;
            Do.inParallel(()-> {
                System.out.println(client.getClient(finalI).command("ubotCreateSession", "packedRequest", requestContracts.get(finalI).getPackedTransaction()));
            });
        }

        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();


        for(int i = 0; i < client.size();i++) {
            int finalI = i;
            Do.inParallel(()->{
                while (true) {
                    Binder res = client.getClient(finalI).command("ubotGetSession", "executableContractId", executableContract.getId());
                    System.out.println(client.getClient(finalI).getNodeNumber() + " " + res);
                    Thread.sleep(500);
                    if(res.get("session") != null && res.getBinderOrThrow("session").getString("state").equals("OPERATIONAL")) {
                        if(readyCounter.incrementAndGet() == client.size()) {
                            readyEvent.fire();
                        }
                        break;
                    }
                }
            });
        }

        readyEvent.await();


    }
}
