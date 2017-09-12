package com.icodici.universa.node.benchmark;

import com.icodici.universa.node.PostgresLedgerTest;
import net.sergeych.tools.MemoryLogger;
import org.junit.Test;

public class TPSTestTest {
    @Test
    public void start() throws Exception {

        TPSTest tps = new TPSTest(5000,
                                  PostgresLedgerTest.CONNECTION_STRING,
                                  64,
                                  10);
        MemoryLogger logger = tps.getLogger();
        logger.printTo(System.out);
        tps.run();
        System.out.println("done");
    }

}