package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class UBotSessionsTest extends BaseMainTest {

    @Test
    public void createSession() throws Exception {
        TestSpace ts = prepareTestSpace();
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
        int attempts = 200;
        System.out.println(ts.client.command("ubotCreateSession","packedRequest",requestContract.getPackedTransaction()));
        while (attempts-- > 0) {
            System.out.println(ts.client.command("ubotGetSession","executableContractId",executableContract.getId()));
            Thread.sleep(10);
        }

    }

    @Test
    public void createSessionConcurrentRequests() throws Exception {
        TestSpace ts = prepareTestSpace();
        Contract executableContract = new Contract(TestKeys.privateKey(1));
        executableContract.getStateData().put("cloud_methods",
                Binder.of("getRandom",
                        Binder.of("pool",Binder.of("size",5),
                                "quorum",Binder.of("size",4))));
        executableContract.seal();
        List<Contract> requestContracts = new ArrayList<>();
        for(int i = 0; i < ts.clients.size();i++) {
            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id",executableContract.getId().toBase64String());
            requestContract.getStateData().put("method_name","getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));
            requestContract.seal();
            System.out.println(requestContract.getId());
            requestContract.getTransactionPack().addReferencedItem(executableContract);
            requestContracts.add(requestContract);
        }
        int attempts = 200;
        for(int i = 0; i < ts.clients.size();i++) {
            ts.clients.get(i).command("ubotCreateSession","packedRequest",requestContracts.get(i).getPackedTransaction());
        }

        while (attempts-- > 0) {
            System.out.println(ts.client.command("ubotGetSession","executableContractId",executableContract.getId()));
            Thread.sleep(10);
        }

    }


    @Test
    public void createUBotRegistryContract() throws Exception {
        /*Contract contract = new Contract(TestKeys.privateKey(1));
        List<Binder> list = new ArrayList<>();

        for(int i = 0; i < 30; i++) {
            list.add(Binder.of(
                    "number",i,
                    "domain_urls",Do.listOf("https://localhost:"+(17000+i)),
                    "direct_urls",Do.listOf("https://127.0.0.1:"+(17000+i)),
                    "key", new PublicKey(Do.read("/Users/romanu/Downloads/ubot_config/ubot0/config/keys/ubot_"+i+".public.unikey")).packToBase64String()
            ));
        }
        contract.getStateData().put("topology",list);
        contract.seal();
        new FileOutputStream("ubot_registry_contract.unicon").write(contract.getLastSealedBinary());*/
        Client client = new Client("universa.pro",null,new PrivateKey(Do.read("/Users/romanu/Downloads/ru/uebank.private.unikey")));
        System.out.println(client.register(Contract.fromPackedTransaction(Do.read("ubot_registry_contract.unicon")).getPackedTransaction(),80000));

    }
}
