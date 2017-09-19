/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class ExtendedSignatureTest extends TestCase{

    @Test
    public void sign() throws Exception {
        byte[] data = "Hello world".getBytes();
        PrivateKey k = TestKeys.privateKey(3);
        byte [] signature = ExtendedSignature.sign(k, data);
        PublicKey pubKey = k.getPublicKey();
        ExtendedSignature es = ExtendedSignature.verify(pubKey, signature, data);
        assertNotNull(es);
        assertAlmostSame(es.getCreatedAt(), LocalDateTime.now());
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.keyId(pubKey));
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.extractKeyId(signature));
    }

    @Test
    public void parallelExecutionSign() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        ExecutorService multiple = Executors.newCachedThreadPool();

        byte[] data = "Hello world".getBytes();
        PrivateKey k = new PrivateKey(2048);

        // warm up
        for(int i=0; i<200; i++)
            ExtendedSignature.sign(k, data);

        double t1 = parallelize(single, 1, ()-> {
            for(int i=0; i<100; i++)
                ExtendedSignature.sign(k, data);
        });
        System.out.println(t1);
        double t2 = parallelize(multiple, 4, ()-> {
            for(int i=0; i<100; i++)
                ExtendedSignature.sign(k, data);
        });
        System.out.println(t2);
        assertThat(Math.abs(t1-t2), is(lessThan(0.15)));
    }

    @Test
    public void parallelExecutionVerify() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        ExecutorService multiple = Executors.newCachedThreadPool();

        byte[] data = "Hello world".getBytes();
        PrivateKey k = new PrivateKey(2048);
        PublicKey key = k.getPublicKey();
        byte[] signature = ExtendedSignature.sign(k, data);

        // warm up
        for(int i=0; i<200; i++)
            ExtendedSignature.sign(k, data);

        double t1 = parallelize(single, 1, ()-> {
            for(int i=0; i<1000; i++)
                ExtendedSignature.verify(key, signature, data);
        });
        System.out.println(t1);
        double t2 = parallelize(multiple, 4, ()-> {
            for(int i=0; i<1000; i++)
                ExtendedSignature.verify(key, signature, data);
        });
        System.out.println(t2);
        assertThat(Math.abs(t1-t2), is(lessThan(0.15)));
    }

    double parallelize(ExecutorService es,int nThreads,Runnable r) throws ExecutionException, InterruptedException {
        long t = System.nanoTime();
        ArrayList<Future<?>> all = new ArrayList<>();
        for( int i=0; i < nThreads; i++ ) {
            all.add( es.submit(()->r.run()));
        }
        for(Future<?> f: all)
            f.get();
        return (System.nanoTime() - t) * 1e-9;
    }
}