package net.sergeych.tools;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class RunnableWithDynamicPeriodTest {

    private ScheduledThreadPoolExecutor executorService = new ScheduledThreadPoolExecutor(4);
    private long time;


    @Test
    public void testPeriods() throws Exception {
        executorService.submit(() -> System.out.println("warm up executor"));
        Thread.sleep(1000);

        List<Integer> periods = Arrays.asList(100,100,100,200,400,800,1600,3200,6000);

        time = System.nanoTime();
        AtomicInteger iTick = new AtomicInteger(0);
        AtomicInteger errorsCount = new AtomicInteger(0);

        RunnableWithDynamicPeriod r = new RunnableWithDynamicPeriod(() -> {
            long t1 = System.nanoTime();
            long dt = (t1 - time) / 1000000;
            time = t1;
            long dt0 = periods.get(Math.min(iTick.get(), periods.size()-1));
            System.out.println("tick: " + dt + "ms, must be " + dt0 + "ms");
            if (!(Math.abs(dt0 - dt) < dt0/10))
                errorsCount.incrementAndGet();
            iTick.incrementAndGet();
        }, periods, executorService);
        r.run();

        Thread.sleep(30000);
        System.out.println("executorService.size: " + executorService.getQueue().size());
        Assert.assertEquals(1, executorService.getQueue().size());
        r.cancel(true);
        Thread.sleep(1000);
        System.out.println("executorService.size: " + executorService.getQueue().size());
        Assert.assertEquals(0, executorService.getQueue().size());
        Assert.assertEquals(0, errorsCount.get());
    }

}
