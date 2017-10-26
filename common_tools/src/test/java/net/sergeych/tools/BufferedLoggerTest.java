package net.sergeych.tools;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class BufferedLoggerTest {
    @Test
    public void lastEntries() throws Exception {
        BufferedLogger log = new BufferedLogger(100);
        for (int i = 0; i < 10; i++) {
            log.log("line " + i);
        }
        log.flush();
        List<BufferedLogger.Entry> entries = log.getLast(3);
        String result = str(entries);
        assertEquals("line 7,line 8,line 9", result);
        result = str(log.getLast(300));
        assertEquals("line 0,line 1,line 2,line 3,line 4,line 5,line 6,line 7,line 8,line 9", result);
    }

    protected String str(List<BufferedLogger.Entry> list) {
        return list.stream()
                .map(x -> x.message)
                .collect(Collectors.joining(","));

    }

    @Test
    public void slice() throws Exception {
        BufferedLogger log = new BufferedLogger(100);
        for (int i = 0; i < 10; i++) {
            log.log("line " + i);
        }
        log.flush();
        List<BufferedLogger.Entry> all = log.getCopy();
        List<BufferedLogger.Entry> entries = log.slice(all.get(5).id, 3);

        String result = str(entries);
        assertEquals("line 6,line 7,line 8", result);

        result = str(log.slice(all.get(5).id, -3));
        assertEquals("line 2,line 3,line 4", result);

        result = str(log.slice(all.get(2).id, -3));
        assertEquals("line 0,line 1", result);

        result = str(log.slice(all.get(8).id, +3));
        assertEquals("line 9", result);

        result = str(log.slice(all.get(0).id, 20));
        assertEquals("line 1,line 2,line 3,line 4,line 5,line 6,line 7,line 8,line 9", result);

        result = str(log.slice(all.get(9).id, -20));
        assertEquals("line 0,line 1,line 2,line 3,line 4,line 5,line 6,line 7,line 8", result);

        result = str(log.slice(all.get(0).id-1, 20));
        assertEquals("line 0,line 1,line 2,line 3,line 4,line 5,line 6,line 7,line 8,line 9", result);

    }

    @Test
    public void log() throws Exception {
        BufferedLogger log = new BufferedLogger(3);
        for (int i = 0; i < 10; i++) {
            log.log("line " + i);
        }
        log.flush();
        assertEquals("line 7,line 8,line 9", str(log.getCopy()));
    }

    @Test
    public void interceptConsole() throws Exception {
        BufferedLogger log = new BufferedLogger(100);
        String res = ConsoleInterceptor.copyOut(()-> {
            log.interceptStdOut();
            System.out.println("Hello, world");
            System.out.println("iCodici rules");
            System.out.println("last line");
            // now we need that streams send their data throgh the pipes:
            while (log.getCopy().size() < 3) Thread.sleep(20);
            log.flush();
            log.stopInterceptingStdOut();
            log.close();
            assertEquals("Hello, world,iCodici rules,last line", str(log.getCopy()));
        });
        assertEquals("Hello, world\n" +
                "iCodici rules\n" +
                "last line",
                     log.getCopy().stream()
                             .map(x->x.message)
                             .collect(Collectors.joining("\n")));
    }

}