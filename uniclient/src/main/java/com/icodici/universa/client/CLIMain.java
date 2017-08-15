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
import net.sergeych.tools.Binder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static net.sergeych.tools.JsonTool.toJson;

public class CLIMain {

    private static final String CLI_VERSION = "0.1";

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;
    private static boolean useJson = false;

    private static final List<String> messages = new ArrayList<>();
    private static final List<Binder> errors = new ArrayList<>();

    static public void main(String[] args) throws IOException {
//        args = new String[]{"-g", "longkey", "-s", "4096"};
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "show help").forHelp();
                acceptsAll(asList("g", "generate"), "generate new key pair")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("filename");
                accepts("s", "with -g, specify key strength")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(2048);
                acceptsAll(asList("c", "create"), "create smart contract from yaml template")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file.yml");
                acceptsAll(asList("j", "json"), "return result in json format");
            }
        };
        try {
            options = parser.parse(args);

            if (options.has("?")) {
                usage(null);
            }

            if( options.has("j")) {
                useJson = true;
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
            System.out.println(toJson(Binder.fromKeysValues(
                    "messages", messages,
                    "errors", errors
            )));
        } catch (Exception e) {
            e.printStackTrace();
            usage(e.getMessage());
            System.exit(100);
        }

    }

    private static void createContract() throws IOException {
        String source = (String) options.valueOf("c");
        Contract c = Contract.fromYamlFile(source);
        byte[] data = c.seal();
        // try sign
        String contractFileName = source.replaceAll("\\.(yml|yaml)$", ".unic");
        try (FileOutputStream fs = new FileOutputStream(contractFileName)) {
            fs.write(data);
        }
        report("created contract file: "+ contractFileName);
        checkContract(c);
        finish();
    }

    private static void checkContract(Contract c) {
        c.check();
        c.getErrors().forEach(error -> {
            addError(error.getCode().toString(), error.getField(), error.getText());
        });
    }

    private static void finish() {
        // print reports if need
        throw new Finished();
    }

    private static void report(String message) {
        if( !useJson )
            System.out.println(message);
        messages.add(message);
    }

    private static void addError(String code, String object, String message) {
        if( useJson ) {
            errors.add(Binder.fromKeysValues(
                    "code", code,
                    "object", object,
                    "message", message
            ));
        }
        else {
            String msg = "** ERROR: " + code;
            if (object != null && !object.isEmpty())
                msg += " in " + object;
            if (message != null && !message.isEmpty())
                msg += ": " + message;
            System.out.println(msg);
        }
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

    public static class Finished extends RuntimeException {
    }
}
