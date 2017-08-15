/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matcher.*;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

public class NetworkTest extends NodeTestCase {

    protected List<LocalNode> allNodes = new ArrayList<>();
    private LocalNode specialNode;

    @Override
    protected LocalNode createLocalConsensus() throws IOException, SQLException {
        network = new Network();
        LocalNode node = createTempNode(network);
        for (int i = 0; i < 8; i++)
            allNodes.add(createTempNode(network));
        specialNode = createTempNode(network);
        allNodes.add(specialNode);
        network.setNegativeConsensus(4);
        network.setPositiveConsensus(7);
        network.setMaxElectionsTime(Duration.ofSeconds(10));
        ledger = node.getLedger();
        return node;
    }

    @Test
    public void checkSetup() throws Exception {
        createLocalConsensus();
        assertEquals(10, network.getAllNodes().size());
        assertThat(network.getPositiveConsensus(), is(greaterThan(5)));
        assertThat(network.getNegativeConsensus(), is(lessThan(5)));
        assertTrue(network.hasQuorum());
    }

    @Test
    public void simpleApprove() throws Exception {
        LocalNode node = createLocalConsensus();
        for( int r=0; r<3; r++) {
            long repetitions = 10;
            long t = StopWatch.measure(() -> {
                for (int i = 0; i < repetitions; i++) {
//        LogPrinter.showDebug(true);
                    TestItem good = new TestItem(true);
                    ItemResult itemResult = node.registerItemAndWait(good);
//        checkStrangeError(good, itemResult);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                }
            });
            double tt = t / (double) repetitions;
//            System.out.println("Transaction time: " + tt + "ms, transactions per second " + (1000.0 / tt));
        }
    }

    @Test
    public void testNotCreatingOnReject() throws Exception {
        LocalNode n = createLocalConsensus();

        TestItem main = new TestItem(false);
        TestItem new1 = new TestItem(true);
        TestItem new2 = new TestItem(true);
        main.addNewItems(new1, new2);
        assertEquals(2, main.getNewItems().size());

        ItemResult itemResult;
        itemResult = n.registerItemAndWait(main);
        assertEquals(ItemState.DECLINED, itemResult.state);
        StateRecord record = n.getLedger().getRecord(new1.getId());
        assertNull(record);
        record = n.getLedger().getRecord(new2.getId());
        assertNull(record);
    }


    @Test
    public void testNodeMustDownloadItemBeforeApproval() throws Exception {
        LocalNode n = createLocalConsensus();

        for (int i = 0; i < 10; i++) {
            TestItem main = new TestItem(true);
            TestItem new1 = new TestItem(true);
            TestItem new2 = new TestItem(true);
            main.addNewItems(new1, new2);
            assertEquals(2, main.getNewItems().size());

            specialNode.emulateLateDownload();

            ItemResult itemResult;
            itemResult = n.registerItemAndWait(main);
            checkStrangeError(main, itemResult);

            assertEquals(ItemState.APPROVED, itemResult.state);
            StateRecord record = n.getLedger().getRecord(new1.getId());
            assertEquals(ItemState.APPROVED, record.getState());
            record = n.getLedger().getRecord(new2.getId());
            assertEquals(ItemState.APPROVED, record.getState());

            for (LocalNode n2 : allNodes) {
                itemResult = n2.registerItemAndWait(main);
                checkStrangeError(main, itemResult);
                // show info about the node
                // it may happen before after the consensus notes stop announsement?
                assertEquals(ItemState.APPROVED, itemResult.state);
                record = n2.getLedger().getRecord(new1.getId());
                assertNotNull(record);
                assertEquals(ItemState.APPROVED, record.getState());
                record = n2.getLedger().getRecord(new2.getId());
                assertNotNull(record);
                assertEquals(ItemState.APPROVED, record.getState());
            }
        }
    }

    @Test
    public void testCreatingItems() throws Exception {
        LocalNode n = createLocalConsensus();


        for (int i = 0; i < 10; i++) {
            TestItem main = new TestItem(true);
            TestItem new1 = new TestItem(true);
            TestItem new2 = new TestItem(true);
            main.addNewItems(new1, new2);
            assertEquals(2, main.getNewItems().size());

//        LogPrinter.showDebug(true);
            ItemResult itemResult;
            itemResult = n.registerItemAndWait(main);
            checkStrangeError(main, itemResult);
            assertEquals(ItemState.APPROVED, itemResult.state);
            StateRecord record = n.getLedger().getRecord(new1.getId());
            assertEquals(ItemState.APPROVED, record.getState());
            record = n.getLedger().getRecord(new2.getId());
            assertEquals(ItemState.APPROVED, record.getState());

            for (LocalNode n2 : allNodes) {
                itemResult = n2.registerItemAndWait(main);
                checkStrangeError(main, itemResult);
                // show info about the node
                // it may happen before after the consensus notes stop announsement?
                assertEquals(ItemState.APPROVED, itemResult.state);
                record = n2.getLedger().getRecord(new1.getId());
                assertEquals(ItemState.APPROVED, record.getState());
                record = n2.getLedger().getRecord(new2.getId());
                assertEquals(ItemState.APPROVED, record.getState());
            }
        }
    }

    private void checkStrangeError(Approvable good, ItemResult itemResult) {
        if (itemResult.state != ItemState.APPROVED) {
            System.out.println("Strange fail: " + good.getId() + " / " + good.getId().toBase64String());
            System.out.println("           >> " + Do.list(good.getId().getDigest()));

        }

    }


}
