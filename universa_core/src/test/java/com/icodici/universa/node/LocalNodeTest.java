/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class LocalNodeTest extends NodeTestCase {

    @Test
    public void registerItem() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        assertNotNull(connection);
    }

    @Test
    public void singleNodeApprove() throws Exception {
        LocalNode n = createLocalConsensus();

        ItemResult itemResult;
        TestItem item = new TestItem(true);

        ItemInfo ii = n.registerItem(item);
        assert(ii.getErrors().isEmpty());
        assertEquals(item.getId(), ii.getItemId());
        assertEquals(ItemState.PENDING_POSITIVE, ii.getItemResult().state);
        assertEquals(ItemState.PENDING_POSITIVE, ii.getItemResult().state);
        itemResult = n.registerItemAndWait(item);
        assertEquals(ItemState.APPROVED, itemResult.state);
        itemResult = n.registerItemAndWait(item);
        assertEquals(ItemState.APPROVED, itemResult.state);
        StateRecord r = n.getLedger().getRecord(item.getId());
        assertEquals(ItemState.APPROVED, r.getState());
//

        // Negative consensus
        TestItem item2 = new TestItem(false);
        itemResult = n.registerItemAndWait(item2);
        assertEquals(ItemState.DECLINED, itemResult.state);
        itemResult = n.registerItemAndWait(item2);
        assertEquals(ItemState.DECLINED, itemResult.state);
        r = n.getLedger().getRecord(item2.getId());
        assertEquals(ItemState.DECLINED, r.getState());
    }

    @Test
    public void noQourumError() throws Exception {
        Network network = new Network();
        LocalNode n = createTempNode(network);
        network.setNegativeConsensus(2);
        network.setPositiveConsensus(2);
        TestItem item = new TestItem(true);
        ItemResult itemResult = n.registerItemAndWait(item);
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        StateRecord record = n.getLedger().getRecord(item.getId());
        assertEquals(ItemState.UNDEFINED, record.getState());
        assertTrue(record.getExpiresAt().isBefore(LocalDateTime.now().plusHours(1)));

        TestItem item2 = new TestItem(false);
        itemResult = n.registerItemAndWait(item2);
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        assertEquals(ItemState.UNDEFINED, n.getLedger().getRecord(item2.getId()).getState());
    }

    @Test
    public void timeoutError() throws Exception {
        Network network = new Network();
        LocalNode n = createTempNode(network);
        network.setNegativeConsensus(1);
        network.setPositiveConsensus(1);
        network.setMaxElectionsTime(Duration.ofMillis(200));

        TestItem item = new TestItem(true);

        // We start elections but no node in the network know the source, so it
        // will short-circuit to self and then stop by the timeout:

        ItemResult itemResult = n.checkItem(null, item.getId(), ItemState.PENDING, false);
        assertEquals(ItemState.PENDING, itemResult.state);
        assertFalse(itemResult.haveCopy);
        assertAlmostSame(itemResult.createdAt, LocalDateTime.now());
        assertThat(itemResult.expiresAt, is(greaterThan(LocalDateTime.now())));

        itemResult = n.waitForItem(item.getId());
        assertEquals(ItemState.UNDEFINED, itemResult.state);
        assertEquals(ItemState.UNDEFINED, n.getLedger().getRecord(item.getId()).getState());
    }

    @Test
    public void testCreatingItems() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);
        main.addNewItems(new1, new2);
        assertEquals(2, main.getNewItems().size());

        ItemResult itemResult;
        itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.APPROVED, itemResult.state);
        StateRecord record = n.getLedger().getRecord(new1.getId());
        assertEquals(ItemState.APPROVED, record.getState());
        record = n.getLedger().getRecord(new2.getId());
        assertEquals(ItemState.APPROVED, record.getState());
    }

    @Test
    public void testNotCreatingOnReject() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);
        main.addNewItems(new1, new2);
        assertEquals(2, main.getNewItems().size());

        ItemResult itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.DECLINED, itemResult.state);
        StateRecord record = n.getLedger().getRecord(new1.getId());
        assertNull(record);
        record = n.getLedger().getRecord(new2.getId());
        assertNull(record);
    }

    @Test
    public void rejectBadNewItem() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(false);
        main.addNewItems(new1, new2);
        assertEquals(2, main.getNewItems().size());

        ItemResult itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.DECLINED, itemResult.state);
        StateRecord record = n.getLedger().getRecord(new1.getId());
        assertNull(record);
        record = n.getLedger().getRecord(new2.getId());
        assertNull(record);
    }



    @Test
    public void badNewdocumentsPreventAccepting() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        // and now we runi the day for teh output document:
        n.getLedger().findOrCreate(new2.getId());

        main.addNewItems(new1, new2);
        assertEquals(2, main.getNewItems().size());

        ItemResult itemResult;
        ItemInfo ii = n.registerItem(main);
        assertEquals(ItemState.PENDING_NEGATIVE,ii.getItemResult().state);
        assertEquals(1,ii.getErrors().size());
        assertEquals(Errors.NEW_ITEM_EXISTS, ii.getErrors().iterator().next().getError());
        itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.DECLINED, itemResult.state);
        StateRecord record = ledger.getRecord(new1.getId());
        assertNull(record);
        // and this one was created before
        record = ledger.getRecord(new2.getId());
        assertNotNull(record);
        assertEquals(ItemState.PENDING, record.getState());
    }

    @Test
    public void acceptWithReferences() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);

        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
        existing1.setState(ItemState.APPROVED).save();
        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
        existing2.setState(ItemState.LOCKED).save();

        main.addReferencedItems(existing1.getId(), existing2.getId());
        main.addNewItems(new1, new2);

        // check that main is fully approved
        ItemResult itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.APPROVED, itemResult.state);

        StateRecord record = ledger.getRecord(new1.getId());
        assertEquals(ItemState.APPROVED, record.getState());
        record = ledger.getRecord(new2.getId());
        assertEquals(ItemState.APPROVED, record.getState());

        // and the references are intact
        assertEquals(ItemState.APPROVED, existing1.reload().getState());
        assertEquals(ItemState.LOCKED, existing2.reload().getState());
    }

    @Test
    public void badReferencesDecline() throws Exception {

        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {
            LocalNode n = createLocalConsensus();

            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addReferencedItems(existing1.getId(), existing2.getId());

            // check that main is fully approved
            ItemResult itemResult = null;
            itemResult = n.registerItemAndWait(main);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            assertEquals(ItemState.APPROVED, existing1.reload().getState());
            assertEquals(badState, existing2.reload().getState());
        }
    }

    @Test
    public void missingReferencesDecline() throws Exception {

        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);

        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
        existing1.setState(ItemState.APPROVED).save();
        // but second is missing
        HashId missingId = HashId.createRandom();

        main.addReferencedItems(existing1.getId(), missingId);

        // check that main is fully approved
        ItemResult itemResult = null;
        itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.DECLINED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.APPROVED, existing1.reload().getState());
        assertNull(ledger.getRecord(missingId));
    }

    @Test
    public void approveAndRevoke() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(true);

        StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
        existing1.setState(ItemState.APPROVED).save();
        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
        existing2.setState(ItemState.APPROVED).save();


        main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

        // check that main is fully approved
        ItemResult itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // and the references are intact
        assertEquals(ItemState.REVOKED, existing1.reload().getState());
        assertEquals(ItemState.REVOKED, existing2.reload().getState());
    }


    @Test
    public void badRevokingItemsDeclineAndRemoveLock() throws Exception {
        for (ItemState badState : Arrays.asList(
                ItemState.PENDING, ItemState.PENDING_POSITIVE, ItemState.PENDING_NEGATIVE, ItemState.UNDEFINED,
                ItemState.DECLINED, ItemState.REVOKED, ItemState.LOCKED_FOR_CREATION)
                ) {
            LocalNode n = createLocalConsensus();

            TestItem main = new TestItem(true);

            StateRecord existing1 = ledger.findOrCreate(HashId.createRandom());
            existing1.setState(ItemState.APPROVED).save();
            // but second is not good
            StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
            existing2.setState(badState).save();

            main.addRevokingItems(new FakeItem(existing1), new FakeItem(existing2));

            ItemResult itemResult = n.registerItemAndWait(main);
            assertEquals(ItemState.DECLINED, itemResult.state);

            // and the references are intact
            assertEquals(ItemState.APPROVED, existing1.reload().getState());
            assertEquals(badState, existing2.reload().getState());

        }
    }

    private String rootPath = "./src/test_contracts/";


    @Test
    public void itemisCachedThenPurged() throws Exception {
        LocalNode n = createLocalConsensus();
        network.setMaxElectionsTime(Duration.ofMillis(100));

        TestItem main = new TestItem(true);
        ItemResult itemResult;
        itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.APPROVED, itemResult.state);

        assertEquals(main, n.getItem(main.getId()));
        Thread.sleep(110);
        assertNull(n.getItem(main.getId()));
    }

    @Test
    public void createRealContract() throws Exception {
        LocalNode n = createLocalConsensus();
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        assertTrue(c.check());
        c.seal();

        ItemResult itemResult = n.registerItemAndWait(c);
        assertEquals(ItemState.APPROVED, itemResult.state);



    }
}