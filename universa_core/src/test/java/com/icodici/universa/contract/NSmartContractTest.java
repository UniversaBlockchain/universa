package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.services.NContract;
import com.icodici.universa.contract.services.NSmartContract;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NSmartContractTest extends ContractTestBase {

    @Test
    public void goodNSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);
    }

    @Test
    public void goodNSmartContractFromDSL() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(rootPath + "NotaryNSmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);
    }

    @Test
    public void serializeNSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new NSmartContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Binder b = BossBiMapper.serialize(smartContract);
        Contract desContract = DefaultBiMapper.deserialize(b);
        assertSameContracts(smartContract, desContract);
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), desContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), desContract.get("definition.extended_type"));
        assertTrue(desContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.N_SMART_CONTRACT.name(), copiedContract.get("definition.extended_type"));
        assertTrue(copiedContract instanceof NSmartContract);
        assertTrue(smartContract instanceof NContract);
    }
}
