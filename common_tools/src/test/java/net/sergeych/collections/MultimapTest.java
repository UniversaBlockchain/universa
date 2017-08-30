package net.sergeych.collections;

import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MultimapTest {
    @Test
    public void create() throws Exception {
        Multimap<String,Integer> mmap = Multimap.newInstance();
        mmap.put("a", 10);
        mmap.put("a", 20);
        mmap.put("b", 100);
        List<Integer> x = mmap.getList("a");
        assertEquals(2,x.size());
        assertEquals(10, (int)x.get(0));
        assertEquals(20, (int)x.get(1));
        assertEquals(100, (int)mmap.getList("b").get(0));
        assertEquals(3, mmap.size());

        assertTrue(mmap.removeValue("a", 10));
        assertFalse(mmap.removeValue("a", 10));
        x = mmap.getList("a");
        assertEquals(1,x.size());
        assertEquals(20, (int)x.get(0));
        assertEquals(100, (int)mmap.getList("b").get(0));
        assertEquals(2, mmap.size());
    }

    @Test
    public void sizeAndRemove() throws Exception {
        Multimap<String,Integer> mmap = Multimap.newInstance();
        mmap.put("a", 10);
        mmap.put("a", 20);
        mmap.put("b", 100);
        assertNull(mmap.remove("x"));
        assertEquals(mmap.size(), 3);
        assertNotNull(mmap.remove("a"));
        assertEquals(mmap.size(), 1);
    }

}