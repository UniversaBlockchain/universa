package net.sergeych.utils;

import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.*;

public class Safe58Test {

    @Test
    public void encode() {
        for(int i=0; i<100;i++) {
            byte [] src = Do.randomBytes(256+Do.randomInt(1024));
            assertArrayEquals(src, Safe58.decode(Safe58.encode(src)));
        }
    }

    @Test
    public void decode() {
        byte[] ok = Safe58.decode("Helloworld");
        assertArrayEquals(ok,Safe58.decode("HellOwOr1d"));
        assertArrayEquals(ok,Safe58.decode("He1IOw0r1d"));
        assertArrayEquals(ok,Safe58.decode("He!|Ow0r|d"));
    }


}