/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.node.network.HttpEndpoint;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.PrintStream;

import static java.util.Arrays.asList;

public class NodeStarter {
    private static final String NODE_VERSION = "0.17";
    private static OptionParser parser;
    private static OptionSet options;

    static public void main(String[] args) {
        // todo: start node client
//        args = new String[] { "-p", "17180"};
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("c", "config"), "configuration file for the network")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("config_file");
                acceptsAll(asList("i", "id"), "this node idedntificator")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("node_id").required();
                accepts("p", "listening port for HTTP endpoint")
                        .withRequiredArg().ofType(Integer.class)
                        .describedAs("port").required();
            }
        };
        try {
            options = parser.parse(args);
            if (options.has("?")) {
                usage(null);
            }
            System.out.println("Staring client interface");
            new HttpEndpoint((Integer) options.valueOf("p"));
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
