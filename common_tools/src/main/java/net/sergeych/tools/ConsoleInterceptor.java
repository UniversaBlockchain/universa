package net.sergeych.tools;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class ConsoleInterceptor {

    public interface Block {
        void call() throws Exception;
    }

    public static String copyOut(Block block) throws Exception {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final PrintStream printStream = new PrintStream(bos, true);
        PrintStream oldStream = System.out;
        PrintStream oldErr = System.err;
        System.setOut(printStream);
        System.setErr(printStream);
        try {
            block.call();
        }
        finally {
            System.setOut(oldStream);
            System.setErr(oldErr);
        }
        return bos.toString();
    }
}
