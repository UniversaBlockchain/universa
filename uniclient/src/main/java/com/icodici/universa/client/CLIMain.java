/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.icodici.crypto.Error;
import com.icodici.crypto.KeyInfo;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.crypto.SymmetricKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.universa.*;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.Quantiser;
import com.icodici.universa.node2.network.BasicHttpClientSession;
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
import net.sergeych.boss.Boss;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.*;
import net.sergeych.utils.Base64;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static java.util.Arrays.asList;

public class CLIMain {

    private static final String CLI_VERSION = Core.VERSION;

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;
    private static String testRootPath;
    private static String nodeUrl;

    private static Reporter reporter = new Reporter();
    private static ClientNetwork clientNetwork;
    private static List<String> keyFileNames = new ArrayList<>();
    private static Map<String, PrivateKey> keyFiles;
    private static List<String> keyFileNamesContract = new ArrayList<>();
    private static Map<String, PrivateKey> keyFilesContract;

    public static final String AMOUNT_FIELD_NAME = "amount";

    static public void main(String[] args) throws IOException {
        // when we run untit tests, it is important:
//        args = new String[]{"-c", "/Users/sergeych/dev/new_universa/uniclient-testcreate/simple_root_contract.yml"};

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
                accepts("wait", "with --register,  wait for network consensus up to specified number of milliseconds.")
                        .withOptionalArg().ofType(Integer.class).defaultsTo(5000)
                        .describedAs("milliseconds");
                acceptsAll(asList("j", "json"), "Return result in json format.");
                acceptsAll(asList("v", "verbose"), "Provide more detailed information.");
                acceptsAll(asList("network"), "Check network status.");
                accepts("register", "register a specified contract, must be a sealed binary file")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class).
                        describedAs("contract.unicon");
                accepts("tu", "Use with -register. Point to file with your transaction units. " +
                        "Use it to pay for contract's register.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("tu.unicon");
                accepts("amount", "Use with -register and -tu. " +
                        "Command is set amount of transaction units will be pay for contract's register.")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(1)
                        .describedAs("tu amount");
                accepts("amount-storage", "Use with -register and -tu. " +
                        "Command is set amount-storage of storage units will be pay for contract's register.")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(0)
                        .describedAs("tu amount-storage");
                accepts("tutest", "Use with -register and -tu. Key is point to use test transaction units.");
                accepts("no-exit", "Used for tests. Uniclient d");
                accepts("probe", "query the state of the document in the Universa network")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("base64_id");
                accepts("resync", "start resync of the document in the Universa network")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("base64_id");
                accepts("node", "used with to specify node number to connect to")
                        .withRequiredArg()
                        .ofType(Integer.class);
                accepts("skey", "used with to specify session private key file")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file");
                ;
                acceptsAll(asList("k", "keys"), "List of comma-separated private key files to" +
                        "use to sign contract with, if appropriated.")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("key_file");
                acceptsAll(asList("k-contract", "keys-contract"), "Use with -register by paying parcel. " +
                        "List of comma-separated private key files to" +
                        "use to sign contract in paying parcel, if appropriated.")
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
                acceptsAll(asList("output", "o"), "Use with -e, --export or -i, --import commands. " +
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
                acceptsAll(asList("split-off"), "Joins specified contract with ones passed as non-optional arguments" +
                        "and splits parts  off the result and transfers ownership of these parts to specified addresses. " +
                        "Use with --parts and --owners to specify the amounts and new owners")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file.unicon");

                accepts("field-name", "Use with split-off to specify the field name to split. ")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("amount")
                        .describedAs("field_name");

                accepts("parts", "Use with split-off to specify the ammount to split of the main contract. ")
                        .withRequiredArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("amount");
                accepts("owners", "Use with split-off to specify new owners of the parts. ")
                        .withRequiredArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("address");

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
                        .describedAs("file.unicon");
                accepts("add-referenced", "Use with --pack-with command. " +
                        "Option add referenced item to transaction pack.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file.unicon");
                accepts("revision-of", "Use with --import command. " +
                        "Option adds parent to revokes.")
                        .withRequiredArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("parent.unicon");
                acceptsAll(asList("unpack"), "Extracts revoking and new items from contracts and save them.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file.unicon");
                acceptsAll(asList("cost"), "Print cost of operations for contracts with given files of contracts. " +
                        "Can be used as key with -register command.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file");
                accepts("anonymize", "Key erase public key from given contract for role given with -role key and replace " +
                        "it with anonymous id for that public key. If -role key is missed will anonymize all roles. " +
                        "After anonymizing contract will be saved as <file_name>_anonymized.unicon. " +
                        "If you want to save with custom name use -name keys.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("file");
                accepts("role", "Use with -anonymize. Set the role name for anonymizing.")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("role_name");
                accepts("address", "Generate address from key. Path to key define in parameter -address. " +
                        "For generate short address use parameter -short.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file");
                accepts("short", "Generate short addres.");
                accepts("address-match", "Matching address with key from file. Address define in parameter -address-match." +
                        "Path to key define in parameter -keyfile.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file");
                accepts("keyfile", "Path to key for matching with address.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file");
                accepts("folder-match", "Associates the entered address with the key file in the specified directory. Path to directory define in parameter -folder-match. "+
                        "Address define in parameter -addr.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("file");
                accepts("addr", "Address for finding key in folder.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("address");
                accepts("id", "extract ID from a packed contract").withRequiredArg().ofType(String.class).describedAs("packed contract");

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

            if (options.has("node")) {
                setNodeNumber((Integer) options.valueOf("node"));
            }

            if(options.has("skey")) {
                setPrivateKey(new PrivateKey(Do.read((String) options.valueOf("skey"))));
            }


            if (options.has("v")) {
                setVerboseMode(true);
            } else {
                setVerboseMode(false);
            }
            if (options.has("k")) {
                keyFileNames = (List<String>) options.valuesOf("k");
            } else {
                keyFileNames = new ArrayList<>();
            }

            keyFiles = null;

            if (options.has("k-contract")) {
                keyFileNamesContract = (List<String>) options.valuesOf("k-contract");
            } else {
                keyFileNamesContract = new ArrayList<>();
                keyFilesContract = null;
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
            if(options.has("id")) {
                doShowId();
            }
            if (options.has("register")) {
                doRegister();
            }
            if (options.has("probe")) {
                doProbe();
            }
            if (options.has("resync")) {
                doResync();
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

            if (options.has("split-off")) {
                doSplit();
            }

            if (options.has("pack-with")) {
                doPackWith();
            }
            if (options.has("unpack")) {
                doUnpackWith();
            }
            if (options.has("cost")) {
                doCost();
            }
            if (options.has("anonymize")) {
                doAnonymize();
            }
            if (options.has("address")) {
                doCreateAddress((String) options.valueOf("address"), options.has("short"));
            }
            if (options.has("address-match")) {
                doAddressMatch((String) options.valueOf("address-match"), (String) options.valueOf("keyfile"));
            }
            if (options.has("folder-match")) {
                doSelectKeyInFolder((String) options.valueOf("folder-match"), (String) options.valueOf("addr"));
            }
            usage(null);

        } catch (OptionException e) {
            if (options != null)
                usage("Unrecognized parameter: " + e.getMessage());
            else
                usage("No options: " + e.getMessage());
        } catch (Finished e) {
            if (reporter.isQuiet())
                System.out.println(reporter.reportJson());
            if(!options.has("no-exit")) {
                System.exit(e.getStatus());
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.toString());
            if (options.has("verbose"))
                e.printStackTrace();
            System.out.println("\nShow usage: uniclient --help");
//            usage("Error: " + e.getMessage());
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

        List<String> names = (List) options.valuesOf("output");
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

            keysMap().values().forEach(k -> contract.addSignerKey(k));
//            byte[] data = contract.seal();

            // try sign
            if (name == null) {
                name = new FilenameTool(source).setExtension("unicon").toString();
            }
            contract.seal();
            saveContract(contract, name);
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
        List<String> names = (List) options.valuesOf("output");
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
                for (int i = 0; i < updateFields.size(); i++) {
                    try {
                        updateFieldsHashMap.put((String) updateFields.get(i), (String) updateValues.get(i));
                    } catch (Exception e) {
                        addError(Errors.FAILURE.name(), (String) updateFields.get(i), "failed to set value " + (String) updateValues.get(i) + ": " + e.getMessage());
                    }
                }
                if (updateFieldsHashMap.size() > 0) {
                    updateFields(contract, updateFieldsHashMap);
                }

                if (extractKeyRoles != null && extractKeyRoles.size() > 0) {
                    String extractKeyRole;
                    for (int i = 0; i < extractKeyRoles.size(); i++) {
                        extractKeyRole = extractKeyRoles.get(i);
                        if (name == null) {
                            name = new FilenameTool(source).setExtension("pub").toString();
                        }
                        exportPublicKeys(contract, extractKeyRole, name, options.has("base64"));
                    }
                } else if (extractFields != null && extractFields.size() > 0) {
                    if (name == null) {
                        name = new FilenameTool(source).setExtension(format).addSuffixToBase("_fields").toString();
                    }
                    exportFields(contract, extractFields, name, format, options.has("pretty"));
                } else {
                    if (name == null) {
                        name = new FilenameTool(source).setExtension(format).toString();
                    }
                    exportContract(contract, name, format, options.has("pretty"));
                }
            }
        }
        finish();
    }

    private static void doImport() throws IOException {

        List<String> sources = new ArrayList<String>((List) options.valuesOf("i"));
        List parentItems = options.valuesOf("revision-of");
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        List<String> names = (List) options.valuesOf("output");
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
                    name = new FilenameTool(source).setExtension("unicon").toString();
                }

                if(parentItems.size() > s) {
                    Contract parent = loadContract((String) parentItems.get(s));
                    if(parent != null) {
                        contract.addRevokingItems(parent);
                    }
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
        String tuSource = (String) options.valueOf("tu");
        int tuAmount = (int) options.valueOf("amount");
        int tuAmountStorage = (int) options.valueOf("amount-storage");
        boolean tutest = options.has("tutest");
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            Contract contract = loadContract(source);

            Contract tu = null;
            if(tuSource != null) {
                tu = loadContract(tuSource, true);
                report("load payment revision: " + tu.getState().getRevision() + " id: " + tu.getId());
            }

            Set<PrivateKey> tuKeys = new HashSet<>(keysMap().values());
            if(contract != null) {
                if(tu != null && tuKeys != null && tuKeys.size() > 0) {
                    Parcel parcel;
                    Contract newTUContract;
                    if(tuAmountStorage == 0) {
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + tuAmount + " TU");
                        report("cnotactId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterContract(contract, tu, tuAmount, tuKeys, tutest);
                        newTUContract = parcel.getPaymentContract();
                    } else { // if storage payment
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + tuAmount + " TU (and " + tuAmountStorage + " TU for storage)");
                        report("cnotactId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterPayingParcel(contract, tu, tuAmount, tuAmountStorage, tuKeys, tutest);
                        newTUContract = parcel.getPayloadContract().getNew().get(0);
                    }

                    if (parcel != null) {
                        report("save payment revision: " + newTUContract.getState().getRevision() + " id: " + newTUContract.getId());

                        CopyOption[] copyOptions = new CopyOption[]{
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        };
                        String tuDest = new FilenameTool(tuSource).addSuffixToBase("_rev" + tu.getRevision()).toString();
                        tuDest = FileTool.writeFileContentsWithRenaming(tuDest, new byte[0]);
                        if (tuDest != null) {
                            Files.move(Paths.get(tuSource), Paths.get(tuDest), copyOptions);
                            if (saveContract(newTUContract, tuSource, true, false)) {
                                ItemResult ir = registerParcel(parcel, (int) options.valueOf("wait"));
                                if(ir.state != ItemState.APPROVED) {
                                    addErrors(ir.errors);
                                }
                            } else {
                                addError(Errors.COMMAND_FAILED.name(),tuSource,"unable to backup tu revision");
                            }
                        } else {
                            addError(Errors.COMMAND_FAILED.name(),tuSource,"unable to backup tu revision");
                        }
                    } else {
                        addError(Errors.COMMAND_FAILED.name(),"parcel","unable to prepare parcel");
                    }

                } else {
//                    report("registering the contract " + contract.getId().toBase64String() + " from " + source);
//                    registerContract(contract, (int) options.valueOf("wait"));
                    addError(Errors.COMMAND_FAILED.name(), tuSource, "payment contract or private keys for payment contract is missing");
                }
            }
        }

        // print cost of processing if asked
        if (options.has("cost")) {
            doCost();
        } else {
            finish();
        }
    }

    private static void doShowId() throws Exception {
        String contractFile = (String) options.valueOf("id");
        Contract c = Contract.fromPackedTransaction(Files.readAllBytes(Paths.get(contractFile)));
        reporter.message(c.getId().toBase64String());
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
//            report("Universa network has reported the state:");
//            report(ir.toString());
        }
        finish();
    }

    private static void doResync() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("resync"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);
        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            ItemResult ir = getClientNetwork().resync(source);
            report("Node has reported the state:");
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
        String tuSource = (String) options.valueOf("tu");
        int tuAmount = (int) options.valueOf("amount");
        boolean tutest = options.has("tutest");
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
            Contract tu = null;
            if(tuSource != null) {
                tu = loadContract(tuSource, true);
                report("load payment revision: " + tu.getState().getRevision() + " id: " + tu.getId());
            }

            Set<PrivateKey> tuKeys = new HashSet<>(keysMap().values());
            report("tuKeys num: " + tuKeys.size());
            if (contract != null) {
                if(tu != null && tuKeys != null && tuKeys.size() > 0) {
                    report("registering the paid contract " + contract.getId() + " from " + source
                            + " for " + tuAmount + " TU");
                    Parcel parcel = null;
                    try {
                        if (contract.check()) {
                            report("revoke contract from " + source);
                            parcel = revokeContract(contract, tu, tuAmount, tuKeys, tutest, keysMap().values().toArray(new PrivateKey[0]));
                        } else {
                            addErrors(contract.getErrors());
                        }
                    } catch (Quantiser.QuantiserException e) {
                        addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
                    }
                    if(parcel != null) {
                        Contract newTUContract = parcel.getPaymentContract();
                        report("save payment revision: " + newTUContract.getState().getRevision() + " id: " + newTUContract.getId());

                        CopyOption[] copyOptions = new CopyOption[]{
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        };
                        String tuDest = new FilenameTool(tuSource).addSuffixToBase("_rev" + tu.getRevision()).toString();
                        tuDest = FileTool.writeFileContentsWithRenaming(tuDest, new byte[0]);
                        if (tuDest != null) {
                            Files.move(Paths.get(tuSource), Paths.get(tuDest), copyOptions);
                            if (saveContract(newTUContract, tuSource, true, false)) {
                                ItemResult ir = registerParcel(parcel, (int) options.valueOf("wait"));
                                if(ir.state != ItemState.APPROVED) {
                                    addErrors(ir.errors);
                                }
                            } else {
                                addError(Errors.COMMAND_FAILED.name(),tuSource,"unable to backup tu revision");
                            }
                        } else {
                            addError(Errors.COMMAND_FAILED.name(),tuSource,"unable to backup tu revision");
                        }
                    }
                } else {
                    try {
                        if (contract.check()) {
                            report("revoke contract from " + source);
                            revokeContract(contract, keysMap().values().toArray(new PrivateKey[0]));
                        } else {
                            addErrors(contract.getErrors());
                        }
                    } catch (Quantiser.QuantiserException e) {
                        addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
                    }
                }
            }
        }

        finish();
    }


    private static void doSplit() throws IOException {
        String source = (String) options.valueOf("split-off");
        String fieldName = (String) options.valueOf("field-name");

        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());

        List<String> names = (List) options.valuesOf("output");

        List parts = options.valuesOf("parts");
        if(parts == null)
            parts = new ArrayList();
        List owners = options.valuesOf("owners");
        if(owners == null)
            owners = new ArrayList();

        if (parts.size() != owners.size()) {
            System.out.println("Specify the same number of parts and owners to split the contract");
            finish();
        }


        Contract contract = loadContract(source, true);
        if (contract != null) {
            try {
                contract = contract.createRevision();

                Decimal value = new Decimal(contract.getStateData().getStringOrThrow(fieldName));
                for(String filename : nonOptions) {
                    Contract contractToJoin = loadContract(filename,true);
                    if(contractToJoin != null) {
                        value = value.add(new Decimal(contractToJoin.getStateData().getStringOrThrow(fieldName)));
                        contract.addRevokingItems(contractToJoin);
                    }
                }
                Contract[] partContracts = null;
                if(parts.size() > 0) {
                    partContracts = contract.split(parts.size());
                    for (int i = 0; i < partContracts.length; i++) {
                        Decimal partSize = new Decimal((String) parts.get(i));
                        value = value.subtract(partSize);
                        partContracts[i].getStateData().set(fieldName, partSize);
                        try {
                            SimpleRole role = new SimpleRole("owner", asList(new KeyAddress((String) owners.get(i))));
                            partContracts[i].registerRole(role);
                            partContracts[i].setCreatorKeys(keysMap().values());
                        } catch (KeyAddress.IllegalAddressException e) {
                            System.out.println("Not a valid address: " + owners.get(i));
                            finish();
                        }
                    }
                }

                contract.getStateData().set(fieldName,value);
                contract.setCreatorKeys(keysMap().values());

                FilenameTool nameTool;
                if(partContracts != null) {
                    for (int i = 0; i < partContracts.length; i++) {
                        if(names.size() > i + 1) {
                            nameTool = new FilenameTool(names.get(i+1));
                        } else {
                            if(names.size() > 0) {
                                nameTool = new FilenameTool(names.get(0));
                            } else {
                                nameTool = new FilenameTool(source);
                            }
                            nameTool.addSuffixToBase("_"+i);
                        }
                        saveContract(partContracts[i], nameTool.toString(), true, true);
                    }
                }

                if(names.size() > 0) {
                    nameTool = new FilenameTool(names.get(0));
                } else {
                    nameTool = new FilenameTool(source);
                    nameTool.addSuffixToBase("_main");
                }

                saveContract(contract, nameTool.toString(), true, true);

            } catch (Quantiser.QuantiserException e) {
                addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
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
        List referencedItems = options.valuesOf("add-referenced");
        List<String> names = (List) options.valuesOf("output");

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            String name = null;
            if (names.size() > s) name = names.get(s);

            Contract contract = loadContract(source, true);
            try {
                if (contract != null) {
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
                    }

                    Set<Contract> referencedContracts = null;
                    if(referencedItems != null) {
                        referencedContracts = new HashSet<>();
                        for (Object referencedFile : referencedItems) {
                            Contract referencedContract = loadContract((String) referencedFile, true);
                            report("add referenced item from " + referencedFile);
                            referencedContracts.add(referencedContract);
                        }
                    }
                    saveContract(contract, name, true, true,referencedContracts);

                }
            } catch (Quantiser.QuantiserException e) {
                addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
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
                try {
                    if (contract.check()) {
                        report("unpack contract from " + source);
                        int i = 1;
                        if (contract.getNewItems() != null) {
                            for (Approvable newItem : contract.getNewItems()) {
                                String newItemFileName = new FilenameTool(source).addSuffixToBase("_new_item_" + i).toString();
                                report("save newItem to " + newItemFileName);
    //                            ((Contract) newItem).seal();
                                saveContract((Contract) newItem, newItemFileName);
                                i++;
                            }
                        }
                        i = 1;
                        if (contract.getRevokingItems() != null) {
                            for (Approvable revokeItem : contract.getRevokingItems()) {
                                String revokeItemFileName = new FilenameTool(source).addSuffixToBase("_revoke_" + i).toString();
                                report("save revokeItem to " + revokeItemFileName);
                                saveContract((Contract) revokeItem, revokeItemFileName);
                                i++;
                            }
                        }
                    } else {
                        addErrors(contract.getErrors());
                    }
                } catch (Quantiser.QuantiserException e) {
                    addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
                }
            }
        }

        finish();
    }


    private static void doCost() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("cost"));
        // if command use as key for -register command
        List<String> registerSources = new ArrayList<String>((List) options.valuesOf("register"));
        for (String opt : registerSources) {
            sources.addAll(asList(opt.split(",")));
        }
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);

            ContractFileTypes fileType = getFileType(source);

            report("");
            report("Calculating cost of " + source + ", type is " + fileType + "...");

            // Here should repeat procedure of contract processi on the Node
            // (Contract.fromPackedTransaction() -> Contract(byte[], TransactionPack) -> Contract.check() -> Contract.getNewItems.check())
            Contract contract = null;
            if (fileType == ContractFileTypes.BINARY) {
                contract = loadContract(source);
            } else {
//                contract = Contract.fromPackedTransaction(importContract(source).getPackedTransaction());
                addError(Errors.COMMAND_FAILED.name(), source, "Contract should be sealed binary");
            }

            if(contract != null) {
                try {
                    contract.check();
                } catch (Quantiser.QuantiserException e) {
                    addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
                }
//                addErrors(contract.getErrors());
//                if (contract.getErrors().size() == 0) {
//                    report("Contract is valid");
//                }

                printProcessingCost(contract);
            }
        }

        finish();
    }

    private static void doAnonymize() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("anonymize"));
        List<String> roles = new ArrayList<String>((List) options.valuesOf("role"));
        List<String> names = (List) options.valuesOf("output");
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            Contract contract = loadContract(source);
            if (contract != null) {
                if(roles.size() <= 0) {
                    roles = new ArrayList<>(contract.getRoles().keySet());
                }
                for (String roleName : roles) {

                    report("Anonymizing role " + roleName + " in " + source + "...");
                    contract.anonymizeRole(roleName);
                    contract.seal();
                }
                if (names.size() > s) {
                    saveContract(contract, names.get(s), true, false);
                } else {
                    saveContract(contract, new FilenameTool(source).addSuffixToBase("_anonymized").toString(), true, false);
                }
            }
        }

        finish();
    }

    private static void doCreateAddress(String keyFilePath, boolean bShort) throws IOException {

        report("Generate " + (bShort ? "short" : "long") + " address from key: " + keyFilePath);

        PrivateKey key = new PrivateKey(Do.read(keyFilePath));
        KeyAddress address = new KeyAddress(key.getPublicKey(), 0, !bShort);

        report("Address: " + address.toString());

        finish();
    }

    private static void doAddressMatch(String address, String keyFilePath) throws IOException {

        boolean bResult = false;

        try {
            PrivateKey key = new PrivateKey(Do.read(keyFilePath));

            KeyAddress keyAddress = new KeyAddress(address);

            bResult = keyAddress.isMatchingKey(key.getPublicKey());
        }
        catch (Exception e) {};

        report("Matching address: " + address + " with key: "+ keyFilePath);
        report("Matching result: " + bResult);

        finish();
    }

    private static void doSelectKeyInFolder(String keysPath, String address) throws IOException {

        File folder = new File(keysPath);
        KeyAddress keyAddress;

        if (!keysPath.endsWith("/")) keysPath = keysPath.concat("/");

        try {
            keyAddress = new KeyAddress(address);
        } catch (Exception e) {
            report("Invalid address.");
            finish();
            return;
        }

        if (folder.exists()) {
            for (File file : folder.listFiles()) {
                if (!file.isDirectory()) {
                    boolean bResult = false;

                    try {
                        PrivateKey key = new PrivateKey(Do.read(keysPath + file.getName()));
                        bResult = keyAddress.isMatchingKey(key.getPublicKey());
                    } catch (Exception e) {
                        bResult = false;
                    }

                    if (bResult) {
                        report("Filekey: " + file.getName());
                        finish();
                        return;
                    }
                }
            }

            report("File not found.");
        }
        else
            report("There is no such directory.");

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
        if (options.has("tutest")) {
            sources.remove("-tutest");
            sources.remove("--tutest");
        }
        List<String> names = (List) options.valuesOf("output");
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
        if (updateValues != null) {
            sources.removeAll(updateValues);
            sources.remove("-value");
            sources.remove("--value");
        }
        List roleValues = options.valuesOf("role");
        if (roleValues != null) {
            sources.removeAll(roleValues);
            sources.remove("-role");
            sources.remove("--role");
        }
    }

    private static void addErrors(List<ErrorRecord> errors) {
        errors.forEach(e -> addError(e.getError().name(), e.getObjectName(), e.getMessage()));
    }

    ///////////////////////////

    private static PrivateKey privateKey;
    private static BasicHttpClientSession session;
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
                    try {
                        Files.createDirectory(keysDir, ownerOnly);
                    }
                    catch(java.lang.UnsupportedOperationException e) {
                        // Windows must die
                        Files.createDirectory(keysDir);
                        // this operation is not supported:
                        //Files.setPosixFilePermissions(keysDir, perms);
                        System.out.println("* Warning: can't set permissions on keys directory on windows");
                        System.out.println("*          it is strongly recommended to restrict access to it manually\n");
                    }
                }

                Path keyFile = keysDir.resolve("main.private.unikey");
                try (OutputStream out = Files.newOutputStream(keyFile)) {
                    out.write(privateKey.pack());
                }
                try {
                    final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(keyFile, perms);
                }
                catch(UnsupportedOperationException e) {
                    System.out.println("* Warning: can't set permissions on key file on windows.");
                    System.out.println("*          it is strongly recommended to restrict access to it manually\n");

                }
                prefs.put("privateKeyFile", keyFile.toString());
                report("new private key has just been generated and stored to the " + keysDir);
            }
        }
        return privateKey;
    }

    static public BasicHttpClientSession getSession(int nodeNumber) throws IOException {
        if (session == null) {
            String keyFileName = prefs.get("session_" + nodeNumber, null);
            if (keyFileName != null) {
                reporter.verbose("Loading session from " + keyFileName);
                try {
                    session = BasicHttpClientSession.reconstructSession(Boss.unpack(Do.read(keyFileName)));
                } catch(FileNotFoundException e) {
                    // it is ok - session is not reconstructed
                } catch (Exception e) {
                    reporter.warning("can't read session file: " + keyFileName);
                    e.printStackTrace();
                }

            } else {
                reporter.verbose("No session found at the prefs ");
            }
        }
        return session;
    }

    /**
     * Only for test purposes
     *
     * @param nodeNumber
     * @throws IOException
     */
    static public void breakSession(int nodeNumber) throws IOException {
        BasicHttpClientSession s = getSession(nodeNumber);
        s.setSessionId(666);
        s.setSessionKey(new SymmetricKey());

        Path keysDir = Paths.get(System.getProperty("user.home") + "/.universa");
        if (!Files.exists(keysDir)) {
            reporter.verbose("creating new keys directory: " + keysDir.toString());
            final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
            final FileAttribute<Set<PosixFilePermission>> ownerOnly = PosixFilePermissions.asFileAttribute(perms);
            Files.createDirectory(keysDir, ownerOnly);
        }

        Path sessionFile = keysDir.resolve("node_" + nodeNumber + ".session");
        try (OutputStream out = Files.newOutputStream(sessionFile)) {
            out.write(Boss.pack(s.asBinder()));
        }
        final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(sessionFile, perms);
        prefs.put("session_" + nodeNumber, sessionFile.toString());
//        reporter.verbose("Broken session has been stored to the " + keysDir + "/" + sessionFile);
    }

    static public void clearSession() {
        clearSession(true);
    }

    static public void clearSession(boolean full) {
        session = null;
        if(full) {
            try {
                String[] keys = prefs.keys();
                for (int i = 0; i < keys.length; i++) {
                    if (keys[i].indexOf("session_") == 0) {
                        prefs.remove(keys[i]);
                    }
                }
            } catch (BackingStoreException e) {
                e.printStackTrace();
            }
        }
    }

    static public void saveSession() throws IOException {
        if( clientNetwork != null) {
            int nodeNumber = getClientNetwork().getNodeNumber();
            reporter.verbose("Session from ClientNetwork is exist: " + (session != null));
            if (session != null) {
                Path keysDir = Paths.get(System.getProperty("user.home") + "/.universa");
                if (!Files.exists(keysDir)) {
                    reporter.verbose("creating new keys directory: " + keysDir.toString());
                    final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
                    final FileAttribute<Set<PosixFilePermission>> ownerOnly = PosixFilePermissions.asFileAttribute(perms);
                    Files.createDirectory(keysDir, ownerOnly);
                }

                Path sessionFile = keysDir.resolve("node_" + nodeNumber + ".session");
                try (OutputStream out = Files.newOutputStream(sessionFile)) {
                    out.write(Boss.pack(session.asBinder()));
                }
                final Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                try {
                    Files.setPosixFilePermissions(sessionFile, perms);
                } catch (UnsupportedOperationException x) {
                    // fucking windows :( have no idea what to do with it
                }
                prefs.put("session_" + nodeNumber, sessionFile.toString());
//            report("Session has been stored to the " + keysDir + "/" + sessionFile);
            }
        }
    }

    static public void setVerboseMode(boolean verboseMode) {
        reporter.setVerboseMode(verboseMode);
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
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", f.getPath(), e.toString());
        } catch (IOException e) {
            addError("READ_ERROR", f.getPath(), e.toString());
        } catch (Exception e) {
            addError("UNKNOWN_ERROR", f.getPath(), e.toString());
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
            Set<PublicKey> keys = contract.getSealedByKeys();

            contract.getRevoking().forEach(r -> {
                try {
                    ClientNetwork n = getClientNetwork();
                    System.out.println();
                    report("revoking item exists: " + r.getId().toBase64String());
                    report("\tstate: " + n.check(r.getId()));
                    HashId origin = r.getOrigin();
                    boolean m = origin.equals(contract.getOrigin());
                    report("\tOrigin: " + origin);
                    report("\t" + (m ? "matches main contract origin" : "does not match main contract origin"));
                    if( r.canBeRevoked(keys) ) {
                        report("\trevocation is allowed");
                    }
                    else
                        reporter.error(Errors.BAD_REVOKE.name(), r.getId().toString(), "revocation not allowed");
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

        try {
            contract.check();
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
        } catch (Exception e) {
            addError(Errors.FAILURE.name(), contract.toString(), e.getMessage());
        }
        addErrors(contract.getErrors());
        if (contract.getErrors().size() == 0) {
            report("Contract is valid");
        }
    }

    private static void checkSj(Contract contract, Permission sj) {
        Binder params = sj.getParams();
        String fieldName = "state.data." + params.getStringOrThrow("field_name");
        reporter.verbose("splitjoins permission fond on field '" + fieldName + "'");
        StringBuilder outcome = new StringBuilder();
        List<Decimal> values = new ArrayList<>();
        contract.getRevoking().forEach(c -> {
            Decimal x;
            if(c.get(fieldName) != null) {
                x = new Decimal(c.get(fieldName).toString());
                values.add(x);
                if (outcome.length() > 0)
                    outcome.append(" + ");
                outcome.append(x.toString());
            }
        });
        List<Contract> news = Do.listOf(contract);
        news.addAll(contract.getNew());
        outcome.append(" -> ");
        news.forEach(c -> {
            Decimal x;
            if(c.get(fieldName) != null) {
                if( c != contract )
                    outcome.append(" + ");
                x = new Decimal(c.get(fieldName).toString());
                outcome.append(x.toString());
                values.add(x.negate());
            }
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
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", "", e.toString());
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

        ContractFileTypes fileType = getFileType(sourceName);

        Contract contract = null;
        File pathFile = new File(sourceName);
        if (pathFile.exists()) {
            try {
                Binder binder;

                FileReader reader = new FileReader(sourceName);
                if (fileType == ContractFileTypes.YAML) {
                    Yaml yaml = new Yaml();
                    binder = Binder.convertAllMapsToBinders(yaml.load(reader));
                } else if (fileType == ContractFileTypes.JSON) {
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

                report("import from " + fileType.toString().toLowerCase() + " ok");
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

            try {
                if (fromPackedTransaction) {
                    contract = Contract.fromPackedTransaction(data);
                } else {
                    contract = new Contract(data);
                }
            } catch (Quantiser.QuantiserException e) {
                addError("QUANTIZER_COST_LIMIT", fileName, e.toString());
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
    public static void exportContract(Contract contract, String fileName, String format, Boolean jsonPretty) throws IOException {

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
            addError(Errors.NOT_FOUND.name(), roleName, "role doesn't exist");
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
            boolean isOk = true;

            for (String fieldName : fieldNames) {
                try {
                    report("export field: " + fieldName + " -> " + contract.get(fieldName));
                    hm.put(fieldName, contract.get(fieldName));
                } catch (IllegalArgumentException e) {
                    addError(Errors.FAILURE.name(),fieldName,"export field error: " + e.getMessage());
                    isOk = false;
                }
            }

            if(!isOk)
                return;

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
            addError(Errors.FAILURE.name(),"exportFields","export fields error: " + e.getMessage());
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
     * @param addSigners - do adding signs to contract from keysMap() or not.
     */
    public static boolean saveContract(Contract contract, String fileName, Boolean fromPackedTransaction, Boolean addSigners) throws IOException {
        return saveContract(contract,fileName,fromPackedTransaction,addSigners,null);
    }
    /**
     * Save specified contract to file.
     *
     * @param contract              - contract for update.
     * @param fileName              - name of file to save to.
     * @param fromPackedTransaction - register contract with Contract.getPackedTransaction()
     * @param addSigners - do adding signs to contract from keysMap() or not.
     * @param referencedItems - referenced contracts to add to transaction pack.
     */
    public static boolean saveContract(Contract contract, String fileName, Boolean fromPackedTransaction, Boolean addSigners, Set<Contract> referencedItems) throws IOException {
        if (fileName == null) {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss").format(contract.getCreatedAt()) + ".unicon";
        }

        if(addSigners) {
            keysMap().values().forEach(k -> contract.addSignerKey(k));
            if (keysMap().values().size() > 0) {
                contract.seal();
            }
        }

        if(referencedItems != null) {
            TransactionPack tp = contract.getTransactionPack();
            referencedItems.forEach(c -> tp.addReferencedItem(c));
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
        String newFileName = FileTool.writeFileContentsWithRenaming(fileName, data);
        report("Contract is saved to: " + newFileName);
        report("Sealed contract size: " + data.length);
        try {
            if (contract.check()) {
                report("Sealed contract has no errors");
            } else
                addErrors(contract.getErrors());
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
        }
        return (newFileName!=null);
    }

    /**
     * Save specified contract to file.
     *
     * @param contract - contract for update.
     * @param fileName - name of file to save to.
     */
    public static void saveContract(Contract contract, String fileName) throws IOException {
        saveContract(contract, fileName, false, true);
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
     * @return Parcel - revoking transaction contract.
     */
    public static Parcel revokeContract(Contract contract, Contract tu, int amount, Set<PrivateKey> tuKeys, boolean withTestPayment, PrivateKey... key) throws IOException {

        report("keys num: " + key.length);

        Contract tc = ContractsService.createRevocation(contract, key);
        Parcel parcel = prepareForRegisterContract(tc, tu, amount, tuKeys, withTestPayment);
        if (parcel != null)
            registerParcel(parcel, 0);

        return parcel;
    }

    /**
     * Revoke specified contract and create a revocation transactional contract.
     *
     * @param contract
     *
     * @return Contract - revoking transaction contract.
     */
    @Deprecated
    public static Contract revokeContract(Contract contract, PrivateKey... key) throws IOException {

        report("keys num: " + key.length);

        Contract tc = ContractsService.createRevocation(contract, key);
        registerContract(tc, 0, true);

        return tc;
    }

    /**
     * Register a specified contract.
     *
     * @param contract              must be a sealed binary.
     * @param waitTime - wait time for responce.
     * @param fromPackedTransaction - register contract with Contract.getPackedTransaction()
     */
    @Deprecated
    public static void registerContract(Contract contract, int waitTime, Boolean fromPackedTransaction) throws IOException {
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
                r = getClientNetwork().register(contract.getPackedTransaction(), waitTime);
            } else {
                r = getClientNetwork().register(contract.getLastSealedBinary(), waitTime);
            }
            report("submitted with result:");
            report(r.toString());
        }
    }

    /**
     * Register a specified contract.
     *
     * @param contract              must be a sealed binary.
     */
    public static Parcel prepareForRegisterContract(Contract contract, Contract tu, int amount, Set<PrivateKey> tuKeys, boolean withTestPayment) throws IOException {

        List<ErrorRecord> errors = contract.getErrors();
        if (errors.size() > 0) {
            report("contract has errors and can't be submitted for registration");
            report("contract id: " + contract.getId().toBase64String());
            addErrors(errors);
        } else {
            Parcel parcel = ContractsService.createParcel(contract, tu, amount,  tuKeys, withTestPayment);
            return parcel;
        }

        return null;
    }


    /**
     * Register a paying parcel.
     *
     * @param contract              must be a sealed binary.
     */
    public static Parcel prepareForRegisterPayingParcel(Contract contract, Contract tu, int amount, int amountStorage, Set<PrivateKey> tuKeys, boolean withTestPayment) throws IOException {

        List<ErrorRecord> errors = contract.getErrors();
        if (errors.size() > 0) {
            report("contract has errors and can't be submitted for registration");
            report("contract id: " + contract.getId().toBase64String());
            addErrors(errors);
        } else {
            Set<PrivateKey> keys = new HashSet<>(keysMapContract().values());
            if (keys != null && keys.size() > 0)
                contract.addSignerKeys(keys);

            Parcel parcel = ContractsService.createPayingParcel(contract.getTransactionPack(), tu, amount, amountStorage, tuKeys, withTestPayment);
            return parcel;
        }

        return null;
    }

    /**
     * Register a specified parcel.
     * @param parcel   must be ready for register
     * @param waitTime wait time for responce.
     * @return ItemResult for parcel's payload
     * @throws IOException
     */
    public static ItemResult registerParcel(Parcel parcel, int waitTime) throws IOException {
        getClientNetwork().registerParcel(parcel.pack(), waitTime);
        ItemResult r = getClientNetwork().check(parcel.getPayloadContract().getId());
        report("paid contract " + parcel.getPayloadContract().getId() +  " submitted with result: " + r.toString());
        report("payment became " + parcel.getPaymentContract().getId());
        report("payment rev 1 " + parcel.getPaymentContract().getRevoking().get(0).getId());
        return r;
    }

    /**
     * Register a specified contract.
     *
     * @param contract must be a sealed binary file.
     */
    public static void registerContract(Contract contract) throws IOException {
        registerContract(contract, 0, true);
    }

    /**
     * Register a specified contract.
     *
     * @param contract must be a sealed binary file.
     * @param waitTime - wait time for responce.
     */
    public static void registerContract(Contract contract, int waitTime) throws IOException {
        registerContract(contract, waitTime, true);
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

            HashSet<WalletValueModel> balance = new HashSet<>();
            for (Contract contract : wallet.getContracts()) {
                try {
                    Decimal numcoins = new Decimal(contract.getStateData().getStringOrThrow(AMOUNT_FIELD_NAME));
                    SplitJoinPermission sjp = (SplitJoinPermission)contract.getPermissions().get("split_join").iterator().next();
                    WalletValueModel walletValueModel = null;
                    for (WalletValueModel wvm : balance) {
                        if (sjp.validateMergeFields(contract, wvm.contract))
                            walletValueModel = wvm;
                    }
                    if (walletValueModel == null)
                        walletValueModel = new WalletValueModel();
                    walletValueModel.value = walletValueModel.value.add(numcoins);
                    String currencyTag = contract.getDefinition().getData().getString("currency_code", null);
                    if (currencyTag == null)
                        currencyTag = contract.getDefinition().getData().getString("unit_short_name", null);
                    if (currencyTag == null)
                        currencyTag = contract.getDefinition().getData().getString("short_currency", null);
                    if (currencyTag == null)
                        currencyTag = contract.getDefinition().getData().getString("currency", null);
                    if (currencyTag == null)
                        currencyTag = contract.getDefinition().getData().getString("name", null);
                    if (currencyTag == null)
                        currencyTag = contract.getDefinition().getData().getString("unit_name", null);
                    if (currencyTag == null)
                        currencyTag = contract.getOrigin().toString();
                    walletValueModel.tag = currencyTag;
                    walletValueModel.contract = contract;
                    balance.add(walletValueModel);
                    reporter.verbose("found coins: " + contract.getOrigin().toString() + " -> " + numcoins + " (" + currencyTag + ") ");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            reporter.verbose("");
            reporter.message("total in the wallet: ");
            for (WalletValueModel w : balance) {
                reporter.message(w.value + " (" + w.tag + ") ");
            }
        }
    }

    private static class WalletValueModel {
        public Contract contract;
        public String tag;
        public Decimal value = new Decimal(0);
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
                String description;
                try {
                    description = contracts.get(key).getDefinition().getData().getString("description");
                } catch (Exception e) {
                    description = "";
                }
                reporter.verbose(key + ": " +
                                         "contract created at " +
                                         DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(contracts.get(key).getCreatedAt()) +
                                         ": " +
                                         description
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Print processing cost (that was spent while checking) of a contract to console.
     *
     * @param contract
     */
    private static void printProcessingCost(Contract contract) {
        report("Contract processing cost is " + contract.getProcessedCostTU() + " TU");
    }


    private static void finish(int status) {
        // print reports if need
        try {
            saveSession();
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new Finished(status);
    }

    private static void finish() {
        finish(reporter.getErrors().size());
    }

    private static ContractFileTypes getFileType(String source) {

        String extension = "";
        int i = source.lastIndexOf('.');
        if (i > 0) {
            extension = source.substring(i + 1);
        }

        extension = extension.toLowerCase();

        if ("unicon".equals(extension)) {
            return ContractFileTypes.BINARY;
        }
        if ("yaml".equals(extension) || "yml".equals(extension)) {
            return ContractFileTypes.YAML;
        }
        if ("json".equals(extension)) {
            return ContractFileTypes.JSON;
        }
        if ("xml".equals(extension)) {
            return ContractFileTypes.XML;
        }
        return ContractFileTypes.UNKNOWN;
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
            System.err.println("error while parsing command line. Use uniclient --help");
        else {
            Integer columns = (Integer) options.valueOf("term-width");
            if (columns == null)
                columns = 120;

            if (text != null)
                out.println("ERROR: " + text + "\n");
            try {
                parser.formatHelpWith(new BuiltinHelpFormatter(columns, 2));
                parser.printHelpOn(out);
                out.println("\nOnline docs: https://lnd.im/UniClientUserManual\n");
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

    public static void setNodeNumber(int nodeNumber) {
        System.out.println("Connecting to node " + nodeNumber);
        setNodeUrl("http://node-" + nodeNumber + "-com.universa.io:8080");
    }

    public static void setNodeUrl(String url) {
        nodeUrl = url;
        clientNetwork = null;
    }

    public static void setPrivateKey(PrivateKey key) {
        privateKey = key;
        clientNetwork = null;
    }

    public static Reporter getReporter() {
        return reporter;
    }

    public static synchronized ClientNetwork getClientNetwork() throws IOException {
        if (clientNetwork == null) {
            reporter.verbose("ClientNetwork not exist, create one");

            BasicHttpClientSession s = null;
            reporter.verbose("ClientNetwork nodeUrl: " + nodeUrl);
            if(nodeUrl != null) {
                clientNetwork = new ClientNetwork(nodeUrl, null, true);
            } else {
                clientNetwork = new ClientNetwork(null, true);
            }
            if (clientNetwork.client == null)
                throw new IOException("failed to connect to to the universa network");

            s = getSession(clientNetwork.getNodeNumber());
            reporter.verbose("Session for " + clientNetwork.getNodeNumber() + " is exist: " + (s != null));
            if(s != null) reporter.verbose("Session id is " + s.getSessionId());
            clientNetwork.start(s);

        }
        if(clientNetwork != null)
            session = clientNetwork.getSession();
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

    public static synchronized Map<String, PrivateKey> keysMapContract() throws IOException {
        if (keyFilesContract == null) {
            keyFilesContract = new HashMap<>();
            for (String fileName : keyFileNamesContract) {
                try {
                    PrivateKey pk = PrivateKey.fromPath(Paths.get(fileName));
                    keyFilesContract.put(fileName, pk);
                } catch (IOException e) {
                    addError(Errors.NOT_FOUND.name(), fileName.toString(), "failed to load key file: " + e.getMessage());
                }
            }
        }
        return keyFilesContract;
    }

    public static class Finished extends RuntimeException {
        private int status;

        public int getStatus() {
            return status;
        }

        public Finished() {
            this.status = 0;
        }

        public Finished(int status) {
            this.status = status;
        }
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


    public enum ContractFileTypes {
        JSON,
        XML,
        YAML,
        BINARY,
        UNKNOWN
    }
}
