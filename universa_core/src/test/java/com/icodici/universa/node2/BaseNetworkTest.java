/*
 * Copyright (c) 2017, All Rights Reserved
 *
 * Written by Leonid Novikov <nil182@mail.ru>
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Test;

import java.time.Duration;
import java.util.*;
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
        ItemResult r = node.waitItem(bad.getId(), 3000);
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
        ItemResult result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);

        result = node.waitItem(item.getId(), 2000);
        assertEquals(ItemState.APPROVED, result.state);
        result = node.waitItem(item.getId(), 2000);
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

        ItemResult itemResult = node.waitItem(main.getId(), 2500);

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
            ItemResult itemResult = node.waitItem(main.getId(), 3000);
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

        assertEquals(ItemState.UNDEFINED, node.waitItem(new1.getId(), 3000).state);
        assertEquals(ItemState.UNDEFINED, node.waitItem(new2.getId(), 3000).state);

        // and the references are intact
        assertEquals(ItemState.DECLINED, node.waitItem(existing1.getId(), 3000).state);
        assertEquals(ItemState.APPROVED, node.waitItem(existing2.getId(), 3000).state);

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
        ItemResult itemResult = node.waitItem(c.getId(), 8000);
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

        Set<PrivateKey> martyPrivateKey = new HashSet<>();
        martyPrivateKey.add(new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey")));
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        for (PrivateKey pk : martyPrivateKey) {
            martyPublicKeys.add(pk.getPublicKey());
        }
        Set<PrivateKey> stepaPrivateKey = new HashSet<>();
        stepaPrivateKey.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKey) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().isAllowedForKeys(martyMcflyRole.getKeys()));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        registerAndCheckApproved(lamborghini);
        Role stepanMamontovRole = lamborghini.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().isAllowedForKeys(stepanMamontovRole.getKeys()));

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPublicKeys);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKey);

        // then Stepa send draft transaction back to Marty
        // and Marty sign Stepa's new contract and send to approving
        swapContract = imitateSendingTransactionToPartner(swapContract);
        ContractsService.finishSwap(swapContract, martyPrivateKey);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
        registerAndCheckApproved(swapContract);

        List<Contract> swappingNewContracts = (List<Contract>) swapContract.getNew();

        // check old revisions for ownership contracts
        System.out.println("--- check old revisions for ownership contracts ---");

        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.REVOKED, deloreanResult.state);

        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.REVOKED, lamborghiniResult.state);

        // check new revisions for ownership contracts
        System.out.println("--- check new revisions for ownership contracts ---");
        Contract newDelorean = swappingNewContracts.get(0);
        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().isAllowedForKeys(stepanMamontovRole.getKeys()));
        assertEquals(ItemState.APPROVED, deloreanResult.state);
        assertTrue(newDelorean.getOwner().isAllowedForKeys(stepanMamontovRole.getKeys()));

        Contract newLamborgini = swappingNewContracts.get(1);
        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().isAllowedForKeys(martyMcflyRole.getKeys()));
        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
        assertTrue(newLamborgini.getOwner().isAllowedForKeys(martyMcflyRole.getKeys()));
    }

//
//    @Test
//    public void swapContractsViaTransactionOneNotSign1() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(0).getSealedByKeys().remove(martyPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneNotSign2() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(0).getSealedByKeys().remove(stepaPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneNotSign3() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(1).getSealedByKeys().remove(martyPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneNotSign4() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(1).getSealedByKeys().remove(stepaPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneWrongSign1() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        // WRONG SIGN!
//        ContractsService.finishSwap__WrongSignTest(swappingNewContracts, martyPrivateKey, manufacturePrivateKey);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneWrongSign2() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap__WrongSignTest(swappingNewContracts, stepaPrivateKey, manufacturePrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(1).getSealedByKeys().remove(stepaPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionOneWrongSign3() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        Contract swapContract = new Contract();
//        swappingNewContracts = ContractsService.startSwap__WrongSignTest(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey(), manufacturePrivateKey);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        // erase one sign
//        swappingNewContracts.get(1).getSealedByKeys().remove(stepaPrivateKey.getPublicKey());
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts, should be not revoked
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts, should be not approved
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    //@Test
//    public void swapContractsViaTransactionOneWrongSign4() throws Exception {
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionWrongTID1() throws Exception {
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        // at this step Stepa try to compromise transactional_id
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        swappingNewContracts.get(0).getTransactional().setId(HashId.createRandom().toBase64String());
//        swappingNewContracts.get(0).seal();
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts
//        System.out.println("--- check old revisions for ownership contracts ---");
//
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts
//        System.out.println("--- check new revisions for ownership contracts ---");
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionWrongTID2() throws Exception {
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        // at this step Marty try to compromise transactional_id
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        swappingNewContracts.get(1).getTransactional().setId(HashId.createRandom().toBase64String());
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts
//        System.out.println("--- check old revisions for ownership contracts ---");
//
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts
//        System.out.println("--- check new revisions for ownership contracts ---");
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionMissingTransactional1() throws Exception {
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        //swappingNewContracts.get(1).getTransactional().setId(HashId.createRandom().toBase64String());
//        System.out.println(swappingNewContracts.get(0).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(1).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(0).getReferencedItems().iterator().next().transactional_id);
//        System.out.println(swappingNewContracts.get(1).getReferencedItems().iterator().next().transactional_id);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//
//        // erase both of transactional_id
//        swappingNewContracts.get(0).getTransactional().setId("");
//        swappingNewContracts.get(1).getTransactional().setId("");
//        swappingNewContracts.get(0).getReferencedItems().iterator().next().transactional_id = "";
//        swappingNewContracts.get(1).getReferencedItems().iterator().next().transactional_id = "";
//
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts
//        System.out.println("--- check old revisions for ownership contracts ---");
//
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts
//        System.out.println("--- check new revisions for ownership contracts ---");
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionMissingTransactional2() throws Exception {
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        //swappingNewContracts.get(1).getTransactional().setId(HashId.createRandom().toBase64String());
//        System.out.println(swappingNewContracts.get(0).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(1).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(0).getReferencedItems().iterator().next().transactional_id);
//        System.out.println(swappingNewContracts.get(1).getReferencedItems().iterator().next().transactional_id);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//
//        // set both of transactional_id to null
//        swappingNewContracts.get(0).getTransactional().setId(null);
//        swappingNewContracts.get(1).getTransactional().setId(null);
//        swappingNewContracts.get(0).getReferencedItems().iterator().next().transactional_id = null;
//        swappingNewContracts.get(1).getReferencedItems().iterator().next().transactional_id = null;
//
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts
//        System.out.println("--- check old revisions for ownership contracts ---");
//
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts
//        System.out.println("--- check new revisions for ownership contracts ---");
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionWrongCID() throws Exception {
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//        ContractsService.finishSwap(swappingNewContracts, martyPrivateKey);
//
//        //swappingNewContracts.get(1).getTransactional().setId(HashId.createRandom().toBase64String());
//        System.out.println(swappingNewContracts.get(0).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(1).getTransactional().getId());
//        System.out.println(swappingNewContracts.get(0).getReferencedItems().iterator().next().transactional_id);
//        System.out.println(swappingNewContracts.get(1).getReferencedItems().iterator().next().transactional_id);
//
//        for (Contract c : swappingNewContracts) {
//            swapContract.addNewItems(c);
//        }
//        for (Contract c : swappingRevokingContracts) {
//            swapContract.addRevokingItems(c);
//        }
//        swapContract.addSignerKey(manufacturePrivateKey);
//        swapContract.seal();
//
//        // set wrong reference.contract_id for second contract
//        swappingNewContracts.get(0).getReferencedItems().iterator().next().contract_id = HashId.createRandom();
//
//        swapContract.check();
//        swapContract.traceErrors();
//        System.out.println("Transaction contract for swapping is valid: " + swapContract.check());
//        registerAndCheckDeclined(swapContract);
//
//        // check old revisions for ownership contracts
//        System.out.println("--- check old revisions for ownership contracts ---");
//
//        ItemResult deloreanResult = node.waitItem(delorean.getId(), 5000);
//        System.out.println("DeLorean revoked ownership contract revision " + delorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean revoked ownership was belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.APPROVED, deloreanResult.state);
//
//        ItemResult lamborghiniResult = node.waitItem(lamborghini.getId(), 5000);
//        System.out.println("Lamborghini revoked ownership contract revision " + lamborghini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini revoked ownership was belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.APPROVED, lamborghiniResult.state);
//
//        // check new revisions for ownership contracts
//        System.out.println("--- check new revisions for ownership contracts ---");
//        Contract newDelorean = swappingNewContracts.get(0);
//        deloreanResult = node.waitItem(newDelorean.getId(), 5000);
//        System.out.println("DeLorean ownership contract revision " + newDelorean.getRevision() + " is " + deloreanResult + " by Network");
//        System.out.println("DeLorean ownership is now belongs to Stepa: " + newDelorean.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, deloreanResult.state);
//
//        Contract newLamborgini = swappingNewContracts.get(1);
//        lamborghiniResult = node.waitItem(newLamborgini.getId(), 5000);
//        System.out.println("Lamborghini ownership contract revision " + newLamborgini.getRevision() + " is " + lamborghiniResult + " by Network");
//        System.out.println("Lamborghini ownership is now belongs to Marty: " + newLamborgini.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//        assertEquals(ItemState.UNDEFINED, lamborghiniResult.state);
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionSnatch1() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//
//        // stepa stole new revisions of contarcts and try to register it as single
//        swappingNewContracts.get(0).check();
//        swappingNewContracts.get(0).traceErrors();
//        registerAndCheckDeclined(swappingNewContracts.get(0));
//
//        swappingNewContracts.get(1).check();
//        swappingNewContracts.get(1).traceErrors();
//        registerAndCheckDeclined(swappingNewContracts.get(1));
//
//    }
//
//
//    @Test
//    public void swapContractsViaTransactionSnatch2() throws Exception {
//
//        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/marty_mcfly.private.unikey"));
//        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
//        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
//
//        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
//        delorean.addSignerKey(manufacturePrivateKey);
//        delorean.seal();
//        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
//        delorean.traceErrors();
//        registerAndCheckApproved(delorean);
//        Role martyMcflyRole = delorean.getOwner();
//        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
//
//        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
//        lamborghini.addSignerKey(manufacturePrivateKey);
//        lamborghini.seal();
//        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
//        lamborghini.traceErrors();
//        registerAndCheckApproved(lamborghini);
//        Role stepanMamontovRole = lamborghini.getOwner();
//        System.out.println("Lamborghini ownership is belongs to Stepa: " + lamborghini.getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
//
//        // register swapped contracts using ContractsService
//        System.out.println("--- register swapped contracts using ContractsService ---");
//
//        Contract swapContract;
//        List<Contract> swappingNewContracts;
//        List<Contract> swappingRevokingContracts = new ArrayList<>();
//        swappingRevokingContracts.add(delorean);
//        swappingRevokingContracts.add(lamborghini);
//        Map<Contract, List<Contract>> swapServiceResult;
//
//        // first Marty create transaction, add both contracts and swap owners, sign own new contract
//        swapServiceResult = ContractsService.startSwap(delorean, lamborghini, martyPrivateKey, stepaPrivateKey.getPublicKey());
//        swapContract = swapServiceResult.keySet().iterator().next();
//        swappingNewContracts = swapServiceResult.get(swapContract);
//
//        // then Marty send new revisions to Stepa
//        // and Stepa sign own new contract, Marty's new contract
//        List result_step_1 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_1.get(0);
//        swappingNewContracts = (List<Contract>) result_step_1.get(1);
//        ContractsService.signPresentedSwap(swappingNewContracts, stepaPrivateKey);
//
//        // then Stepa send draft transaction back to Marty
//        // and Marty sign Stepa's new contract and send to approving
//        List result_step_2 = imitateSendingTransactionToPartner(swapContract, swappingNewContracts, swappingRevokingContracts);
//        swapContract = (Contract) result_step_2.get(0);
//        swappingNewContracts = (List<Contract>) result_step_2.get(1);
//
//        // marty stole new revisions of contracts and try to register it as single
//        swappingNewContracts.get(0).check();
//        swappingNewContracts.get(0).traceErrors();
//        registerAndCheckDeclined(swappingNewContracts.get(0));
//
//        swappingNewContracts.get(1).check();
//        swappingNewContracts.get(1).traceErrors();
//        registerAndCheckDeclined(swappingNewContracts.get(1));
//
//    }

    /**
     * Imitate of sending contract from one part of swappers to another.
     *
     * Method packs sending contracts with main swap contract (can be blank - doesn't matter) into TransactionPack.
     * Then restore from packed binary main swap contract, contracts sending with.
     * And fill sent contract with oldContracts.
     * It is hook because current implementation of uTransactionPack,unpack() missing them.
     * Second hook is Contarct.clearContext() - if do not call, checking will fail. 
     *
     * @param mainContract
     * @return
     * @throws Exception
     */
    public Contract imitateSendingTransactionToPartner(Contract mainContract) throws Exception {

        TransactionPack tp_before = mainContract.getTransactionPack();
        byte[] data = tp_before.pack();

        // here we "send" data and "got" it

        TransactionPack tp_after = TransactionPack.unpack(data);
        Contract gotMainContract = tp_after.getContract();

        return gotMainContract;
    }

}
