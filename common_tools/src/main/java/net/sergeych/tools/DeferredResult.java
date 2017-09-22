/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, July 2017.
 *
 */

package net.sergeych.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Promise-like object to wait for command answer. Is returned by the send methods and provides 3
 * optional callbacks - one for failure and success and one when operation is done anyway. Latter is
 * universal - if it is called with a throwable instance then the operation is failed.
 * <p>
 * DeferredResult is threadsafe and allows any number of handlers of any type to be added at any
 * time. Handlers added after the {@link #sendSuccess(Object)} or {@link #sendFailure(Object)},
 * could be called immediately if condition is met (success or failure) with the proper data.
 * <p>
 * Only first call to {@link #sendSuccess(Object)} or {@link #sendFailure(Object)} matters, more
 * calls are silently ignored.
 */
@SuppressWarnings("unused")
public class DeferredResult {
    private final ArrayList<Handler> successHandlers = new ArrayList<>();
    private final ArrayList<Handler> errorHandlers = new ArrayList<>();
    private final ArrayList<Handler> doneHandlers = new ArrayList<>();
    private final Object access = new Object();
    private Object result;
    private boolean success, done;

    /**
     * Adds the success callback. The callback parameter will get the value passed to the {@link
     * #sendSuccess(Object)} call. Multiple callbacks allowed; calling in order of registration.
     * <p>
     * This handler is executed before {@link #done(Handler)}.
     * <p>
     * If called when {@link #isDone()} and {@link #isSuccess()} the callback is invoked
     * immediately.
     *
     * @param handler
     *         callback
     */
    public DeferredResult success(Handler handler) {
        synchronized (successHandlers) {
            successHandlers.add(handler);
        }
        if (done && success)
            invokeSuccess();
        return this;
    }

    private <T> void invokeSuccess() {
        invoke(successHandlers);
        invoke(doneHandlers);
    }

    private void invoke(final List<Handler> list) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (list) {
            for (Handler h : list)
                h.handle(result);
            list.clear();
        }
    }

    /**
     * Adds failure callback. The parameter is what is passed to the {@link #sendFailure(Object)}
     * call. Multiple callbacks allowed; calling in order of registration.
     * <p>
     * This handler is executed before {@link #done(Handler)}.
     * <p>
     * If called when {@link #isDone()} and !{@link #isSuccess()} the callback is invoked
     * immediately.
     *
     * @param handler
     *         RemoteError instance. callback
     */
    public DeferredResult failure(Handler handler) {
        synchronized (errorHandlers) {
            errorHandlers.add(handler);
        }
        if (done && !success)
            invokeError();
        return this;
    }

    private void invokeError() {
        invoke(errorHandlers);
        invoke(doneHandlers);
    }

    /**
     * Adds the callback that will be called when the operation is done, after success()
     * or failure() respecive invocation. The operation result can be distinguished by checking
     * {@link #isSuccess()} value. Multiple callbacks allowed; calling in order of registration.
     * <p>
     * If called when {@link #isDone()} == true the callback is invoked immediately.
     *
     * @param handler
     *         callback
     */
    public DeferredResult done(Handler handler) {
        synchronized (doneHandlers) {
            doneHandlers.add(handler);
        }
        if (done)
            invoke(doneHandlers);
        return this;
    }

    /**
     * Trigger the success event and pass argument to all {@link #success(Handler)} and then
     * {@link #done(Handler)} callbacks. Can be called only once if {@link #sendFailure(Object)}
     * was not called before. Further calls to this method and to {@link #sendFailure(Object)} are
     * silently ignored.
     *
     * @param data
     *         to pass to callbacks
     */
    public void sendSuccess(Object data) {
        synchronized (access) {
            if (!done) {
                success = true;
                done = true;
                result = data;
                invokeSuccess();
                access.notifyAll();
            }
        }
    }

    /**
     * Trigger the failure event and pass argument to all {@link #failure(Handler)} and
     * to {@link #done(Handler)} callbacks. Can be called only once if {@link #sendSuccess(Object)}
     * was not called before. Further calls to this method and to {@link #sendSuccess(Object)} are
     * silently ignored.
     *
     * @param data
     *         to pass to callbacks
     */

    public void sendFailure(Object data) {
        synchronized (access) {
            if (!done) {
                success = false;
                done = true;
                result = data;
                invokeError();
                access.notifyAll();
            }
        }
    }

    /**
     * Whether the deferred result is available already.
     */
    public boolean isDone() {
        return done;
    }

    /**
     * Get the result passed with {@link #sendFailure(Object)} or {@link #sendSuccess(Object)}.
     * Check the state using {@link #isSuccess()} call.
     * <p>
     * Do not call it when {@link #isDone()} is false.
     *
     * @param <T>
     *         result value.
     *
     * @return result passed with the first {@link #sendSuccess(Object)} or {@link
     * #sendFailure(Object)} call.
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult() {
        if (!done)
            throw new IllegalStateException("result is not yet known");
        return (T) result;
    }

    /**
     * waits until the operation is finished and return it's result on success, or throws an error
     * on failure.
     *
     * @throws Error
     *         or {@link Failure} if operation fails
     */
    public <T> T waitSuccess() throws Error {
        join();
        if (isSuccess())
            return (T) result;
        if (result instanceof Exception)
            throw new Error((Exception) result);
        throw new Failure(result);
    }

    public <T> T await() {
        join();
        return (T) result;
    }

    public <T> T await(long millis) {
        return join(millis) ? (T) result : null;
    }

    /**
     * Wait until the operation is somehow finished
     */
    public DeferredResult join() {
        join(0);
        return this;
    }

    /**
     * Whether the deferred result is available already and calculated successfully.
     */
    public boolean isSuccess() {
        return done && success;
    }

    /**
     * wait until the operation is finished with maximum timeout
     *
     * @param millis
     *         time to wait for operation to complete
     *
     * @return true if the operation is finished, false if timeout expired or thread was interrupted
     * and the operation is not yet done
     */
    public boolean join(long millis) {
        synchronized (access) {
            if (done)
                return true;
            try {
                access.wait(millis);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
            return done;
        }
    }

    /**
     * Callback to be executed on deferred result state changed
     */
    public interface Handler {
        void handle(Object data);
    }

    /**
     * Exception raised if operation is failed and {@link #getResult()} returns {@link Throwable},
     * which is passed as exception cause (with stack trace this way).
     */
    public static class Error extends RuntimeException {
        Error(Exception inner) {
            super("Exception in defferred operation: " + inner.toString(), inner);
        }

        Error() {
            super("deffered operation failed");
        }
    }

    /**
     * Exception raised when operation is failed and {@link #getResult()} passes non-exception
     * object, which is available as {@link #failureData}.
     */
    public static class Failure extends Error {
        private final Object failureData;

        Failure(Object failureData) {
            this.failureData = failureData;
        }

        public <T> T getFailureData() {
            return (T) failureData;
        }
    }
}
