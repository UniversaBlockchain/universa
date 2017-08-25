/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.node.network.NetworkBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Reporter;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;

public class NodeStarter {
    private static final String NODE_VERSION = "0.18";
    private static OptionParser parser;
    private static OptionSet options;
    public static final Reporter reporter = new Reporter();
    private static String NAME_STRING = "Universa node server v" + NODE_VERSION + "\n";
    private static NetworkBuilder networkBuilder;
    private static AsyncEvent eventReqady = new AsyncEvent();


    static public void main(String[] args) {
        // todo: start node client
//        args = new String[] { "-p", "17180"};
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("c", "config"), "configuration file for the network")
                        .withRequiredArg().ofType(String.class).defaultsTo(".")
                        .describedAs("config_file");
                acceptsAll(asList("i", "id"), "this node idedntifier")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("node_id").required();
                acceptsAll(asList("p", "port"), "listening port for HTTP endpoint to override value in .yaml")
                        .withRequiredArg().ofType(Integer.class)
                        .defaultsTo(0).describedAs("port");
                accepts("test", "intended to be used in integration tests");
            }
        };
        try {
            options = parser.parse(args);
            if (options.has("?")) {
                usage(null);
            }
            reporter.message(NAME_STRING);
            reporter.message("Starting client interface");
            startNetwork();
            reporter.message("System started");
            eventReqady.fire(null);
            if (!options.has("test"))
                synchronized (parser) {
                    parser.wait();
                }

        } catch (OptionException e) {
            usage("Unrecognized parameter: " + e.getMessage());
        } catch (InterruptedException e) {

        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
        }
    }

    public static void waitReady() throws InterruptedException {
        eventReqady.await();
    }

    private static void startNetwork() throws IOException, InterruptedException, SQLException, TimeoutException {
        reporter.progress("reading network configuration");
        networkBuilder = NetworkBuilder.from((String) options.valueOf("config"));
        int port = (int) options.valueOf("port");
        networkBuilder.buildNetwork((String) options.valueOf("id"), port);
    }

    static public void shutdown() {
        networkBuilder.shutdown();
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
