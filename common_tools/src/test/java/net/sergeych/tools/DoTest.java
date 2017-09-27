package net.sergeych.tools;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;

public class DoTest {
    @Test
    public void snakeToCamelCase() throws Exception {
        assertEquals("HelloWorld!", Do.snakeToCamelCase("hello_wORld_!"));
        assertEquals("Hello", Do.snakeToCamelCase("hello_"));
        assertEquals("Hello", Do.snakeToCamelCase("hello"));
        assertEquals("Hello", Do.snakeToCamelCase("_hello"));
    }

    @Test
    public void sample() throws Exception {
        HashSet<String> x = new HashSet<>(Do.listOf("aa", "bb", "cc", "cd"));
        // RandomAccess-ed lists have different algorithms, so test them separately
        HashSet<String> y = new HashSet<>();
        ArrayList<String> z = new ArrayList<>(Do.listOf("aa", "bb", "cc", "cd"));
        HashSet<String> t = new HashSet<>();

        int repetitions = 10000;
        for (int i = 0; i < repetitions; i++) {
            y.add(Do.sample(x));
            t.add(Do.sample(z));
        }
        assertEquals(y,x);
    }

    @Test
    public void randomInt() throws Exception {
        double sum = 0;
        int repetitions = 20000;
        int min = 10000, max = -1000;
        for(int i=0; i < repetitions; i++ ) {
            int x = Do.randomInt(100);
            sum += x;
            if( x < min )
                    min = x;
            if( x > max )
                max = x;
        }
        sum /= repetitions;
        assertThat( sum, is(closeTo(50, 0.9)));
        assertThat(min, is(lessThan(20)));
        assertThat(max, is(greaterThan(80)));
    }

}