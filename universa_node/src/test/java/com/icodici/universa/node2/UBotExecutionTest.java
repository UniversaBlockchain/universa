package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.assertEquals;

public class UBotExecutionTest extends BaseMainTest {

    @Test
    public void connectToUbot() throws Exception {

        Contract executableContract = new Contract(TestKeys.privateKey(1));

        executableContract.getStateData().put("cloud_methods",Binder.of(
                "getRandom", Binder.of(
                        "pool",Binder.of("size",5),
                        "quorum",Binder.of("size",4)),
                "readRandom", Binder.of(
                        "pool",Binder.of("size",5),
                        "quorum",Binder.of("size",4))
        ));

        executableContract.getStateData().put("js", new String(Do.read("./src/ubot_scripts/random.js")));
        executableContract.seal();


        Contract requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","getRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));
        requestContract.seal();
        requestContract.getTransactionPack().addReferencedItem(executableContract);

        Client client = new Client("universa.pro",null,TestKeys.privateKey(1));
        System.out.println(client.executeCloudMethod(requestContract.getPackedTransaction(),true,0.3f));

        requestContract = new Contract(TestKeys.privateKey(2));
        requestContract.getStateData().put("executable_contract_id",executableContract.getId());
        requestContract.getStateData().put("method_name","readRandom");
        requestContract.getStateData().put("method_args", Do.listOf(1000));
        requestContract.seal();
        requestContract.getTransactionPack().addReferencedItem(executableContract);
        System.out.println(client.executeCloudMethod(requestContract.getPackedTransaction(),true,0.3f));

    }
}
