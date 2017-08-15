/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.farcall;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import net.sergeych.farcall.*;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import net.sergeych.tools.StreamConnector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 10.04.16.
 */
public class FarcallTest {

    @Test
    public void testPreRequisites() throws Exception {
        JsonObject object = Json.parse("{\"name\":\"test\"}").asObject();
        String name = object.get("name").asString();
        assertEquals("test", name);
    }

    Object lastObject;

    private void basicTest(Interconnection ic, Farcall a, Farcall b) {
        b.start(new Farcall.Target() {
            @Override
            public Object onCommand(Command command) throws Exception {
                if (command.is("ping")) {
                    return "pong";
                } else if (command.is("echoargs")) {
                    HashMap<String, Object> res = new HashMap<>();
                    res.put("simple", command.getParams());
                    res.put("key", command.getKeyParams());
                    return res;
                }
                throw new IllegalArgumentException("unknown command: " + command.getName());
            }
        });

        if (ic != null)
            ic.getConnectorA().pause(10);

        a.start();

        a.send("ping").success((Object data) -> lastObject = data).join();
        assertEquals("pong", lastObject);
        a.send("bad", null, null).failure((Object data) -> lastObject = data).join();
        assertTrue(lastObject instanceof Farcall.RemoteException);

        ArrayList<Object> array = new ArrayList<>();
        array.add(10);
        array.add(2);
        array.add(7);

        HashMap<String, Object> map = new HashMap<>();
        map.put("one", 1);
        map.put("ary", array);

        lastObject = null;

        a.send("echoargs", array, map).success((Object data) -> {
            lastObject = data;
        }).failure((err) -> {
            fail(err.toString());
        }).join();
        HashMap<String, Object> res = (HashMap<String, Object>) lastObject;
        assertEquals(2, res.size());
        assertDeepEquals(res.get("simple"), array);
    }

    public static void assertDeepEquals(Object a, Object b) {
        if (!Do.deepEqualityTest(a, b)) {
            System.out.println("Test failed: Not same");
            System.out.println("a: " + a.toString());
            System.out.println("b: " + b.toString());
            fail("deep equality check failed");
        }
    }

    @Test(timeout = 200)
    public void testBasicRPC() throws Exception {
        Interconnection ic = new Interconnection(5);
        Farcall a = new Farcall(ic.getConnectorA());
        Farcall b = new Farcall(ic.getConnectorB());
        basicTest(ic, a, b);
    }

    @Test(timeout = 200)
    public void jsonConnector() throws Exception {
        StreamConnector sa = new StreamConnector();
        StreamConnector sb = new StreamConnector();

        JsonConnector connA = new JsonConnector(sa.getInputStream(), sb.getOutputStream());
        JsonConnector connB = new JsonConnector(sb.getInputStream(), sa.getOutputStream());

        Farcall a = new Farcall(connA);
        Farcall b = new Farcall(connB);
        basicTest(null, a, b);
    }

    @Test(timeout = 200)
    public void bossConnector() throws Exception {
        StreamConnector sa = new StreamConnector();
        StreamConnector sb = new StreamConnector();

        BossConnector connA = new BossConnector(sa.getInputStream(), sb.getOutputStream());
        BossConnector connB = new BossConnector(sb.getInputStream(), sa.getOutputStream());

        Farcall a = new Farcall(connA);
        Farcall b = new Farcall(connB);
        basicTest(null, a, b);
    }

    @Test
    public void asyncMethods() throws Exception {
        Interconnection ic = new Interconnection(10);
        Farcall a = new Farcall(ic.getConnectorA());
        Farcall b = new Farcall(ic.getConnectorB());

        Object lock = new Object();
        AsyncEvent done = new AsyncEvent();
        a.asyncCommands();
        a.start(command -> {
            switch (command.getName()) {
                case "wait":
                    synchronized (lock) {
                        lock.wait();
                    }
                    return null;
                case "interrupt":
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                    return null;
            }
            return null;
        });
        b.start();

        boolean waitDone[] = new boolean[]{false};
        b.send("wait").done((data) -> {
            waitDone[0] = true;
            done.fire(null);
        });
        b.send("interrupt").waitSuccess();
        done.waitFired();
        assertTrue(waitDone[0]);
    }

    @Test
    public void asyncLoadTest() throws Exception {
        Interconnection a = new Interconnection(10);

        Farcall fa = new Farcall(a.getConnectorA());
        Farcall fb = new Farcall(a.getConnectorB());

        int [] counts = new int[2];

        fa.asyncCommands();
        fa.start(command -> {
            if (command.getName().equals("fast")) {
                counts[0]++;
                Thread.sleep(1);
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
        for( int rep=0; rep < 4; rep++ ) {
            ArrayList<Future<?>> futures = new ArrayList<>();
            counts[0] = counts[1] = 0;
            long t = StopWatch.measure(() -> {
                CompletableFuture<?> cf = new CompletableFuture<>();
                for (int r = 0; r < 200; r++) {
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
//            System.out.println(""+t+":  "+counts[0] + ", "+counts[1]);
        }
    }
}