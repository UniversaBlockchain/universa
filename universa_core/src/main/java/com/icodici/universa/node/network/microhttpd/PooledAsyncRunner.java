/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.network.microhttpd;

import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.nanohttpd.protocols.http.ClientHandler;
import org.nanohttpd.protocols.http.threading.IAsyncRunner;

import java.util.concurrent.*;

/**
 * {@link IAsyncRunner} implementation which uses some {@link ExecutorService} to launch new threads;
 * depending on the constructor arguments, it is either (unlimited) CachedThreadPool
 * or FixedThreadPool with a predefined upper limit.
 */
public class PooledAsyncRunner implements IAsyncRunner {

    private static LogPrinter log = new LogPrinter("MHTP");

    private final ExecutorService executor;

    private ConcurrentMap<ClientHandler, Future> executedFutures = new ConcurrentHashMap<>();

    /**
     * Constructor.
     */
    PooledAsyncRunner(@Nullable Integer threadLimit) {
        if (threadLimit == null) {
            executor = Executors.newCachedThreadPool();
        } else {
            executor = Executors.newFixedThreadPool(threadLimit.intValue());
        }
    }

    public static class CustomThreadFactory implements ThreadFactory{
        private static int factoriesCount = 0;
        private int factoryNumber;
        private int threadNumber = 1;

        public CustomThreadFactory() {
            factoriesCount++;
            factoryNumber = factoriesCount;

        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("microhttpd-worker-"+factoryNumber+"-"+threadNumber);
            threadNumber++;
            return thread;
        }
    }

    /**
     * Constructor.
     */
    PooledAsyncRunner(@Nullable Integer threadLimit, String poolName) {
        if (threadLimit == null) {
            executor = Executors.newCachedThreadPool();
        } else {
            executor = new ThreadPoolExecutor(threadLimit, threadLimit,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(),new CustomThreadFactory()
                    );
        }

    }

    @Override
    public void closeAll() {
        try {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.wtf("Cannot close PooledAsyncRunner", e);
        }
    }

    @Override
    public void closed(ClientHandler clientHandler) {
        assert clientHandler != null;

        executedFutures.get(clientHandler).cancel(true);
    }

    @Override
    public void exec(ClientHandler clientHandler) {
        assert clientHandler != null;

        final Future<?> future = executor.submit(clientHandler);
        executedFutures.put(clientHandler, future);
    }
}
