package com.icodici.universa;

import java.util.HashMap;
import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class NodeExecutorService extends ScheduledThreadPoolExecutor {

    HashMap<ScheduledFuture<?>, String> commands = new HashMap<>();

    public NodeExecutorService(int corePoolSize) {
        super(corePoolSize);
    }

    public String tracePools() {
        String s = "";

        for(Runnable r : getQueue()) {
            s += " \n" + commands.get(r);
        }

        return s;
    }



//    /**
//     * @throws RejectedExecutionException {@inheritDoc}
//     * @throws NullPointerException       {@inheritDoc}
//     */
//    public ScheduledFuture<?> schedule(Runnable command,
//                                       long delay,
//                                       TimeUnit unit) {
//        ScheduledFuture<?> ret = super.schedule(command, delay, unit);
//        commands.put(ret, command);
//        return ret;
//    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public Future<?> submit(Runnable task, String name) {
        ScheduledFuture<?> ret = super.schedule(task, 0, NANOSECONDS);
        commands.put(ret, name);

        return ret;
    }


    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit,
                                                  String name) {
        ScheduledFuture<?> ret = super.scheduleAtFixedRate(command, initialDelay, period, unit);
        commands.put(ret, name);

        return ret;
    }
}
