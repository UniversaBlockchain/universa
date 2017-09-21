/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.node.benchmark.TPSBenchmarkServer;
import com.icodici.universa.node.network.NetConfig;
import com.icodici.universa.node.network.NetworkV1;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Reporter;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;

public class NodeStarter {
    private static final String NODE_VERSION = "0.19.2";
    private static OptionParser parser;
    private static OptionSet options;
    public static final Reporter reporter = new Reporter();
    private static String NAME_STRING = "Universa node server v" + NODE_VERSION + "\n";
    private static NetConfig netConfig;
    private static AsyncEvent eventReady = new AsyncEvent();
    public static final BufferedLogger logger = new BufferedLogger(4096);
    private static NetworkV1 network;


    static public void main(String[] args) {
//        args = new String[] { "--bmsingle", "1234", "-c", "universa_core/src/test_config_3"};
        parser = new OptionParser() {
            {
                accepts("bmsingle", "single-node benchmark mode")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("access password");
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("c", "config"), "configuration file for the network")
                        .withRequiredArg().ofType(String.class).defaultsTo(".")
                        .describedAs("config_file");
                acceptsAll(asList("i", "id"), "this node idedntifier")
                        .requiredUnless("bmsingle")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("node_id");
                acceptsAll(asList("p", "port"), "listening port for HTTP endpoint to override value in .yaml")
                        .withRequiredArg().ofType(Integer.class)
                        .defaultsTo(17200).describedAs("port");
                accepts("test", "intended to be used in integration tests");
                accepts("nolog", "do not buffer log messages (good fot testing)");
            }
        };
        try {
            options = parser.parse(args);
            if( !options.has("nolog"))
                logger.interceptStdOut();

            if (options.has("?")) {
                usage(null);
            }
            logger.log(NAME_STRING);
            if( options.has("bmsingle")) {
                logger.log("Starting single node benchmark mode");
                logger.log("client listening port: "+options.valuesOf("port"));
                new TPSBenchmarkServer(logger,
                                       (String) options.valueOf("bmsingle"),
                                       (int) options.valueOf("port"))
                        .setupTest((String) options.valueOf("c"));
                startAndWaitEnd();
            }
            else {
                logger.log("Starting client interface");
                startNetwork();
                reporter.message("System started");
                startAndWaitEnd();
            }

        } catch (OptionException e) {
            usage("Unrecognized parameter: " + e.getMessage());
        } catch (InterruptedException e) {

        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
        }
    }

    private static void startAndWaitEnd() throws InterruptedException {
        eventReady.fire(null);
        if (!options.has("test"))
            synchronized (parser) {
                parser.wait();
            }
    }

    public static void waitReady() throws InterruptedException {
        eventReady.await();
    }

    private static void startNetwork() throws IOException, InterruptedException, SQLException, TimeoutException {
        reporter.progress("reading network configuration");
        netConfig = NetConfig.from((String) options.valueOf("config"));
        int port = (int) options.valueOf("port");
        network = netConfig.buildNetworkV1((String) options.valueOf("id"), port);
    }

    static public void shutdown() {
        try {
            network.close();
        }
        catch(Exception e) {}

        synchronized (parser) {
            parser.notifyAll();
        }
    }

    static private void usage(String text) {
        boolean error = false;
        PrintStream out = System.out;
        if (text != null) {
            out = System.err;
            error = true;
        }
        out.println("\n" + NAME_STRING);
        if (text != null)
            out.println("ERROR: " + text + "\n");
        try {
            parser.printHelpOn(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (!options.has("test"))
            System.exit(100);
    }

}
