package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeConfigProvider;
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
        config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress());

        Contract referencesContract = new Contract(key);
        referencesContract.seal();

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = new UnsContract(key);

        String reducedName = "testUnsContract" + Instant.now().getEpochSecond();

        uns.addName(reducedName,reducedName,"test description");
        uns.addKey(randomPrivKey.getPublicKey());
        uns.addOrigin(referencesContract);

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addNewItems(paymentDecreased);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(randomPrivKey);
        uns.setPayingAmount(1460);
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

        assertEquals(uns.getName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(uns.getName(reducedName).getUnsDescription(), "test description");

        assertTrue(uns.getOrigins().contains(referencesContract.getOrigin()));
        assertTrue(uns.getAddresses().contains(randomPrivKey.getPublicKey().getLongAddress()));
        assertTrue(uns.getAddresses().contains(randomPrivKey.getPublicKey().getShortAddress()));
    }

    @Test
    public void goodUnsContractFromDSL() throws Exception {
        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress());

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = UnsContract.fromDslFile(rootPath + "uns/simple_uns_contract.yml");
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addNewItems(paymentDecreased);
        uns.setPayingAmount(1460);
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
        config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress());

        Contract referencesContract = new Contract(key);
        referencesContract.seal();

        Contract paymentDecreased = createUnsPayment();

        UnsContract uns = new UnsContract(key);

        String reducedName = "testUnsContract" + Instant.now().getEpochSecond();

        uns.addName(reducedName,reducedName,"test description");
        uns.addKey(randomPrivKey.getPublicKey());
        uns.addOrigin(referencesContract);


        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.addNewItems(paymentDecreased);
        uns.addSignerKey(authorizedNameServiceKey);
        uns.addSignerKey(randomPrivKey);
        uns.setPayingAmount(1460);
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

        assertEquals(((UnsContract)desUns).getName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(((UnsContract)desUns).getName(reducedName).getUnsDescription(), "test description");

        assertTrue(((UnsContract)desUns).getOrigins().contains(referencesContract.getOrigin()));
        assertTrue(((UnsContract)desUns).getAddresses().contains(randomPrivKey.getPublicKey().getShortAddress()));
        assertTrue(((UnsContract)desUns).getAddresses().contains(randomPrivKey.getPublicKey().getLongAddress()));

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

        assertEquals(((UnsContract)copiedUns).getName(reducedName).getUnsReducedName(), reducedName);
        assertEquals(((UnsContract)copiedUns).getName(reducedName).getUnsDescription(), "test description");

        assertTrue(((UnsContract)copiedUns).getOrigins().contains(referencesContract.getOrigin()));
        assertTrue(((UnsContract)copiedUns).getAddresses().contains(randomPrivKey.getPublicKey().getShortAddress()));
        assertTrue(((UnsContract)copiedUns).getAddresses().contains(randomPrivKey.getPublicKey().getLongAddress()));
    }

    private Config config = new Config();

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NodeConfigProvider(config);

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
