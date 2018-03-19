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

public class RunnableWithDynamicPeriod implements Runnable {

    private Runnable lambda;
    private List<Integer> periods;
    private int waitsCount = 0;
    private ScheduledFuture<?> future;
    private ScheduledExecutorService es;

    public RunnableWithDynamicPeriod(Runnable lambda, List<Integer> periods, ScheduledExecutorService es) {
        this.lambda = lambda;
        this.periods = periods;
        this.es = es;
    }

    @Override
    public void run() {
        if (waitsCount > 0)
            lambda.run();
        int l = periods.get(periods.size()-1);
        if (waitsCount < periods.size()-1)
            l = periods.get(waitsCount);
        future = es.schedule(this, l, TimeUnit.MILLISECONDS);
        waitsCount += 1;
    }

    public void cancel() {
        if (future != null)
            future.cancel(true);
    }

}
