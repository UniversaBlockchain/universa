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
            Quantiser q = new Quantiser();
            q.reset(wantedSum);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_2048_SIG);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_REFERENCED_VERSION);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REVOKE_VERSION);
            q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_SPLITJOIN_PERM);
            assertEquals(wantedSum, q.getQuantaSum());
        } catch (Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void noLimit() throws Exception {
        try {
            byte[] hashBytes = new byte[128];
            new Random().nextBytes(hashBytes);
            Quantiser q = new Quantiser();
            q.resetNoLimit();
            for (int i = 0; i < 1000000; ++i)
                q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
        } catch(Quantiser.QuantiserException e) {
            fail();
        }
    }



    @Test
    public void limit() throws Exception {
        byte[] hashBytes = new byte[128];
        new Random().nextBytes(hashBytes);
        try {
            Quantiser q = new Quantiser();
            q.reset(1000);
            for (int i = 0; i < 1000000; ++i)
                q.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            fail();
        } catch(Quantiser.QuantiserException e) {
            return;
        }
    }

}
