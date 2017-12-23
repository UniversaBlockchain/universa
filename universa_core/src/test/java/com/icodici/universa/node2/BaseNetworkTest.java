/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Leonid Novikov <nil182@mail.ru>
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.TransactionContract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class BaseNetworkTest extends TestCase {

    protected static final String ROOT_PATH = "./src/test_contracts/";
    protected static final String CONFIG_2_PATH = "./src/test_config_2/";

    protected Network network = null;
    protected Node node = null;
    protected List<Node> nodes = null;
    protected Ledger ledger = null;
    protected Config config = null;



    public void init(Node node, List<Node> nodes, Network network, Ledger ledger, Config config) throws Exception {
        this.node = node;
        this.nodes = nodes;
        this.network = network;
        this.ledger = ledger;
        this.config = config;
    }



    @Test
    public void registerGoodItem() throws Exception {
        TestItem ok = new TestItem(true);
        node.registerItem(ok);
        ItemResult r = node.waitItem(ok.getId(), 100);
        assertEquals(ItemState.APPROVED, r.state);
    }



    @Test
    public void registerBadItem() throws Exception {
        TestItem bad = new TestItem(false);
        node.registerItem(bad);
        ItemResult r = node.waitItem(bad.getId(), 2000);
        assertEquals(ItemState.DECLINED, r.state);
    }



    @Test
    public void checkItem() throws Exception {
        TestItem ok = new TestItem(true);
        TestItem bad = new TestItem(false);
        node.registerItem(ok);
        node.registerItem(bad);
        node.waitItem(ok.getId(), 2000);
        node.waitItem(bad.getId(), 2000);
        assertEquals(ItemState.APPROVED, node.checkItem(ok.getId()).state);
        assertEquals(ItemState.DECLINED, node.checkItem(bad.getId()).state);
    }



    @Test
    public void shouldCreateItems() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);
    }



    @Test
    public void shouldDeclineItems() throws Exception {
        TestItem item = new TestItem(false);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);
        result = node.waitItem(item.getId(), 1000);
        assertEquals(ItemState.DECLINED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.DECLINED, result.state);
    }



    @Test
    public void singleNodeMixApprovedAndDeclined() throws Exception {
        TestItem item = new TestItem(true);

        node.registerItem(item);
        ItemResult result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.checkItem(item.getId());
        assertEquals(ItemState.APPROVED, result.state);


        // Negative consensus
        TestItem item2 = new TestItem(false);

        node.registerItem(item2);
        ItemResult result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);
        result2 = node.waitItem(item2.getId(), 2000);
        assertEquals(ItemState.DECLINED, result2.state);

        result2 = node.checkItem(item2.getId());
        assertEquals(ItemState.DECLINED, result2.state);
    }



    @Test
    public void timeoutError() throws Exception {
        Duration savedMaxElectionsTime = config.getMaxElectionsTime();

        config.setMaxElectionsTime(Duration.ofMillis(200));

        TestItem item = new TestItem(true);

        // We start elections but no node in the network know the source, so it
        // will short-circuit to self and then stop by the timeout:

        ItemResult itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        assertFalse(itemResult.haveCopy);
        assertNull(itemResult.createdAt);
        assertNull(itemResult.expiresAt);

        itemResult = node.waitItem(item.getId(), 100);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        itemResult = node.checkItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        config.setMaxElectionsTime(savedMaxElectionsTime);
    }



    @Test
    public void testNotCreatingOnReject() throws Exception {
        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 500);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test
    public void rejectBadNewItem() throws Exception {
        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(false);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        node.registerItem(main);
        ItemResult itemResult = node.waitItem(main.getId(), 2000);

        assertEquals(ItemState.DECLINED, itemResult.state);

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertEquals(ItemState.UNDEFINED, itemNew1.state);

        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertEquals(ItemState.UNDEFINED, itemNew2.state);
    }



    @Test
    public void badNewDocumentsPreventAccepting() throws Exception {
        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we run the day for teh output document:
        node.registerItem(new2);

        main.addNewItems(new1, new2);

        assertEquals(2, main.getNewItems().size());

        @NonNull ItemResult item = node.checkItem(main.getId());
        assertEquals(ItemState.UNDEFINED, item.state);

        node.registerItem(main);

        ItemResult itemResult = node.checkItem(main.getId());
        assertThat(itemResult.state, anyOf(equalTo(ItemState.PENDING), equalTo(ItemState.PENDING_NEGATIVE), equalTo(ItemState.DECLINED)));

        @NonNull ItemResult itemNew1 = node.checkItem(new1.getId());
        assertThat(itemNew1.state, anyOf(equalTo(ItemState.UNDEFINED), equalTo(ItemState.LOCKED_FOR_CREATION)));

        // and this one was created before
        @NonNull ItemResult itemNew2 = node.checkItem(new2.getId());
        assertThat(itemNew2.state, anyOf(equalTo(ItemState.APPROVED), equalTo(ItemState.PENDING_POSITIVE)));
    }



    @Test
    public void acceptWithReferences() throws Exception {
        return;
    }



    @Test(timeout = 30000)
    public void badReferencesDeclineListStates() throws Exception {

//        LogPrinter.showDebug(true);

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");
            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();

            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addReferencedItems(existing1.getId(), existing2.getId());

            // check that main is fully approved
            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 2000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            Thread.sleep(500);

            // and the references are intact

            while(ItemState.APPROVED != existing1.reload().getState()) {
                Thread.sleep(500);
                System.out.println(existing1.getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.reload().getState()) {
                Thread.sleep(500);
                System.out.println(existing2.getState());
            }
            assertEquals(badState, existing2.getState());
        }
    }



    @Test
    public void badReferencesDecline() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);


        TestItem existing1 = new TestItem(false);
        TestItem existing2 = new TestItem(true);

        System.out.println("--------resister (bad) item " + existing1.getId() + " ---------");
        node.registerItem(existing1);

        System.out.println("--------resister (good) item " + existing2.getId() + " ---------");
        node.registerItem(existing2);

        main.addReferencedItems(existing1.getId(), existing2.getId());
        main.addNewItems(new1, new2);

        System.out.println("--------resister (main) item " + main.getId() + " ---------");

        // check that main is fully approved
        node.registerItem(main);

        ItemResult itemResult = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        assertEquals(ItemState.UNDEFINED, node.checkItem(new1.getId()).state);
        assertEquals(ItemState.UNDEFINED, node.checkItem(new2.getId()).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.checkItem(existing1.getId()).state);
        if (node.checkItem(existing2.getId()).state.isPending())
            Thread.sleep(500);
        assertEquals(ItemState.APPROVED, node.checkItem(existing2.getId()).state);

//        LogPrinter.showDebug(false);
    }



    @Test
    public void missingReferencesDecline() throws Exception {

//        LogPrinter.showDebug(true);

        TestItem main = new TestItem(true);

        TestItem existing = new TestItem(true);
        node.registerItem(existing);
        @NonNull ItemResult existingItem = node.waitItem(existing.getId(), 2000);

        // but second is missing
        HashId missingId = HashId.createRandom();

        main.addReferencedItems(existing.getId(), missingId);

        // check that main is declined
        System.out.println("--------- missind id: " + missingId);
        System.out.println("--------- existing id: " + existing.getId());
        node.registerItem(main);
        // need some time to resync missingId
        ItemResult itemResult = node.waitItem(main.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existingItem.state);

        System.out.println(node.getItem(missingId));

        assertNull(node.getItem(missingId));
    }



    // this test can't be executed in a network as it needs setup in all 3 ledgers
//    @Test
//    public void approveAndRevoke() throws Exception {
//        TestItem main = new TestItem(true);
//
//        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
//        existing1.setState(ItemState.APPROVED).save();
//        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
//        existing2.setState(ItemState.APPROVED).save();
//
//        main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));
//
//        // check that main is fully approved
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 100);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//
//        // and the references are intact
//        assertEquals(ItemState.REVOKED, node.checkItem(existing1.getId()).state);
//        assertEquals(ItemState.REVOKED, node.checkItem(existing2.getId()).state);
//    }
//



    @Test(timeout = 15000)
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {

//        LogPrinter.showDebug(true);

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {

            System.out.println("-------------- check bad state " + badState + " isConsensusFind(" + badState.isConsensusFound() + ") --------");

            Thread.sleep(200);
            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            node.registerItem(main);
            ItemResult itemResult = node.waitItem(main.getId(), 2000);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            while (ItemState.APPROVED != existing1.reload().getState()) {
                Thread.sleep(100);
                System.out.println(existing1.getState());
            }
            assertEquals(ItemState.APPROVED, existing1.getState());

            while (badState != existing2.reload().getState()) {
                Thread.sleep(100);
                System.out.println(existing2.getState());
            }
            assertEquals(badState, existing2.getState());

        }
    }



    @Test
    public void shouldDeclineSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 50
        c = c.createRevision();
        Contract c2 = c.splitValue("amount", new Decimal(550));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertFalse(c2.check());
        c2.seal();
        assertEquals(new Decimal(-450), c.getStateData().get("amount"));

        registerAndCheckDeclined(c2);
    }



//    @Test
//    public void checkSergeychCase() throws Exception {
//    String transactionName = "./src/test_contracts/transaction/e00b7488-9a8f-461f-96f6-177c6272efa0.transaction";
//
//        for( int i=0; i < 5; i++) {
//            Contract contract = readContract(transactionName, true);
//
//            HashId id;
//            StateRecord record;
//
//            for (Approvable c : contract.getRevokingItems()) {
//                id = c.getId();
//                record = ledger.findOrCreate(id);
//                record.setState(ItemState.APPROVED).save();
//            }
//
//            for( Approvable c: contract.getNewItems()) {
//                record = ledger.getRecord(c.getId());
//                if( record != null )
//                    record.destroy();
//            }
//
//            StateRecord r = ledger.getRecord(contract.getId());
//            if( r !=  null ) {
//                r.destroy();
//            }
//
//            contract.check();
//            contract.traceErrors();
//            assertTrue(contract.isOk());
//
//            @NonNull ItemResult ir = node.registerItem(contract);
////            System.out.println("-- "+ir);
//            ItemResult itemResult = node.waitItem(contract.getId(), 15000);
//            if( ItemState.APPROVED != itemResult.state)
//                fail("Wrong state on repetition "+i+": "+itemResult+", "+itemResult.errors);
//            assertEquals(ItemState.APPROVED, itemResult.state);
//        }
//    }



    @Test
    public void shouldApproveSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);

        // 50
        c = c.createRevision();
        Contract c2 = c.splitValue("amount", new Decimal(50));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c2.check());
        c2.seal();
        assertEquals(new Decimal(50), c.getStateData().get("amount"));

        registerAndCheckApproved(c2);
        assertEquals("50", c2.getStateData().get("amount"));
    }



    @Test
    public void shouldBreakByQuantizer() throws Exception {
        // 100
        Contract.setTestQuantaLimit(10);
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c.check());
        c.seal();

        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }



    @Test
    public void shouldBreakByQuantizerSplit() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);


        Contract.setTestQuantaLimit(60);
        // 50
        Contract forSplit = c.createRevision();
        Contract c2 = forSplit.splitValue("amount", new Decimal(30));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
//        assertTrue(c2.check());
        c2.seal();
        forSplit.seal();
        assertEquals(new Decimal(30), new Decimal(Long.valueOf(c2.getStateData().get("amount").toString())));
        assertEquals(new Decimal(70), forSplit.getStateData().get("amount"));

        node.registerItem(forSplit);
        ItemResult itemResult = node.waitItem(forSplit.getId(), 1500);
        System.out.println(itemResult);
        Contract.setTestQuantaLimit(-1);

        assertEquals(ItemState.UNDEFINED, itemResult.state);
    }



    @Test
    public void shouldApproveSplitAndJoinWithNewSend() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();


        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));


        // 50
        Contract cRev = c.createRevision();
        Contract c2 = cRev.splitValue("amount", new Decimal(50));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c2.check());
        c2.seal();
        assertEquals(new Decimal(50), cRev.getStateData().get("amount"));

        registerAndCheckApproved(c2);
        assertEquals("50", c2.getStateData().get("amount"));


        //send 150 out of 2 contracts (100 + 50)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", (new Decimal((Integer)c.getStateData().get("amount"))).
                add(new Decimal(Integer.valueOf((String)c3.getStateData().get("amount")))));
        c3.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c3.addRevokingItems(c);
        assertTrue(c3.check());
        c3.seal();

        registerAndCheckApproved(c3);
        assertEquals(new Decimal(150), c3.getStateData().get("amount"));
    }



    @Test
    public void shouldDeclineSplitAndJoinWithWrongAmount() throws Exception {
        // 100
        Contract c = Contract.fromDslFile(ROOT_PATH + "coin100.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
        assertEquals(100, c.getStateData().get("amount"));


        // 50
        Contract cRev = c.createRevision();
        Contract c2 = cRev.splitValue("amount", new Decimal(50));
        c2.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c2.check());
        c2.seal();
        assertEquals(new Decimal(50), cRev.getStateData().get("amount"));

        registerAndCheckApproved(c2);
        assertEquals("50", c2.getStateData().get("amount"));


        //wrong. send 500 out of 2 contracts (100 + 50)
        Contract c3 = c2.createRevision();
        c3.getStateData().set("amount", new Decimal(500));
        c3.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        c3.addRevokingItems(c);
        assertFalse(c3.check());
        c3.seal();

        registerAndCheckDeclined(c3);
    }



    private void registerAndCheckApproved(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }



    private void registerAndCheckDeclined(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);
    }



    @Test
    public void itemsCachedThenPurged() throws Exception {

        // todo: rewrite
//        config.setMaxElectionsTime(Duration.ofMillis(100));
//
//        TestItem main = new TestItem(true);
//        main.setExpiresAtPlusFive(false);
//
//        node.registerItem(main);
//        ItemResult itemResult = node.waitItem(main.getId(), 1500);
//        assertEquals(ItemState.APPROVED, itemResult.state);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
//
//        assertEquals(main, node.getItem(main.getId()));
//        Thread.sleep(500);
//        assertEquals(ItemState.UNDEFINED, node.checkItem(main.getId()).state);
    }



    @Test
    public void createRealContract() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH +"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        registerAndCheckApproved(c);
    }


    @Test
    public void swapContractsViaTransactionAllGood() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));

        // register swapped contracts using TransactionContract
        System.out.println("--- register swapped contracts using TransactionContract ---");

        List<Contract> swappedContracts;

        // first Marty create transaction, add own contract and point to Stepa as swapper
        TransactionContract transaction_step_1 = new TransactionContract();
        transaction_step_1.setIssuer(martyPrivateKey.getPublicKey(), stepaPrivateKey.getPublicKey());
        transaction_step_1.addForSwap(delorean, martyPrivateKey, stepaPrivateKey.getPublicKey());

        // then Marty send draft transaction to Stepa
        // and Stepa add own contract and point to Marty as swapper
        TransactionContract transaction_step_2 = imitateSendingContractToPartner(transaction_step_1, manufacturePrivateKey);
        transaction_step_2.addForSwap(lamborghini, stepaPrivateKey, martyPrivateKey.getPublicKey());
        swappedContracts = (List<Contract>) transaction_step_2.getNew();

        // then Stepa send draft transaction back to Marty
        // and Marty sign it
        TransactionContract transaction_step_3 = imitateSendingContractToPartner(transaction_step_2, manufacturePrivateKey);
        transaction_step_3.addSignerKey(martyPrivateKey);

        // then Marty send final draft transaction to Stepa
        // and Stepa sign it too and send to approving
        TransactionContract transaction_step_4 = imitateSendingContractToPartner(transaction_step_3, manufacturePrivateKey);
        transaction_step_4.addSignerKey(stepaPrivateKey);

        transaction_step_4.seal();
        transaction_step_4.check();
        transaction_step_4.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + transaction_step_4.check());
        registerAndCheckApproved(transaction_step_4);

        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");
        Contract newDelorean = swappedContracts.get(0);
        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        Contract newLamborgini = swappedContracts.get(1);
        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
    }


    @Test
    public void swapContractsViaTransactionAllGood_2() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));

        // register swapped contracts using TransactionContract
        System.out.println("--- register swapped contracts using TransactionContract ---");

        List<Contract> swappedContracts;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        TransactionContract transactionContract = new TransactionContract();
        transactionContract.setIssuer(manufacturePrivateKey);
        swappedContracts = TransactionContract.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        TransactionPack tp_step_1 = imitateSendingTransactionToPartner(transactionContract, swappedContracts);
        transactionContract = new TransactionContract(tp_step_1.getContract().getLastSealedBinary(), tp_step_1);
        swappedContracts = new ArrayList<>(tp_step_1.getReferences().values());
        for (Contract c : swappedContracts) {
            if (c.getOrigin().equals(delorean.getId())) {
                c.addRevokingItems(delorean);
            }
            if (c.getOrigin().equals(lamborghini.getId())) {
                c.addRevokingItems(lamborghini);
            }
            c.clearContext();
        }
        TransactionContract.signPresentedSwap(swappedContracts, stepaPrivateKey);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        TransactionPack tp_step_2 = imitateSendingTransactionToPartner(transactionContract, swappedContracts);
        transactionContract = new TransactionContract(tp_step_2.getContract().getLastSealedBinary(), tp_step_1);
        swappedContracts = new ArrayList<>(tp_step_2.getReferences().values());
        for (Contract c : swappedContracts) {
            if (c.getOrigin().equals(delorean.getId())) {
                c.addRevokingItems(delorean);
            }
            if (c.getOrigin().equals(lamborghini.getId())) {
                c.addRevokingItems(lamborghini);
            }
            c.clearContext();
        }
        TransactionContract.finishSwap(swappedContracts, martyPrivateKey);

        for (Contract c : swappedContracts) {
            if(c.getOrigin().equals(delorean.getId())) {
                c.addRevokingItems(delorean);
            }
            if(c.getOrigin().equals(lamborghini.getId())) {
                c.addRevokingItems(lamborghini);
            }
            c.clearContext();
            transactionContract.addNewItems(c);
            for (Contract rev : c.getRevoking()) {
                transactionContract.addRevokingItems(rev);
            }
        }
        transactionContract.addSignerKey(manufacturePrivateKey);
        transactionContract.seal();
        transactionContract.check();
        transactionContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + transactionContract.check());
        registerAndCheckApproved(transactionContract);

        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");
        Contract newDelorean = swappedContracts.get(0);
        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        Contract newLamborgini = swappedContracts.get(1);
        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
    }


    @Test
    public void swapContractsViaTransactionOneNotSign() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));

        // register swapped contracts using TransactionContract
        System.out.println("--- register swapped contracts using TransactionContract ---");


        List<Contract> swappedContracts;

        // first Marty create transaction, add own contract and point to Stepa as swapper
        TransactionContract transaction_step_1 = new TransactionContract();
        transaction_step_1.setIssuer(martyPrivateKey.getPublicKey(), stepaPrivateKey.getPublicKey());
        transaction_step_1.addForSwap(delorean, martyPrivateKey, stepaPrivateKey.getPublicKey());

        // then Marty send draft transaction to Stepa
        // and Stepa add own contract and point to Marty as swapper
        TransactionContract transaction_step_2 = imitateSendingContractToPartner(transaction_step_1, manufacturePrivateKey);
        transaction_step_2.addForSwap(lamborghini, stepaPrivateKey, martyPrivateKey.getPublicKey());
        swappedContracts = (List<Contract>) transaction_step_2.getNew();

        // then Stepa send draft transaction back to Marty
        // and Marty sign it
        TransactionContract transaction_step_3 = imitateSendingContractToPartner(transaction_step_2, manufacturePrivateKey);
        transaction_step_3.addSignerKey(martyPrivateKey);

        // then Marty send final draft transaction to Stepa
        // but Stepa do not sign it!
        TransactionContract transaction_step_4 = imitateSendingContractToPartner(transaction_step_3, manufacturePrivateKey);
//        transaction_step_4.addSignerKey(stepaPrivateKey);

        transaction_step_4.seal();
        transaction_step_4.check();
        transaction_step_4.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + transaction_step_4.check());
        registerAndCheckDeclined(transaction_step_4);

        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");
        Contract newDelorean = swappedContracts.get(0);
        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.UNDEFINED, deloreanResult.state);

        Contract newLamborgini = swappedContracts.get(1);
        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
    }


    @Test
    public void swapContractsViaTransactionOneBadSign() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));

        // register swapped contracts using TransactionContract
        System.out.println("--- register swapped contracts using TransactionContract ---");


        List<Contract> swappedContracts;

        // first Marty create transaction, add own contract and point to Stepa as swapper
        TransactionContract transaction_step_1 = new TransactionContract();
        transaction_step_1.setIssuer(martyPrivateKey.getPublicKey(), stepaPrivateKey.getPublicKey());
        transaction_step_1.addForSwap(delorean, martyPrivateKey, stepaPrivateKey.getPublicKey());

        // then Marty send draft transaction to Stepa
        // and Stepa add own contract and point to Marty as swapper
        TransactionContract transaction_step_2 = imitateSendingContractToPartner(transaction_step_1, manufacturePrivateKey);
        transaction_step_2.addForSwap(lamborghini, stepaPrivateKey, martyPrivateKey.getPublicKey());
        swappedContracts = (List<Contract>) transaction_step_2.getNew();

        // then Stepa send draft transaction back to Marty
        // and Marty sign it
        TransactionContract transaction_step_3 = imitateSendingContractToPartner(transaction_step_2, manufacturePrivateKey);
        transaction_step_3.addSignerKey(martyPrivateKey);

        // then Marty send final draft transaction to Stepa
        // but it signed by third party!
        TransactionContract transaction_step_4 = imitateSendingContractToPartner(transaction_step_3, manufacturePrivateKey);
//        transaction_step_4.addSignerKey(stepaPrivateKey);
        transaction_step_4.addSignerKey(manufacturePrivateKey);

        transaction_step_4.seal();
        transaction_step_4.check();
        transaction_step_4.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + transaction_step_4.check());
        registerAndCheckDeclined(transaction_step_4);

        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.APPROVED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");
        Contract newDelorean = swappedContracts.get(0);
        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.UNDEFINED, deloreanResult.state);

        Contract newLamborgini = swappedContracts.get(1);
        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
    }


    @Test
    public void swapContractsViaTransactionSnatch() throws Exception {

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));

        // register swapped contracts using TransactionContract
        System.out.println("--- register swapped contracts using TransactionContract ---");

        List<Contract> swappedContracts;

        // first Marty create transaction, add own contract and point to Stepa as swapper
        TransactionContract transaction_step_1 = new TransactionContract();
        transaction_step_1.setIssuer(martyPrivateKey.getPublicKey(), stepaPrivateKey.getPublicKey());
        transaction_step_1.addForSwap(delorean, martyPrivateKey, stepaPrivateKey.getPublicKey());

        // then Marty send draft transaction to Stepa
        // and Stepa add own contract and point to Marty as swapper
        TransactionContract transaction_step_2 = imitateSendingContractToPartner(transaction_step_1, manufacturePrivateKey);
        transaction_step_2.addForSwap(lamborghini, stepaPrivateKey, martyPrivateKey.getPublicKey());
        swappedContracts = (List<Contract>) transaction_step_2.getNew();

        // then Stepa send draft transaction back to Marty
        // and Marty sign it
        TransactionContract transaction_step_3 = imitateSendingContractToPartner(transaction_step_2, manufacturePrivateKey);
        transaction_step_3.addSignerKey(martyPrivateKey);

        // then Marty send final draft transaction to Stepa
        // and Stepa don't sign it, just get both cars
        System.out.println("--- now steal the car ---");
        TransactionContract transaction_step_4 = imitateSendingContractToPartner(transaction_step_3, manufacturePrivateKey);
        //delorean = transaction_step_3.hackerGetContract(0);
        delorean = swappedContracts.get(0);
        System.out.println("delorean.check(): " + delorean.check());
        System.out.println("lamborghini.check(): " + lamborghini.check());
        registerAndCheckApproved(delorean);
        registerAndCheckApproved(lamborghini);
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        System.out.println("DeLorean ownership is belongs to Stepa: " + delorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        System.out.println("Lamborghini ownership is belongs to Marty: " + lamborghini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("delorean.state: " + deloreanResult.state);
        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("lamborghini.state: " + lamborghiniResult.state);
        assertEquals(false, delorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()) && lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.DECLINED, deloreanResult.state);
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
    }


    public TransactionContract imitateSendingContractToPartner(TransactionContract sendingContract, PrivateKey manufacturePrivateKey) throws Exception {

        System.out.println("--- imitate sending to partner ---");
        sendingContract.addSignerKey(manufacturePrivateKey);
        sendingContract.seal();

        TransactionPack tp_before = sendingContract.getTransactionPack();
//        tp_before.trace();
        byte[] data = tp_before.pack();

//        Thread.sleep(1000);

        TransactionPack tp_after  = TransactionPack.unpack(data);
        TransactionContract gotContract = new TransactionContract(tp_after.getContract().getLastSealedBinary(), tp_after);

        // Add missing parents and resetContext
        for (Approvable c : gotContract.getNewItems()) {

            boolean parentExist = false;
            for (Approvable c2 : ((Contract) c).getRevokingItems()) {
                if(((Contract) c2).getId().equals(((Contract) c).getParent())) {
                    parentExist = true;
                }
            }

            if(!parentExist) {
                for (Approvable c3 : gotContract.getRevokingItems()) {
                    if(((Contract) c3).getId().equals(((Contract) c).getParent())) {
                        ((Contract) c).addRevokingItems(((Contract) c3));
                    }
                }
            }
            ((Contract) c).clearContext();
        }

        return gotContract;
    }

    public TransactionPack imitateSendingTransactionToPartner(TransactionContract mainContract, List<Contract> contracts) throws Exception {

        mainContract.seal();

        TransactionPack tp = mainContract.getTransactionPack();
        for (Contract c : contracts) {

            tp.addReference(c);
        }
        byte[] data = tp.pack();

        return TransactionPack.unpack(data);
    }

}
