/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.tools;

public class StopWatch {

    private long startedAt;

    public StopWatch() {
        start();
    }

    public void start() {
        startedAt = System.currentTimeMillis();
    }

    public long stop() {
        return System.currentTimeMillis() - startedAt;
    }

    static public long measure(Do.Action action) throws Exception {
        return measure(false, action);
    }

    static public long measure(boolean report, Do.Action action) throws Exception {
        StopWatch w = new StopWatch();
        action.perform();
        long ms = w.stop();
        if(report) {
            System.out.println("Elapsed time: "+ms+"ms");
        }
        return ms;
    }


}
