/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.RoleLink;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class ContractTest extends ContractTestBase {

    @Test
    public void fromYamlFile() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        assertProperSimpleRootContract(c);
//
//        Binder s = DefaultBiMapper.serialize(c);
//
////        Boss.trace((Object)s.getOrThrow("definition","permissions"));
//        Yaml yaml = new Yaml();
//        System.out.println(yaml.dump(s));
    }


    @Test
    public void createFromSealed() throws Exception {
        String fileName = "./src/test_contracts/simple_root_contract.unc";

        readContract(fileName);
    }

    @Test
    public void createWithData() throws Exception {
        String fileName = "./src/test_contracts/ONSUBMIT_30.unicon";

        Contract contract = readContract(fileName);

        Multimap<String, Permission> permissions = contract.getPermissions();
        assertNotNull(permissions);
        assertEquals(1, permissions.size());
        Object modify_data = permissions.get("modify_data").toArray()[0];

        assertTrue(modify_data instanceof ModifyDataPermission);
        Map<String, List<String>> fields = ((ModifyDataPermission) modify_data).getFields();
        assertEquals(1, fields.size());
        List<String> names = fields.get("name");
        assertEquals(2, names.size());
        assertEquals("Maksim", names.get(0));
        assertEquals("Vasily", names.get(1));

        sealCheckTrace(contract, true);

    }

    @Test
    public void createWithSplit() throws Exception {
        String fileName = "./src/test_contracts/123121892sdf.unicon";
        int globalValue = 123123123;
        int splitValue = 1231;

        Contract contract = readContract(fileName);

        //parent value
        Set<Approvable> revokingItems = contract.getRevokingItems();
        assertNotNull(revokingItems);
        assertEquals(1, revokingItems.size());
        assertEquals(globalValue, ((Contract) revokingItems.toArray()[0]).getStateData().get("amount"));

        //sibling value
        Set<Approvable> newItems = contract.getNewItems();
        assertNotNull(newItems);
        assertEquals(1, newItems.size());
        assertEquals(splitValue, ((Contract) newItems.toArray()[0]).getStateData().get("amount"));

        //current value
        assertEquals(2, contract.getRevision());
        Binder stateData = contract.getStateData();
        assertEquals(globalValue - splitValue, stateData.get("amount"));


        sealCheckTrace(contract, true);
    }

    @Test
    public void createFromBinaryWithRealContract() throws Exception {
        String fileName = "./src/test_contracts/simple_root_contract.yml";

        Contract c = Contract.fromYamlFile(fileName);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        fileName = "./src/test_contracts/binaryContract.unc";

        FileOutputStream stream = new FileOutputStream(fileName);
        try {
            stream.write(c.seal());
        } finally {
            stream.close();
        }

        readContract(fileName);
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

    @Test
    public void createFromSealedWithRealContractData() throws Exception {
        String fileName = "./src/test_contracts/subscription_with_data.yml";

        Contract c = Contract.fromYamlFile(fileName);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        // Contract from seal
        byte[] seal = c.seal();
        Contract sealedContract = new Contract(seal);
        sealedContract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(sealedContract, true);
    }

    private Contract readContract(String fileName) throws Exception {
        Contract contract = null;

        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);

        try {
            contract = new Contract(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertNotEquals(contract, null);
        return contract;
    }

    @Test
    public void getPath() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.seal();
        assertNull(c.get("state.data.hello.world"));
        assertAlmostSame(ZonedDateTime.now(), c.get("definition.created_at"));
        assertAlmostSame(ZonedDateTime.now(), c.get("state.created_at"));
        assertEquals(c.getId(), c.get("id"));
        assertEquals(c.getId(), c.get("state.origin"));
        assertEquals(c.getId(), c.get("definition.origin"));
        assertEquals("access certificate", c.get("definition.data.type"));
    }

    @Test
    public void serializeToBinder() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        Binder b = BossBiMapper.serialize(c);
        Yaml yaml = new Yaml();
        Contract c1 = DefaultBiMapper.deserialize(b);
//        System.out.println(yaml.dump(b));
//        System.out.println(yaml.dump(c1.serializeToBinder()));
        assertProperSimpleRootContract(c1);
        Contract c2 = c.copy();
        assertProperSimpleRootContract(c2);
    }

    @Test
    public void checkCreatingRootContract() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        boolean ok = c.check();
        assertFalse(ok);
        List<ErrorRecord> errors = c.getErrors();
        // It is just ok but not signed
        assertEquals(1, errors.size());
        assertEquals(errors.get(0).getError(), Errors.NOT_SIGNED);

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        ok = c.check();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (ErrorRecord e : errors) {
                System.out.println(e);
                fail("errors in contract");
            }
        }
        assertTrue(c.check());
        Files.write(Paths.get(rootPath + "simple_root_contract.unc"), c.seal());
        Yaml yaml = new Yaml();
        Files.write(Paths.get(rootPath + "simple_root_contract.raw.yaml"),
                yaml.dump(DefaultBiMapper.serialize(c)).getBytes()
        );
    }

    @Test
    public void checkSealingRootContract() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        byte[] sealed = c.seal();
//        Bytes.dump(sealed);
//        System.out.println(sealed.length);
        Contract c2 = new Contract(sealed);
        assertProperSimpleRootContract(c2);

        boolean ok = c2.check();
        List<ErrorRecord> errors = c2.getErrors();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (ErrorRecord e : errors) {
                System.out.println(e);
                fail("errors in contract");
            }
        }
        assertTrue(c.check());
    }

    @Test
    public void shouldFindWithValidReference() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "references/subscriptionReference.yml");
        Contract c2 = Contract.fromYamlFile(rootPath + "references/subscriptionRoot.yml");

        List<Contract> contracts = c2.extractByValidReference(Arrays.asList(c));
        assertNotNull(contracts);
        assertEquals(1, contracts.size());
    }

    @Test
    public void shouldFindWithValidReferenceSeal() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "references/subscriptionReference.yml");
        Contract c2 = Contract.fromYamlFile(rootPath + "references/subscriptionRoot.yml");

        c = new Contract(c.seal());
        c2 = new Contract(c2.seal());

        List<Contract> contracts = c2.extractByValidReference(Arrays.asList(c));
        assertNotNull(contracts);
        assertEquals(1, contracts.size());
    }

    @Test
    public void testRoleFailures() throws Exception {
        final PrivateKey key = new PrivateKey(2048);
        final PublicKey publicKey = key.getPublicKey();

        {
            // Missing issuer
            final Contract c = new Contract(key);
            c.getRoles().remove("issuer");
            c.seal();

            c.check();
            assertFalse(c.isOk());

            assertNull(c.getIssuer());
            assertFalse(c.getOwner().isValid());
            assertFalse(c.getCreator().isValid());
        }
        {
            // Missing creator
            final Contract c = new Contract(key);
            c.getRoles().remove("creator");
            c.seal();

            c.check();
            assertFalse(c.isOk());

            assertTrue(c.getIssuer().isValid());
            assertTrue(c.getOwner().isValid());
            assertNull(c.getCreator());
        }
        {
            // Missing owner
            final Contract c = new Contract(key);
            c.getRoles().remove("owner");
            c.seal();

            c.check();
            assertFalse(c.isOk());

            assertTrue(c.getIssuer().isValid());
            assertNull(c.getOwner());
            assertTrue(c.getCreator().isValid());
        }
        {
            // Test chain of links (good), then test breaking it (bad).
            // issuer:key, creator->owner, owner->issuer
            final Contract c = new Contract(key);
            c.getRoles().remove("creator");
            c.registerRole(new RoleLink("creator", "owner"));
            c.seal();

            c.check();
            assertTrue(c.isOk());

            assertTrue(c.getIssuer().isValid());
            assertTrue(c.getOwner().isValid());
            assertTrue(c.getCreator().isValid());

            // Let's break the link in the middle
            c.getRoles().remove("owner");

            // We haven't called `check` once again, so it's still ok
            assertTrue(c.isOk());

            c.check(); // and now it is no more ok.
            assertFalse(c.isOk());

            assertTrue(c.getIssuer().isValid());
            assertNull(c.getOwner());
            assertFalse(c.getCreator().isValid());
            assertFalse(c.getErrors().isEmpty());
        }

        {
            // Test loop of links (bad).
            // issuer->creator, creator->owner, owner->issuer
            final Contract c = new Contract(key);
            c.getRoles().clear();
            c.registerRole(new RoleLink("issuer", "creator"));
            c.registerRole(new RoleLink("creator", "owner"));
            c.registerRole(new RoleLink("owner", "issuer"));
            c.seal();

            c.check(); // and now it is no more ok.
            assertFalse(c.isOk());

            assertFalse(c.getIssuer().isValid());
            assertFalse(c.getOwner().isValid());
            assertFalse(c.getCreator().isValid());
        }
    }

    @Test
    public void loadBadFile() throws Exception {
//        byte[] packed = Do.read(rootPath + "/testcoin5.unicon");
//        Contract c = new Contract(packed);
    }
}
