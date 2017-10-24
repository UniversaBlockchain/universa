package com.icodici.universa.contract;

import com.icodici.universa.Decimal;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class ContractBugTest extends ContractTestBase {


    @Test
    public void createFromSealed() throws Exception {
        // Contract bug: Contract cannot be initiated from sealed data until
        // Permissions being created or initialized or something like that. To avoid it now uncomment string below.
//        loadContractHook();

        Contract contract = null;

        String fileName = "./src/test_contracts/simple_root_contract.unc";

        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);

        try {
            contract = new Contract(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertNotEquals(contract, null);
    }

    @Test
    public void createFromSealedWithRealContract() throws Exception {
        String fileName = "./src/test_contracts/subscription.yml";

        Contract c = Contract.fromYamlFile(fileName);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        // Contract from seal
        byte[] seal = c.seal();
        Contract sealedContract = new Contract(seal);
        sealedContract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(sealedContract, true);
    }

    // This method is a hook, it resolve Contract bug: Contract cannot be initiated from sealed data until
    // Permissions beaing created or initialized or something like that.
    private void loadContractHook() throws IOException {
        Contract.fromYamlFile("./src/test_contracts/simple_root_contract.yml");
    }
}
