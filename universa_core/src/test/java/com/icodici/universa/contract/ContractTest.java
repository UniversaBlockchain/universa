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
import com.icodici.universa.Decimal;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiSerializationException;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
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
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        assertProperSimpleRootContract(c);
//
//        Binder s = DefaultBiMapper.serialize(c);
//
////        Boss.trace((Object)s.getOrThrow("definition","permissions"));
//        Yaml yaml = new Yaml();
//        System.out.println(yaml.dump(s));
    }


    @Test
    public void createFromBinaryWithRealContract() throws Exception {
        String fileName = "./src/test_contracts/simple_root_contract.yml";

        Contract c = Contract.fromDslFile(fileName);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        fileName = "./src/test_contracts/binaryContract.unc";

        try(FileOutputStream stream = new FileOutputStream(fileName)) {
            stream.write(c.seal());
        }

        readContract(fileName);
    }

    @Test
    public void createFromSealedWithRealContract() throws Exception {
        String fileName = "./src/test_contracts/subscription.yml";

        Contract c = Contract.fromDslFile(fileName);
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

        Contract c = Contract.fromDslFile(fileName);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(c, true);

        // Contract from seal
        byte[] seal = c.seal();
        Contract sealedContract = new Contract(seal);
        sealedContract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(sealedContract, true);
    }

    @Test
    public void getPath() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
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
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        Binder b = BossBiMapper.serialize(c);
        Contract c1 = DefaultBiMapper.deserialize(b);
//        System.out.println(yaml.dump(b));
//        System.out.println(yaml.dump(c1.serializeToBinder()));
        assertProperSimpleRootContract(c1);
        Contract c2 = c.copy();
        assertProperSimpleRootContract(c2);
    }

    @Test
    public void checkCreatingRootContract() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
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
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
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
        Contract c = Contract.fromDslFile(rootPath + "references/subscriptionReference.yml");
        Contract c2 = Contract.fromDslFile(rootPath + "references/subscriptionRoot.yml");

        List<Contract> contracts = c2.extractByValidReference(Arrays.asList(c));
        assertNotNull(contracts);
        assertEquals(1, contracts.size());
    }

    @Test
    public void shouldFindWithValidReferenceSeal() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "references/subscriptionReference.yml");
        Contract c2 = Contract.fromDslFile(rootPath + "references/subscriptionRoot.yml");

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
    public void calculateProcessingCostSimple() throws Exception {

        // Should create contract, sign and seal it. Then calculate cost of processing.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = 28;
        Contract processingContract = processContractAsItWillBeOnTheNode(contract);

        System.out.println("Calculated processing cost: " + processingContract.getProcessedCost() + " (UTN)");

        assertEquals(costShouldBe, processingContract.getProcessedCost());
    }

    @Test
    public void calculateProcessingCostSimpleBreak() throws Exception {

        // Should create contract, sign and seal it. Then while calculating cost should break.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = 28;
        boolean exceptionThrown = false;
        try {
            processContractAsItWillBeOnTheNode(contract, 10);
        } catch (Quantiser.QuantiserException e) {
            System.out.println("Thrown correct exception: " + e.toString());
            exceptionThrown = true;
        }
        assertEquals(true, exceptionThrown);
    }

    @Test
    public void calculateProcessingCostSimpleBreakWhileUnpacking() throws Exception {

        // Should create contract, sign and seal it. Then while calculating cost should break while unpacking contract (signs verifying).
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = 28;
        boolean exceptionThrown = false;
        try {
            processContractAsItWillBeOnTheNode(contract, 1);
        } catch (Quantiser.QuantiserException e) {
            System.out.println("Thrown correct exception: " + e.getMessage());
            exceptionThrown = true;
        } catch (BiSerializationException e) {
            System.out.println("Thrown correct exception: " + e.getMessage());
            exceptionThrown = true;
        }
        assertEquals(true, exceptionThrown);
    }

    @Test
    public void calculateRevisionProcessingCost() throws Exception {

        // Should create contract, sign and seal it. Then calculate cost of processing.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        Contract forRevision = contract.createRevision();
//        forRevision.getStateData().set("amount", new Decimal(111));
        forRevision.getState().setExpiresAt(ZonedDateTime.now().plusDays(7));
        forRevision.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        forRevision.seal();
        forRevision.check();
        forRevision.traceErrors();

        // It is contract in vacuum, so signs not verifying
        // Register a version (20)
        int costShouldBeForParent = 20;

        // It is contract in vacuum, so signs not verifying
        // Register a version (20) +
        // Register revoking item a version (20) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1)
        int costShouldBeForRevision = 45;

        System.out.println("Calculated processing cost (parent): " + contract.getProcessedCost() + " (UTN)");
        System.out.println("Calculated processing cost (revision): " + forRevision.getProcessedCost() + " (UTN)");

        assertEquals(costShouldBeForParent, contract.getProcessedCost());
        assertEquals(costShouldBeForRevision, forRevision.getProcessedCost());
    }

    @Test
    public void calculateSplitProcessingCost() throws Exception {

        // Should create contract, sign and seal it, then create revision and split. Then calculate cost of processing.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        System.out.println("----- split -----");
        Contract forSplit = contract.createRevision();
        Contract splitted = forSplit.splitValue(FIELD_NAME, new Decimal(20));
        splitted.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(splitted, true);
        sealCheckTrace(forSplit, true);
        assertEquals(new Decimal(80), forSplit.getStateData().get("amount"));
        assertEquals(new Decimal(20), new Decimal(Long.valueOf(splitted.getStateData().get("amount").toString())));

        // Check 4096 bits signature own (8) +
        // Check 4096 bits signature new item (8) +
        // Check 4096 bits signature revoking item (8) +
        // Register a self version (20) +
        // Register new item a version (20) +
        // Register revoking item a version (20) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1) +
        // Check new item change owner permission (1) +
        // Check new item change split join permission (1+2) +
        // Check new item change revoke permission (1)
        int costShouldBeForSplit = 94;

        Contract processingContract = processContractAsItWillBeOnTheNode(forSplit);
        System.out.println("Calculated processing cost (forSplit): " + processingContract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBeForSplit, processingContract.getProcessedCost());


        System.out.println("----- join -----");
        Contract forJoin = splitted.createRevision();
        forJoin.getStateData().set("amount", ((Decimal)forSplit.getStateData().get("amount")).
                add(new Decimal(Integer.valueOf((String)forJoin.getStateData().get("amount")))));
        forJoin.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
        forJoin.addRevokingItems(forSplit);
        sealCheckTrace(forJoin, true);
        assertEquals(new Decimal(100), forJoin.getStateData().get("amount"));

        // Check 4096 bits signature own (8) +
        // Check 4096 bits signature revoking item (8) +
        // Check 4096 bits signature revoking item (8) +
        // Register a self version (20) +
        // Register revoking item a version (20) +
        // Register revoking item a version (20) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1)
        int costShouldBeForJoin = 89;

        processingContract = processContractAsItWillBeOnTheNode(forJoin);
        System.out.println("Calculated processing cost (forJoin): " + processingContract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBeForJoin, processingContract.getProcessedCost());
    }

    @Test
    public void calculateSplit7To2ProcessingCost() throws Exception {

        // Should create 7 contracts, sign and seal it all, then create revision and split to 2 contracts. Then calculate cost of processing.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        System.out.println("----- split -----");
        List<Contract> splittedList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            contract = contract.createRevision();
            Contract splitted = contract.splitValue(FIELD_NAME, new Decimal(15));
            splitted.addSignerKeyFromFile(PRIVATE_KEY_PATH);
            sealCheckTrace(splitted, true);
            assertEquals(new Decimal(15), new Decimal(Long.valueOf(splitted.getStateData().get("amount").toString())));
            sealCheckTrace(contract, true);
            assertEquals(new Decimal(85 - i*15), contract.getStateData().get("amount"));
            splittedList.add(splitted);
            Thread.sleep(1000);
        }


        Contract forJoin = contract.createRevision();
        Contract splittedChanges;
        for (int i = 0; i < 6; i++) {
            if(i < 5) {
                forJoin.getRevokingItems().add(splittedList.get(i));
            } else {
                Contract splitting = splittedList.get(i).createRevision();
                splittedChanges = splitting.splitValue(FIELD_NAME, new Decimal(5));
                splittedChanges.addSignerKeyFromFile(PRIVATE_KEY_PATH);
                sealCheckTrace(splittedChanges, true);
                sealCheckTrace(splitting, true);
                forJoin.getRevokingItems().add(splitting);
                forJoin.addNewItems(splittedChanges);
            }
        }
        forJoin.getStateData().set(FIELD_NAME, new Decimal(95));
        forJoin.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(forJoin, true);

        // x9 Check 4096 bits signature own (8*9=72) +
        // Register a self version (20) +
        // Register new item a version (20) +
        // x7 Register revoking item a version (20*7=140) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1) +
        // Check new item change owner permission (1) +
        // Check new item change split join permission (1+2) +
        // Check new item change revoke permission (1)
        int costShouldBeForSplit = 262;

        Contract processingContract = processContractAsItWillBeOnTheNode(forJoin);
        System.out.println("Calculated processing cost (forJoin): " + processingContract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBeForSplit, processingContract.getProcessedCost());
    }

    @Test
    public void calculateSplitProcessingCostbreakWhileUnpacking() throws Exception {

        // Should create contract, sign and seal it, then create revision and split.
        // Then while calculating cost should break while unpacking contract (signs verifying).
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        System.out.println("Split");
        Contract forSplit = contract.createRevision();
        Contract splitted = forSplit.splitValue(FIELD_NAME, new Decimal(20));
        splitted.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(splitted, true);
        sealCheckTrace(forSplit, true);
        assertEquals(new Decimal(80), forSplit.getStateData().get("amount"));
        assertEquals(new Decimal(20), new Decimal(Long.valueOf(splitted.getStateData().get("amount").toString())));

        System.out.println("Calculated processing cost (forSplit): " + forSplit.getProcessedCost() + " (UTN)");
        System.out.println("Calculated processing cost (splitted): " + splitted.getProcessedCost() + " (UTN)");

        // Check 4096 bits signature own (8) +
        // Check 4096 bits signature new item (8) +
        // Check 4096 bits signature revoking item (8) +
        // Register a self version (20) +
        // Register new item a version (20) +
        // Register revoking item a version (20) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1) +
        // Check new item change owner permission (1) +
        // Check new item change split join permission (1+2) +
        // Check new item change revoke permission (1)
        int costShouldBeForSplit = 94;

        boolean exceptionThrown = false;
        try {
            processContractAsItWillBeOnTheNode(forSplit, 20);
        } catch (Quantiser.QuantiserException e) {
            System.out.println("Thrown correct exception: " + e.getMessage());
            exceptionThrown = true;
        } catch (BiSerializationException e) {
            System.out.println("Thrown correct exception: " + e.getMessage());
            exceptionThrown = true;
        }
        assertEquals(true, exceptionThrown);
    }

    @Test
    public void calculateSplitTwiceJoinProcessingCost() throws Exception {

        // Should create contract, sign and seal it, then create revision and split twice.
        // Then join all and calculate cost of processing.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(contract, true);

        System.out.println("-------- split 1 --------");
        Contract forSplit = contract.createRevision();
        Contract splitted1 = forSplit.splitValue(FIELD_NAME, new Decimal(20));
        splitted1.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(splitted1, true);
        sealCheckTrace(forSplit, true);

        System.out.println("-------- split 2 --------");
        splitted1 = splitted1.createRevision();
        Contract splitted2 = splitted1.splitValue(FIELD_NAME, new Decimal(5));
        splitted2.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        sealCheckTrace(splitted2, true);
        sealCheckTrace(splitted1, true);

        assertEquals(new Decimal(80), forSplit.getStateData().get("amount"));
        assertEquals(new Decimal(15), new Decimal(Long.valueOf(splitted1.getStateData().get("amount").toString())));
        assertEquals(new Decimal(5), new Decimal(Long.valueOf(splitted2.getStateData().get("amount").toString())));

        System.out.println("-------- join --------");

        Contract forJoin = splitted2.createRevision();
        forJoin.getStateData().set("amount", ((Decimal)forSplit.getStateData().get("amount")).
                add((Decimal)splitted1.getStateData().get("amount")).
                add(new Decimal(Integer.valueOf((String)forJoin.getStateData().get("amount")))));
        forJoin.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
        forJoin.addRevokingItems(forSplit);
        forJoin.addRevokingItems(splitted1);
        sealCheckTrace(forJoin, true);
        assertEquals(new Decimal(100), forJoin.getStateData().get("amount"));

        // Check 4096 bits signature own (8) +
        // Check 4096 bits signature revoking item (8) +
        // Check 4096 bits signature revoking item (8) +
        // Check 4096 bits signature revoking item (8) +
        // Register a self version (20) +
        // Register revoking item a version (20) +
        // Register revoking item a version (20) +
        // Register revoking item a version (20) +
        // Check self change owner permission (1) +
        // Check self change split join permission (1+2) +
        // Check self change revoke permission (1)
        int costShouldBeForJoin = 117;
        Contract processingContract = processContractAsItWillBeOnTheNode(forJoin);
        System.out.println("Calculated processing cost (forJoin): " + processingContract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBeForJoin, processingContract.getProcessedCost());

    }

    @Test
    public void calculateLoopInNewItemsProcessingCost() throws Exception {

        // Should create contracts that has loops in newItems.
        // should repeat contract processing procedure on the Node
        // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check())

        Contract contract = createCoin100apiv3();
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c_1 = createCoin100apiv3();
        c_1.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c_2 = createCoin100apiv3();
        c_2.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        Contract c_3 = createCoin100apiv3();
        c_3.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        c_1.addNewItems(c_2);
        c_1.addNewItems(c_3);
        c_2.addNewItems(c_3);
//        c_3.addNewItems(c_1);

        sealCheckTrace(c_1, true);
        sealCheckTrace(c_2, true);
        sealCheckTrace(c_3, true);

        contract.addNewItems(c_1);
        contract.addNewItems(c_2);

        c_1.getQuantiser().resetNoLimit();
        c_2.getQuantiser().resetNoLimit();
        c_3.getQuantiser().resetNoLimit();

        System.out.println("-------");

        sealCheckTrace(contract, true);

        // Register a self version (20) +
        // Register c_1 version (20) +
        // Register c_2 version (20) +
        // Register c_2 version from c_1 (20) +
        // Register c_3 version from c_1 (20) +
        // Register c_3 version from c_2 version (20) +
        // Register c_3 version from c_2 version from c_1 (20)
        int costShouldBe = 140;
        System.out.println("Calculated processing cost (contract): " + contract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBe, contract.getProcessedCost());

        // Check 4096 bits signature own (8) +
        // Check 4096 bits signature new item (8) +
        // Check 4096 bits signature new item (8)
        // Register a self version (20) +
        // Register new item a version (20) +
        // Register new item a version (20)
        int costShouldBeAfterProcessing = 84;
        Contract processingContract = processContractAsItWillBeOnTheNode(contract);
        System.out.println("Calculated processing cost (contract): " + processingContract.getProcessedCost() + " (UTN)");
        assertEquals(costShouldBeAfterProcessing, processingContract.getProcessedCost());
    }


    @Test
    public void checkTransactional() throws Exception {

    }

    @Test
    public void checkSealedBytes() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("----");

        byte[] firstSeal = delorean.getLastSealedBinary();

        TransactionPack tp_before = delorean.getTransactionPack();
        byte[] data = tp_before.pack();
        System.out.println("----");
        TransactionPack tp_after = TransactionPack.unpack(data);
        Contract delorean2 = tp_after.getContract();

        System.out.println("----");
        delorean2.seal();

        byte[] secondSeal = delorean2.getLastSealedBinary();
        Binder data1 = Boss.unpack(firstSeal);
        byte[] contractBytes1 = data1.getBinaryOrThrow("data");

        for (Object signature : (List) data1.getOrThrow("signatures")) {
            byte[] s = ((Bytes) signature).toArray();
            System.out.println(ExtendedSignature.verify(manufacturePrivateKey.getPublicKey(), s, contractBytes1));
        }

        System.out.println("----");
        Binder data2 = Boss.unpack(secondSeal);
        byte[] contractBytes2 = data2.getBinaryOrThrow("data");

        for (Object signature : (List) data2.getOrThrow("signatures")) {
            byte[] s = ((Bytes) signature).toArray();
            System.out.println(ExtendedSignature.verify(manufacturePrivateKey.getPublicKey(), s, contractBytes2));
        }

        System.out.println("----");
        for (Object signature : (List) data1.getOrThrow("signatures")) {
            byte[] s = ((Bytes) signature).toArray();
            System.out.println(ExtendedSignature.verify(manufacturePrivateKey.getPublicKey(), s, contractBytes2));
        }
    }

    /**
     * Imitate procedure of contract processing as it will be on the Node.
     * Gte contract from param, create from it new contract,
     * that will be processed and return processed contract with cost inside.
     * @param contract - from which contract will be created contract for processing.
     * @param limit - Quantizer limit.
     * @return new contract that was processed.
     * @throws Exception
     */
    public Contract processContractAsItWillBeOnTheNode(Contract contract, int limit) throws Exception {

        Contract processingContract;
        try {
            System.out.println("------ Imitate registering -------");
            Contract.setTestQuantaLimit(limit);
            byte[] data = contract.getPackedTransaction();
            processingContract = Contract.fromPackedTransaction(data);
            System.out.println("------ final check -------");
            processingContract.check();
        } catch (Exception e) {
            throw e;
        } finally {
            Contract.setTestQuantaLimit(-1);
        }

        return processingContract;
    }

    /**
     * Imitate procedure of contract processing as it will be on the Node.
     * Gte contract from param, create from it new contract,
     * that will be processed and return processed contract with cost inside.
     * @param contract - from which contract will be created contract for processing.
     * @return new contract that was processed.
     * @throws Exception
     */
    public Contract processContractAsItWillBeOnTheNode(Contract contract) throws Exception {

        return processContractAsItWillBeOnTheNode(contract, -1);
    }

//    @Test
//    public void loadBadFile() throws Exception {
//        byte[] packed = Do.read(rootPath + "7.35jun15 (outgoing).unicon");
//        Contract c = new Contract(packed);
//        c.check();
//        c.traceErrors();
//
//    }
}
