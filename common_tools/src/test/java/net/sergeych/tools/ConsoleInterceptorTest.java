package net.sergeych.tools;

import org.junit.Test;

import static org.junit.Assert.*;

public class ConsoleInterceptorTest {
    @Test
    public void interceptStdout() throws Exception {
        String result = ConsoleInterceptor.copyOut(() ->{
            System.out.print("hello world");
            System.out.print('!');
            System.out.println();
            System.out.println("foobar");
        });
        assertEquals("hello world!\nfoobar\n", result);
        result = ConsoleInterceptor.copyOut( () ->{
            System.out.print("hello");
            System.out.println(" world!");
        });
        assertEquals("hello world!\n", result);
        result = ConsoleInterceptor.copyOut( () ->{
            System.out.print(58387);
            System.out.println(" world!");
        });
        assertEquals("58387 world!\n", result);
    }

}