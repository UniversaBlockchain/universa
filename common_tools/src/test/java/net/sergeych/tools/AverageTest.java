package net.sergeych.tools;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class AverageTest {
    @Test
    public void average() throws Exception {
        Average a = new Average();
        for( int i=0; i<5; i++)
            a.update(i);
        for( int i=4; i>=0; i--)
            a.update(i);
        assertTrue(a.toString().startsWith("2.0±1.414213562373095"));
        assertEquals(1.490711985, a.correctedStdev(), 0.00001);
    }

}