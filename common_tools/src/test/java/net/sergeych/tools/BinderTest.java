package net.sergeych.tools;

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

    @Test
    public void getInt() throws Exception {
        Binder b = Binder.fromKeysValues(
                "i1", 100,
                "i2", "101",
                "l1", "1505774997427",
                "l2", 1505774997427L
        );
        assertEquals(100, (int) b.getInt("i1",222));
        assertEquals(101, (int) b.getInt("i2",222));
        assertEquals(100, b.getIntOrThrow("i1"));
        assertEquals(101, b.getIntOrThrow("i2"));
        assertEquals(1505774997427L, b.getLongOrThrow("l1"));
        assertEquals(1505774997427L, b.getLongOrThrow("l2"));
    }
}