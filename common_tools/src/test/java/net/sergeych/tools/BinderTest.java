package net.sergeych.tools;

import net.sergeych.tools.Binder;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 24.12.16.
 */
public class BinderTest {
    @Test
    public void isFrozen() throws Exception {
        Binder b = new Binder();
        b.put("hello", "world");
        Binder x = b.getOrCreateBinder("inner");
        x.put("foo", "bar");
        assertFalse(b.isFrozen());
        assertFalse(b.of("inner").isFrozen());

        b.freeze();
        assertTrue(b.isFrozen());
        assertTrue(b.of("inner").isFrozen());
        assertEquals("bar", b.of("inner").get("foo"));

        Binder e = Binder.EMPTY;
        assertTrue(e.isFrozen());
        x = new Binder();
        assertEquals(e, x);
        assertFalse(b.equals(e));
    }

}