package com.icodici.universa.node2;



import com.icodici.universa.HashId;
import com.icodici.universa.node.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class QuantiserTest extends TestCase {

    @Before
    public void setUp() throws Exception {
        //System.out.println("setUp()...");
    }


    @After
    public void tearDown() throws Exception {
        //System.out.println("tearDown()...");
    }



    @Test
    public void fullSum() throws Exception {
        int wantedSum = Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM.getCost() +
                Quantiser.QuantiserProcesses.PRICE_CHECK_2048_SIG.getCost() +
                Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost() +
                Quantiser.QuantiserProcesses.PRICE_CHECK_REFERENCED_VERSION.getCost() +
                Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION.getCost() +
                Quantiser.QuantiserProcesses.PRICE_REVOKE_VERSION.getCost() +
                Quantiser.QuantiserProcesses.PRICE_SPLITJOIN_PERM.getCost();
        try {
            byte[] hashBytes = new byte[128];
            new Random().nextBytes(hashBytes);
            HashId hashId = new HashId(hashBytes);
            QuantiserSingleton.getInstance().getQuantiser(hashId).reset(wantedSum);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_2048_SIG);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_REFERENCED_VERSION);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_REVOKE_VERSION);
            QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_SPLITJOIN_PERM);
            assertEquals(wantedSum, QuantiserSingleton.getInstance().getQuantiser(hashId).getQuantaSum());
            QuantiserSingleton.getInstance().deleteQuantiser(hashId);
            assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
        } catch (Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void noLimit() throws Exception {
        try {
            byte[] hashBytes = new byte[128];
            new Random().nextBytes(hashBytes);
            HashId hashId = new HashId(hashBytes);
            QuantiserSingleton.getInstance().getQuantiser(hashId).resetNoLimit();
            for (int i = 0; i < 1000000; ++i)
                QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            QuantiserSingleton.getInstance().deleteQuantiser(hashId);
            assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
        } catch(Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void limit() throws Exception {
        byte[] hashBytes = new byte[128];
        new Random().nextBytes(hashBytes);
        HashId hashId = new HashId(hashBytes);
        try {
            QuantiserSingleton.getInstance().getQuantiser(hashId).reset(1000);
            for (int i = 0; i < 1000000; ++i)
                QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            fail();
        } catch(Quantiser.QuantiserException e) {
            QuantiserSingleton.getInstance().deleteQuantiser(hashId);
            assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
            return;
        }
    }



    private int randInt(int min, int max) {
        return min + (int)(Math.random()*(max-min)+1);
    }



    private void concurrencySum(boolean makeDelays, int worksCount, int eachWorkIterationsCount) throws Exception {
        AtomicInteger assertsCounter = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        class Work implements Runnable  {
            public Work(int workNum) {
                workNum_ = workNum;
            }

            @Override
            public void run() {
                //System.out.println("work.run(n="+workNum_+")...");
                byte[] hashBytes = new byte[128];
                new Random().nextBytes(hashBytes);
                HashId hashId = new HashId(hashBytes);
                int wantedIterations = randInt(eachWorkIterationsCount, eachWorkIterationsCount*2);
                QuantiserSingleton.getInstance().getQuantiser(hashId).reset(wantedIterations*Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost());
                for (int i = 0; i < wantedIterations; ++i) {
                    try {
                        QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
                    } catch (Quantiser.QuantiserException e) {
                        assertsCounter.incrementAndGet();
                    }
                    if (makeDelays)
                        try{Thread.sleep(randInt(10, 50));}catch(Exception e){}
                }
                if (wantedIterations*Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost() != QuantiserSingleton.getInstance().getQuantiser(hashId).getQuantaSum())
                    assertsCounter.incrementAndGet();
                //System.out.println("work.run(n="+workNum_+")... done!");
                QuantiserSingleton.getInstance().deleteQuantiser(hashId);
            }

            private int workNum_ = 0;
        };
        int N = worksCount;
        for (int i = 0; i < N; ++i) {
            executor.submit(new Work(i));
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(executor.isTerminated());
        assertEquals(0, assertsCounter.get());
        assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
    }



    @Test
    public void concurrencySumWithoutDelays() throws Exception {
        concurrencySum(false, 20000, 1000);
    }



    @Test
    public void concurrencySumWithDelays() throws Exception {
        concurrencySum(true, 20, 15);
    }



    private void concurrencyLimit(boolean makeDelays, int worksCount, int eachWorkIterationsCount) throws Exception {
        AtomicInteger assertsCounter = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        class Work implements Runnable  {
            public Work(int workNum) {
                workNum_ = workNum;
            }

            @Override
            public void run() {
                //System.out.println("work.run(n="+workNum_+")...");
                byte[] hashBytes = new byte[128];
                new Random().nextBytes(hashBytes);
                HashId hashId = new HashId(hashBytes);
                int wantedIterations = randInt(eachWorkIterationsCount, eachWorkIterationsCount*2);
                QuantiserSingleton.getInstance().getQuantiser(hashId).reset(wantedIterations*Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost());
                for (int i = 0; i < wantedIterations+10; ++i) {
                    try {
                        QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
                        if (i >= wantedIterations)
                            assertsCounter.incrementAndGet();
                    } catch (Quantiser.QuantiserException e) {
                    }
                    if (makeDelays)
                        try{Thread.sleep(randInt(10, 50));}catch(Exception e){}
                }
                //System.out.println("work.run(n="+workNum_+")... done!");
                QuantiserSingleton.getInstance().deleteQuantiser(hashId);
            }

            private int workNum_ = 0;
        };
        int N = worksCount;
        for (int i = 0; i < N; ++i) {
            executor.submit(new Work(i));
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(executor.isTerminated());
        assertEquals(0, assertsCounter.get());
        assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
    }



    @Test
    public void concurrencyLimitWithoutDelays() throws Exception {
        concurrencyLimit(false, 20000, 1000);
    }



    @Test
    public void concurrencyLimitWithDelays() throws Exception {
        concurrencyLimit(true, 20, 15);
    }



    @Test
    public void singleQuantiserConcurrency() throws Exception {
        AtomicInteger assertsCounter = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        byte[] hashBytes = new byte[128];
        new Random().nextBytes(hashBytes);
        HashId hashId = new HashId(hashBytes);
        int N = 20000;
        int workIterations = 1000;
        QuantiserSingleton.getInstance().getQuantiser(hashId).reset(N*workIterations*Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost());

        class Work implements Runnable  {
            @Override
            public void run() {
                for (int i = 0; i < workIterations; ++i) {
                    try {
                        QuantiserSingleton.getInstance().getQuantiser(hashId).addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
                    } catch (Quantiser.QuantiserException e) {
                        assertsCounter.incrementAndGet();
                    }
                }
            }
        };

        for (int i = 0; i < N; ++i) {
            executor.submit(new Work());
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        assertEquals(N*workIterations*Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG.getCost(), QuantiserSingleton.getInstance().getQuantiser(hashId).getQuantaSum());
        QuantiserSingleton.getInstance().deleteQuantiser(hashId);
        assertEquals(0, assertsCounter.get());
        assertEquals(0, QuantiserSingleton.getInstance().getQuantiserCount());
    }

}
