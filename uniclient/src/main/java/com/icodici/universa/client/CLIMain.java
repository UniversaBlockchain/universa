/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.icodici.crypto.*;
import com.icodici.universa.*;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.jsapi.JSApiScriptParameters;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.Quantiser;
import com.icodici.universa.node2.network.*;
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
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.*;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.Safe58;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class CLIMain {

    public static class UUTNWallet {
        Binder config;
        public HashMap<HashId,Contract> utns;
        public HashMap<HashId,String> utnPathes;
        public HashMap<HashId,Contract> us;
        public HashMap<HashId,String> uPathes;
        public int uBalance;
        public BigDecimal utnBalance;
        public String path;
    }

    private static final String CLI_VERSION = Core.VERSION;
    private static final String URS_ROOT_URL = "https://xchange.mainnetwork.io/api/v1";
    private static final HashId UTN_ORIGIN = HashId.withDigest("NPo4dIkNdgYfGiNrdExoX003+lFT/d45OA6GifmcRoTzxSRSm5c5jDHBSTaAS+QleuN7ttX1rTvSQbHIIqkcK/zWjx/fCpP9ziwsgXbyyCtUhLqP9G4YZ+zEY/yL/GVE");
    private static final String DEFAULT_WALLET_CONFIG = "config";
    public static String DEFAULT_WALLET_PATH = System.getProperty("user.home") +File.separator+".universa"+File.separator+"uutnwallet";

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;
    private static String testRootPath;
    private static String nodeUrl;
    private static String topologyFileName;

    private static Reporter reporter = new Reporter();
    private static ClientNetwork clientNetwork;
    private static List<String> keyFileNames = new ArrayList<>();
    private static Map<String, PrivateKey> keyFiles;
    private static List<String> keyFileNamesContract = new ArrayList<>();
    private static Map<String, PrivateKey> keyFilesContract;
    private static CliServices cliServices = new CliServices();

    public static final String AMOUNT_FIELD_NAME = "amount";
    private static int nodeNumber = -1;

    private static BiAdapter customBytesBiAdapter = new BiAdapter() {
        @Override
        public Binder serialize(Object object, BiSerializer serializer) {
            throw new IllegalArgumentException("can't serialize Bytes");
        }

        @Override
        public Object deserialize(Binder binder, BiDeserializer deserializer) {
            if(binder.containsKey("base58")) {
                return new Bytes(Safe58.decode(binder.getStringOrThrow("base58"),true));
            }

            if(binder.containsKey("safe58")) {
                return new Bytes(Safe58.decode(binder.getStringOrThrow("safe58")));
            }

            return new Bytes(Base64.decodeLines(binder.getStringOrThrow("base64")));
        }

        @Override
        public String typeName() {
            return "binary";
        }
    };


    private static BiAdapter customByteArrayBiAdapter = new BiAdapter() {
        @Override
        public Binder serialize(Object object, BiSerializer serializer) {
            throw new IllegalArgumentException("can't serialize byte[]");
        }

        @Override
        public Object deserialize(Binder binder, BiDeserializer deserializer) {
            if(binder.containsKey("base58")) {
                return (Safe58.decode(binder.getStringOrThrow("base64"),true));
            }

            if(binder.containsKey("safe58")) {
                return (Safe58.decode(binder.getStringOrThrow("base64")));
            }

            return Base64.decodeLines(binder.getStringOrThrow("base64"));
        }

        @Override
        public String typeName() {
            return "binary";
        }
    };



    private static BiAdapter customKeyAddressBiAdapter = new BiAdapter() {
        @Override
        public Binder serialize(Object object, BiSerializer serializer) {
            Object s = serializer.serialize(((KeyAddress) object).getPacked());;
            Binder serialized = (Binder) s;
            if(serialized.containsKey("base64")) {
                serialized.put("base58", Safe58.encode(Base64.decodeLines((String) serialized.remove("base64"))));
            }

            return Binder.of("uaddress", serialized);
        }

        @Override
        public Object deserialize(Binder binder, BiDeserializer deserializer) {
            throw new IllegalArgumentException("can't reconstruct KeyAddress");
        }

        @Override
        public String typeName() {
            return "KeyAddress";
        }
    };

    private static BiAdapter customReferenceBiAdapter = new BiAdapter() {
        @Override
        public Binder serialize(Object object, BiSerializer s) {
            Reference ref = (Reference) object;
            Binder data = new Binder();

            data.set("name", s.serialize(ref.name));
            data.set("type", s.serialize(ref.type));
            data.set("transactional_id", s.serialize(ref.transactional_id));
            if (ref.contract_id != null)
                data.set("contract_id", s.serialize(ref.contract_id));
            data.set("required", s.serialize(ref.required));
            if (ref.origin != null)
                data.set("origin", s.serialize(ref.origin));
            data.set("signed_by", s.serialize(ref.signed_by));

            data.set("roles", s.serialize(ref.roles));
            data.set("fields", s.serialize(ref.fields));

            data.set("where", s.serialize(ref.exportConditions()));

            return data;
        }

        @Override
        public Object deserialize(Binder binder, BiDeserializer deserializer) {
            throw new IllegalArgumentException("can't reconstruct Reference");
        }

        @Override
        public String typeName() {
            return "Reference";
        }
    };


    static public void main(String[] args) throws IOException {
        // when we run untit tests, it is important:
//        args = new String[]{"-c", "/Users/sergeych/dev/new_universa/uniclient-testcreate/simple_root_contract.yml"};

        Config.forceInit(Contract.class);
        Config.forceInit(Parcel.class);

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
                acceptsAll(asList("u-rate"), "Get how many U are given for 1 UTN at this time.");
                acceptsAll(asList("no-cache"), "Do not use session cache");
                accepts("register", "register a specified contract, must be a sealed binary file. Use with either --wallet or --u with --keys to specify payment options. If none are specified default wallet will be used. If no default wallet exist command fails. Amount to pay is specified with --amount")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class).
                        describedAs("contract.unicon");
                accepts("register-parcel", "register a specified parcel")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class).
                        describedAs("parcel.uniparcel");
                accepts("create-parcel", "prepare parcel for registering given contract. use with either --wallet or --u with --keys to specify payment options. If none are specified default wallet will be used. If no default wallet exist command fails. Amount to pay is specified with --amount")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class).
                        describedAs("contract.unicon");

                accepts("put-into-wallet", "Adds specified U/UTN contracts and keys to UUTN wallet (creates one if not exists). "+
                        "Use with non-optional arguments passing U and UTN contracts and --keys to specify keys required to split UTNs and decrement Us. " +
                        "Argument to --put-into-wallet is optional and specifies path to create wallet at. If no path specifed default will be taken (~/.universa)" +
                        "Wallet can then be used with --register and --create-parcel. It will also try to top up when needed Us if there are any UTN contract in the wallet" )
                        .withOptionalArg()
                        .ofType(String.class).
                        describedAs("/path/to/wallet");

                accepts("wallet", "specify wallet to pay with. Use with --register or --create-parcel.")
                        .withRequiredArg()
                        .ofType(String.class).
                        describedAs("/path/to/wallet");



                accepts("u-for-utn", "reserve U for UTN. Use with --keys to specify keys required to split UTNs and --amount to specify amount of U to reserve")
                        .withRequiredArg()
                        .ofType(String.class).
                        describedAs("utn.unicon");


                accepts("tu", "Deprecated. Use -u instead.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("tu.unicon");

                accepts("u", "Use with --register and --create-parcel. Point to file with your U. " +
                        "Use it to pay for contract's register.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("u.unicon");
                accepts("amount", "Use with --register, --create-parcel and -u. " +
                        "Command is set amount of U will be pay for contract's register.")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(1)
                        .describedAs("u amount");
                accepts("amount-storage", "Use with --register, --create-parcel and -u. " +
                        "Command is set amount-storage of storage U will be pay for contract's register.")
                        .withRequiredArg()
                        .ofType(Integer.class)
                        .defaultsTo(0)
                        .describedAs("u amount-storage");
                accepts("tutest", "Deprecated. Use -utest instead.");
                accepts("utest", "Use with --register, --create-parcel and -u. Key is point to use test U.");

                accepts("no-exit", "Used for tests. Uniclient d");
                accepts("probe", "query the state of the document in the Universa network")
                        .withOptionalArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("base64_id");
                accepts("probe-file", "query the state of the document in the Universa network")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("filename");

                accepts("sign", "add signatures to contract. Use with --keys to specify keys to sign with")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("filename");

                accepts("set-log-levels", "sets log levels of the node,network and udp adapter")
                        .withRequiredArg()
                        .withValuesSeparatedBy(",")
                        .ofType(String.class)
                        .describedAs("level");

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

                acceptsAll(asList("topology"), "Specify topology to be used as entry point. Should be " +
                        "either path to json file for creating new / updating existing named topology " +
                        "or cached topology name")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("./path/to/topology_name.json or topology_name");

                acceptsAll(asList("password"), "List of comma-separated passwords " +
                        "to generate or unpack password protected keys. Use with -g or -k")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("key_password");
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
                        "Option adds parent to revokes and sets origin, parent and revision to imported contract.")
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
                accepts("hash", "get HashId of a file").withRequiredArg().ofType(String.class).describedAs("file");
                accepts("start-http-server",
                        "Starts http server, whose endpoints are implemented in contracts.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("routes_file");
                accepts("exec-js",
                        "Executes javascript attached to contract. If your contract have many scripts attached, specify concrete script with --script-name.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("packed_contract");
                accepts("script-name",
                        "Use with -exec-js. Set the script filename that would be executed.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("script_filename");
                accepts("create-contract-with-js",
                        "Creates new contract with given javascript attached. Use -o to set output filename; -k to set issuer and owner.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .describedAs("javascript_file");

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
                if(!options.has("register") && !options.has("create-parcel")) {
                    keyFileNames = new ArrayList<>();
                } else {
                    String walletPath = (String) options.valueOf("wallet");
                    if (walletPath == null) {
                        walletPath = DEFAULT_WALLET_PATH;
                    }
                    UUTNWallet wallet = loadWallet(walletPath,false);
                    if (wallet == null)
                        keyFileNames = new ArrayList<>();
                    else {
                        String finalWalletPath = walletPath;
                        keyFileNames = (List<String>) wallet.config.getListOrThrow("keys").stream().map(kPath -> finalWalletPath + File.separator + kPath).collect(Collectors.toList());
                    }
                }
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

            if(options.has("topology")) {
                List<String> names = (List) options.valuesOf("topology");
                setTopologyFileName(names.get(0));
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

            if(options.has("hash")) {
                doShowHash();
            }

            if(options.has("probe-file")) {
                doProbeFile();
            }

            if(options.has("sign")) {
                doSign();
            }

            if (options.has("register")) {
                doRegister();
            }

            if (options.has("register-parcel")) {
                doRegisterParcel();
            }


            if (options.has("u-rate")) {
                doGetURate();
            }


            if (options.has("u-for-utn")) {
                doReserveUforUTN();
            }

            if (options.has("create-parcel")) {
                doCreateParcel();
            }

            if (options.has("put-into-wallet")) {
                doPutIntoWallet();
            }

            if (options.has("probe")) {
                doProbe();
            }
            if (options.has("resync")) {
                doResync();
            }

            if(options.has("set-log-levels")) {
                doSetLogLevels();
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
            if (options.has("start-http-server")) {
                doStartHttpServer((String) options.valueOf("start-http-server"));
            }
            if (options.has("exec-js")) {
                doExecJs();
            }
            if (options.has("create-contract-with-js")) {
                doCreateContractWithJs();
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
            if(!options.has("no-exit")) {
                System.exit(100);
            }

        }

    }

    private static void doPutIntoWallet() throws IOException {
        String mainArg = (String) options.valueOf("put-into-wallet");
        List<String> contracts = new ArrayList<>();
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            contracts.addAll(asList(opt.split(",")));
        }
        String walletPath = DEFAULT_WALLET_PATH;
        if(mainArg == null)
            mainArg = walletPath;

        File mainArgFile = new File(mainArg);
        if (mainArgFile.exists()) {
            if (mainArgFile.isDirectory()) {
                walletPath = mainArg;
            } else {
                contracts.add(mainArg);
            }
        } else {
            walletPath = mainArg;
        }

        File walletDir = new File(walletPath);
        if(!walletDir.exists()) {
            try {
                Files.createDirectories(Paths.get(walletPath));
            } catch (FileAlreadyExistsException e) {
                addError(Errors.FAILURE.name(), e.getFile(), "File can not be a part of the path");
                finish();
            }
        }

        File walletConfig = new File(walletDir, DEFAULT_WALLET_CONFIG);
        UUTNWallet wallet;
        Yaml yaml = new Yaml();

        if (!walletConfig.exists()) {
            Binder config = new Binder();
            config.put("ucontracts",new ArrayList<>());
            config.put("utncontracts",new ArrayList<>());
            config.put("keys",new ArrayList<>());
            config.put("version",1);
            config.put("auto_payment",Binder.of("min_balance",10,"amount",15));
            Files.write(Paths.get(walletConfig.getPath()),yaml.dumpAsMap(config).getBytes(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }

        wallet = loadWallet(walletDir.getPath(),false);

        List<String> keys = wallet.config.getListOrThrow("keys");
        Set<PublicKey> publicKeys = new HashSet<>();
        for(String existingKeyPath : keys) {
            publicKeys.add(new PrivateKey(Do.read(new File(walletDir,existingKeyPath))).getPublicKey());
        }

        int keysAdded = 0;

        Map<String, PrivateKey> map = keysMap();
        for(String keyPath : map.keySet()) {
            PrivateKey pk = map.get(keyPath);
            if(publicKeys.contains(pk.getPublicKey())) {
                addError(Errors.FORBIDDEN.name(),pk.getPublicKey().toString(),"Key is already in wallet");
            }
            publicKeys.add(pk.getPublicKey());
            String targetFile = FileTool.writeFileContentsWithRenaming(new FilenameTool(keyPath).setPath(walletPath).toString(), pk.pack());
            keys.add(new FilenameTool(targetFile).getFilename());
            keysAdded++;
        }

        List utncontracts = wallet.config.getListOrThrow("utncontracts");
        List ucontracts = wallet.config.getListOrThrow("ucontracts");

        int ucontractsAdded = 0;
        int utncontractsAdded = 0;
        for(String contractPath : contracts) {
            Contract contract = loadContract(contractPath);
            if(contract != null) {
                List<String> targetList;
                if(contract.getOrigin().equals(UTN_ORIGIN)) {
                    //CONTRACT IS UTN
                    if(!contract.getOwner().isAllowedForKeys(publicKeys)) {
                        addError(Errors.NOT_SIGNED.name(),contractPath,"contract is not operational for given keys (including the one in the wallet already)");
                        continue;
                    }

                    if(wallet.utns.containsKey(contract.getId())) {
                        addError(Errors.FORBIDDEN.name(),contractPath,"contract with the same ID is already in wallet");
                        continue;
                    }

                    if(getClientNetwork().check(contract.getId()).state != ItemState.APPROVED) {
                        addError(Errors.FORBIDDEN.name(),contractPath,"contract is not approved");
                        continue;
                    }
                    utncontractsAdded++;
                    targetList = utncontracts;
                } else if(contract.getStateData().containsKey("transaction_units")) {
                    //CONTRACT IS U
                    if(!contract.isPermitted("decrement_permission",publicKeys)) {
                        addError(Errors.NOT_SIGNED.name(),contractPath,"contract is not operational for given keys (including the one in the wallet already)");
                        continue;
                    }

                    if(wallet.us.containsKey(contract.getOrigin())) {
                        addError(Errors.FORBIDDEN.name(),contractPath,"contract with the same ORIGIN is already in wallet");
                        continue;
                    }

                    if(getClientNetwork().check(contract.getId()).state != ItemState.APPROVED) {
                        addError(Errors.FORBIDDEN.name(),contractPath,"contract is not approved");
                        continue;
                    }
                    ucontractsAdded++;
                    targetList = ucontracts;
                } else {
                    addError(Errors.NOT_SUPPORTED.name(),contractPath,"contract is neither U nor UTN");
                    continue;
                }

                String targetFile = FileTool.writeFileContentsWithRenaming(new FilenameTool(contractPath).setPath(walletPath).toString(), contract.getLastSealedBinary());
                targetList.add(new FilenameTool(targetFile).getFilename());
            }
        }

        Files.write(Paths.get(walletConfig.getPath()),yaml.dumpAsMap(wallet.config).getBytes(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("WALLET CHANGES Added/Total: keys - " + keysAdded + "/" +keys.size() + "; U contracts - " + ucontractsAdded + "/" + ucontracts.size() +
        "; UTN contracts - " + utncontractsAdded + "/" +utncontracts.size()+".");
        finish();
    }

    private static void doReserveUforUTN() throws IOException {
        String utnPath = (String) options.valueOf("u-for-utn");

        List<String> names = (List) options.valuesOf("output");

        int amount = (int) options.valueOf("amount");
        String uPath = reserveUforUTN(utnPath,names.size() > 0 ? names.get(0) : null,amount);
        if(uPath != null) {
            System.out.println(amount + "U successfully purchased! Path to U contract is : " + uPath);
        }
        finish();
    }

    private static String reserveUforUTN(String utnPath, String output, int amount) throws IOException {
        Contract utnContract = loadContract(utnPath);
        if(utnContract != null) {
            String utnBase64 = Base64u.encodeString(utnContract.getLastSealedBinary());
            if (keysMap().isEmpty()) {
                addError(Errors.NOT_FOUND.name(), "keys", "keys are not specified");
                finish();
            }
            PrivateKey ownerPrivateKey = keysMap().values().iterator().next();
            PublicKey ownerPublicKey = ownerPrivateKey.getPublicKey();
            KeyAddress ownerAddress = ownerPublicKey.getLongAddress();
            Set<PublicKey> ownerPublicKeys = new HashSet<>();
            ownerPublicKeys.add(ownerPublicKey);

            BasicHttpClient httpClient = new BasicHttpClient(URS_ROOT_URL);
            BasicHttpClient.Answer answer = httpClient.commonRequest("uutn/create_purchase",
                    "utn_base64",utnBase64,
                    "owner_address",ownerAddress,
                    "amount",amount);

            if(checkAnswer(answer)) {
                Contract compound = Contract.fromPackedTransaction(Base64u.decodeLines(answer.data.getString("compound_base64")));
                keysMap().values().forEach( k -> compound.addSignatureToSeal(k));

                Contract utnRest = null;
                Contract uContract = null;

                for(Contract subItem : compound.getTransactionPack().getSubItems().values()) {
                    if(subItem.getOrigin().equals(UTN_ORIGIN) && subItem.getOwner().isAllowedForKeys(ownerPublicKeys) && subItem.getParent().equals(utnContract.getId())) {
                        utnRest = subItem;
                    }

                    if(subItem.getStateData().containsKey("transaction_units")) {
                        uContract = subItem;
                    }
                }


                if(uContract == null) {
                    addError(Errors.COMMAND_FAILED.name(), "transaction", "unable to locate U in transaction");
                    finish();
                }

                if(utnRest == null) {
                    System.out.println("No owned UTN found in transaction. Looks like you've spent all the UTNs.");
                }

                CopyOption[] copyOptions = new CopyOption[]{
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                };

                String utnDest = new FilenameTool(utnPath).addSuffixToBase("_rev" + utnContract.getRevision()).toString();
                utnDest = FileTool.writeFileContentsWithRenaming(utnDest, new byte[0]);
                if (utnDest != null) {
                    Files.move(Paths.get(utnPath), Paths.get(utnDest), copyOptions);
                    if(utnRest != null) {
                        if (FileTool.writeFileContentsWithRenaming(utnPath, utnRest.getLastSealedBinary()) == null) {
                            addError(Errors.COMMAND_FAILED.name(), utnPath, "unable to save owned utn");
                            return null;
                        }
                    }
                } else {
                    addError(Errors.COMMAND_FAILED.name(),utnPath,"unable to backup utn revision");
                    return null;
                }

                String uPath;
                if(output != null) {
                    uPath = output;
                } else {
                    uPath = new FilenameTool(utnPath).setBase("U_" + amount).toString();
                }

                if((uPath = FileTool.writeFileContentsWithRenaming(uPath, uContract.getLastSealedBinary())) == null) {
                    addError(Errors.COMMAND_FAILED.name(),utnPath,"unable to save U from transaction");
                    return null;
                }

                byte[] compoundBytes = compound.getPackedTransaction();
                System.out.println("U purchase transaction is saved to : " +FileTool.writeFileContentsWithRenaming(new FilenameTool(utnPath).setBase("u_purchase_"+ZonedDateTime.now().toEpochSecond()).toString(),compoundBytes));
                String compoundBase64 = Base64u.encodeString(compoundBytes);
                answer = httpClient.commonRequest("uutn/purchase","compound_base64",compoundBase64);

                if(checkAnswer(answer)) {
                    long id = answer.data.getBinder("purchase").getLong("id",0);
                    if(id == 0) {
                        addError(Errors.COMMAND_FAILED.name(),"purchase","purchase id is unknown");
                        return null;
                    }
                    String state = answer.data.getBinder("purchase").getString("state");
                    while(state.equals("in_progress")) {
                        System.out.println("Purchase in progress...");
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        answer = httpClient.commonRequest("uutn/"+id);
                        if(checkAnswer(answer)) {
                            state = answer.data.getBinder("purchase").getString("state");
                        } else {
                            return null;
                        }
                    }

                    if(state.equals("ready")) {
                        return uPath;
                    } else if(state.equals("cancel")) {
                        System.out.println("Purchase canceled. Rolling back contracts...");
                        new File(uPath).delete();
                        Files.move(Paths.get(utnDest), Paths.get(utnPath), copyOptions);
                        return null;
                    } else {
                        addError(Errors.COMMAND_FAILED.name(),"purchase","unknown purchase state: " + state);
                        return null;
                    }

                }

            }
        }
        return null;
    }

    private static boolean checkAnswer(BasicHttpClient.Answer answer) {
        if(answer.code != 200) {
            addError(Errors.FAILURE.name(),"HTTP","http code " + answer.code);
            return false;
        }

        if(answer.data.getString("status").equals("error")) {
            addError(Errors.COMMAND_FAILED.name(),answer.data.getString("code"),answer.data.getString("text",""));
            return false;
        }

        if(!answer.data.getString("status").equalsIgnoreCase("ok")) {
            addError(Errors.COMMAND_FAILED.name(),"response_status","unknown_response status");
        }
        return true;
    }

    private static void doGetURate() throws IOException {
        BasicHttpClient httpClient = new BasicHttpClient(URS_ROOT_URL);
        BasicHttpClient.Answer answer = httpClient.commonRequest("uutn/info");
        System.out.println("Current U per UTN rate is " + answer.data.getBinder("rates").getString("U_UTN"));
        System.out.println("Minimum U to reserve " + answer.data.getBinder("limits").getBinder("U").getString("min"));
        System.out.println("Maximum U to reserve " + answer.data.getBinder("limits").getBinder("U").getString("max"));
        finish();
    }

    private static void doSetLogLevels() {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("set-log-levels"));
        if(sources.size() != 3) {
            addError(Errors.COMMAND_FAILED.name(),"levels", "specify 3 log levels: node,network,udp");
        }

        try {
            ItemResult ir = getClientNetwork().client.setVerboseLevel(VerboseLevel.stringToInt(sources.get(0)),
                    VerboseLevel.stringToInt(sources.get(1)),
                    VerboseLevel.stringToInt(sources.get(2)));
            addErrors(ir.errors);
        } catch (ClientError clientError) {
            if (options.has("verbose"))
                clientError.printStackTrace();
            addError(Errors.COMMAND_FAILED.name(),"levels",clientError.getMessage());
        } catch (IOException e) {
            if (options.has("verbose"))
                e.printStackTrace();
            addError(Errors.COMMAND_FAILED.name(),"levels",e.getMessage());
        }

        finish();
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
                        contract.getState().setParent(parent.getId());
                        contract.getState().setOrigin(parent.getOrigin());
                        contract.getState().setRevision(parent.getRevision()+1);
                        contract.getDefinition().setExpiresAt(parent.getDefinition().getExpiresAt());
                        contract.getDefinition().setCreatedAt(parent.getDefinition().getCreatedAt());
                        contract.addRevokingItems(parent);
                    }
                }

                contract.seal();
                saveContract(contract, name,true,true);
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

        String uSource = (String) options.valueOf("u");
        if (uSource == null) {
            uSource = (String) options.valueOf("tu");
            if (uSource != null)
                // deprecate warning
                System.out.println("WARNING: Deprecated. Use -u instead.");
        }

        int uAmount = (int) options.valueOf("amount");
        int uAmountStorage = (int) options.valueOf("amount-storage");

        boolean utest = options.has("utest") || options.has("tutest");
        if (options.has("tutest"))
            System.out.println("WARNING: Deprecated. Use -utest instead.");


        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            Contract contract = loadContract(source);

            Contract u = null;
            if(uSource == null) {
                uSource = getUFromWallet(uAmount+uAmountStorage,utest);
            }
            if(uSource != null) {
                u = loadContract(uSource, true);
                report("load payment revision: " + u.getState().getRevision() + " id: " + u.getId());
            }

            Set<PrivateKey> uKeys = new HashSet<>(keysMap().values());
            if(contract != null) {
                if(u != null && uKeys != null && uKeys.size() > 0) {
                    Parcel parcel;
                    Contract newUContract;
                    if(uAmountStorage == 0) {
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + uAmount + " U");
                        report("contractId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterContract(contract, u, uAmount, uKeys, utest);
                        newUContract = parcel.getPaymentContract();
                    } else { // if storage payment
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + uAmount + " U (and " + uAmountStorage + " U for storage)");
                        report("contractId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterPayingParcel(contract, u, uAmount, uAmountStorage, uKeys, utest);
                        newUContract = parcel.getPayloadContract().getNew().get(0);
                    }

                    if (parcel != null) {
                        saveParcel(parcel,new FilenameTool(source).setExtension("uniparcel").toString());
                        report("save payment revision: " + newUContract.getState().getRevision() + " id: " + newUContract.getId());

                        CopyOption[] copyOptions = new CopyOption[]{
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        };
                        String uDest = new FilenameTool(uSource).addSuffixToBase("_rev" + u.getRevision()).toString();
                        uDest = FileTool.writeFileContentsWithRenaming(uDest, new byte[0]);
                        if (uDest != null) {
                            Files.move(Paths.get(uSource), Paths.get(uDest), copyOptions);
                            if (saveContract(newUContract, uSource, true, false)) {
                                report("registering with parcel: " + parcel.getId());
                                ItemResult ir = registerParcel(parcel, (int) options.valueOf("wait"));
                                if(ir.state != ItemState.APPROVED) {
                                    addErrors(ir.errors);
                                }
                            } else {
                                addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup u revision");
                            }
                        } else {
                            addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup u revision");
                        }
                    } else {
                        addError(Errors.COMMAND_FAILED.name(),"parcel","unable to prepare parcel");
                    }

                } else {
//                    report("registering the contract " + contract.getId().toBase64String() + " from " + source);
//                    registerContract(contract, (int) options.valueOf("wait"));
                    addError(Errors.COMMAND_FAILED.name(), uSource, "payment contract or private keys for payment contract is missing");
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

    private static String getUFromWallet(UUTNWallet wallet, int amount,boolean isTest) throws IOException {
        if(wallet == null) {
            System.out.println("UUTN wallet not found");
            return null;
        }

        String fieldName = isTest ? "test_transaction_units" : "transaction_units";

        int minAcceptableBalance = 0;
        HashId acceptableContractId = null;
        for(HashId uIds : wallet.us.keySet()) {
            Contract ucontract = wallet.us.get(uIds);

            int unitsCount = ucontract.getStateData().getIntOrThrow(fieldName);
            if(unitsCount >= amount && (minAcceptableBalance > unitsCount || acceptableContractId == null)) {
                minAcceptableBalance = unitsCount;
                acceptableContractId =uIds;
            }
        }
        String acceptableContract = acceptableContractId == null ? null : wallet.uPathes.get(acceptableContractId);

        if(acceptableContract == null) {
            addError(Errors.NOT_FOUND.name(),"U contract", "U contract is not found in UUTN wallet.");
            return null;
        } else {
            System.out.println("U contract is found in UUTN wallet: " + acceptableContract);
            System.out.println("Checking status...");
            if(getClientNetwork().check(wallet.us.get(acceptableContractId).getId()).state != ItemState.APPROVED) {
                return getUFromWallet(fixUUTNWallet(wallet.path),amount,isTest);
            }
        }

        return wallet.path + File.separator + acceptableContract;
    }
    private static String getUFromWallet(int amount,boolean isTest) throws IOException {

        String walletPath = (String) options.valueOf("wallet");
        if(walletPath == null) {
            walletPath = DEFAULT_WALLET_PATH;
        }
        System.out.println("Looking for U contract in UUTN wallet.");

        UUTNWallet wallet = loadWallet(walletPath,true);
        return getUFromWallet(wallet, amount, isTest);
    }

    private static UUTNWallet loadWallet(String walletPath, boolean reserveMoreIfNeeded) throws IOException {
        File walletDir = new File(walletPath);
        File walletConfig = new File(walletDir, DEFAULT_WALLET_CONFIG);
        if (!walletConfig.exists()) {
            return null;
        }
        FileReader reader = new FileReader(walletConfig);
        Yaml yaml = new Yaml();
        Binder config = Binder.convertAllMapsToBinders(yaml.load(reader));
        UUTNWallet wallet = new UUTNWallet();
        wallet.path = walletPath;
        wallet.config = config;

        boolean saveConfig = false;
        AtomicInteger uBalance = new AtomicInteger(0);
        wallet.us = new HashMap<>();
        wallet.uPathes = new HashMap<>();
        List<String> uContracts = config.getListOrThrow("ucontracts");
        if(uContracts.removeIf(contractPath -> {
            //if contract does not exist - remove
            if(!new File(walletPath + File.separator + contractPath).exists()) {
                return true;
            }
            try {
                Contract contract = loadContract(walletPath + File.separator + contractPath);
                //if contract can't be loaded - remove
                if(contract == null)
                    return true;

                int UAmount = contract.getStateData().getInt("transaction_units",0);
                uBalance.addAndGet(UAmount);
                int testUAmount = contract.getStateData().getInt("test_transaction_units",0);

                //if contract is empty - remove
                if(testUAmount  + UAmount == 0)
                    return true;

                wallet.us.put(contract.getOrigin(),contract);
                wallet.uPathes.put(contract.getOrigin(),contractPath);

            } catch (IOException e) {
                //if contract can't be loaded - remove
                return true;
            }

            return false;
        })) {
            saveConfig = true;
        }
        wallet.uBalance = uBalance.get();

        final BigDecimal[] untBalance = {new BigDecimal("0")};
        wallet.utns = new HashMap();
        wallet.utnPathes = new HashMap();
        List<String> utnContracts = config.getListOrThrow("utncontracts");
        if(utnContracts.removeIf(contractPath -> {
            //if contract does not exist - remove
            if(!new File(walletPath + File.separator + contractPath).exists()) {
                return true;
            }
            try {
                Contract contract = loadContract(walletPath + File.separator + contractPath);
                //if contract can't be loaded - remove
                if(contract == null)
                    return true;
                BigDecimal amount = new BigDecimal(contract.getStateData().getString("amount", "0"));
                wallet.utns.put(contract.getId(),contract);
                wallet.utnPathes.put(contract.getId(),contractPath);
                untBalance[0] = untBalance[0].add(amount);

            } catch (IOException e) {
                //if contract can't be loaded - remove
                return true;
            }

            return false;
        })) {
            saveConfig = true;
        }

        wallet.utnBalance = untBalance[0];

        if(reserveMoreIfNeeded) {
            System.out.println("Loaded wallet '" + walletPath + "' Balance is: U " + uBalance +" UTN " + untBalance[0]);
            int minBalance = config.getBinder("auto_payment").getInt("min_balance", -1);
            if (minBalance > 0 && uBalance.get() < minBalance) {
                System.out.println("U balance is less than threshold of " + minBalance + ". Trying to get more Us");

                BasicHttpClient httpClient = new BasicHttpClient(URS_ROOT_URL);
                BasicHttpClient.Answer answer = httpClient.commonRequest("uutn/info");

                Double rate = answer.data.getBinder("rates").getDouble("U_UTN");
                int min = answer.data.getBinder("limits").getBinder("U").getIntOrThrow("min");
                int max = answer.data.getBinder("limits").getBinder("U").getIntOrThrow("max");
                int amount = config.getBinder("auto_payment").getInt("amount", min);
                if (amount < min) {
                    amount = min;
                } else if (amount > max) {
                    amount = max;
                }

                BigDecimal utnReqired = new BigDecimal(amount * 1.0 / rate);


                if (untBalance[0].compareTo(utnReqired) >= 0) {
                    String utn = null;
                    for (HashId id : wallet.utns.keySet()) {
                        if (new BigDecimal(wallet.utns.get(id).getStateData().getString("amount","0")).compareTo(utnReqired) >= 0) {
                            utn = wallet.utnPathes.get(id);
                            System.out.println("Found UTN contract with required amount. Checking status...");
                            if(getClientNetwork().check(id).state != ItemState.APPROVED) {
                                System.out.println("Wallet needs to recover. Skipping auto purchase for now.");
                                return fixUUTNWallet(walletPath);
                            }

                            break;
                        }
                    }
                    if (utn == null) {
                        //TODO: JOIN utns to prepare signle contract
                        System.out.println("WARNING: Auto purchase can not be completed as all UTN contracts have less amount than required " + utnReqired + ". Join manually or wait for auto-joinig support in future versions. You can also add another UTN contract to the wallet");
                    } else {
                        String newUContract = reserveUforUTN(walletPath + File.separator + utn, null, amount);
                        if (newUContract != null) {
                            System.out.println("Auto purchase completed. New U contract is: " + newUContract);
                            uContracts.add(new FilenameTool(newUContract).getFilename());
                            saveConfig = true;
                        } else {
                            System.out.println("WARNING: Auto purchase of U failed. Check log/errors for details");
                        }
                    }
                } else {
                    System.out.println("WARNING: UTN balance is less than required to reserve more Us (" + utnReqired + "). Put more UTN to wallet to enable U auto purchases.");
                }
            }
        }

        if(saveConfig) {
            config.put("utncontracts",utnContracts);
            config.put("ucontracts",uContracts);
            Files.write(Paths.get(walletConfig.getPath()),yaml.dumpAsMap(config).getBytes(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            return loadWallet(walletPath,reserveMoreIfNeeded);
        } else {
            return wallet;
        }
    }

    private static UUTNWallet fixUUTNWallet(String walletPath) throws IOException {
        System.out.println("Fixing wallet");
        UUTNWallet wallet = loadWallet(walletPath,false);
        Set<HashId> utnsToRemove = new HashSet<>();
        Map<HashId,Contract> utnsToAdd = new HashMap<>();
        Map<HashId,String> utnPathesToAdd = new HashMap<>();

        for(HashId id : wallet.utns.keySet()) {
            Contract utn = wallet.utns.get(id);
            ItemState state = getClientNetwork().check(utn.getId()).state;
            System.out.println("Checking " + wallet.utnPathes.get(id));
            if(state == ItemState.APPROVED) {
                continue;
            } else if(state == ItemState.UNDEFINED) {
                System.out.println("UTN contract revision is created but not registered. Looking for a correct one");
                HashMap<String, Contract> contractsInPath = findContracts(walletPath);
                List<Contract> candidates = contractsInPath.values().stream().filter(c -> c.getId().equals(utn.getParent())).collect(Collectors.toList());
                for(Contract candidate : candidates) {
                    if (getClientNetwork().check(candidate.getId()).state == ItemState.APPROVED) {
                        Files.write(Paths.get(walletPath + File.separator + wallet.utnPathes.get(id)), candidate.getLastSealedBinary(), StandardOpenOption.TRUNCATE_EXISTING);
                        utnsToAdd.put(candidate.getId(), candidate);
                        utnPathesToAdd.put(candidate.getId(), wallet.utnPathes.get(id));
                        System.out.println("Found correct one! Replacing...");
                        break;
                    }
                }
                utnsToRemove.add(id);
            } else if(state == ItemState.REVOKED) {
                System.out.println("UTN contract in wallet is revoked. Probably contract was used outside of a wallet OR config was changed manually. Removing...");
                utnsToRemove.add(id);
            } else {
                System.out.println("UTN contract in wallet is other state. Probably contract was used outside of a wallet OR config was changed manually. Removing...");
                utnsToRemove.add(id);
            }
        }

        utnsToRemove.forEach(id -> {
            wallet.utns.remove(id);
            wallet.config.getListOrThrow("utncontracts").remove(wallet.utnPathes.remove(id));
        });

        wallet.utns.putAll(utnsToAdd);
        wallet.utnPathes.putAll(utnPathesToAdd);

        utnPathesToAdd.values().forEach(path -> {
            wallet.config.getListOrThrow("utncontracts").add(path);
        });





        Set<HashId> usToRemove = new HashSet<>();
        Map<HashId,Contract> usToAdd = new HashMap<>();
        Map<HashId,String> uPathesToAdd = new HashMap<>();

        for(HashId id : wallet.us.keySet()) {
            System.out.println("Checking " + wallet.uPathes.get(id));
            Contract ucontract = wallet.us.get(id);
            ItemState state = getClientNetwork().check(ucontract.getId()).state;
            if(state == ItemState.APPROVED) {
                continue;
            } else if(state == ItemState.UNDEFINED) {
                System.out.println("U contract revision is created but not registered. Looking for a correct one");
                HashMap<String, Contract> contractsInPath = findContracts(walletPath);
                List<Contract> candidates = contractsInPath.values().stream().filter(c -> c.getOrigin().equals(ucontract.getOrigin())).collect(Collectors.toList());
                for(Contract candidate : candidates) {
                    if (getClientNetwork().check(candidate.getId()).state == ItemState.APPROVED) {
                        Files.write(Paths.get(walletPath + File.separator + wallet.uPathes.get(id)), candidate.getLastSealedBinary(), StandardOpenOption.TRUNCATE_EXISTING);
                        usToAdd.put(candidate.getId(), candidate);
                        uPathesToAdd.put(candidate.getId(), wallet.uPathes.get(id));
                        System.out.println("Found correct one! Replacing...");
                        break;
                    }
                }
                usToRemove.add(id);
            } else if(state == ItemState.REVOKED) {
                System.out.println("U contract in wallet is revoked. Probably contract was used outside of a wallet OR config was changed manually. Removing...");
                usToRemove.add(id);
            } else {
                System.out.println("U contract in wallet is other state. Probably contract was used outside of a wallet OR config was changed manually. Removing...");
                usToRemove.add(id);
            }
        }

        usToRemove.forEach(id -> {
            wallet.us.remove(id);
            wallet.config.getListOrThrow("ucontracts").remove(wallet.uPathes.remove(id));
        });

        wallet.us.putAll(usToAdd);
        wallet.uPathes.putAll(uPathesToAdd);

        uPathesToAdd.values().forEach(path -> {
            wallet.config.getListOrThrow("ucontracts").add(path);
        });

        Yaml yaml = new Yaml();
        Files.write(Paths.get(walletPath+File.separator+DEFAULT_WALLET_CONFIG),yaml.dumpAsMap(wallet.config).getBytes(), StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);

        return wallet;
    }


    private static void doCreateParcel() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("create-parcel"));
        List<String> names = (List) options.valuesOf("output");

        String uSource = (String) options.valueOf("u");
        if (uSource == null) {
            uSource = (String) options.valueOf("tu");
            if (uSource != null)
                // deprecate warning
                System.out.println("WARNING: Deprecated. Use -u instead.");
        }

        int uAmount = (int) options.valueOf("amount");
        int uAmountStorage = (int) options.valueOf("amount-storage");

        boolean utest = options.has("utest") || options.has("tutest");
        if (options.has("tutest"))
            System.out.println("WARNING: Deprecated. Use -utest instead.");

        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            Contract contract = loadContract(source);

            Contract u = null;

            if(uSource == null) {
                uSource = getUFromWallet(uAmount+uAmountStorage,utest);
            }

            if(uSource != null) {
                u = loadContract(uSource, true);
                report("load payment revision: " + u.getState().getRevision() + " id: " + u.getId());
            }

            Set<PrivateKey> uKeys = new HashSet<>(keysMap().values());
            if(contract != null) {
                if(u != null && uKeys != null && uKeys.size() > 0) {
                    Parcel parcel;
                    Contract newUContract;
                    if(uAmountStorage == 0) {
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + uAmount + " U");
                        report("cnotactId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterContract(contract, u, uAmount, uKeys, utest);
                        newUContract = parcel.getPaymentContract();
                    } else { // if storage payment
                        report("registering the paid contract " + contract.getId() + " from " + source
                                + " for " + uAmount + " U (and " + uAmountStorage + " U for storage)");
                        report("cnotactId: "+contract.getId().toBase64String());
                        parcel = prepareForRegisterPayingParcel(contract, u, uAmount, uAmountStorage, uKeys, utest);
                        newUContract = parcel.getPayloadContract().getNew().get(0);
                    }

                    if (parcel != null) {

                        report("save payment revision: " + newUContract.getState().getRevision() + " id: " + newUContract.getId());

                        CopyOption[] copyOptions = new CopyOption[]{
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        };
                        String uDest = new FilenameTool(uSource).addSuffixToBase("_rev" + u.getRevision()).toString();
                        uDest = FileTool.writeFileContentsWithRenaming(uDest, new byte[0]);
                        if (uDest != null) {
                            Files.move(Paths.get(uSource), Paths.get(uDest), copyOptions);
                            if (saveContract(newUContract, uSource, true, false)) {
                                String name;
                                if(names.size() > s) {
                                    name = names.get(s);
                                } else {
                                    name = new FilenameTool(source).setExtension("uniparcel").toString();
                                }
                                saveParcel(parcel,name);
                            } else {
                                addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup tu revision");
                            }
                        } else {
                            addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup tu revision");
                        }

                    } else {
                        addError(Errors.COMMAND_FAILED.name(),"parcel","unable to prepare parcel");
                    }

                } else {
//                    report("registering the contract " + contract.getId().toBase64String() + " from " + source);
//                    registerContract(contract, (int) options.valueOf("wait"));
                    addError(Errors.COMMAND_FAILED.name(), uSource, "payment contract or private keys for payment contract is missing");
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



    private static void doRegisterParcel() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("register-parcel"));
        List<String> nonOptions = new ArrayList<String>((List) options.nonOptionArguments());
        for (String opt : nonOptions) {
            sources.addAll(asList(opt.split(",")));
        }

        cleanNonOptionalArguments(sources);

        for (int s = 0; s < sources.size(); s++) {
            String source = sources.get(s);
            Parcel parcel = loadParcel(source);

            if (parcel != null) {
                ItemResult ir = registerParcel(parcel, (int) options.valueOf("wait"));
                if(ir.state != ItemState.APPROVED) {
                    addErrors(ir.errors);
                }
            }
        }

        finish();
    }

    private static void doShowId() throws Exception {
        String contractFile = (String) options.valueOf("id");
        Contract c = Contract.fromPackedTransaction(Files.readAllBytes(Paths.get(contractFile)));
        reporter.message(c.getId().toBase64String());
        finish();
    }


    private static void doShowHash() throws Exception {
        String file = (String) options.valueOf("hash");
        reporter.message(HashId.of(Files.readAllBytes(Paths.get(file))).toBase64String());
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

    private static void doProbeFile() throws IOException {
        String contractFile = (String) options.valueOf("probe-file");
        Contract c = Contract.fromPackedTransaction(Files.readAllBytes(Paths.get(contractFile)));
        if(c != null) {
            getClientNetwork().check(c.getId());
        }

        finish();
    }

    private static void doSign() throws IOException {
        String source = (String) options.valueOf("sign");
        List<String> names = (List) options.valuesOf("output");
        Contract c = Contract.fromPackedTransaction(Files.readAllBytes(Paths.get(source)));

        if(c != null) {
            keysMap().values().forEach( k -> c.addSignatureToSeal(k));
            String name;
            if(names.size() > 0) {
                name = names.get(0);
            } else {
                String suffix = "_signedby_"+String.join("_",c.getSealedByKeys().stream().map(k->k.getShortAddress().toString()).collect(Collectors.toSet()));
                name = new FilenameTool(source).addSuffixToBase(suffix).toString();
            }
            saveContract(c,name,true,false);
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
        if(options.has("password")) {
            new FileOutputStream(name + ".private.unikey").write(k.packWithPassword((String) options.valueOf("password")));
        } else {
            new FileOutputStream(name + ".private.unikey").write(k.pack());
        }
        new FileOutputStream(name + ".public.unikey").write(k.getPublicKey().pack());
        if (options.has("base64")) {
            new FileOutputStream(name + ".public.unikey.txt")
                    .write(Base64.encodeLines(k.getPublicKey().pack()).getBytes());
        }
        System.out.println("New key pair ready");
    }


    private static void doRevoke() throws IOException {
        List<String> sources = new ArrayList<String>((List) options.valuesOf("revoke"));

        String uSource = (String) options.valueOf("u");
        if (uSource == null) {
            uSource = (String) options.valueOf("tu");
            if (uSource != null)
                // deprecate warning
                System.out.println("WARNING: Deprecated. Use -u instead.");
        }

        int uAmount = (int) options.valueOf("amount");

        boolean utest = options.has("utest") || options.has("tutest");
        if (options.has("tutest"))
            System.out.println("WARNING: Deprecated. Use -utest instead.");

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
            Contract u = null;
            if(uSource != null) {
                u = loadContract(uSource, true);
                report("load payment revision: " + u.getState().getRevision() + " id: " + u.getId());
            }

            Set<PrivateKey> uKeys = new HashSet<>(keysMap().values());
            report("uKeys num: " + uKeys.size());
            if (contract != null) {
                if(u != null && uKeys != null && uKeys.size() > 0) {
                    report("registering the paid contract " + contract.getId() + " from " + source
                            + " for " + uAmount + " U");
                    Parcel parcel = null;
                    try {
                        if (contract.check()) {
                            report("revoke contract from " + source);
                            parcel = revokeContract(contract, u, uAmount, uKeys, utest, keysMap().values().toArray(new PrivateKey[0]));
                        } else {
                            addErrors(contract.getErrors());
                        }
                    } catch (Quantiser.QuantiserException e) {
                        addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
                    }
                    if(parcel != null) {
                        Contract newUContract = parcel.getPaymentContract();
                        report("save payment revision: " + newUContract.getState().getRevision() + " id: " + newUContract.getId());

                        CopyOption[] copyOptions = new CopyOption[]{
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE
                        };
                        String uDest = new FilenameTool(uSource).addSuffixToBase("_rev" + u.getRevision()).toString();
                        uDest = FileTool.writeFileContentsWithRenaming(uDest, new byte[0]);
                        if (uDest != null) {
                            Files.move(Paths.get(uSource), Paths.get(uDest), copyOptions);
                            if (saveContract(newUContract, uSource, true, false)) {
                                ItemResult ir = registerParcel(parcel, (int) options.valueOf("wait"));
                                if(ir.state != ItemState.APPROVED) {
                                    addErrors(ir.errors);
                                }
                            } else {
                                addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup u revision");
                            }
                        } else {
                            addError(Errors.COMMAND_FAILED.name(),uSource,"unable to backup u revision");
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
                contract = contract.createRevision(keysMap().values());

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
                    boolean sealRequired = false;
                    if (siblingItems != null) {
                        for (Object sibFile : siblingItems) {
                            Contract siblingContract = loadContract((String) sibFile, true);
                            report("add sibling from " + sibFile);
                            contract.addNewItems(siblingContract);
                            sealRequired = true;
                        }
                    }
                    if (revokeItems != null) {
                        for (Object revokeFile : revokeItems) {
                            Contract revokeContract = loadContract((String) revokeFile, true);
                            report("add revoke from " + revokeFile);
                            contract.addRevokingItems(revokeContract);
                            sealRequired = true;
                        }
                    }
                    if (name == null) {
                        name = source;
                    }
                    if (sealRequired) {
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

    private static void saveContractSubitems(String source, String suffix, Contract contract) throws IOException {
        try {
            report("unpack contract from " + source);
            int i = 1;
            if (contract.getNewItems() != null) {
                for (Approvable newItem : contract.getNewItems()) {
                    String newItemFileName = new FilenameTool(source).addSuffixToBase(suffix+"_new_item_" + i).toString();
                    report("save newItem to " + newItemFileName);
                    //                            ((Contract) newItem).seal();
                    saveContract((Contract) newItem, newItemFileName);
                    i++;
                }
            }
            i = 1;
            if (contract.getRevokingItems() != null) {
                for (Approvable revokeItem : contract.getRevokingItems()) {
                    String revokeItemFileName = new FilenameTool(source).addSuffixToBase(suffix+"_revoke_" + i).setExtension("unicon").toString();
                    report("save revokeItem to " + revokeItemFileName);
                    saveContract((Contract) revokeItem, revokeItemFileName);
                    i++;
                }
            }
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
        }
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

            File pathFile = new File(source);
            if (pathFile.exists()) {
                byte[] data = Do.read(pathFile);
                Object x = Boss.load(data);
                if(x == null) {
                    addError(Errors.NOT_SUPPORTED.name(), source, "Unknown packed object. Should be either unicapsule or transaction pack or parcel");
                }
                if(x instanceof Parcel) {
                    saveContractSubitems(source,"_payment", ((Parcel) x).getPaymentContract());
                    saveContractSubitems(source,"_payload", ((Parcel) x).getPayloadContract());
                    saveContract(((Parcel) x).getPaymentContract(),new FilenameTool(source).addSuffixToBase("_payment").setExtension("unicon").toString());
                    saveContract(((Parcel) x).getPayloadContract(),new FilenameTool(source).addSuffixToBase("_payload").setExtension("unicon").toString());
                } else {
                    Contract contract = loadContract(source);
                    saveContractSubitems(source,"", contract);
                }
            } else {
                addError(Errors.NOT_FOUND.name(), source, "Path " + source + " does not exist");
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

    private static PublicKey readKeyAndGetPublic(String keyFilePath) throws IOException {
        PublicKey key;

        if (keyFilePath.endsWith(".private.unikey"))
            key = new PrivateKey(Do.read(keyFilePath)).getPublicKey();
        else if (keyFilePath.endsWith(".public.unikey"))
            key = new PublicKey(Do.read(keyFilePath));
        else {
            try {
                key = new PrivateKey(Do.read(keyFilePath)).getPublicKey();
            } catch (EncryptionError e) {
                key = new PublicKey(Do.read(keyFilePath));
            }
        }

        return key;
    }

    private static void doCreateAddress(String keyFilePath, boolean bShort) throws IOException {

        report("Generate " + (bShort ? "short" : "long") + " address from key: " + keyFilePath);

        PublicKey key = readKeyAndGetPublic(keyFilePath);
        KeyAddress address = new KeyAddress(key, 0, !bShort);

        report("Address: " + address.toString());

        finish();
    }

    private static void doAddressMatch(String address, String keyFilePath) throws IOException {

        boolean bResult = false;

        try {
            PublicKey key = readKeyAndGetPublic(keyFilePath);

            KeyAddress keyAddress = new KeyAddress(address);

            bResult = keyAddress.isMatchingKey(key);
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
                        PublicKey key = readKeyAndGetPublic(keysPath + file.getName());
                        bResult = keyAddress.isMatchingKey(key);
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

    private static void doStartHttpServer(String routeFilePath) throws IOException {
        try {
            cliServices.startJsApiHttpServer(
                    routeFilePath,
                    hashId -> {
                        try {
                            return getClientNetwork().check(hashId).state == ItemState.APPROVED;
                        } catch (IOException e) {
                            report("error while checking contract for approve: " + e);
                            return false;
                        }
                    },
                    (slotId, originId) -> {
                        try {
                            if (slotId == null)
                                return null;
                            return getClientNetwork().client.queryContract(slotId, originId, null);
                        } catch (IOException e) {
                            report("error while querying contract from slot1: " + e);
                            return null;
                        }
                    });
        } catch (Exception e) {
            report("http server error: " + e);
        }
        finish();
    }

    private static void doExecJs() throws Exception {
        String contractFile = (String) options.valueOf("exec-js");
        String scriptName = null;
        if (options.has("script-name"))
            scriptName = (String)options.valueOf("script-name");
        Contract c = Contract.fromPackedTransaction(Files.readAllBytes(Paths.get(contractFile)));
        if(c != null) {
            ItemResult itemResult = getClientNetwork().client.getState(c.getId(), reporter);
            if (itemResult.state == ItemState.APPROVED) {
                if (scriptName != null) {
                    c.execJSByName(scriptName);
                }
                else {
                    List<String> scriptNames = c.extractJSNames();
                    if (scriptNames.size() == 1)
                        c.execJSByName(scriptNames.get(0));
                    else if (scriptNames.size() > 1)
                        report("error: contract has " + scriptNames.size() + " scripts attached, specify script filename please");
                    else
                        report("error: contract has no scripts attached");
                }
            } else {
                report("error: contract should be approved");
            }
        }
        finish();
    }

    private static void doCreateContractWithJs() throws Exception {
        String jsFile = (String) options.valueOf("create-contract-with-js");
        byte[] jsFileBytes = Files.readAllBytes(Paths.get(jsFile));
        List<String> names = (List) options.valuesOf("o");
        List<String> keys = (List) options.valuesOf("k");
        if (names.size() == 0) {
            report("error: output file not specified, use -o option");
            finish();
        }
        if (keys.size() == 0) {
            report("error: key file(s) not specified, use -k option");
            finish();
        }
        String outputFile = names.get(0);
        Contract contract = new Contract();
        Set<PrivateKey> privateKeys = new HashSet<>();
        for (String keyPath : keys) {
            PrivateKey privateKey = PrivateKey.fromPath(Paths.get(keyPath));
            privateKeys.add(privateKey);
        }
        contract.setIssuerKeys(privateKeys);
        contract.setExpiresAt(ZonedDateTime.now().plusDays(90));
        contract.registerRole(new RoleLink("owner", "issuer"));
        contract.registerRole(new RoleLink("creator", "issuer"));
        RoleLink roleLink = new RoleLink("@change_owner_role","owner");
        roleLink.setContract(contract);
        contract.addPermission(new ChangeOwnerPermission(roleLink));
        contract.addSignerKeys(privateKeys);
        String filename = Paths.get(jsFile).getFileName().toString();
        JSApiScriptParameters scriptParameters = new JSApiScriptParameters();
        scriptParameters.domainMasks.add("localhost:*");
        scriptParameters.setPermission(JSApiScriptParameters.ScriptPermissions.PERM_HTTP_CLIENT, true);
        contract.getState().setJS(jsFileBytes, filename, scriptParameters, true);
        contract.seal();
        String createdFileName = FileTool.writeFileContentsWithRenaming(outputFile, contract.getPackedTransaction());
        report("file " + createdFileName + " saved");
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
        if (options.has("utest")) {
            sources.remove("-utest");
            sources.remove("--utest");
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

                BiMapper biMapper = DefaultBiMapper.getInstance();
                //replace existing KeyAddress serializer
                byte[] dummy = new byte[0];
                biMapper.unregister(Bytes.class);
                biMapper.unregister(dummy.getClass());
                DefaultBiMapper.registerAdapter(dummy.getClass(), customByteArrayBiAdapter);
                DefaultBiMapper.registerAdapter(Bytes.class, customBytesBiAdapter);


                BiDeserializer bm = biMapper.newDeserializer();
                contract = new Contract();
                contract.deserialize(binder, bm);

                biMapper.unregister(Bytes.class);
                biMapper.unregister(dummy.getClass());
                DefaultBiMapper.registerAdapter(dummy.getClass(), DefaultBiMapper.getByteArrayBiAdapter());
                DefaultBiMapper.registerAdapter(Bytes.class, DefaultBiMapper.getBytesBiAdapter());


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
     * Load parcel from specified path.
     *
     * @param fileName

     *
     * @return loaded Parcel.
     */
    public static Parcel loadParcel(String fileName) throws IOException {
        Parcel parcel = null;

        File pathFile = new File(fileName);
        if (pathFile.exists()) {
            Path path = Paths.get(fileName);
            byte[] data = Files.readAllBytes(path);
            parcel = Parcel.unpack(data);

        } else {
            addError(Errors.NOT_FOUND.name(), fileName, "Path " + fileName + " does not exist");
        }

        return parcel;
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



        BiMapper biMapper = DefaultBiMapper.getInstance();
        //replace existing KeyAddress serializer
        biMapper.unregister(KeyAddress.class);
        biMapper.registerAdapter(KeyAddress.class, customKeyAddressBiAdapter);

        //replace existing Reference serializer
        biMapper.unregister(Reference.class);
        biMapper.registerAdapter(Reference.class, customReferenceBiAdapter);

        Binder binder = contract.serialize(biMapper.newSerializer());

        //return existed earlier KeyAddress serializer
        biMapper.unregister(KeyAddress.class);
        biMapper.registerAdapter(KeyAddress.class, KeyAddress.getBiAdapter());

        //return existed earlier Reference serializer
        biMapper.unregister(Reference.class);
        DefaultBiMapper.registerClass(Reference.class);

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

            Binder data = null;
            Object obj = null;

            try {
                XStream xstream = new XStream(new DomDriver());
                xstream.registerConverter(new MapEntryConverter());
                xstream.alias(fieldName, Binder.class);
                data = Binder.convertAllMapsToBinders(xstream.fromXML(fields.get(fieldName)));
            } catch (Exception xmlEx) {
                try {
                    Gson gson = new GsonBuilder().create();
                    data = Binder.convertAllMapsToBinders(gson.fromJson(fields.get(fieldName), Binder.class));
                    if (data.containsKey(fieldName))
                        data = (Binder) data.get(fieldName);
                } catch (Exception jsonEx) {
                    try {
                        Yaml yaml = new Yaml();
                        Object loaded = Binder.convertAllMapsToBinders(yaml.load(fields.get(fieldName)));
                        if (loaded.getClass().equals(Binder.class)) {
                            data = (Binder) loaded;
                            if (data.containsKey(fieldName))
                                data = (Binder) data.get(fieldName);
                        } else
                            obj = loaded;
                    } catch (Exception yamlEx) {
                        try {
                            obj = fields.get(fieldName);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            xmlEx.printStackTrace();
                            jsonEx.printStackTrace();
                            yamlEx.printStackTrace();
                        }
                    }
                }
            }

            if ((data != null) || (obj != null)) {
                Binder binder = new Binder();

                if (data != null) {
                    BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();
                    binder.put("data", bm.deserialize(data));
                }
                else
                    binder.put("data", obj);

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
     * Save specified parcel to file.
     *
     * @param parcel              - parcel to save.
     * @param fileName              - name of file to save to.
     *
     */
    public static boolean saveParcel(Parcel parcel, String fileName) throws IOException {
        if (fileName == null) {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss").format(parcel.getPayloadContract().getCreatedAt()) + ".uniparcel";
        }



        byte[] data = parcel.pack();
        String newFileName = FileTool.writeFileContentsWithRenaming(fileName, data);
        report("Parcel is saved to: " + newFileName);
        report("Parcel size: " + data.length);
        try {
            if (parcel.getPaymentContract().check() && parcel.getPayloadContract().check()) {
                report("Parcel has no errors");
            } else {
                addErrors(parcel.getPaymentContract().getErrors());
                addErrors(parcel.getPayloadContract().getErrors());
            }
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", parcel.toString(), e.getMessage());
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
    public static Parcel revokeContract(Contract contract, Contract u, int amount, Set<PrivateKey> uKeys, boolean withTestPayment, PrivateKey... key) throws IOException {

        report("keys num: " + key.length);

        Contract tc = ContractsService.createRevocation(contract, key);
        Parcel parcel = prepareForRegisterContract(tc, u, amount, uKeys, withTestPayment);
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
    public static Parcel prepareForRegisterContract(Contract contract, Contract u, int amount, Set<PrivateKey> uKeys, boolean withTestPayment) throws IOException {

        List<ErrorRecord> errors = contract.getErrors();
        if (errors.size() > 0) {
            report("contract has errors and can't be submitted for registration");
            report("contract id: " + contract.getId().toBase64String());
            addErrors(errors);
        } else {
            Parcel parcel = ContractsService.createParcel(contract, u, amount,  uKeys, withTestPayment);
            return parcel;
        }

        return null;
    }


    /**
     * Register a paying parcel.
     *
     * @param contract              must be a sealed binary.
     */
    public static Parcel prepareForRegisterPayingParcel(Contract contract, Contract u, int amount, int amountStorage, Set<PrivateKey> uKeys, boolean withTestPayment) throws IOException {

        List<ErrorRecord> errors = contract.getErrors();
        if (errors.size() > 0) {
            report("contract has errors and can't be submitted for registration");
            report("contract id: " + contract.getId().toBase64String());
            addErrors(errors);
        } else {
            Set<PrivateKey> keys = new HashSet<>(keysMapContract().values());
            if (keys != null && keys.size() > 0)
                contract.addSignerKeys(keys);

            Parcel parcel = ContractsService.createPayingParcel(contract.getTransactionPack(), u, amount, amountStorage, uKeys, withTestPayment);
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
        report("Contract processing cost is " + contract.getProcessedCostU() + " U");
    }


    private static void finish(int status) {
        if (cliServices.isAnythingStarted())
            cliServices.waitForUserInput();
        // print reports if need
        try {
            if(!options.has("no-cache")) {
                saveSession();
            }
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

    public static void setNodeNumber(int number) {
        System.out.println("Connecting to node " + number);
        nodeNumber = number;
        nodeUrl = null;
        clientNetwork = null;
    }

    public static void setNodeUrl(String url) {
        nodeUrl = url;
        clientNetwork = null;
    }

    public static void setTopologyFileName(String filename) {
        topologyFileName = filename;
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
            } else if(topologyFileName != null){
                clientNetwork = new ClientNetwork(null, topologyFileName, true);
            } else {
                clientNetwork = new ClientNetwork(null, true);
            }

            if (clientNetwork.client == null)
                throw new IOException("failed to connect to to the universa network");

            if(nodeNumber != -1) {
                List<Client.NodeRecord> nodes = clientNetwork.client.getNodes();
                clientNetwork.client = clientNetwork.client.getClient(nodeNumber-1);
                clientNetwork.client.setNodes(nodes);
            }

            if(!options.has("no-cache")) {
                s = getSession(clientNetwork.getNodeNumber());
            }
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
            boolean failed = false;
            for (String fileName : keyFileNames) {

                try {
                    PrivateKey pk = null;

                    byte[] keyBytes = Do.read(fileName);
                    List<String> passwords = (List<String>) options.valuesOf("password");

                    try {
                        pk = new PrivateKey(keyBytes);
                    } catch (PrivateKey.PasswordProtectedException e) {
                        for(String password : passwords) {
                            try {
                                pk = PrivateKey.unpackWithPassword(keyBytes,password);
                                break;
                            } catch (PrivateKey.PasswordProtectedException e1) {

                            }
                        }
                    }
                    if(pk == null) {
                        if(passwords.isEmpty()) {
                            while(true) {
                                System.console().printf("Enter key password " + (keyFileNames.size() > 1 ? "(" + fileName + ")":"") + ": ");
                                String password = new String(System.console().readPassword());
                                if(password.isEmpty())
                                    break;
                                try {
                                    pk = PrivateKey.unpackWithPassword(keyBytes, password);
                                    break;
                                } catch (PrivateKey.PasswordProtectedException e1) {
                                    System.console().printf("Wrong password!\n");
                                }
                            }
                        }
                    }

                    if(pk == null) {
                        addError(Errors.FAILURE.name(),fileName,"Wrong key password");
                        failed = true;
                    } else {
                        keyFiles.put(fileName, pk);
                    }

                } catch (IOException e) {
                    addError(Errors.NOT_FOUND.name(), fileName.toString(), "failed to load key file: " + e.getMessage());
                    failed = true;
                }
            }
            if(failed)
                finish();
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
        static public final String REFERENCE = "Reference";
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
