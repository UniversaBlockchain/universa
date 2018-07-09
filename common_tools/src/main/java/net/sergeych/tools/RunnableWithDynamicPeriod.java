/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Leonid Novikov <flint.emerald@gmail.com>
 */

package net.sergeych.tools;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RunnableWithDynamicPeriod implements Runnable {

    private Runnable lambda;
    private List<Integer> periods;
    private int waitsCount = 0;
    private ScheduledFuture<?> future;
    private ScheduledExecutorService es;
    private AtomicBoolean cancelled = new AtomicBoolean(false);

    public RunnableWithDynamicPeriod(Runnable lambda, List<Integer> periods, ScheduledExecutorService es) {
        this.lambda = lambda;
        this.periods = periods;
        this.es = es;
    }

    @Override
    public void run() {
        int waitsCountWas = waitsCount;
        synchronized (cancelled) {
            if (!cancelled.get()) {
                int l = periods.get(periods.size() - 1);
                if (waitsCount < periods.size() - 1)
                    l = periods.get(waitsCount);
                waitsCount += 1;
                future = es.schedule(this, l, TimeUnit.MILLISECONDS);
            }
        }
        if (!cancelled.get()) {
            if (waitsCountWas > 0)
                lambda.run();
        }
    }

    public void cancel(boolean b) {
        synchronized (cancelled) {
            cancelled.set(true);
        }
    }

    public void restart() {
        waitsCount = 0;
        if (future != null)
            future.cancel(true);
        run();
    }

}
