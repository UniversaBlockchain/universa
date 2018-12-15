package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class UnsContractTest extends ContractTestBase {

    @Test
    public void goodUnsContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Contract referencesContract = new Contract(key);
        referencesContract.seal();

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = new UnsContract(key);

        String reducedName = "testUnsContract" + Instant.now().getEpochSecond();

        UnsName unsName = new UnsName(reducedName, "test description", "http://test.com");
        unsName.setUnsReducedName(reducedName);
        unsName.setUnsDescription("test description modified");
        unsName.setUnsURL("http://test_modified.com");

        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addNewItems(paymentDecreased);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(randomPrivKey);
        uns.seal();
        uns.check();
        uns.traceErrors();
        assertTrue(uns.isOk());

        assertEquals(NSmartContract.SmartContractType.UNS1.name(), uns.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.UNS1.name(), uns.get("definition.extended_type"));

        assertTrue(uns instanceof UnsContract);
        assertTrue(uns instanceof NSmartContract);
        assertTrue(uns instanceof NContract);

        Multimap<String, Permission> permissions = uns.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(uns.getUnsName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(uns.getUnsName(reducedName).getUnsDescription(), "test description modified");
        assertEquals(uns.getUnsName(reducedName).getUnsURL(), "http://test_modified.com");

        assertNotEquals(uns.getUnsName(reducedName).findUnsRecordByOrigin(referencesContract.getOrigin()), -1);
        assertNotEquals(uns.getUnsName(reducedName).findUnsRecordByKey(randomPrivKey.getPublicKey()), -1);
        assertNotEquals(uns.getUnsName(reducedName).findUnsRecordByAddress(new KeyAddress(randomPrivKey.getPublicKey(), 0, true)), -1);
    }

    @Test
    public void goodUnsContractFromDSL() throws Exception {
        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = UnsContract.fromDslFile(rootPath + "uns/simple_uns_contract.yml");
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addNewItems(paymentDecreased);
        uns.seal();
        uns.check();
        uns.traceErrors();
        assertTrue(uns.isOk());

        assertEquals(NSmartContract.SmartContractType.UNS1.name(), uns.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.UNS1.name(), uns.get("definition.extended_type"));

        assertTrue(uns instanceof UnsContract);
        assertTrue(uns instanceof NSmartContract);
        assertTrue(uns instanceof NContract);
    }

    @Test
    public void serializeUnsContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey randomPrivKey = new PrivateKey(2048);

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterKeyData(new Bytes(authorizedNameServiceKey.getPublicKey().pack()));

        Contract referencesContract = new Contract(key);
        referencesContract.seal();

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = new UnsContract(key);

        String reducedName = "testUnsContract" + Instant.now().getEpochSecond();

        UnsName unsName = new UnsName(reducedName, "test description", "http://test.com");
        unsName.setUnsReducedName(reducedName);
        UnsRecord unsRecord1 = new UnsRecord(randomPrivKey.getPublicKey());
        UnsRecord unsRecord2 = new UnsRecord(referencesContract.getId());
        unsName.addUnsRecord(unsRecord1);
        unsName.addUnsRecord(unsRecord2);
        uns.addUnsName(unsName);
        uns.addOriginContract(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addNewItems(paymentDecreased);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(randomPrivKey);
        uns.seal();
        uns.check();
        uns.traceErrors();
        assertTrue(uns.isOk());

        Binder b = BossBiMapper.serialize(uns);
        Contract desUns = DefaultBiMapper.deserialize(b);

        assertEquals(NSmartContract.SmartContractType.UNS1.name(), desUns.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.UNS1.name(), desUns.get("definition.extended_type"));

        assertTrue(desUns instanceof UnsContract);
        assertTrue(desUns instanceof NSmartContract);
        assertTrue(desUns instanceof NContract);

        Multimap<String, Permission> permissions = desUns.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((UnsContract)desUns).getUnsName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(((UnsContract)desUns).getUnsName(reducedName).getUnsDescription(), "test description");
        assertEquals(((UnsContract)desUns).getUnsName(reducedName).getUnsURL(), "http://test.com");

        assertNotEquals(((UnsContract)desUns).getUnsName(reducedName).findUnsRecordByOrigin(referencesContract.getOrigin()), -1);
        assertNotEquals(((UnsContract)desUns).getUnsName(reducedName).findUnsRecordByKey(randomPrivKey.getPublicKey()), -1);
        assertNotEquals(((UnsContract)desUns).getUnsName(reducedName).findUnsRecordByAddress(new KeyAddress(randomPrivKey.getPublicKey(), 0, true)), -1);

        Contract copiedUns = uns.copy();

        assertEquals(NSmartContract.SmartContractType.UNS1.name(), copiedUns.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.UNS1.name(), copiedUns.get("definition.extended_type"));

        assertTrue(copiedUns instanceof UnsContract);
        assertTrue(copiedUns instanceof NSmartContract);
        assertTrue(copiedUns instanceof NContract);

        permissions = copiedUns.getPermissions();
        mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((UnsContract)copiedUns).getUnsName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(((UnsContract)copiedUns).getUnsName(reducedName).getUnsDescription(), "test description");
        assertEquals(((UnsContract)copiedUns).getUnsName(reducedName).getUnsURL(), "http://test.com");

        assertNotEquals(((UnsContract)copiedUns).getUnsName(reducedName).findUnsRecordByOrigin(referencesContract.getOrigin()), -1);
        assertNotEquals(((UnsContract)copiedUns).getUnsName(reducedName).findUnsRecordByKey(randomPrivKey.getPublicKey()), -1);
        assertNotEquals(((UnsContract)copiedUns).getUnsName(reducedName).findUnsRecordByAddress(new KeyAddress(randomPrivKey.getPublicKey(), 0, true)), -1);
    }

    private Config config = new Config();

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NSmartContract.NodeInfoProvider() {

        @Override
        public Set<KeyAddress> getUIssuerKeys() {
            return config.getUIssuerKeys();
        }

        @Override
        public String getUIssuerName() {
            return config.getUIssuerName();
        }

        @Override
        public int getMinPayment(String extendedType) {
            return config.getMinPayment(extendedType);
        }

        @Override
        @Deprecated
        public double getRate(String extendedType) {
            return config.getRate(extendedType);
        }

        @Override
        public BigDecimal getServiceRate(String extendedType) {
            return config.getServiceRate(extendedType);
        }

        @Override
        public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
            Set<PublicKey> set = new HashSet<>();
            if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
                set.add(config.getAuthorizedNameServiceCenterKey());
            }
            return set;
        }
    };

    public Contract createUnsPayment() throws IOException {

        PrivateKey ownerKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        Set<PublicKey> keys = new HashSet();
        keys.add(ownerKey.getPublicKey());
        Contract stepaU = InnerContractsService.createFreshU(100000000, keys);
        Contract paymentDecreased = stepaU.createRevision(ownerKey);
        paymentDecreased.getStateData().set("transaction_units", stepaU.getStateData().getIntOrThrow("transaction_units") - 2000);
        paymentDecreased.seal();

        return paymentDecreased;
    }
}
