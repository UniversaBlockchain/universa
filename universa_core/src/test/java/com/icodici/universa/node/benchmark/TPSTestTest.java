package com.icodici.universa.node.benchmark;

import com.icodici.universa.contract.Role;
import com.icodici.universa.node.PostgresLedgerTest;
import net.sergeych.tools.BufferedLogger;
import org.junit.Test;

import java.util.Map;
import java.util.Random;

public class TPSTestTest {

    private static final int contractsPerStep = 5000;
    private static final int nThreads = 64;
    private static final int repetitions = 10;

    @Test
    public void startStressTest() throws Exception {
        TPSTest tps = new TPSTest(
                contractsPerStep,
                PostgresLedgerTest.CONNECTION_STRING,
                nThreads,
                repetitions,
                null);
        BufferedLogger logger = tps.getLogger();
        logger.printTo(System.out, false);
        tps.run();
        System.out.println("done");
    }

//    @Test
    public void startNoCreatorTest() throws Exception {
        TPSTest tps = new TPSTest(
                contractsPerStep,
                PostgresLedgerTest.CONNECTION_STRING,
                nThreads,
                repetitions,
                c -> c.getRoles().remove("creator")
        );
        BufferedLogger logger = tps.getLogger();
        logger.printTo(System.out, false);
        tps.run();
        System.out.println("done");
    }

//    @Test
    public void startNoRandomRoleTest() throws Exception {
        final Random rng = new Random();
        TPSTest tps = new TPSTest(
                contractsPerStep,
                PostgresLedgerTest.CONNECTION_STRING,
                nThreads,
                repetitions,
                c -> {
                    Map<String, Role> roles = c.getRoles();
                    Object[] keys = roles.keySet().stream().toArray();
                    String keyToDelete = (String) keys[rng.nextInt(keys.length)];
                    roles.remove(keyToDelete);
                });
        BufferedLogger logger = tps.getLogger();
        logger.printTo(System.out, false);
        tps.run();
        System.out.println("done");
    }
}
