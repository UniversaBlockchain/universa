/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.network.TestKeys;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TransactionPackTest {
    private Contract n0;
    private Contract n1;
    private Contract r0;
    private Contract c;


    @Before
    public void setUp() throws Exception {
        TestContracts testContracts = new TestContracts().invoke();
        c = testContracts.getC();
        n0 = testContracts.getN0();
        n1 = testContracts.getN1();
        r0 = testContracts.getR0();
    }

    @Test
    public void deserializeOldContract() throws Exception {
        TransactionPack tp = TransactionPack.unpack(c.sealAsV2());
        checkSimplePack(tp);
    }

    @Test
    public void serializeNew() throws Exception {
        TransactionPack tp = new TransactionPack();
        tp.addContract(c);
        checkSimplePack(tp);
    }

    @Test
    public void deserializeNew() throws Exception {
        TransactionPack tp = new TransactionPack();
        tp.addContract(c);
        checkSimplePack(tp);

        TransactionPack tp1 = TransactionPack.unpack(tp.pack());
        checkSimplePack(tp1);

        Contract c1 = tp1.getContracts().get(0);
        // it should be the same
        assertEquals(c.getId(), c1.getId());

        List<Approvable> r1 = new ArrayList<>(c.getRevokingItems());
        List<Approvable> r2 = new ArrayList<>(c1.getRevokingItems());

        assertEquals(r1.get(0).getId(), r2.get(0).getId());

        List<Approvable> n1 = new ArrayList<>(c.getNewItems());
        List<Approvable> n2 = new ArrayList<>(c1.getNewItems());

        if( n1.get(0).getId().equals(n2.get(0).getId())) {
            assertEquals(n1.get(0).getId(), n2.get(0).getId());
            assertEquals(n1.get(1).getId(), n2.get(1).getId());
        }
        else {
            assertEquals(n1.get(0).getId(), n2.get(1).getId());
            assertEquals(n1.get(1).getId(), n2.get(0).getId());
        }
    }

    @Test
    public void packedContractNotContainsOtherItems() throws Exception {
        // if we seal and load a contract without a pack, it should have empty
        // containers for new and revoking items - these should be fill separately.
        Contract c2 = new Contract(c.seal());
        assertEquals(0, c2.getRevokingItems().size());
        assertEquals(0, c2.getNewItems().size());
    }

    public void checkSimplePack(TransactionPack tp) {
        assertEquals(1, tp.getContracts().size());
        assertEquals(3, tp.getReferences().size());
        assertEquals(c.getId(), tp.getContracts().get(0).getId());

        Set<HashId> rids = c.getRevokingItems().stream().map(x->x.getId()).collect(Collectors.toSet());
        Set<HashId> nids = c.getNewItems().stream().map(x->x.getId()).collect(Collectors.toSet());

        assertTrue(rids.contains(r0.getId()));
        assertTrue(nids.contains(n0.getId()));
        assertTrue(nids.contains(n1.getId()));
    }

    private class TestContracts {
        private Contract r0;
        private Contract c;
        private Contract n0;
        private Contract n1;

        public Contract getR0() {
            return r0;
        }

        public Contract getC() {
            return c;
        }

        public Contract getN0() {
            return n0;
        }

        public Contract getN1() {
            return n1;
        }

        public TestContracts invoke() throws EncryptionError {
            r0 = new Contract(TestKeys.privateKey(0));
            r0.seal();
            c = r0.createRevision(TestKeys.privateKey(0));

            n0 = new Contract(TestKeys.privateKey(0));
            n1 = new Contract(TestKeys.privateKey(0));

            c.addNewItem(n0);
            c.addNewItem(n1);
            c.seal();
            return this;
        }
    }
}