package net.sergeych.informer;

import net.sergeych.informer.Informer;
import net.sergeych.informer.LostEvent;
import net.sergeych.informer.Subscriber;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Created by sergeych on 14/02/16.
 */
public class InformerTest {

    static public class Receiver1 {

        public String lastString;
        public LostEvent lastLost;

        static public int stringCalls = 0;
        static public int lostCount = 0;

        public static void clear() {
            stringCalls = 0;
            lostCount = 0;
        }

        @Subscriber
        public void r1(String arg) {
            lastString = arg;
            stringCalls++;
        }

        @Subscriber
        public void r2(String arg) {
            lastString = arg;
            stringCalls++;
        }

        @Subscriber
        public void pnLost(LostEvent e) {
            lostCount++;
            lastLost = e;
        }
    }

    static public class Receiver2 extends Receiver1 {

        public Object lastObject;
        static public int objectCalls = 0;

        public static void clear() {
            stringCalls = objectCalls = 0;
            Receiver1.clear();
        }

        @Subscriber
        public void rObj(Object arg) {
            lastObject = arg;
            objectCalls += 1;
        }
    }

    Informer informer;

    @Before
    public void setUp() {
        Receiver2.clear();
        informer = new Informer();
    }

    @Test
    public void testPost() throws Exception {
        Receiver2 receiver = new Receiver2();

        informer.registerStrong(receiver);
        informer.post(123);

        assertEquals(0, Receiver2.stringCalls);
        assertEquals(1, Receiver2.objectCalls);
        assertEquals(123, receiver.lastObject);

        informer.post("test1");
        assertEquals(2, Receiver2.stringCalls);
        assertEquals(2, Receiver2.objectCalls);

        assertEquals("test1", receiver.lastObject);
        assertEquals(0, Receiver2.lostCount);
    }

    @Test
    public void testLost() throws Exception {
        Receiver1 receiver = new Receiver1();
        assertEquals(0, Receiver1.lostCount);
        informer.registerStrong(receiver);
        informer.post("should pass");
        assertEquals(0, Receiver1.lostCount);
        informer.post(771);
        assertEquals(1, Receiver1.lostCount);
        assertEquals(771, receiver.lastLost.getSource());
    }

    void sleep(long millis) throws InterruptedException {
        Thread.currentThread().sleep(millis);
    }

    @Test
    public void testPostAfter() throws Exception {
        Receiver1 r1 = new Receiver1();
        informer.registerWeak(r1);
        informer.postAfter(11, 100);
        assertEquals(0, Receiver1.lostCount);
        sleep(40);
        assertEquals(0, Receiver1.lostCount);
        sleep(70);
        assertEquals(1, Receiver1.lostCount);
    }

    @Test
    public void testRegisterWeak() throws Exception {
        assertEquals(0, Receiver1.lostCount);
        informer.registerWeak(new Receiver1());
        System.gc();
        informer.post(11);
        assertEquals(0, Receiver1.lostCount);
    }

    @Test
    public void testRegisterStrong() throws Exception {
        assertEquals(0, Receiver1.lostCount);
        informer.registerStrong(new Receiver1());
        informer.post(11);
        assertEquals(1, Receiver1.lostCount);
    }

    @Test
    public void testUnregister() throws Exception {
        assertEquals(0, Receiver1.lostCount);
        Receiver1 r1 = new Receiver1();
        informer.registerWeak(r1);
        informer.unregister(r1);
        informer.post(11);
        assertEquals(0, Receiver1.lostCount);

        informer.registerStrong(r1);
        informer.unregister(r1);
        informer.post(11);
        assertEquals(0, Receiver1.lostCount);
    }

    private Informer.ExceptionContext lastExceptionContext = null;

    @Test
    public void exceptionInSubscriber() throws Exception {
//        informer.registerStrong(new Obje);
        Receiver1 x = new Receiver1() {
            @Subscriber
            public void badOne(Integer x) {
                x = x / 0;
            }
        };
        informer.registerStrong(x);
        informer.post(111);
        assertNull(lastExceptionContext);

        informer = new Informer(new Informer.ExceptionListener() {
            @Override
            public void onSubscriberException(Informer.ExceptionContext x) {
                lastExceptionContext = x;
            }
        });
        informer.registerStrong(x);
        informer.post(222);
        assertEquals("badOne", lastExceptionContext.getMethod().getName());
    }
}
