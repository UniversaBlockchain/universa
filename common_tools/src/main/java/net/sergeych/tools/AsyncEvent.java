/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;


/**
 * Asynchronous consumer-based event. Uses cached pool to invoke consumers. All the methods are thread-safe.
 *
 * @param <T> type of the parameter passed to the consumers
 */
public class AsyncEvent<T> {
    private T result;
    private boolean fired = false;
    private Object mutex = new Object();
    private List<Consumer<T>> consumers = new ArrayList<>();

    static private ExecutorService pool = Executors.newCachedThreadPool();

    /**
     * Non blocking add consumer to the event. Returns immediately. If the event is already fired, the consumer will be
     * called immediately and asynchronously (by the pooled thread). Thread safe.
     *
     * @param consumer
     *
     * @return this instance allowing calls chain
     */
    public AsyncEvent<T> addConsumer(Consumer<T> consumer) {
        synchronized (mutex) {
            if (fired)
                pool.execute(() -> consumer.accept(result));
            else
                consumers.add(consumer);
        }
        return this;
    }

    /**
     * Fire the event if it was not yet fired. Non blocking functions. The consumers are being called in the pooled
     * threads. Does nothing if already fired. Thread-safe.
     *
     * @param result to pass to consumers
     */
    public void fire(T result) {
        synchronized (mutex) {
            this.result = result;
            fired = true;
            for (Consumer<T> consumer : consumers)
                pool.execute(() -> consumer.accept(result));
            consumers.clear();
            mutex.notifyAll();
        }
    }

    /**
     * @return true if the event is already fired.
     */
    public boolean isFired() {
        return fired;
    }

    /**
     * Wait until the event is fired. If it is already fired, returns immediately the result passed to the {@link
     * #fire(Object)} call. Same as {@link #await()}
     *
     * @return the result passed to the {@link #fire(Object)}
     *
     * @throws InterruptedException
     */
    @Deprecated
    public T waitFired() throws InterruptedException {
        return await();
    }


    /**
     * Wait until the event is fired. If it is already fired, returns immediately the result passed to the {@link
     * #fire(Object)} call.
     *
     * @return the result passed to the {@link #fire(Object)}
     *
     * @throws InterruptedException
     */
    public T await() throws InterruptedException {
        try {
            return await(0);
        } catch (TimeoutException e) {
            throw new RuntimeException("impossible: timeout can't be expired with 0 wait time");
        }
    }

    /**
     * Wait until the event is fired as much as specified number of milliseconds. If it is already fired, returns
     * immediately the result passed to the {@link #fire(Object)} call. Throws {@link TimeoutException} if the event is
     * not fired in time.
     *
     * @param milliseconds maximum number of milliseconds to wait for the event to be fired. 0 value waits forever.
     *
     * @return the result passed to the {@link #fire(Object)}
     *
     * @throws InterruptedException
     * @throws TimeoutException     if the event was not fired during the specified time
     */
    public T await(long milliseconds) throws TimeoutException, InterruptedException {
        synchronized (mutex) {
            if (!fired) {
                mutex.wait(milliseconds);
                if (!fired)
                    throw new TimeoutException();
            }
        }
        return result;
    }

    public final void fire() {
        fire(null);
    }
}
