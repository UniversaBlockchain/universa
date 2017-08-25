/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.farcall;

import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import net.sergeych.tools.StreamConnector;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

import static org.hamcrest.number.OrderingComparison.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Created by sergeych on 13.04.16.
 */
public class BossConnectorTest {
    @Test
    public void send() throws Exception {
        StreamConnector sa = new StreamConnector();
        BossConnector bsc = new BossConnector(sa.getInputStream(), sa.getOutputStream());

        bsc.send(Do.map("hello", "мыльня"));
        Map<String, Object> res = bsc.receive();
        assertEquals(1, res.size());
        assertEquals("мыльня", res.get("hello"));
    }


    @Test
    public void directLoadTest() throws Exception {
        StreamConnector sa = new StreamConnector();
        StreamConnector sb = new StreamConnector();

        Farcall fa = new Farcall(new BossConnector(sa.getInputStream(), sb.getOutputStream()));
        Farcall fb = new Farcall(new BossConnector(sb.getInputStream(), sa.getOutputStream()));

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
        assertThat((double) Math.abs(t2-t1) / ((double) mean), CoreMatchers.is(lessThan(0.16)) );
    }
}