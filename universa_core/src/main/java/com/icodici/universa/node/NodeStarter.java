/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.node.network.ClientEndpoint;
import com.icodici.universa.node.network.NetworkBuilder;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.tools.Reporter;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;

public class NodeStarter {
    private static final String NODE_VERSION = "0.17";
    private static OptionParser parser;
    private static OptionSet options;
    public static final Reporter reporter = new Reporter();

    static public void main(String[] args) {
        // todo: start node client
//        args = new String[] { "-p", "17180"};
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("c", "config"), "configuration file for the network")
                        .withRequiredArg().ofType(String.class).defaultsTo(".")
                        .describedAs("config_file");
                acceptsAll(asList("i", "id"), "this node idedntificator")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("node_id").required();
                accepts("p", "listening port for HTTP endpoint to override value in .yaml")
                        .withRequiredArg().ofType(Integer.class)
                        .defaultsTo(0).describedAs("port");
            }
        };
        try {
            options = parser.parse(args);
            if (options.has("?")) {
                usage(null);
            }
            System.out.println("Starting client interface");

            startNetwork();

//            new ClientEndpoint((Integer) options.valueOf("p"));
            System.out.println("System started");
            synchronized (parser) { parser.wait(); };
//            usage(null);

        } catch (OptionException e) {
            usage("Unrecognized parameter: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
        }
    }

    private static void startNetwork() throws IOException, InterruptedException, SQLException, TimeoutException {
        reporter.progress("reading network configuration");
        NetworkBuilder nb = NetworkBuilder.from((String) options.valueOf("congig"));
        nb.buildNetwork((String)options.valueOf("id"),  (int)options.valueOf("port"));
    }

    static private void usage(String text) {
        boolean error = false;
        PrintStream out = System.out;
        if (text != null) {
            out = System.err;
            error = true;
        }
        out.println("\nUniversa node server v" + NODE_VERSION + "\n");
        if (text != null)
            out.println("ERROR: " + text + "\n");
        try {
            parser.printHelpOn(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(100);
    }

}
