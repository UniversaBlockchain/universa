package net.sergeych.tools;

import net.sergeych.tools.AsyncEvent;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class AsyncEventTest {
    @Test
    public void fire() throws Exception {
        for(int n=0; n<500; n++) {
            AsyncEvent<Integer> event = new AsyncEvent<>();
            int values[] = new int[] { 0, 0};
            CountDownLatch latch = new CountDownLatch(2);
            event.addConsumer(i -> {
                values[0] = i;
                latch.countDown();

            });
            event.addConsumer(i -> {
                values[1] = i;
                latch.countDown();
            });
            event.fire(11);
            int res = event.waitFired();
            assertEquals(11, res);
            latch.await();
            assertEquals(11, values[0]);
            assertEquals(11, values[1]);
        }
    }

}