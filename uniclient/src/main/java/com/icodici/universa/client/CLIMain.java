/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class CLIMain {

    private static final String CLI_VERSION = "0.1";

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;

    private static Reporter reporter = new Reporter();
    private static ClientNetwork clientNetwork;
    private static List<String> keyFileNames = new ArrayList<>();
    private static Set<PrivateKey> privateKeys;

    static public void main(String[] args) throws IOException {
//        args = new String[]{"-g", "longkey", "-s", "4096"};
        // when we run untitests, it is important
        reporter.clear();
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("g", "generate"), "generate new key pair and store in a files starting " +
                        "with a given prefix")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("name_prefix");
                accepts("s", "with -g, specify key strength")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(2048);
                acceptsAll(asList("c", "create"), "create smart contract from yaml template")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file.yml");
                acceptsAll(asList("j", "json"), "return result in json format");
                acceptsAll(asList("v", "verbose"), "provide more detailed information");
                acceptsAll(asList("network"), "check network status");
                acceptsAll(asList("k", "keys"), "list of comma-separated private key files to" +
                        "sign contract with, if appropriated")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("key_file");
//                acceptsAll(asList("show", "s"), "show contract")
//                        .withRequiredArg().ofType(String.class)
            }
        };
        try {
            options = parser.parse(args);

            if (options.has("?")) {
                usage(null);
            }
            if( options.has("v")) {
                reporter.setVerboseMode(true);
            }
            if(options.has("k")) {
                keyFileNames = (List<String>) options.valuesOf("k");
            }

            if (options.has("j")) {
                reporter.setQuiet(true);
            }

            if (options.has("network")) {
                ClientNetwork n = getClientNetwork();
                int total = n.size();
                int active = n.checkNetworkState(reporter);
                reporter.message("network availablity: " + active + "/" + total);
                reporter.message("status: suspended for maintenance");
                finish();
            }

            if (options.has("g")) {
                generateKeyPair();
                return;
            }

            if (options.has("c")) {
                createContract();
            }

            usage(null);

        } catch (OptionException e) {
            usage("Unrecognized parameter: " + e.getMessage());
        } catch (Finished e) {
            if( reporter.isQuiet())
                System.out.println(reporter.reportJson());
        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
            System.exit(100);
        }

    }

    private static void createContract() throws IOException {
        String source = (String) options.valueOf("c");
        Contract c = Contract.fromYamlFile(source);
        getPrivateKeys().forEach(k -> c.addSignerKey(k));
        byte[] data = c.seal();
        // try sign
        String contractFileName = source.replaceAll("\\.(yml|yaml)$", ".unic");
        try (FileOutputStream fs = new FileOutputStream(contractFileName)) {
            fs.write(data);
        }
        report("created contract file: " + contractFileName);
        checkContract(c);
        finish();
    }

    private static void checkContract(Contract c) {
        c.check();
        c.getErrors().forEach(error -> {
            addError(error.getError().toString(), error.getObjectName(), error.getMessage());
        });
    }

    private static void finish() {
        // print reports if need
        throw new Finished();
    }

    private static void report(String message) {
        reporter.message(message);
    }

    private static void addError(String code, String object, String message) {
        reporter.error(code,object,message);
    }

    private static void generateKeyPair() throws IOException {
        PrivateKey k = new PrivateKey((Integer) options.valueOf("s"));
        String name = (String) options.valueOf("g");
        new FileOutputStream(name + ".private.unikey").write(k.pack());
        new FileOutputStream(name + ".public.unikey").write(k.getPublicKey().pack());
        System.out.println("New key pair ready");
    }

    static private void usage(String text) {
        boolean error = false;
        PrintStream out = System.out;
        if (text != null) {
            out = System.err;
            error = true;
        }
        out.println("\nUniversa client tool, v. " + CLI_VERSION + "\n");
        if (text != null)
            out.println("ERROR: " + text + "\n");
        try {
            parser.printHelpOn(out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(100);
    }

    public static void setTestMode() {
        testMode = true;
    }

    public static Reporter getReporter() {
        return reporter;
    }

    public static synchronized ClientNetwork getClientNetwork() {
        if( clientNetwork == null )
            clientNetwork = new ClientNetwork();
        return clientNetwork;
    }

    public static synchronized Set<PrivateKey> getPrivateKeys() throws IOException {
        if( privateKeys == null ) {
            privateKeys = new HashSet<>();
            for(String fileName: keyFileNames) {
                PrivateKey pk = new PrivateKey(Do.read(fileName));
                privateKeys.add(pk);
            }
        }
        return privateKeys;
    }

    public static class Finished extends RuntimeException {
    }
}
