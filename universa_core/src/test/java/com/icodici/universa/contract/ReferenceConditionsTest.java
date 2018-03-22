package com.icodici.universa.contract;

import org.junit.Test;

import static org.junit.Assert.*;

public class ReferenceConditionsTest {
    protected static final String ROOT_PATH = "./src/test_contracts/references/";

    @Test
    public void checkReferences() throws Exception {

        Contract contract1 = Contract.fromDslFile(ROOT_PATH + "ReferencedConditions_contract1.yml");
        Contract contract2 = Contract.fromDslFile(ROOT_PATH + "ReferencedConditions_contract2.yml");
        contract1.seal();
        contract2.seal();

        TransactionPack tp = new TransactionPack();
        tp.setContract(contract1);
        tp.addReference(contract2);
        tp.addForeignReference(contract2);

        Contract refContract = new Contract(contract1.seal(), tp);

        System.out.println("Check roles conditions");
        assertTrue(refContract.getReferences().get("ref_roles").matchingItems.contains(contract2));
        System.out.println("Check integer conditions");
        assertTrue(refContract.getReferences().get("ref_integer").matchingItems.contains(contract2));
        System.out.println("Check float conditions");
        assertTrue(refContract.getReferences().get("ref_float").matchingItems.contains(contract2));
        System.out.println("Check string conditions");
        assertTrue(refContract.getReferences().get("ref_string").matchingItems.contains(contract2));
        System.out.println("Check boolean conditions");
        assertTrue(refContract.getReferences().get("ref_boolean").matchingItems.contains(contract2));
     }
}