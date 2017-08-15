package com.icodici.universa.node;

import com.icodici.universa.HashId;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 16/07/2017.
 */
public class
HashIdTest {
    @Test
    public void testToString() throws Exception {
        byte[] data = "hello world!".getBytes();
        HashId hid = new HashId(data);
        assertArrayEquals(hid.getDigest(), Base64u.decodeCompactString(hid.toBase64String()));
    }

    private HashId idA, idA1, idB;

    @Before
    public void init() {
        idA = new HashId(new byte[] {1,2,3});
        idB = new HashId(new byte[] {1,2,4});
        idA1 = new HashId(new byte[] {1,2,3});
    }

    @Test
    public void equals() throws Exception {
        assertTrue(idA.equals(idA));
        assertTrue(idA.equals(idA1));
        assertFalse(idA.equals(idB));
    }

    @Test
    public void testHashCode() throws Exception {
        long a = idA.hashCode();
        long a1 = idA1.hashCode();
        long b = idB.hashCode();
        assertEquals(a, a1);
        assertNotEquals(a, b);
    }

    @Test
    public void compareTo() throws Exception {
        assert( idA.compareTo(idA1) == 0 );
        assert( idA.compareTo(idB) < 0 );
        assert( idB.compareTo(idA) > 0 );
    }

    @Test
    public void hashNegatives() throws Exception {
        idA = HashId.withDigest(Do.randomNegativeBytes(64));
        idA1 = HashId.withDigest(idA.getDigest());
        assertEquals(idA, idA1);
        assertEquals(idA.hashCode(), idA1.hashCode());
        Map<HashId,String> test = new ConcurrentHashMap<>();
        test.put(idA, "hello");
        assertEquals("hello", test.get(idA));
        assertEquals("hello", test.get(idA1));
    }
}