/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.Main;
import com.icodici.universa.node2.Quantiser;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.utils.Base64;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

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
        tp.setContract(c);
        checkSimplePack(tp);
    }

    @Test
    public void deserializeNew() throws Exception {
        TransactionPack tp = new TransactionPack();
        tp.setContract(c);
        checkSimplePack(tp);

        assertSame(tp,c.getTransactionPack());

        byte[] packedTp = tp.pack();

        System.out.println(Base64.encodeString(packedTp));

        TransactionPack tp1 = TransactionPack.unpack(packedTp);
        checkSimplePack(tp1);

        Contract c1 = tp1.getContract();
        // it should be the same
        assertEquals(c.getId(), c1.getId());
        assertSame(tp1,c1.getTransactionPack());
//        assertSame(packedTp, c1.getPackedTransaction());

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
        assertEquals(3, tp.getReferences().size());
        assertEquals(c.getId(), tp.getContract().getId());

        Set<HashId> rids = c.getRevokingItems().stream().map(x->x.getId()).collect(Collectors.toSet());
        Set<HashId> nids = c.getNewItems().stream().map(x->x.getId()).collect(Collectors.toSet());

        assertTrue(rids.contains(r0.getId()));
        assertTrue(nids.contains(n0.getId()));
        assertTrue(nids.contains(n1.getId()));
    }


    @Test
    public void parallelTest() throws Exception {
        PrivateKey myKey = TestKeys.privateKey(3);

        List<Contract> contractsForThreads = new ArrayList<>();
        int N = 100;
        int M = 2;
        float threshold = 1.2f;
        float ratio = 0;
        boolean createNewContracts = false;

        contractsForThreads = new ArrayList<>();
        for(int j = 0; j < M; j++) {
            Contract contract = new Contract(myKey);

            for (int k = 0; k < 10; k++) {
                Contract nc = new Contract(myKey);
                nc.seal();
                contract.addNewItems(nc);
            }
            contract.seal();
            assertTrue(contract.isOk());
            contractsForThreads.add(contract);
        }

        Contract singleContract = new Contract(myKey);

        for (int k = 0; k < 10; k++) {
            Contract nc = new Contract(myKey);
            nc.seal();
            singleContract.addNewItems(nc);
        }
        singleContract.seal();

        // register

        for(int i = 0; i < N; i++) {

            if(createNewContracts) {
                contractsForThreads = new ArrayList<>();
                for(int j = 0; j < M; j++) {
                    Contract contract = new Contract(myKey);

                    for (int k = 0; k < 10; k++) {
                        Contract nc = new Contract(myKey);
                        nc.seal();
                        contract.addNewItems(nc);
                    }
                    contract.seal();
                    assertTrue(contract.isOk());
                    contractsForThreads.add(contract);


                }

                singleContract = new Contract(myKey);

                for (int k = 0; k < 10; k++) {
                    Contract nc = new Contract(myKey);
                    nc.seal();
                    singleContract.addNewItems(nc);
                }
                singleContract.seal();
            }

            long ts1;
            long ts2;
            Semaphore semaphore = new Semaphore(-(M-1));

            ts1 = new Date().getTime();

            for(Contract c : contractsForThreads) {
                Thread thread = new Thread(() -> {

                    long t = System.nanoTime();
                    TransactionPack tp_before = c.getTransactionPack();
                    byte[] data = tp_before.pack();

                    // here we "send" data and "got" it

                    TransactionPack tp_after = null;
                    try {
                        tp_after = TransactionPack.unpack(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Contract gotMainContract = tp_after.getContract();
                    System.out.println("multi thread: " + gotMainContract.getId() + " time: " + ((System.nanoTime() - t) * 1e-9));
                    semaphore.release();
                });
                thread.setName("Multi-thread register: " + c.getId().toString());
                thread.start();
            }

            semaphore.acquire();

            ts2 = new Date().getTime();

            long threadTime = ts2 - ts1;

            //

            ts1 = new Date().getTime();

            Contract finalSingleContract = singleContract;
            Thread thread = new Thread(() -> {
                long t = System.nanoTime();
                TransactionPack tp_before = finalSingleContract.getTransactionPack();
                byte[] data = tp_before.pack();

                // here we "send" data and "got" it

                TransactionPack tp_after = null;
                try {
                    tp_after = TransactionPack.unpack(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Contract gotMainContract = tp_after.getContract();
                System.out.println("multi thread: " + gotMainContract.getId() + " time: " + ((System.nanoTime() - t) * 1e-9));
                semaphore.release();
            });
            thread.setName("single-thread register: " + singleContract.getId().toString());
            thread.start();

            semaphore.acquire();

            ts2 = new Date().getTime();

            long singleTime = ts2 - ts1;

            System.out.println(threadTime * 1.0f / singleTime);
            ratio += threadTime * 1.0f / singleTime;
        }

        ratio /= N;
        System.out.println("average " + ratio);
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

        public TestContracts invoke() throws EncryptionError, Quantiser.QuantiserException {
            r0 = new Contract(TestKeys.privateKey(0));
            r0.seal();
            c = r0.createRevision(TestKeys.privateKey(0));

            n0 = new Contract(TestKeys.privateKey(0));
            n1 = new Contract(TestKeys.privateKey(0));

            c.addNewItems(n0);
            c.addNewItems(n1);
            c.seal();
            return this;
        }
    }
}