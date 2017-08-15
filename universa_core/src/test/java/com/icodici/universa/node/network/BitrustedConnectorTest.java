/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node.network;

import net.sergeych.farcall.Farcall;
import net.sergeych.tools.Binder;
import net.sergeych.tools.StopWatch;
import net.sergeych.tools.StreamConnector;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.*;

public class BitrustedConnectorTest {
    ExecutorService pool = Executors.newCachedThreadPool();

    @Test
    public void testExceptinosOutOfExecutor() throws Exception {
        ExecutorService service = Executors.newCachedThreadPool();
        Future<Integer> future = service.submit(() -> {
            throw new IOException("just a test");
        });
        try {
            future.get();
            fail();
        }
        catch(Exception e) {
            assertThat(e.getMessage().indexOf("just a test"),is(greaterThan(0)) );
        }
    }

    @Test
    public void timoutOnConnection() throws Exception {
        StreamConnector sca = new StreamConnector();
        BitrustedConnector connector = new BitrustedConnector(
                TestKeys.privateKey(0),
                sca.getInputStream(),
                new ByteArrayOutputStream()
        );
        connector.setHandshakeTimeoutMillis(10);
        try {
            connector.connect(null);
            fail("must throw TimeoutException");
        }
        catch(TimeoutException x) {
            // all ok
        }
    }

    @Test
    public void connectOnAny() throws Exception {
        StreamConnector sca = new StreamConnector();
        StreamConnector scb = new StreamConnector();
        BitrustedConnector ca = new BitrustedConnector(
                TestKeys.privateKey(0),
                sca.getInputStream(),
                scb.getOutputStream()
        );
        BitrustedConnector cb = new BitrustedConnector(
                TestKeys.privateKey(1),
                scb.getInputStream(),
                sca.getOutputStream()
        );
        Future<Object> connectA = pool.submit(() -> {
            ca.connect(null);
            return null;
        });
//        LogPrinter.showDebug(true);
        cb.connect(null);
        assertTrue(cb.isConnected());
        connectA.get();
        assertTrue(ca.isConnected());
        assertEquals(ca.getMySessionKey(), cb.getRemoteSessionKey());
        assertEquals(cb.getMySessionKey(), ca.getRemoteSessionKey());


        Future<?> f1 = pool.submit(() -> {
            cb.send(Binder.fromKeysValues("hello", "world"));
            cb.send(Binder.fromKeysValues("hello", "world2"));
            Binder res = Binder.from(cb.receive());
            assertEquals("bar", res.getStringOrThrow("foo"));
            res = Binder.from(cb.receive());
//            System.out.println("------> res "+res);
            assertEquals("bar2", res.getStringOrThrow("foo"));
            res = Binder.from(cb.receive());
//            System.out.println("------> res "+res);
            assertEquals("bar", res.getStringOrThrow("foo3"));
            return null;
        });

        Future<?> f2 = pool.submit(() -> {
            ca.send(Binder.fromKeysValues("foo", "bar"));
            ca.send(Binder.fromKeysValues("foo", "bar2"));
            ca.send(Binder.fromKeysValues("foo3", "bar"));
            Binder res = Binder.from(ca.receive());
            assertEquals("world", res.getStringOrThrow("hello"));
            res = Binder.from(ca.receive());
            assertEquals("world2", res.getStringOrThrow("hello"));
            return null;
        });
        f1.get();
        f2.get();
//        ca.
    }

    @Test
    public void sampleSync() throws Exception {
        StreamConnector sca = new StreamConnector();
        StreamConnector scb = new StreamConnector();
        BitrustedConnector ca = new BitrustedConnector(
                TestKeys.privateKey(0),
                sca.getInputStream(),
                scb.getOutputStream()
        );
        BitrustedConnector cb = new BitrustedConnector(
                TestKeys.privateKey(1),
                scb.getInputStream(),
                sca.getOutputStream()
        );
        Future<Object> connectA = pool.submit(() -> {
            ca.connect(null);
            return null;
        });
//        LogPrinter.showDebug(true);
        cb.connect(null);
        assertTrue(cb.isConnected());
        connectA.get();
        assertTrue(ca.isConnected());

        Farcall fa = new Farcall(ca);
        Farcall fb = new Farcall(cb);

        int[] counts = new int[2];

        fa.start(command -> {
            if (command.getName().equals("fast")) {
                counts[0]++;
                return "fast done";
            } else if (command.getName().equals("slow")) {
                Thread.sleep(3);
                counts[1]++;
                return "slow done";
            }
            return null;
        });
        fb.start();
        Future<Object> f1 = pool.submit(() -> fb.send("fast").waitSuccess());
        Future<Object> f2 = pool.submit(() -> fb.send("fast").waitSuccess());
        Future<Object> f3 = pool.submit(() -> fb.send("fast").waitSuccess());
//        Future<Object> f4 = pool.submit(() -> fb.send("fast").waitSuccess());
//        Future<Object> f5 = pool.submit(() -> fb.send("fast").waitSuccess());
        f1.get();
    }

        @Test
    public void asyncLoadTest() throws Exception {
        StreamConnector sca = new StreamConnector();
        StreamConnector scb = new StreamConnector();
        BitrustedConnector ca = new BitrustedConnector(
                TestKeys.privateKey(0),
                sca.getInputStream(),
                scb.getOutputStream()
        );
        BitrustedConnector cb = new BitrustedConnector(
                TestKeys.privateKey(1),
                scb.getInputStream(),
                sca.getOutputStream()
        );
        Future<Object> connectA = pool.submit(() -> {
            ca.connect(null);
            return null;
        });
//        LogPrinter.showDebug(true);
        cb.connect(null);
        assertTrue(cb.isConnected());
        connectA.get();
        assertTrue(ca.isConnected());

        Farcall fa = new Farcall(ca);
        Farcall fb = new Farcall(cb);

        int [] counts = new int[2];

        fa.start(command -> {
            if (command.getName().equals("fast")) {
                counts[0]++;
                return "fast done";
            } else if (command.getName().equals("slow")) {
                Thread.sleep(3);
                counts[1]++;
                return "slow done";
            }
            return null;
        });
        fb.start();

        ExecutorService es = Executors.newWorkStealingPool();
//        ExecutorService es = Executors.newSingleThreadExecutor();
        ArrayList<Long> times = new ArrayList<>();
        for( int rep=0; rep < 7; rep++ ) {
            ArrayList<Future<?>> futures = new ArrayList<>();
            counts[0] = counts[1] = 0;
            long t = StopWatch.measure(() -> {
                CompletableFuture<?> cf = new CompletableFuture<>();
                for (int r = 0; r < 40; r++) {
                    futures.add(es.submit(() -> {
                        for (int i = 0; i < 10; i++)
                            assertEquals("fast done", fb.send("fast").waitSuccess());
                        return null;
                    }));
                    futures.add(es.submit(() -> {
                        for (int i = 0; i < 2; i++)
                            assertEquals("slow done", fb.send("slow").waitSuccess());
                        return null;
                    }));
                }
                futures.forEach(f -> {
                    try {
                        f.get();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                });
            });
            times.add(t);
//            System.out.println(""+t+":  "+counts[0] + ", "+counts[1]);
        }
        // the call expenses should not rise with time, and by the 3rd JIT compilation should be done
        // note this test heavily depends on jit behavior!
        long t1 = times.get(2);
        long t2 = times.get(times.size()-1);
        long mean = (t1 + t2)/2;
        assertThat((double) Math.abs(t2-t1) / ((double) mean), CoreMatchers.is(lessThan(0.20)) );
    }
}