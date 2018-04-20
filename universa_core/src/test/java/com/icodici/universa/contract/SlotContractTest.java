package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.services.SlotContract;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlotContractTest extends ContractTestBase {

    @Test
    public void goodSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new SlotContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof SlotContract);
    }

    @Test
    public void goodSmartContractFromDSL() throws Exception {
        Contract smartContract = SlotContract.fromDslFile(rootPath + "SlotDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        assertTrue(smartContract instanceof SlotContract);
    }

    @Test
    public void serializeSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract smartContract = new SlotContract(key);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Binder b = BossBiMapper.serialize(smartContract);
        Contract desContract = DefaultBiMapper.deserialize(b);
        assertSameContracts(smartContract, desContract);
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), desContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), desContract.get("definition.extended_type"));
        assertTrue(desContract instanceof SlotContract);

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), copiedContract.get("definition.extended_type"));
        assertTrue(copiedContract instanceof SlotContract);
    }
}
