/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Reporter;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;

import static java.util.Arrays.asList;

public class Main {
    private static final String NODE_VERSION = "2.0.0";
    private static OptionParser parser;
    private static OptionSet options;
    public static final Reporter reporter = new Reporter();

    private static String NAME_STRING = "Universa node server v" + NODE_VERSION + "\n";

    private static AsyncEvent eventReady = new AsyncEvent();
    public static final BufferedLogger logger = new BufferedLogger(4096);


    static public void main(String[] args) {
//        args = new String[] { "--bmsingle", "1234", "-c", "universa_core/src/test_config_3"};
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("c", "config"), "configuration file for the network")
                        .withRequiredArg().ofType(String.class).defaultsTo(".")
                        .required()
                        .describedAs("config_file");
                accepts("test", "intended to be used in integration tests");
                accepts("nolog", "do not buffer log messages (good fot testing)");
            }
        };
        try {
            options = parser.parse(args);
            if (!options.has("nolog"))
                logger.interceptStdOut();

            if (options.has("?")) {
                usage(null);
            }
            logger.log(NAME_STRING);
            logger.log("Starting client interface");
            reporter.message("System started");
            logger.log("-- "+loadNodeConfig());
            startAndWaitEnd();
        } catch (OptionException e) {
            usage("Unrecognized parameter: " + e.getMessage());
        } catch (InterruptedException e) {

        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
        }
    }

    /**
     * To use in unti-tests. Start a node and blocks the thread until the nodes stops.
     *
     * @throws InterruptedException
     */
    private static void startAndWaitEnd() throws InterruptedException {
        eventReady.fire(null);
        if (!options.has("test"))
            synchronized (parser) {
                parser.wait();
            }
    }

    /**
     * For unit-tests. Blocks until the node is initialized.
     *
     * @throws InterruptedException
     */
    public static void waitReady() throws InterruptedException {
        eventReady.await();
    }

    /**
     * For unit-tests. REquest the shutdown and wait until the node stops.
     */
    static public void shutdown() {
        try {
//            network.close();
        } catch (Exception e) {
        }

        synchronized (parser) {
            parser.notifyAll();
        }
    }

    private static Binder loadNodeConfig() throws IOException {
        Yaml yaml = new Yaml();
        String root = (String)options.valueOf("config");
        return Binder.of(yaml.load(new FileInputStream(root + "/config/config.yaml")));
    }

    private static void startClientServer(Binder settings) {

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
