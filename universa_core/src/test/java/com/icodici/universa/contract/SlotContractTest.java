package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SlotContractTest extends ContractTestBase {

    static Config nodeConfig;

    @BeforeClass
    public static void beforeClass() throws Exception {
        nodeConfig = new Config();
        nodeConfig.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
    }

    @Test
    public void goodSmartContract() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        Contract simpleContract = new Contract(key);
        simpleContract.seal();

        Contract paymentDecreased = createSlotPayment();

        Contract smartContract = new SlotContract(key);

        assertTrue(smartContract instanceof SlotContract);

        ((SlotContract)smartContract).setTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(smartContract.getStateData().getBinary("tracking_contract")).getContract().getId());
    }

    @Test
    public void goodSmartContractFromDSL() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract simpleContract = new Contract(key);
        simpleContract.seal();

        Contract paymentDecreased = createSlotPayment();

        Contract smartContract = SlotContract.fromDslFile(rootPath + "SlotDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");

        assertTrue(smartContract instanceof SlotContract);

        ((SlotContract)smartContract).setTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        assertEquals(2, ((SlotContract) smartContract).getKeepRevisions());

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(smartContract.getStateData().getBinary("tracking_contract")).getContract().getId());
    }

    @Test
    public void serializeSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract simpleContract = new Contract(key);
        simpleContract.seal();

        Contract paymentDecreased = createSlotPayment();

        Contract smartContract = new SlotContract(key);

        assertTrue(smartContract instanceof SlotContract);

        ((SlotContract)smartContract).setTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
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

        Multimap<String, Permission> permissions = desContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) desContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) desContract).getPackedTrackingContract()).getContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(desContract.getStateData().getBinary("tracking_contract")).getContract().getId());

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(SmartContract.SmartContractType.SLOT1.name(), copiedContract.get("definition.extended_type"));
        assertTrue(copiedContract instanceof SlotContract);

        permissions = copiedContract.getPermissions();
        mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) copiedContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) copiedContract).getPackedTrackingContract()).getContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(copiedContract.getStateData().getBinary("tracking_contract")).getContract().getId());
    }

    public Contract createSlotPayment() throws IOException {

        PrivateKey ownerKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaTU = InnerContractsService.createFreshTU(100000000, keys);
        Contract paymentDecreased = stepaTU.createRevision(ownerKey);
        paymentDecreased.getStateData().set("transaction_units", stepaTU.getStateData().getIntOrThrow("transaction_units") - 100);
        paymentDecreased.seal();

        return paymentDecreased;
    }
}
