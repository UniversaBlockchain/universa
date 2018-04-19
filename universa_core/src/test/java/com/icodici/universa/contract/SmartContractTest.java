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

        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof SmartContract);
        assertTrue(smartContract instanceof NodeContract);
    }

    @Test
    public void goodSmartContractFromDSL() throws Exception {
        Contract smartContract = SmartContract.fromDslFile(rootPath + "NotarySmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof SmartContract);
        assertTrue(smartContract instanceof NodeContract);
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
        assertTrue(desContract instanceof SmartContract);
        assertTrue(desContract instanceof NodeContract);

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name(), copiedContract.get("definition.extended_type"));
        assertTrue(copiedContract instanceof SmartContract);
        assertTrue(copiedContract instanceof NodeContract);
    }
}
