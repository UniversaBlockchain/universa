package com.icodici.universa.node2;



import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Arrays.asList;
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
        int wantedSum = Quantiser.PRICE_APPLICABLE_PERM +
                Quantiser.PRICE_CHECK_2048_SIG +
                Quantiser.PRICE_CHECK_4096_SIG +
                Quantiser.PRICE_CHECK_VERSION +
                Quantiser.PRICE_REGISTER_VERSION +
                Quantiser.PRICE_REVOKE_VERSION +
                Quantiser.PRICE_SPLITJOIN_PERM;
        try {
            QuantiserSingleton.getInstance().resetQuantiser(wantedSum);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_APPLICABLE_PERM);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_CHECK_2048_SIG);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_CHECK_4096_SIG);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_CHECK_VERSION);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_REGISTER_VERSION);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_REVOKE_VERSION);
            QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_SPLITJOIN_PERM);
            assertEquals(wantedSum, QuantiserSingleton.getInstance().getQuantiser().getQuantaSum()); // must throw QuantiserException
        } catch (Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void noLimit() throws Exception {
        try {
            QuantiserSingleton.getInstance().resetQuantiserNoLimit();
            for (int i = 0; i < 1000000; ++i)
                QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_REGISTER_VERSION);
        } catch(Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void limit() throws Exception {
        try {
            QuantiserSingleton.getInstance().resetQuantiser(1000);
            for (int i = 0; i < 1000000; ++i)
                QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_REGISTER_VERSION);
            fail();
        } catch(Quantiser.QuantiserException e) {
            return;
        }
    }



    private int randInt(int min, int max) {
        return min + (int)(Math.random()*(max-min)+1);
    }



    @Test
    public void concurrency() throws Exception {
        AtomicInteger assertsCounter = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        class Work implements Runnable  {
            public Work(int workNum) {
                workNum_ = workNum;
            }

            @Override
            public void run() {
                //System.out.println("work.run(n="+workNum_+")...");
                int wantedIterations = randInt(10, 30);
                QuantiserSingleton.getInstance().resetQuantiser(wantedIterations*Quantiser.PRICE_CHECK_4096_SIG);
                for (int i = 0; i < wantedIterations; ++i) {
                    try {
                        QuantiserSingleton.getInstance().getQuantiser().addWorkCost(Quantiser.PRICE_CHECK_4096_SIG);
                    } catch (Quantiser.QuantiserException e) {
                        assertsCounter.incrementAndGet();
                    }
                    try{Thread.sleep(randInt(10, 50));}catch(Exception e){}
                }
                if (wantedIterations*Quantiser.PRICE_CHECK_4096_SIG != QuantiserSingleton.getInstance().getQuantiser().getQuantaSum())
                    assertsCounter.incrementAndGet();
                //System.out.println("work.run(n="+workNum_+")... done!");
                fail();
            }

            private int workNum_ = 0;
        };
        int N = 20;
        for (int i = 0; i < N; ++i) {
            executor.submit(new Work(i));
        }
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(executor.isTerminated());
        assertEquals(0, assertsCounter.get());
    }

}
