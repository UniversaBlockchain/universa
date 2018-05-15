package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

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

        ((SlotContract)smartContract).putTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());

        Binder trackingHashesAsBase64 = smartContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertEquals(simpleContract.getId(), c.getId());
            }
        }
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

        ((SlotContract)smartContract).putTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), smartContract.get("definition.extended_type"));

        assertEquals(2, ((SlotContract) smartContract).getKeepRevisions());

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());

        Binder trackingHashesAsBase64 = smartContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertEquals(simpleContract.getId(), c.getId());
            }
        }
    }

    @Test
    public void serializeSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract simpleContract = new Contract(key);
        simpleContract.seal();

        Contract paymentDecreased = createSlotPayment();

        Contract smartContract = new SlotContract(key);

        assertTrue(smartContract instanceof SlotContract);

        ((SlotContract)smartContract).putTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Binder b = BossBiMapper.serialize(smartContract);
        Contract desContract = DefaultBiMapper.deserialize(b);
        assertSameContracts(smartContract, desContract);
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), desContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), desContract.get("definition.extended_type"));
        assertTrue(desContract instanceof SlotContract);

        Multimap<String, Permission> permissions = desContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) desContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) desContract).getPackedTrackingContract()).getContract().getId());

        Binder trackingHashesAsBase64 = desContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertEquals(simpleContract.getId(), c.getId());
            }
        }

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), copiedContract.get("definition.extended_type"));
        assertTrue(copiedContract instanceof SlotContract);

        permissions = copiedContract.getPermissions();
        mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(simpleContract.getId(), ((SlotContract) copiedContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) copiedContract).getPackedTrackingContract()).getContract().getId());

        trackingHashesAsBase64 = copiedContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertEquals(simpleContract.getId(), c.getId());
            }
        }
   }

    @Test
    public void keepRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        Contract simpleContract = new Contract(key);
        simpleContract.seal();

        Contract paymentDecreased = createSlotPayment();

        Contract smartContract = new SlotContract(key);

        assertTrue(smartContract instanceof SlotContract);

        ((SlotContract)smartContract).putTrackingContract(simpleContract);
        ((SlotContract)smartContract).setNodeConfig(nodeConfig);
        ((SlotContract)smartContract).setKeepRevisions(2);
        smartContract.addNewItems(paymentDecreased);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(1, ((SlotContract)smartContract).getTrackingContracts().size());
        assertEquals(simpleContract.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());

        Binder trackingHashesAsBase64 = smartContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertEquals(simpleContract.getId(), c.getId());
            }
        }

        Contract simpleContract2 = simpleContract.createRevision(key);
        simpleContract2.seal();
        ((SlotContract)smartContract).putTrackingContract(simpleContract2);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(2, ((SlotContract)smartContract).getTrackingContracts().size());
        assertEquals(simpleContract2.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract2.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());

        trackingHashesAsBase64 = smartContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertThat(c.getId(), Matchers.anyOf(equalTo(simpleContract.getId()), equalTo(simpleContract2.getId())));
            }
        }

        Contract simpleContract3 = simpleContract2.createRevision(key);
        simpleContract3.seal();
        ((SlotContract)smartContract).putTrackingContract(simpleContract3);
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(2, ((SlotContract)smartContract).getTrackingContracts().size());
        assertEquals(simpleContract3.getId(), ((SlotContract) smartContract).getTrackingContract().getId());
        assertEquals(simpleContract3.getId(), TransactionPack.unpack(((SlotContract) smartContract).getPackedTrackingContract()).getContract().getId());

        trackingHashesAsBase64 = smartContract.getStateData().getBinder("tracking_contract");
        for (String k : trackingHashesAsBase64.keySet()) {
            byte[] packed = trackingHashesAsBase64.getBinary(k);
            if (packed != null) {
                Contract c = Contract.fromPackedTransaction(packed);
                assertThat(c.getId(), Matchers.anyOf(
                        equalTo(simpleContract.getId()),
                        equalTo(simpleContract2.getId()),
                        equalTo(simpleContract3.getId())
                ));
            }
        }
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
