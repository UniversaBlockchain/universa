package com.icodici.universa.node.benchmark;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.*;
import net.sergeych.tools.Average;
import net.sergeych.tools.BufferedLogger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Transactions-per-seconds benchmark. Works in stages: generates test contracts, then approves it with a 1-node
 * simulated network.
 */

public class TPSTest {

    private int contractsPerStep = 500;
    private final BufferedLogger logger = new BufferedLogger(1024);
    private LocalNode node;
    private final int nThreads;
    private final int repetitions;
    private Ledger ledger;
    private Network network;
    private boolean stop;
    private ExecutorService es;
    private Future<?> mainLoop;

    public TPSTest(int contractsPerStep, String connectionString, int nThreads, int repetitions) throws SQLException {
        this.contractsPerStep = contractsPerStep;
        this.nThreads = nThreads;
        this.repetitions = repetitions;
        es = Executors.newFixedThreadPool(nThreads);
        createTestEnvironment(connectionString);
    }

    private void createTestEnvironment(String connectionString) throws SQLException {
        network = new Network();
        ledger = new PostgresLedger(connectionString);
        node = new LocalNode("speed_test_node", network, ledger);
        network.registerNode(node);
        network.setNegativeConsensus(1);
        network.setPositiveConsensus(1);
    }

    public BufferedLogger getLogger() {
        return logger;
    }

    public void run() {
        TestContracts tc = new TestContracts(32);
        Average t = new Average();
        for (int i = 0; i < repetitions && !stop; i++) {
            try {
                logger.log("generating test contracts");
                tc.generate(contractsPerStep);
                logger.log("test contracts ready, performing transactions");
                long start = System.currentTimeMillis();
                Collection<Contract> contracts = tc.getContracts();
                ArrayList<Future<?>> tasks = new ArrayList<>();
                contracts.forEach((c) -> {
                    tasks.add(es.submit(() -> {
                        ItemResult itemResult = node.registerItemAndWait(c);
                        if (!itemResult.state.isApproved()) {
                            logger.log("Error: contract is not approved");
                            logger.log(itemResult.toString());
                            logger.log(c.getErrorsString());
                        }
                        return null;
                    }));
                });
                for (Future f : tasks) {
                    f.get();
                }
                long elapsed = System.currentTimeMillis() - start;
                double tps = 1000.0 * contracts.size() / elapsed;
                logger.log("transactions done in " + elapsed + "ms, TPS: " +
                                   tps);
                t.update(tps);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                stop = true;
            }
            System.out.println("TPS total: "+t);
        }
    }
}
