package net.sergeych.tools;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class MemoryLoggerTest {
    @Test
    public void log() throws Exception {
        MemoryLogger log = new MemoryLogger(3);
        for(int i=0; i<10; i++) {
            log.log("line "+i);
        }
        List<MemoryLogger.Entry> entries = log.getCopy();
        String result = entries
                .stream()
                .map(x->x.message)
                .collect(Collectors.joining(","));
        assertEquals("line 7,line 8,line 9", result);
    }

}