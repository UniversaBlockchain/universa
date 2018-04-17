package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmartContractTest extends ContractTestBase {

    @Test
    public void goodSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new SmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());
    }

    @Test
    public void serializeSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new SmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Binder b = BossBiMapper.serialize(smartContract);
        Contract desContract = DefaultBiMapper.deserialize(b);
        assertSameContracts(smartContract, desContract);
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), desContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), desContract.get("definition.extended_type"));

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), copiedContract.get("definition.extended_type"));
    }
}
