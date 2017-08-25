package net.sergeych.utils;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Base64Test {
    @Test
    public void compactString() throws Exception {
        byte[] data = new byte[] { -2, 2, 3, 4};
        assertEquals("/gIDBA==", Base64.encodeString(data));
        assertEquals("/gIDBA", Base64.encodeCompactString(data));
        assertArrayEquals(data, Base64u.decodeCompactString(Base64u.encodeCompactString(data)));
    }

}