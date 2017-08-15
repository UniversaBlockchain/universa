package net.sergeych.informer;

import net.sergeych.utils.LogPrinter;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The mechanism to convey any type of events to subscribers. Inspired by the EventBus. Unlike the
 * latter provides either strong ot weak references of subscriber objects and an ability to prevent
 * further event processing from the subscripber.
 * <p>
 * Simple sample:
 * <pre><code>
 *
 * public class Foo {
 *     &#064;Subscriber
 *     public void onEvent(SomeEvent event) {
 *         //...
 *     }
 * }
 *
 * Foo foo = new Foo();
 * Informer informer = new Informer();
 * informer.registerStrong(foo);
 * // ...
 * informer.post(new SomeEvent());
 *
 * </code></pre>
 * It is possible subscriber exceptions and catch unprocessed events, support for Event hierarchies
 * (e.g. subscrive to event superclass or any type with Object). See {@link #post(Object)}, {@link
 * #registerStrong(Object)}, {@link #registerWeak(Object)} for further details.
 * <p>
 * Created by sergeych on 13/02/16.
 */
public class Informer {

    static LogPrinter log = new LogPrinter("");

    /**
     * Context to carry subscriber exception information
     */
    public class ExceptionContext {

        SubInvocation si;

        public Informer getInformer() {
            return Informer.this;
        }

        /**
         * Which subscriber method has thrown and exception
         */
        public Method getMethod() {
            return si.method;
        }

        /**
         * @return subscriber object whose method caused the exception
         */
        public Object getSubscriber() {
            return si.weakObject.get();
        }

        public Exception getException() {
            return exception;
        }

        private ExceptionContext(SubInvocation invocation, Exception e) {
            exception = e;
            si = invocation;
        }

        private Method method;
        private Object subscriber;
        private Exception exception;
    }

    /**
     * Listener for exceptions thrown by subscribers
     */
    public interface ExceptionListener {
        void onSubscriberException(ExceptionContext x);
    }

    private ExceptionListener exceptionListener = null;

    public Informer() {
    }

    /**
     * Create informer that listens to the subscriber exceptions which are otherwise ignored with no
     * report.
     *
     * @param listener
     */
    public Informer(ExceptionListener listener) {
        exceptionListener = listener;
    }

    enum Result {
        NO_MATCH, PROCESSED, CONSUMED
    }

    private class SubInvocation {

        private Class eventClass;
        private boolean canConsume = false;
        private Method method;
        private WeakReference<Object> weakObject;

        SubInvocation(Object object, Method method) {
            this.method = method;
            weakObject = new WeakReference<Object>(object);
            Class[] pt = method.getParameterTypes();
            if (pt.length != 1)
                throw new IllegalArgumentException("@Subscriber must take only one parameter");
            eventClass = pt[0];
            if (method.getReturnType().getCanonicalName().equals("boolean")) {
                canConsume = true;
            }
        }

        Result invokeIfMatch(Object parameter) {
            if (eventClass.isInstance(parameter)) {
                Object receiver = weakObject.get();
                try {
                    if (receiver != null) {
                        Object result = method.invoke(receiver, parameter);
                        return (canConsume && (boolean) result) ? Result.CONSUMED : Result.PROCESSED;
                    }
                } catch (Exception e) {
                    if( e instanceof IllegalAccessException)
                        throw new RuntimeException("Informer has no access to subscriber", e);
                    if (exceptionListener != null) {
                        exceptionListener.onSubscriberException(new ExceptionContext(this, e));
                    }
                }
            }
            return Result.NO_MATCH;
        }
    }

    private WeakHashMap<Object, ArrayList<SubInvocation>> weakInvocations =
            new WeakHashMap<>();

    private HashMap<Object, ArrayList<SubInvocation>> strongInvocations = new HashMap<>();

    /**
     * Synchronously post an event. Will block until all matching subscribers will be called.
     *
     * @param event
     */
    public void post(Object event) {
        int result;
        int processedCount = invokeCollection(weakInvocations, event);
        processedCount += invokeCollection(strongInvocations, event);
        if (processedCount == 0 && !(event instanceof LostEvent)) {
            post(new LostEvent(event));
        }
    }

    private int invokeCollection(Map<Object, ArrayList<SubInvocation>> collection, Object event) {
        int processedCount = 0;
        for (ArrayList<SubInvocation> list : collection.values()) {
            for (SubInvocation si : list) {
                Result result = si.invokeIfMatch(event);
                if (result == Result.NO_MATCH)
                    continue;
                processedCount++;
                if (result == Result.CONSUMED)
                    break;
            }
        }
        return processedCount;
    }

    /**
     * Post an event to subscribers after the specified timeout, see {@link #post(Object)} for
     * details. Subscribers will be informed adter specified interval from some other thread. The
     * call returns immediately.
     *
     * @param event
     *         event to post
     * @param millis
     *         timeout to wait before posting
     */
    public void postAfter(final Object event, final long millis) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().sleep(millis);
                    post(event);
                } catch (InterruptedException e) {
                }
            }
        }).start();
    }

    /**
     * Register a subscriber object as a weak reference. See {@link #register(Object, boolean)} for
     * more.
     *
     * @param subscriber
     */
    public void registerWeak(Object subscriber) {
        register(subscriber, true);
    }

    /**
     * Register subscriber as a strong reference, and it will be retained at least as long as it is
     * registered and the Informer instance is not collected. See {@link #register(Object, boolean)}
     * for more.
     *
     * @param subscriber
     */
    public void registerStrong(Object subscriber) {
        register(subscriber, false);
    }

    /**
     * Unregister subscriber.
     *
     * @param subscriber
     *         to unregister.
     *
     * @return true if this subscriber was previously registered.
     */
    public boolean unregister(Object subscriber) {
        boolean found = (weakInvocations.remove(subscriber) != null);
        found = found || (strongInvocations.remove(subscriber) != null);
        return found;
    }

    /**
     * Register a subscriber object as a strong or a weak reference. Strong reference will guarantee
     * that the subscriber object will not be reclaimed as long as it is not unregistered and the
     * informer object is alive.
     * <p>
     * The old subscription of this object if any will be removed. e.g. you can re-register it as
     * weak, then the strong registration will be thrown. The same, no matter how many time the
     * subscriber was registered, its @Subscriber methods will be called only once per {@link
     * #post(Object)} invocation.
     * <p>
     * To receive events, subscriber class declares one or more public methods with @Subscriber
     * annotation and the single argument. Suchm methods should return void or boolean, in which
     * case returning true instructs notifier to not to pass this event to any other subscribers.
     * Subscriber receive only objects that can be casted to its declared argument type, this way
     * you can have subscribers to process different types of events.
     * <p>
     * It is allowed to have several subsciber methods ofr the same type of argument in the same
     * class. No restrictions :)
     * <p>
     * In the cases there is no suitable subscribers for some evvent, it will be reposted inside the
     * {@link LostEvent} instance that could be subscribed as usual.
     * <p>
     * Any exceptions inside the subscriber are ignored
     * <p>
     * See {@link Subscriber} for details on event processing.
     *
     * @param subscriber
     *         instance, should be of the public class.
     * @param registerWeak
     */

    public void register(Object subscriber, boolean registerWeak) {
        unregister(subscriber);
        Map<Object, ArrayList<SubInvocation>> map = registerWeak ? weakInvocations :
                strongInvocations;
        for (Method m : subscriber.getClass().getMethods()) {
            if (m.isAnnotationPresent(Subscriber.class)) {
                SubInvocation si = new SubInvocation(subscriber, m);
                ArrayList<SubInvocation> ii = map.get(subscriber);
                if (ii == null) {
                    ii = new ArrayList<>();
                    map.put(subscriber, ii);
                }
                ii.add(si);
            }
        }
    }
}
