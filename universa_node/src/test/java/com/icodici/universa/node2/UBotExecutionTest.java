package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.InnerContractsService;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.BasicHttpClient;
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

        Client client = new Client("universa.pro",null,TestKeys.privateKey(0));

        Contract u = InnerContractsService.createFreshU(100000,new HashSet(Do.listOf(TestKeys.privateKey(1).getPublicKey())));
        u.setIssuerKeys(TestKeys.privateKey(0).getPublicKey().getLongAddress());
        u.seal();
        u.addSignatureToSeal(TestKeys.privateKey(0));
        assertEquals(client.register(u.getPackedTransaction(),100000).state, ItemState.APPROVED);


        Contract executableContract = new Contract(TestKeys.privateKey(1));

        executableContract.getStateData().put("cloud_methods", Binder.of(
                "getRandom", Binder.of(
                        "pool", Binder.of("size", 31),
                        "quorum", Binder.of("size", 31)),
                "readRandom", Binder.of(
                        "pool", Binder.of("size", 31),
                        "quorum", Binder.of("size", 31))
        ));

        executableContract.getStateData().put("js", new String(Do.read("./src/ubot_scripts/random.js")));
        executableContract.seal();
        assertEquals(client.register(executableContract.getPackedTransaction(), 10000).state, ItemState.APPROVED);

        final int ATTEMPTS = 3;

        for(int i = 0; i < ATTEMPTS; i++) {
            u = u.createRevision(TestKeys.privateKey(1));
            u.getStateData().put("transaction_units", u.getStateData().getIntOrThrow("transaction_units") - 100);
            u.seal();


            Contract requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id", executableContract.getId());
            requestContract.getStateData().put("method_name", "getRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));

            ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
                    Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

            requestContract.seal();
            requestContract.getTransactionPack().addReferencedItem(executableContract);



            System.out.println(client.executeCloudMethod(requestContract.getPackedTransaction(), u.getPackedTransaction(), true, 0.3f));

            requestContract = new Contract(TestKeys.privateKey(2));
            requestContract.getStateData().put("executable_contract_id", executableContract.getId());
            requestContract.getStateData().put("method_name", "readRandom");
            requestContract.getStateData().put("method_args", Do.listOf(1000));

            ContractsService.addReferenceToContract(requestContract, executableContract, "executable_contract_constraint",
                    Reference.TYPE_EXISTING_DEFINITION, Do.listOf("ref.id==this.state.data.executable_contract_id"), true);

            requestContract.seal();
            requestContract.getTransactionPack().addReferencedItem(executableContract);

            u = u.createRevision(TestKeys.privateKey(1));
            u.getStateData().put("transaction_units", u.getStateData().getIntOrThrow("transaction_units") - 100);
            u.seal();


            System.out.println(client.executeCloudMethod(requestContract.getPackedTransaction(), u.getPackedTransaction(), true, 0.3f));
        }

    }
}
