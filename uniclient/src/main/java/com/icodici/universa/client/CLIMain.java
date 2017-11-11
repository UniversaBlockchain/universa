/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.icodici.crypto.KeyInfo;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.TransactionContract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.wallet.Wallet;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.DomDriver;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import net.sergeych.utils.Base64;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;

import static java.util.Arrays.asList;

public class CLIMain {

    private static final String CLI_VERSION = "2.2.0";

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;
    private static String testRootPath;

    private static Reporter reporter = new Reporter();
    private static ClientNetwork clientNetwork;
    private static List<String> keyFileNames = new ArrayList<>();
    private static Map<String, PrivateKey> keyFiles;


    public static final String AMOUNT_FIELD_NAME = "amount";

    static public void main(String[] args) throws IOException {
        // when we run untit tests, it is important:
        reporter.clear();
        // it could be called more than once from tests
        keyFiles = null;
        parser = new OptionParser() {
            {
                acceptsAll(asList("?", "h", "help"), "Show help.").forHelp();
                acceptsAll(asList("g", "generate"), "Generate new key pair and store in a files starting " +
                        "with a given prefix.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("name_prefix");
                accepts("s", "With -g, specify key strength.")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(2048);
                acceptsAll(asList("c", "create"), "Create smart contract from dsl template.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file.yml");
                acceptsAll(asList("j", "json"), "Return result in json format.");
                acceptsAll(asList("v", "verbose"), "Provide more detailed information.");
                acceptsAll(asList("network"), "Check network status.");
                accepts("register", "register a specified contract, must be a sealed binary file")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class).
                        describedAs("contract.unicon");
                accepts("probe", "query the state of the document in the Universa network")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("base64_id");
                acceptsAll(asList("k", "keys"), "List of comma-separated private key files to" +
                        "use to sign contract with, if appropriated.")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("key_file");
                acceptsAll(asList("fingerprints"), "Print fingerprints of keys specified with -k.");
//                acceptsAll(asList("show", "s"), "show contract")
//                        .withRequiredArg().ofType(String.class)
                acceptsAll(asList("e", "export"), "Export specified contract. " +
                        "Default export format is JSON. " +
                        "Use '-as' option with values 'json', 'xml' or 'yaml' for export as specified format.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file");
                accepts("as", "Use with -e, --export command. Specify format for export contract. " +
                        "Possible values are 'json', 'xml' or 'yaml'.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("format");
                acceptsAll(asList("i", "import"), "Import contract from specified xml, json or yaml file.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file");
                acceptsAll(asList("name", "o"), "Use with -e, --export or -i, --import commands. " +
                        "Specify name of destination file.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("filename");
                accepts("extract-key", "Use with -e, --export command. " +
                        "Extracts any public key(s) from specified role into external file.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("role");
                accepts("base64", "with --extract-key keys to the text base64 format");
                accepts("get", "Use with -e, --export command. " +
                        "Extracts any field of the contract into external file.")
                        .withRequiredArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("field_name");
                accepts("set", "Use with -e, --export command. " +
                        "Specify field of the contract for update. Use -value option to specify value for the field")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("field_name");
                accepts("value", "Use with -e, --export command and after -set argument. " +
                        "Update specified with -set argument field of the contract.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("field_value");
                acceptsAll(asList("f", "find"), "Search all contracts in the specified path including subpaths. " +
                        "Use -r key to check all contracts in the path recursively.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("path");
                acceptsAll(asList("d", "download"), "Download contract from the specified url.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("url");
                acceptsAll(asList("ch", "check"), "Check contract for validness. " +
                        "Use -r key to check all contracts in the path recursively.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file/path");
                accepts("r", "Use with --ch, --check or -f, --find commands. " +
                        "Specify to check contracts in the path and do it recursively.");
//                accepts("binary", "Use with --ch, --check. " +
//                        "Specify to check contracts from binary data.");
                accepts("term-width").withRequiredArg().ofType(Integer.class).defaultsTo(80);
                accepts("pretty", "Use with -as json option. Make json string pretty.");
                acceptsAll(asList("revoke"), "Revoke specified contract and create a revocation transactional contract. " +
                        "Use -k option to specify private key for revoke contract, " +
                        "key should be same as key you signed contract for revoke with. " +
                        "You cannot revoke contract without pointing private key.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file.unicon");
                acceptsAll(asList("pack-with"), "Pack contract with counterparts (new, revoking). " +
                        "Use -add-sibling option to add sibling and -add-revoke to add revoke item.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file.unicon");
                accepts("add-sibling", "Use with --pack-with command. " +
                        "Option add sibling item for packing contract.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("sibling.unicon");
                accepts("add-revoke", "Use with --pack-with command. " +
                        "Option add revoke item for packing contract.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("revoke.unicon");
                acceptsAll(asList("unpack"), "Extracts revoking and new items from contracts and save them.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file.unicon");


//                acceptsAll(asList("ie"), "Test - delete.")
//                        .withRequiredArg().ofType(String.class)
//                        .describedAs("file");
            }
        };
        try {
            options = parser.parse(args);

            if (options.has("?")) {
                usage(null);
            }
            if (options.has("v")) {
                reporter.setVerboseMode(true);
            } else {
                reporter.setVerboseMode(false);
            }
            if (options.has("k")) {
                keyFileNames = (List<String>) options.valuesOf("k");
            } else {
                keyFileNames = new ArrayList<>();
                keyFiles = null;
            }
            if (options.has("fingerprints")) {
                printFingerprints();
                finish();
            }
            if (options.has("j")) {
                reporter.setQuiet(true);
            } else {
                reporter.setQuiet(false);
            }
            if (options.has("network")) {
                ClientNetwork n = getClientNetwork();
                int total = n.size();
                n.checkNetworkState(reporter);
                finish();
            }
            if (options.has("register")) {
                doRegister();
            }
            if (options.has("probe")) {
                doProbe();
            }
            if (options.has("g")) {
                doGenerateKeyPair();
                return;
            }
            if (options.has("c")) {
                doCreateContract();
            }
            if (options.has("e")) {
                doExport();
            }
            if (options.has("i")) {
                doImport();
            }
            if (options.has("f")) {
                doFindContracts();
            }
            if (options.has("d")) {
                String source = (String) options.valueOf("d");

                downloadContract(source);

                finish();
            }
            if (options.has("ch")) {
                doCheckContracts();
            }
            if (options.has("revoke")) {
                doRevoke();
            }
            if (options.has("pack-with")) {
                doPackWith();
            }
            if (options.has("unpack")) {
                doUnpackWith();
            }

            usage(null);

        } catch (OptionException e) {
            if (options != null)
                usage("Unrecognized parameter: " + e.getMessage());
            else
                e.printStackTrace();
        } catch (Finished e) {
            if (reporter.isQuiet())
                System.out.println(reporter.reportJson());
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
//            usage(e.getMessage());
            System.exit(100);
        }

    }

    private static String[] unescape(String[] args) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder sb = null;
        for (String s : args) {
            System.out.println(s);
            if (sb != null) {
                if (s.endsWith("\"")) {
                    sb.append(s.substring(0, s.length() - 1));
                    result.add(sb.toString());
                    sb = null;
                }
            } else {
                if (s.startsWith("\"")) {
                    sb = new StringBuilder(s.substring(1));
                } else {
                    result.add(s);
                }
            }

        }
        System.out.println(result);
        return result.toArray(new String[0]);
    }

    private static void doCreateContract() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("c"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        List<String> names = (List) options.valuesOf("name");
        List updateFields = options.valuesOf("set");
        List updateValues = options.valuesOf("value");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            String name = null;
            if (names.size() > s) name = names.get(s);

            HashMap<String, String> updateFieldsHashMap = new HashMap<>();

            Contract contract = Contract.fromDslFile(source);

            try {
                for (int i = 0; i < updateFields.size(); i++) {
                    updateFieldsHashMap.put((String) updateFields.get(i), (String) updateValues.get(i));
                }
            } catch (Exception e) {

            }
            if (updateFieldsHashMap.size() > 0) {
                updateFields(contract, updateFieldsHashMap);
            }

//            keysMap().values().forEach(k -> contract.addSignerKey(k));
//            byte[] data = contract.seal();

            // try sign
            if (name == null) {
                name = source.replaceAll("(?i)\\.(yml|yaml)$", ".unicon");
            }
            contract.seal();
            saveContract(contract, name);
            report("created contract file: " + name);
            checkContract(contract);
        }
        finish();
    }

    private static void doExport() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("e"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        List<String> formats = new ArrayList<String>((List) options.valuesOf("as"));
        List<String> names = (List) options.valuesOf("name");
        List<String> extractKeyRoles = (List) options.valuesOf("extract-key");
        List extractFields = options.valuesOf("get");
        List updateFields = options.valuesOf("set");
        List updateValues = options.valuesOf("value");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            String name = null;
            String format = "json";
            if (names.size() > s) {
                name = names.get(s);
                String extension = "";
                int i = name.lastIndexOf('.');
                if (i > 0) {
                    extension = name.substring(i + 1).toLowerCase();
                }
                switch (extension) {
                    case "json":
                    case "xml":
                    case "yaml":
                    case "yml":
                        format = extension;
                }
            }
            if (formats.size() > s) format = formats.get(s);
            else if (formats.size() == 1) format = formats.get(0);

            HashMap<String, String> updateFieldsHashMap = new HashMap<>();
            Contract contract = loadContract(source);
            if (contract != null) {
                try {
                    for (int i = 0; i < updateFields.size(); i++) {
                        updateFieldsHashMap.put((String) updateFields.get(i), (String) updateValues.get(i));
                    }
                } catch (Exception e) {

                }
                if (updateFieldsHashMap.size() > 0) {
                    updateFields(contract, updateFieldsHashMap);
                }

                if (extractKeyRoles != null && extractKeyRoles.size() > 0) {
                    String extractKeyRole;
                    for (int i = 0; i < extractKeyRoles.size(); i++) {
                        extractKeyRole = extractKeyRoles.get(i);
                        if (name == null) {
                            name = source.replaceAll("(?i)\\.(unicon)$", ".pub");
                        }
                        exportPublicKeys(contract, extractKeyRole, name, options.has("base64"));
                    }
                } else if (extractFields != null && extractFields.size() > 0) {
                    if (name == null) {
                        name = source.replaceAll("(?i)\\.(unicon)$", "_fields." + format);
                    }
                    exportFields(contract, extractFields, name, format, options.has("pretty"));
                } else {
                    if (name == null) {
                        name = source.replaceAll("(?i)\\.(unicon)$", "." + format);
                    }
                    exportContract(contract, name, format, options.has("pretty"));
                }
            }
        }
        finish();
    }

    private static void doImport() throws IOException {

        List<String> sources = new ArrayList<String>((List) options.valuesOf("i"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        List<String> names = (List) options.valuesOf("name");
        List updateFields = options.valuesOf("set");
        List updateValues = options.valuesOf("value");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            String name = null;
            if (names.size() > s) name = names.get(s);

            Contract contract = importContract(source);
            if (contract != null) {
                HashMap<String, String> updateFieldsHashMap = new HashMap<>();
                try {
                    for (int i = 0; i < updateFields.size(); i++) {
                        updateFieldsHashMap.put((String) updateFields.get(i), (String) updateValues.get(i));
                    }
                } catch (Exception e) {

                }
                if (updateFieldsHashMap.size() > 0) {
                    updateFields(contract, updateFieldsHashMap);
                }
                if (name == null) {
                    name = source.replaceAll("(?i)\\.(json|xml|yml|yaml)$", ".unicon");
                }
                contract.seal();
                saveContract(contract, name);
            }
        }
        finish();
    }

    private static void doCheckContracts() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("ch"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);

//            HashMap<String, Contract> contracts = findContracts(source, options.has("r"));
            List<File> files = findFiles(source, options.has("r"));


            if (files.size() > 0) {
                files.forEach(f -> checkFile(f));
//                for (String key : contracts.keySet()) {
//                    report("Checking contract: " + key);
//                    checkContract(contracts.get(key));
//                    report("");
//                }
            } else {
                report("No contracts found at the " + source);
            }
            report("");
//                }
        }
        finish();
    }

    private static void doFindContracts() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("f"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);

            report("Looking for contracts at the " + source);

            HashMap<String, Contract> allFoundContracts = findContracts(source, options.has("r"));

            List<Wallet> wallets = Wallet.determineWallets(new ArrayList<>(allFoundContracts.values()));

            if (wallets.size() > 0) {
                printWallets(wallets);
            } else {
                report("No wallets found");
            }

            if (allFoundContracts.size() > 0) {
                printContracts(allFoundContracts);
            } else {
                report("No contracts found");
            }
        }

        finish();
    }

    private static void doRegister() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("register"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
//            Contract contract = Contract.fromSealedFile(source);
            Contract contract = loadContract(source);

//            contract.seal();
            report("registering the contract " + contract.getId().toBase64String() + " from " + source);
            registerContract(contract);
        }
        finish();
    }

    private static void doProbe() throws IOException {

        List<String> sources = new ArrayList<String>((List) options.valuesOf("probe"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            ItemResult ir = getClientNetwork().check(source);
            report("Universa network has reported the state:");
            report(ir.toString());
        }
        finish();
    }

    private static void doGenerateKeyPair() throws IOException {
        PrivateKey k = new PrivateKey((Integer) options.valueOf("s"));
        String name = (String) options.valueOf("g");
        new FileOutputStream(name + ".private.unikey").write(k.pack());
        new FileOutputStream(name + ".public.unikey").write(k.getPublicKey().pack());
        if (options.has("base64")) {
            new FileOutputStream(name + ".public.unikey.txt")
                    .write(Base64.encodeLines(k.getPublicKey().pack()).getBytes());
        }
        System.out.println("New key pair ready");
    }


    private static void doRevoke() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("revoke"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);
        report("doRevoke");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);

            Contract contract = loadContract(source);
            report("doRevoke " + contract);
            if (contract != null) {
                if (contract.check()) {
                    report("revoke contract from " + source);
                    revokeContract(contract, keysMap().values().toArray(new PrivateKey[0]));
                } else {
                    addErrors(contract.getErrors());
                }
            }
        }

        finish();
    }


    private static void doPackWith() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("pack-with"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);
        List siblingItems = options.valuesOf("add-sibling");
        List revokeItems = options.valuesOf("add-revoke");
        List<String> names = (List) options.valuesOf("name");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            String name = null;
            if (names.size() > s) name = names.get(s);

            Contract contract = loadContract(source, true);
            if (contract != null) {
                if (contract.check()) {
                    report("pack contract from " + source);
                    if (siblingItems != null) {
                        for (Object sibFile : siblingItems) {
                            Contract siblingContract = loadContract((String) sibFile, true);
                            report("add sibling from " + sibFile);
                            contract.addNewItems(siblingContract);
                        }
                    }
                    if (revokeItems != null) {
                        for (Object revokeFile : revokeItems) {
                            Contract revokeContract = loadContract((String) revokeFile, true);
                            report("add revoke from " + revokeFile);
                            contract.addRevokingItems(revokeContract);
                        }
                    }
                    if (name == null) {
                        name = source;
                    }
                    if (siblingItems != null || revokeItems != null) {
                        contract.seal();
                        saveContract(contract, name, true);
                    }
                } else {
                    addErrors(contract.getErrors());
                }
            }
        }

        finish();
    }


    private static void doUnpackWith() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("unpack"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);

            Contract contract = loadContract(source, true);
            if (contract != null) {
                if (contract.check()) {
                    report("unpack contract from " + source);
                    int i = 1;
                    if (contract.getNewItems() != null) {
                        for (Approvable newItem : contract.getNewItems()) {
                            String newItemFileName = source.replaceAll("(?i)\\.(unicon)$", "_new_item_" + i + ".unicon");
                            report("save newItem to " + newItemFileName);
//                            ((Contract) newItem).seal();
                            saveContract((Contract) newItem, newItemFileName);
                            i++;
                        }
                    }
                    i = 1;
                    if (contract.getRevokingItems() != null) {
                        for (Approvable revokeItem : contract.getRevokingItems()) {
                            String revokeItemFileName = source.replaceAll("(?i)\\.(unicon)$", "_revoke_" + i + ".unicon");
                            report("save revokeItem to " + revokeItemFileName);
//                            ((Contract) revokeItem).seal();
                            saveContract((Contract) revokeItem, revokeItemFileName);
                            i++;
                        }
                    }
//                    String parentFileName = source.replaceAll("(?i)\\.(unicon)$", "_parent.unicon");
//                    report("save parentFileName to " + parentFileName);
//                    saveContract(contract, parentFileName);
                } else {
                    addErrors(contract.getErrors());
                }
            }
        }

        finish();
    }

    private static void cleanNonOptionalArguments(List sources) throws IOException {

        List<String> formats = new ArrayList<String>((List) options.valuesOf("as"));
        if (formats != null) {
            sources.removeAll(formats);
            sources.remove("-as");
            sources.remove("--as");
        }
        if (options.has("j")) {
            sources.remove("-j");
            sources.remove("--j");
            sources.remove("-json");
            sources.remove("--json");
        }
        if (options.has("v")) {
            sources.remove("-v");
            sources.remove("--v");
            sources.remove("-verbose");
            sources.remove("--verbose");
        }
        if (options.has("r")) {
            sources.remove("-r");
            sources.remove("--r");
        }
        if (options.has("pretty")) {
            sources.remove("-pretty");
            sources.remove("--pretty");
        }
        List<String> names = (List) options.valuesOf("name");
        if (names != null) {
            sources.removeAll(names);
            sources.remove("-name");
            sources.remove("--name");
        }
        List<String> extractKeyRoles = (List) options.valuesOf("extract-key");
        if (extractKeyRoles != null) {
            sources.removeAll(extractKeyRoles);
            sources.remove("-extract-key");
            sources.remove("--extract-key");
        }
        List extractFields = options.valuesOf("get");
        if (extractFields != null) {
            sources.removeAll(extractFields);
            sources.remove("-get");
            sources.remove("--get");
        }

        List updateFields = options.valuesOf("set");
        if (updateFields != null) {
            sources.removeAll(updateFields);
            sources.remove("-set");
            sources.remove("--set");
        }
        List updateValues = options.valuesOf("value");
        if (extractFields != null) {
            sources.removeAll(updateValues);
            sources.remove("-value");
            sources.remove("--value");
        }
    }

    private static void addErrors(List<ErrorRecord> errors) {
        errors.forEach(e -> addError(e.getError().name(), e.getObjectName(), e.getMessage()));
    }

    ///////////////////////////

    private static PrivateKey privateKey;
    private static Preferences prefs = Preferences.userRoot();

    static public PrivateKey getPrivateKey() throws IOException {
        if (privateKey == null) {
            String keyFileName = prefs.get("privateKeyFile", null);
            if (keyFileName != null) {
                reporter.verbose("Loading private key from " + keyFileName);
                try {
                    privateKey = new PrivateKey(Do.read(keyFileName));
                } catch (IOException e) {
                    reporter.warning("can't read privte Key file: " + keyFileName);
                }

            }
            if (privateKey == null) {
                reporter.warning("\nUser private key is not set, generating new one.");
                reporter.message("new private key has been generated");
                privateKey = new PrivateKey(2048);

                Path keysDir = Paths.get(System.getProperty("user.home") + "/.universa");
                if (!Files.exists(keysDir)) {
                    reporter.verbose("creating new keys directory: " + keysDir.toString());
                    final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
                    final FileAttribute<Set<PosixFilePermission>> ownerOnly = PosixFilePermissions.asFileAttribute(perms);
                    Files.createDirectory(keysDir, ownerOnly);
                }

                Path keyFile = keysDir.resolve("main.private.unikey");
                try (OutputStream out = Files.newOutputStream(keyFile)) {
                    out.write(privateKey.pack());
                }
                final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(keyFile, perms);
                prefs.put("privateKeyFile", keyFile.toString());
                report("new private key has just been generated and stored to the " + keysDir);
            }
        }
        return privateKey;
    }


    private static void printFingerprints() throws IOException {
        Map<String, PrivateKey> kk = keysMap();
        if (kk.isEmpty())
            report("please specify at least one key file with --fingerprints");
        else {
            kk.forEach((name, key) -> {
                report("Fingerprints:");
                report(name + "\t" + Base64.encodeCompactString(key.fingerprint()));
            });
        }
    }

    private static void anonymousKeyPrints() throws IOException {
        Map<String, PrivateKey> kk = keysMap();
        if (kk.isEmpty())
            report("please specify at least one key file with --fingerprints");
        else {
            kk.forEach((name, key) -> {
                report("Anonymous key prints:");
                report(name + "\t" + Base64.encodeCompactString(key.fingerprint()));
            });
        }
    }

    private static void checkFile(File f) {
        try {
            TransactionPack tp = TransactionPack.unpack(Do.read(f), true);
            if (tp.isReconstructed()) {
                report("file " + f + " is a single contract");
            } else {
                report("file " + f + " is a transaction pack");
            }
            System.out.println();
            checkContract(tp.getContract());
        } catch (IOException e) {
            addError("READ_ERROR", f.getPath(), e.toString());
        }
    }

    /**
     * Check contract for errors. Print errors if found.
     *
     * @param contract - contract to check.
     */
    private static void checkContract(Contract contract) {
        // First, check the sealed state
        if (!contract.isOk()) {
            reporter.message("The capsule is not sealed properly:");
            contract.getErrors().forEach(e -> reporter.error(e.getError().toString(), e.getObjectName(), e.getMessage()));
        }
        Yaml yaml = new Yaml();
        if (reporter.isVerboseMode()) {

            report("api level:   " + contract.getApiLevel());
            report("contract id: " + contract.getId().toBase64String());
            report("issued:      " + contract.getIssuedAt());
            report("revision:    " + contract.getRevision());
            report("created:     " + contract.getCreatedAt());
            report("expires:     " + contract.getExpiresAt());

            System.out.println();

            contract.getRevokingItems().forEach(r -> {
                try {
                    ClientNetwork n = getClientNetwork();
                    System.out.println();
                    report("revoking item exists: " + r.getId().toBase64String());
                    report("\tstate: " + n.check(r.getId()));
                    HashId origin = ((Contract) r).getOrigin();
                    boolean m = origin.equals(contract.getOrigin());
                    report("\tOrigin: " + origin);
                    report("\t" + (m ? "matches main contract origin" : "does not match main contract origin"));
                } catch (Exception clientError) {
                    clientError.printStackTrace();
                }
            });

            contract.getNewItems().forEach(n -> {
                System.out.println();
                report("New item exists:      " + n.getId().toBase64String());
                Contract nc = (Contract) n;
                boolean m = nc.getOrigin().equals(contract.getOrigin());
                report("\tOrigin: " + ((Contract) n).getOrigin());
                report("\t" + (m ? "matches main contract origin" : "does not match main contract origin"));
            });

            Set<PublicKey> keys = contract.getSealedByKeys();
            if (keys.size() > 0) {
                report("\nSignature contains " + keys.size() + " valid key(s):\n");
                keys.forEach(k -> {
                    KeyInfo i = k.info();
                    report("\t✔︎ " + i.getAlgorythm() + ":" + i.getKeyLength() * 8 + ":" + i.getBase64Tag());
                });
                report("\nWhich can play roles:\n");
                contract.getRoles().forEach((name, role) -> {
                    String canPlay = role.isAllowedForKeys(keys) ? "✔" : "✘";
                    report("\t" + canPlay + " " + role.getName());
                });

                report("\nAnd have permissions:\n");
                contract.getPermissions().values().forEach(perm -> {
                    String canPlay = perm.isAllowedForKeys(keys) ? "✔" : "✘";
                    report("\t" + canPlay + " " + perm.getName());
                    Binder x = DefaultBiMapper.serialize(perm.getParams());
                    BufferedReader br = new BufferedReader(
                            new StringReader(yaml.dumpAsMap(x)
                            ));
                    try {
                        for (String line; (line = br.readLine()) != null; ) {
                            report("\t    " + line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                reporter.newLine();
            }
        }

        Multimap<String, Permission> permissions = contract.getPermissions();
        Collection<Permission> sjs = permissions.get("split_join");
        if (sjs != null) {
            sjs.forEach(sj -> checkSj(contract, sj));
        }

        contract.check();
        addErrors(contract.getErrors());
        if (contract.getErrors().size() == 0) {
            report("Contract is valid");
        }
    }

    private static void checkSj(Contract contract, Permission sj) {
        Binder params = sj.getParams();
        String fieldName = "state.data." + params.getStringOrThrow("field_name");
        report("splitjoins permission fond on field '" + fieldName + "'");
        StringBuilder outcome = new StringBuilder();
        List<Decimal> values = new ArrayList<>();
        contract.getRevoking().forEach(c -> {
            System.out.println(fieldName);
            Decimal x = new Decimal((String) c.get(fieldName));
            values.add(x);
            if (outcome.length() > 0)
                outcome.append(" + ");
            outcome.append(x.toString());
        });
        List<Contract> news = Do.listOf(contract);
        news.addAll(contract.getNew());
        outcome.append(" -> ");
        news.forEach(c -> {
            if( c != contract )
                outcome.append(" + ");
            Decimal x = new Decimal((String) c.get(fieldName));
            outcome.append(x.toString());
            values.add(x.negate());
        });
        reporter.verbose("operation is: "+ outcome.toString());
        Decimal saldo = values.stream().reduce(Decimal.ZERO, (a, b) -> a.add(b));
        if( saldo.compareTo(Decimal.ZERO) == 0 )
            reporter.verbose("Saldo looks good (zero)");
        else
            reporter.warning("Saldo is not zero: "+saldo);
    }

    /**
     * Check bytes is contract. And if bytes is, check contract for errors. Print errors if found.
     *
     * @param data - data to check.
     *
     * @return true if bytes is Contract and Contract is valid.
     */
    private static Boolean checkBytesIsValidContract(byte[] data) {
        try {
            Contract contract = new Contract(data);
            if (!contract.isOk()) {
                reporter.message("The capsule is not sealed");
                contract.getErrors().forEach(e -> reporter.error(e.getError().toString(), e.getObjectName(), e.getMessage()));
            }
            checkContract(contract);
        } catch (RuntimeException e) {
            addError(Errors.BAD_VALUE.name(), "byte[] data", e.getMessage());
            return false;
        } catch (IOException e) {
            addError(Errors.BAD_VALUE.name(), "byte[] data", e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Import contract from specified yaml, xml or json file.
     *
     * @param sourceName
     *
     * @return loaded and from loaded data created Contract.
     */
    private static Contract importContract(String sourceName) throws IOException {

        String extension = "";
        int i = sourceName.lastIndexOf('.');
        if (i > 0) {
            extension = sourceName.substring(i + 1);
        }

        extension = extension.toLowerCase();

        Contract contract = null;
        File pathFile = new File(sourceName);
        if (pathFile.exists()) {
            try {
                Binder binder;

                FileReader reader = new FileReader(sourceName);
                if ("yaml".equals(extension) || "yml".equals(extension)) {
                    Yaml yaml = new Yaml();
                    binder = Binder.convertAllMapsToBinders(yaml.load(reader));
                } else if ("json".equals(extension)) {
                    Gson gson = new GsonBuilder().create();
                    binder = Binder.convertAllMapsToBinders(gson.fromJson(reader, Binder.class));
                } else {
                    XStream xstream = new XStream(new DomDriver());
                    xstream.registerConverter(new MapEntryConverter());
                    xstream.alias("contract", Binder.class);
                    binder = Binder.convertAllMapsToBinders(xstream.fromXML(reader));
                }

                BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();
                contract = new Contract();
                contract.deserialize(binder, bm);

                report(">>> imported contract: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getCreatedAt()));

                report("import from " + extension + " ok");
            } catch (Exception e) {
                addError(Errors.FAILURE.name(), sourceName, e.getMessage());
            }

        } else {
            addError(Errors.NOT_FOUND.name(), sourceName, "Path " + sourceName + " does not exist");
//            usage("Path " + sourceName + " does not exist");
        }

        return contract;
    }

    /**
     * Load contract from specified path.
     *
     * @param fileName
     * @param fromPackedTransaction - create contract from loaded data with Contract.fromPackedTransaction(data)
     *
     * @return loaded and from loaded data created Contract.
     */
    public static Contract loadContract(String fileName, Boolean fromPackedTransaction) throws IOException {
        Contract contract = null;

        File pathFile = new File(fileName);
        if (pathFile.exists()) {
//            reporter.verbose("Loading contract from: " + fileName);
            Path path = Paths.get(fileName);
            byte[] data = Files.readAllBytes(path);

            if (fromPackedTransaction) {
                contract = Contract.fromPackedTransaction(data);
            } else {
                contract = new Contract(data);
            }
        } else {
            addError(Errors.NOT_FOUND.name(), fileName, "Path " + fileName + " does not exist");
//            usage("Path " + fileName + " does not exist");
        }

        return contract;
    }

    /**
     * Load contract from specified path.
     *
     * @param fileName
     *
     * @return loaded and from loaded data created Contract.
     */
    public static Contract loadContract(String fileName) throws IOException {
        return loadContract(fileName, true);
    }

    /**
     * Export contract to specified xml or json file.
     *
     * @param contract - contract to export.
     * @param fileName - name of file to export to.
     * @param format   - format of file to export to. Can be xml or json.
     */
    private static void exportContract(Contract contract, String fileName, String format) throws IOException {
        exportContract(contract, fileName, format, false);
    }

    /**
     * Export contract to specified xml or json file.
     *
     * @param contract   - contract to export.
     * @param fileName   - name of file to export to.
     * @param format     - format of file to export to. Can be xml, yaml or json.
     * @param jsonPretty - if true, json will be pretty formated.
     */
    private static void exportContract(Contract contract, String fileName, String format, Boolean jsonPretty) throws IOException {

        format = format.toLowerCase();
        report("export format: " + format);

        if (fileName == null) {
            if (testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getCreatedAt());
            } else {
                fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getCreatedAt());
            }
        }

        Binder binder = contract.serialize(DefaultBiMapper.getInstance().newSerializer());

        byte[] data;
        if ("xml".equals(format)) {
            XStream xstream = new XStream(new DomDriver());
            xstream.registerConverter(new MapEntryConverter());
            xstream.alias("contract", Binder.class);
            data = xstream.toXML(binder).getBytes();
        } else if ("yaml".equals(format) || "yml".equals(format)) {
            Yaml yaml = new Yaml();
            data = yaml.dumpAsMap(binder).getBytes();
        } else {
            Gson gson;
            if (jsonPretty) {
                gson = new GsonBuilder().setPrettyPrinting().create();
            } else {
                gson = new GsonBuilder().create();
            }
            String jsonString = gson.toJson(binder);
            data = jsonString.getBytes();
        }
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }


        report(fileName + " export as " + format + " ok");

    }

    /**
     * Export public keys from specified contract.
     *
     * @param contract - contract to export.
     * @param roleName - from which role keys should be exported.
     * @param fileName - name of file to export to.
     */
    private static void exportPublicKeys(Contract contract, String roleName, String fileName, boolean base64) throws IOException {

        if (fileName == null) {
            if (testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_" + roleName + "_public_key";
            } else {
                fileName = "Universa_" + roleName + "_public_key.pub";
            }
        }

        Role role = contract.getRole(roleName);

        if (role != null) {
            Set<PublicKey> keys = role.getKeys();

            int index = 0;
            byte[] data;
            for (PublicKey key : keys) {
                index++;
                data = key.pack();
                String name = fileName.replaceAll("\\.(pub)$", "_key_" + roleName + "_" + index + ".public.unikey");

                if (base64) {
                    name += ".txt";
                }
                try (FileOutputStream fs = new FileOutputStream(name)) {
                    if (base64)
                        fs.write(Base64.encodeLines(data).getBytes());
                    else
                        fs.write(data);
                    fs.close();
                }
            }

            report(roleName + " export public keys ok");
        } else {
            report("export public keys error, role does not exist: " + roleName);
        }
    }

    /**
     * Export fields from specified contract.
     *
     * @param contract   - contract to export.
     * @param fieldNames - list of field names to export.
     * @param fileName   - name of file to export to.
     * @param format     - format of file to export to. Can be xml or json.
     */
    private static void exportFields(Contract contract, List<String> fieldNames, String fileName, String format) throws IOException {
        exportFields(contract, fieldNames, fileName, format, false);
    }

    /**
     * Export fields from specified contract.
     *
     * @param contract   - contract to export.
     * @param fieldNames - list of field names to export.
     * @param fileName   - name of file to export to.
     * @param format     - format of file to export to. Can be xml or json.
     */
    private static void exportFields(Contract contract, List<String> fieldNames, String fileName, String format, Boolean jsonPretty) throws IOException {

        format = format.toLowerCase();
        report("export format: " + format);

        if (fileName == null) {
            if (testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_fields_" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getCreatedAt());
            } else {
                fileName = "Universa_fields_" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getCreatedAt());
            }
        }

        Binder hm = new Binder();

        try {
            for (String fieldName : fieldNames) {
                report("export field: " + fieldName + " -> " + contract.get(fieldName));
                hm.put(fieldName, contract.get(fieldName));
            }

            Binder binder = DefaultBiMapper.getInstance().newSerializer().serialize(hm);

            byte[] data;
            if ("xml".equals(format)) {
                XStream xstream = new XStream(new DomDriver());
                xstream.registerConverter(new MapEntryConverter());
                xstream.alias("fields", Binder.class);
                data = xstream.toXML(binder).getBytes();
            } else if ("yaml".equals(format) || "yml".equals(format)) {
                Yaml yaml = new Yaml();
                data = yaml.dumpAsMap(binder).getBytes();
            } else {
                Gson gson;
                if (jsonPretty) {
                    gson = new GsonBuilder().setPrettyPrinting().create();
                } else {
                    gson = new GsonBuilder().create();
                }
                String jsonString = gson.toJson(binder);
                data = jsonString.getBytes();
            }
            try (FileOutputStream fs = new FileOutputStream(fileName)) {
                fs.write(data);
                fs.close();
            }

            report("export fields as " + format + " ok");

        } catch (IllegalArgumentException e) {
            report("export fields error: " + e.getMessage());
        }
    }

    /**
     * Update fields for specified contract.
     *
     * @param contract - contract for update.
     * @param fields   - map of field names and values.
     */
    private static void updateFields(Contract contract, HashMap<String, String> fields) throws IOException {

        for (String fieldName : fields.keySet()) {
            report("update field: " + fieldName + " -> " + fields.get(fieldName));

            Binder binder = new Binder();
            Binder data = null;

            try {
                XStream xstream = new XStream(new DomDriver());
                xstream.registerConverter(new MapEntryConverter());
                xstream.alias(fieldName, Binder.class);
                data = Binder.convertAllMapsToBinders(xstream.fromXML(fields.get(fieldName)));
            } catch (Exception xmlEx) {
//                xmlEx.printStackTrace();
                try {
                    Gson gson = new GsonBuilder().create();
                    binder = Binder.convertAllMapsToBinders(gson.fromJson(fields.get(fieldName), Binder.class));
                    data = (Binder) data.get(fieldName);
                } catch (Exception jsonEx) {
//                    jsonEx.printStackTrace();
                    try {
                        Yaml yaml = new Yaml();
                        data = Binder.convertAllMapsToBinders(yaml.load(fields.get(fieldName)));
                        data = (Binder) data.get(fieldName);
                    } catch (Exception yamlEx) {
                        yamlEx.printStackTrace();
                    }
                }
            }

            if (data != null) {
                BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();

                binder.put("data", bm.deserialize(data));

                contract.set(fieldName, binder);

                report("update field " + fieldName + " ok");
            } else {
                report("update field " + fieldName + " error: no valid data");
            }
        }

        report("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contract.getExpiresAt()));
    }

    /**
     * Save specified contract to file.
     *
     * @param contract              - contract for update.
     * @param fileName              - name of file to save to.
     * @param fromPackedTransaction - register contract with Contract.getPackedTransaction()
     */
    public static void saveContract(Contract contract, String fileName, Boolean fromPackedTransaction) throws IOException {
        if (fileName == null) {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss").format(contract.getCreatedAt()) + ".unicon";
        }

        keysMap().values().forEach(k -> contract.addSignerKey(k));
        if (keysMap().values().size() > 0) {
            contract.seal();
        }

        byte[] data;
        if (fromPackedTransaction) {
//            contract.seal();
            data = contract.getPackedTransaction();
        } else {
            data = contract.getLastSealedBinary();
        }
        int count = contract.getKeysToSignWith().size();
        if (count > 0)
            report("Contract is sealed with " + count + " key(s)");
        report("Contract is saved to: " + fileName);
        report("Sealed contract size: " + data.length);
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }
        if (contract.check()) {
            report("Sealed contract has no errors");
        } else
            addErrors(contract.getErrors());
    }

    /**
     * Save specified contract to file.
     *
     * @param contract - contract for update.
     * @param fileName - name of file to save to.
     */
    public static void saveContract(Contract contract, String fileName) throws IOException {
        saveContract(contract, fileName, false);
    }

    /**
     * Find wallets in the given path including all subfolders. Looking for files with .unicon extensions.
     *
     * @param path
     *
     * @return
     */
    public static List<Wallet> findWallets(String path) {
        return Wallet.determineWallets(new ArrayList<>(findContracts(path).values()));
    }

    /**
     * Find contracts in the given path including all subfolders. Looking for files with .unicon extensions.
     *
     * @param path
     *
     * @return
     */
    public static HashMap<String, Contract> findContracts(String path) {
        return findContracts(path, true);
    }

    /**
     * Find contracts in the given path. Looking for files with .unicon extensions.
     *
     * @param path
     * @param recursively - make search in subfolders too.
     *
     * @return
     */
    public static HashMap<String, Contract> findContracts(String path, Boolean recursively) {
        HashMap<String, Contract> foundContracts = new HashMap<>();
        List<File> foundContractFiles = new ArrayList<>();

        File pathFile = new File(path);

        if (pathFile.exists()) {

            fillWithContractsFiles(foundContractFiles, path, recursively);

            Contract contract;
            for (File file : foundContractFiles) {
                try {
                    contract = loadContract(file.getAbsolutePath());
                    foundContracts.put(file.getAbsolutePath(), contract);
                } catch (RuntimeException e) {
                    addError(Errors.FAILURE.name(), file.getAbsolutePath(), e.getMessage());
                } catch (IOException e) {
                    addError(Errors.FAILURE.name(), file.getAbsolutePath(), e.getMessage());
                }
            }
        } else {
            addError(Errors.NOT_FOUND.name(), path, "Path " + path + " does not exist");
//            usage("Path " + path + " does not exist");
        }
        return foundContracts;
    }

    public static List<File> findFiles(String path, Boolean recursively) {
        List<File> foundContractFiles = new ArrayList<>();

        File pathFile = new File(path);

        if (pathFile.exists()) {

            fillWithContractsFiles(foundContractFiles, path, recursively);
        } else {
            addError(Errors.NOT_FOUND.name(), path, "Path " + path + " does not exist");
//            usage("Path " + path + " does not exist");
        }
        return foundContractFiles;
    }

    /**
     * Download contract from the specified url.
     *
     * @param url
     *
     * @return
     */
    public static Contract downloadContract(String url) {
        // TODO: Download and check contract.
        report("downloading from " + url);
        return null;
    }

    /**
     * Revoke specified contract and create a revocation transactional contract.
     *
     * @param contract
     *
     * @return TransactionContract - revoking transaction contract.
     */
    public static TransactionContract revokeContract(Contract contract, PrivateKey... key) throws IOException {

        report("keys num: " + key.length);

        TransactionContract tc = new TransactionContract();
        tc.setIssuer(key);
        tc.addContractToRemove(contract);

        tc.seal();

        registerContract(tc, true);

        return tc;
    }

    /**
     * Register a specified contract.
     *
     * @param contract              must be a sealed binary.
     * @param fromPackedTransaction - register contract with Contract.getPackedTransaction()
     */
    public static void registerContract(Contract contract, Boolean fromPackedTransaction) throws IOException {
//        checkContract(contract);
        List<ErrorRecord> errors = contract.getErrors();
        if (errors.size() > 0) {
            report("contract has errors and can't be submitted for registration");
            report("contract id: " + contract.getId().toBase64String());
            addErrors(errors);
        } else {
//            contract.seal();

            ItemResult r;
            if (fromPackedTransaction) {
                r = getClientNetwork().register(contract.getPackedTransaction());
            } else {
                r = getClientNetwork().register(contract.getLastSealedBinary());
            }
            report("submitted with result:");
            report(r.toString());
        }
    }

    /**
     * Register a specified contract.
     *
     * @param contract must be a sealed binary file.
     */
    public static void registerContract(Contract contract) throws IOException {
        registerContract(contract, true);
    }


    /**
     * Fill given List with contract files, found in given path recursively.
     * <p>
     * Does not return new Lists but put found files into the given List for optimisation purposes.
     *
     * @param foundContractFiles
     * @param path
     */
    private static void fillWithContractsFiles(List<File> foundContractFiles, String path, Boolean recursively) {
        File pathFile = new File(path);

        if (pathFile.exists()) {
            ContractFilesFilter filter = new ContractFilesFilter();
            DirsFilter dirsFilter = new DirsFilter();

            if (pathFile.isDirectory()) {
                File[] foundFiles = pathFile.listFiles(filter);
                foundContractFiles.addAll(Arrays.asList(foundFiles));

                if (recursively) {
                    File[] foundDirs = pathFile.listFiles(dirsFilter);
                    for (File file : foundDirs) {
                        fillWithContractsFiles(foundContractFiles, file.getPath(), true);
                    }
                }
            } else {
//                if (filter.accept(pathFile)) {
                foundContractFiles.add(pathFile);
//                }
            }
        }
    }

    /**
     * Just print wallets info to console.
     *
     * @param wallets
     */
    private static void printWallets(List<Wallet> wallets) {
        reporter.message("");

        List<Contract> foundContracts = new ArrayList<>();
        for (Wallet wallet : wallets) {
            foundContracts.addAll(wallet.getContracts());

            reporter.message("found wallet: " + wallet.toString());
            reporter.verbose("");

            HashMap<String, Integer> balance = new HashMap<String, Integer>();
            Integer numcoins;
            String currency;
            for (Contract contract : wallet.getContracts()) {
                try {
                    numcoins = contract.getStateData().getIntOrThrow(AMOUNT_FIELD_NAME);
                    currency = contract.getDefinition().getData().getOrThrow("currency_code");
                    if (balance.containsKey(currency)) {
                        balance.replace(currency, balance.get(currency) + numcoins);
                    } else {
                        balance.put(currency, numcoins);
                    }
                    reporter.verbose("found coins: " +
                                             contract.getDefinition().getData().getOrThrow("name") +
                                             " -> " + numcoins + " (" + currency + ") ");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            reporter.verbose("");
            reporter.message("total in the wallet: ");
            for (String c : balance.keySet()) {
                reporter.message(balance.get(c) + " (" + c + ") ");
            }
        }
    }

    /**
     * Just print contracts info to console.
     *
     * @param contracts
     */
    private static void printContracts(HashMap<String, Contract> contracts) {
        reporter.verbose("");
        reporter.verbose("found contracts list: ");
        reporter.verbose("");
        for (String key : contracts.keySet()) {
            try {
                reporter.verbose(key + ": " +
                                         "contract created at " +
                                         DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contracts.get(key).getCreatedAt()) +
                                         ": " +
                                         contracts.get(key).getDefinition().getData().getString("description")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void finish() {
        // print reports if need
        throw new Finished();
    }

    private static void report(String message) {
        reporter.message(message);
    }

    private static void addError(String code, String object, String message) {
        reporter.error(code, object, message);
    }

    static private void usage(String text) {
        boolean error = false;
        PrintStream out = System.out;
        if (text != null) {
            out = System.err;
            error = true;
        }
        out.println("\nUniversa client tool, v. " + CLI_VERSION + "\n");

        if (options == null)
            System.err.println("error while parsing command linee");
        else {
            Integer columns = (Integer) options.valueOf("term-width");
            if (columns == null)
                columns = 120;

            if (text != null)
                out.println("ERROR: " + text + "\n");
            try {
                parser.formatHelpWith(new BuiltinHelpFormatter(columns, 2));
                parser.printHelpOn(out);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.exit(100);
    }

    public static void setTestMode() {
        testMode = true;
    }

    public static void setTestRootPath(String rootPath) {
        testRootPath = rootPath;
    }

    public static Reporter getReporter() {
        return reporter;
    }

    public static synchronized ClientNetwork getClientNetwork() throws IOException {
        if (clientNetwork == null)
            clientNetwork = new ClientNetwork();
        return clientNetwork;
    }

    public static synchronized Map<String, PrivateKey> keysMap() throws IOException {
        if (keyFiles == null) {
            keyFiles = new HashMap<>();
            for (String fileName : keyFileNames) {
//                PrivateKey pk = new PrivateKey(Do.read(fileName));
//                keyFiles.put(fileName, pk);
                try {
                    PrivateKey pk = PrivateKey.fromPath(Paths.get(fileName));
                    keyFiles.put(fileName, pk);
                } catch (IOException e) {
                    addError(Errors.NOT_FOUND.name(), fileName.toString(), "failed to load key file: " + e.getMessage());
                }
            }
        }
        return keyFiles;
    }

    public static class Finished extends RuntimeException {
    }

    static class ContractFilesFilter implements FileFilter {

        List<String> extensions = Arrays.asList("unicon");

        ContractFilesFilter() {
        }

        public boolean accept(File pathname) {
            String extension = getExtension(pathname);
            for (String unc : extensions) {
                if (unc.equals(extension)) {
                    return true;
                }
            }
            return false;
        }

        private String getExtension(File pathname) {
            String filename = pathname.getPath();
            int i = filename.lastIndexOf('.');
            if (i > 0 && i < filename.length() - 1) {
                return filename.substring(i + 1).toLowerCase();
            }
            return "";
        }

    }

    static class DirsFilter implements FileFilter {

        DirsFilter() {
        }

        public boolean accept(File pathname) {
            return pathname.isDirectory();
        }

    }

    public static class MapEntryConverter implements Converter {
        public boolean canConvert(Class c) {
            return AbstractMap.class.isAssignableFrom(c);
        }

        public void marshal(Object value, HierarchicalStreamWriter writer, MarshallingContext context) {
            AbstractMap<String, Object> map = (AbstractMap<String, Object>) value;
            Object checkingValue;
            String checkingKey;
            String hasType = "";
            for (String key : map.keySet()) {
                if ("__type".equals(key)) {
                    hasType = (String) map.get(key);
                    break;
                }
            }

            switch (hasType) {
                case MapEntryConverterKnownTypes.UNIXTIME:
                    writer.startNode(hasType);
                    ZonedDateTime date = ZonedDateTime.ofInstant(Instant.ofEpochSecond((Long) map.get("seconds")),
                                                                 ZoneOffset.systemDefault());
                    writer.setValue(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss [XXX]").format(date));
                    writer.endNode();
                    break;

                default:
                    if (checkForKnownTypes(hasType)) {
                        writer.startNode(hasType);
                    }
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        checkingKey = entry.getKey();
                        checkingValue = entry.getValue();
                        if (!"__type".equals(checkingKey) || !checkForKnownTypes(hasType)) {
                            if (!Character.isDigit(checkingKey.charAt(0))) {
                                writer.startNode(checkingKey);
                            } else {
                                writer.startNode("__digit_" + checkingKey);
                            }
                            if (checkingValue != null) {
                                if (checkingValue instanceof Integer) {
                                    writer.setValue(String.valueOf(checkingValue));
                                } else if (checkingValue instanceof AbstractMap) {
                                    context.convertAnother(checkingValue);
                                } else if (checkingValue instanceof List) {
                                    writer.addAttribute("isArray", "true");
                                    for (Object o : (List) checkingValue) {
                                        writer.startNode("item");
                                        context.convertAnother(o);
                                        writer.endNode();
                                    }
                                } else {
                                    writer.setValue(checkingValue.toString());
                                }
                            }
                            writer.endNode();
                        }
                    }
                    if (checkForKnownTypes(hasType)) {
                        writer.endNode();
                    }

            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Map<String, Object> map = new HashMap<String, Object>();

            Object checkingValue;
            String checkingKey;
            String isArray;
            while (reader.hasMoreChildren()) {
                reader.moveDown();
                checkingKey = reader.getNodeName();
                checkingValue = reader.getValue();
                isArray = reader.getAttribute("isArray");
                try {
                    String hasType = checkingKey;
                    if (checkForKnownTypes(hasType)) {
                        switch (hasType) {
                            case MapEntryConverterKnownTypes.UNIXTIME:
                                map.put("__type", checkingKey);
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss [XXX]");
                                ZonedDateTime date = ZonedDateTime.parse((String) checkingValue, formatter);
                                map.put("seconds", date.toEpochSecond());
                                break;

                            default:
                                map.put("__type", checkingKey);
                                map.putAll((AbstractMap) context.convertAnother(checkingValue, AbstractMap.class));
                        }
                    } else {
                        if (checkingKey.length() > 8 && "__digit_".equals(checkingKey.substring(0, 8))) {
                            checkingKey = checkingKey.substring(8, checkingKey.length());
                        }
                        if ("true".equals(isArray)) {
                            List<Object> list = new ArrayList<>();
                            while (reader.hasMoreChildren()) {
                                reader.moveDown();
                                list.add(context.convertAnother(reader.getValue(), AbstractMap.class));
                                reader.moveUp();
                            }
                            map.put(checkingKey, list);
                        } else {
                            if (reader.hasMoreChildren()) {
                                map.put(checkingKey, context.convertAnother(checkingValue, AbstractMap.class));
                            } else {
                                if ("".equals(checkingValue)) {
                                    map.put(checkingKey, null);
                                } else {
                                    map.put(checkingKey, checkingValue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    map.put(checkingKey, null);
                    e.printStackTrace();
                }
                reader.moveUp();
            }
            return map;
        }

        private Boolean checkForKnownTypes(String type) {
            switch (type) {
                case MapEntryConverterKnownTypes.UNIXTIME:
                case MapEntryConverterKnownTypes.SIMPLE_ROLE:
                case MapEntryConverterKnownTypes.KEY_RECORD:
                case MapEntryConverterKnownTypes.RSA_PUBLIC_KEY:
                case MapEntryConverterKnownTypes.REFERENCE:
                case MapEntryConverterKnownTypes.ROLE_LINK:
                case MapEntryConverterKnownTypes.CHANGE_OWNER_PERMISSION:
                case MapEntryConverterKnownTypes.REVOKE_PERMISSION:
                case MapEntryConverterKnownTypes.BINARY:
                    return true;
            }
            return false;
        }
    }

    public static class MapEntryConverterKnownTypes {

        static public final String UNIXTIME = "unixtime";
        static public final String SIMPLE_ROLE = "SimpleRole";
        static public final String KEY_RECORD = "KeyRecord";
        static public final String RSA_PUBLIC_KEY = "RSAPublicKey";
        static public final String REFERENCE = "com.icodici.universa.contract.Reference";
        static public final String ROLE_LINK = "RoleLink";
        static public final String CHANGE_OWNER_PERMISSION = "ChangeOwnerPermission";
        static public final String REVOKE_PERMISSION = "RevokePermission";
        static public final String BINARY = "binary";


    }
}
