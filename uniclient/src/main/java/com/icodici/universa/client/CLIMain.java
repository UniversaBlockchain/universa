/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.wallet.Wallet;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import net.sergeych.tools.Reporter;
import net.sergeych.utils.Base64;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Arrays.asList;

public class CLIMain {

    private static final String CLI_VERSION = "0.1";

    private static OptionParser parser;
    private static OptionSet options;
    private static boolean testMode;
    private static String testRootPath;

    private static Reporter reporter = new Reporter();
    private static ClientNetwork clientNetwork;
    private static List<String> keyFileNames = new ArrayList<>();
    private static Map<String,PrivateKey> keyFiles;


    public static final String AMOUNT_FIELD_NAME = "amount";

    static public void main(String[] args) throws IOException {
//        args = new String[]{"-g", "longkey", "-s", "4096"};
        // when we run untitests, it is important
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
                acceptsAll(asList("c", "create"), "Create smart contract from yaml template.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file.yml");
                acceptsAll(asList("j", "json"), "Return result in json format.");
                acceptsAll(asList("v", "verbose"), "Provide more detailed information.");
                acceptsAll(asList("network"), "Check network status.");
                acceptsAll(asList("k", "keys"), "List of comma-separated private key files to" +
                        "use tosign contract with, if appropriated.")
                        .withRequiredArg().ofType(String.class)
                        .withValuesSeparatedBy(",").describedAs("key_file");
                acceptsAll(asList("fingerprints"), "Print fingerprints of keys specified with -k.");
//                acceptsAll(asList("show", "s"), "show contract")
//                        .withRequiredArg().ofType(String.class)
                acceptsAll(asList("e", "export"), "Export specified contract. " +
                        "Default export format is XML. " +
                        "Use '-as' option with values 'json', 'xml' for export as specified format.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file");
                accepts("as", "Use with -e, --export command. Specify format for export contract.")
                        .withRequiredArg()
                        .ofType(String.class)
                        .defaultsTo("xml");
                acceptsAll(asList("i", "import"), "Import contract from specified xml or json file.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file");
                accepts("name", "Use with -e, --export or -i, --import commands. " +
                        "Specify name of destination file.")
                        .withRequiredArg()
                        .ofType(String.class);
                accepts("extract-key", "Use with -e, --export command. " +
                        "Extracts any public key(s) from specified role into external file.")
                        .withRequiredArg()
                        .ofType(String.class);
                accepts("get", "Use with -e, --export command. " +
                        "Extracts any field of the contract into external file.")
                        .withRequiredArg()
                        .ofType(String.class);
                accepts("set", "Use with -e, --export command. " +
                        "Specify field of the contract for update.")
                        .withRequiredArg()
                        .ofType(String.class);
                accepts("value", "Use with -e, --export command and after -set argument. " +
                        "Update specified with -set argument field of the contract.")
                        .withRequiredArg()
                        .ofType(String.class);
                acceptsAll(asList("f", "find"), "Search all contracts in the specified path including subpaths." +
                        "Use -r key to check all contracts in the path recursively.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("path");
                acceptsAll(asList("d", "download"), "Download contract from the specified url.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("url");
                acceptsAll(asList("ch", "check"), "Check contract for validness. " +
                        "Use -r key to check all contracts in the path recursively.")
                        .withRequiredArg().ofType(String.class)
                        .describedAs("file/path");
                accepts("r", "Use with --ch, --check or -f, --find command. " +
                        "Specify to check contracts in the path and do it recursively.");
                accepts("binary", "Use with --ch, --check. " +
                        "Specify to check contracts from binary data.");
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
            if( options.has("fingerprints") ) {
                printFingerprints();
                finish();
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
            if (options.has("e")) {
                String source = (String) options.valueOf("e");
                String format = (String) options.valueOf("as");
                String name = (String) options.valueOf("name");
                String extractKeyRole = (String) options.valueOf("extract-key");
                List extractFields = options.valuesOf("get");

                List updateFields = options.valuesOf("set");
                List updateValues = options.valuesOf("value");
                HashMap<String, String> updateFieldsHashMap = new HashMap<>();
                Contract contract = loadContract(source);
                try {
                    for (int i = 0; i < updateFields.size(); i++) {
                        updateFieldsHashMap.put((String) updateFields.get(i), (String) updateValues.get(i));
                    }
                } catch (Exception e) {

                }
                if(updateFieldsHashMap != null && updateFieldsHashMap.size() > 0) {
                    updateFields(contract, updateFieldsHashMap);
                }
                if(extractKeyRole != null) {
                    exportPublicKeys(contract, extractKeyRole, name);
                } else if(extractFields != null && extractFields.size() > 0) {
                    exportFields(contract, extractFields, name, format);
                } else {
                    exportContract(contract, name, format);
                }
                finish();
            }
            if (options.has("i")) {
                String source = (String) options.valueOf("i");

                Contract contract = importContract(source);

                String name = (String) options.valueOf("name");
                if (name == null)
                {
                    File file = new File(source);
                    name = file.getParent() + "/Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unicon";
                }
                saveContract(contract, name);
                finish();
            }
            if (options.has("f")) {
                String source = (String) options.valueOf("f");

                HashMap<String, Contract> allFoundContracts = findContracts(source, options.has("r"));

                List<Wallet> wallets = Wallet.determineWallets(new ArrayList<>(allFoundContracts.values()));

                if(wallets.size() > 0) {
                    printWallets(wallets);
                } else {
                    report("No wallets found");
                }

                if(allFoundContracts.size() > 0) {
                    printContracts(allFoundContracts);
                } else {
                    report("No contracts found");
                }

                finish();
            }
            if (options.has("d")) {
                String source = (String) options.valueOf("d");

                downloadContract(source);

                finish();
            }
            if (options.has("ch")) {
                String source = (String) options.valueOf("ch");

                if(options.has("binary")) {
                    // TODO: load bytes from source and check it in the checkBytesIsValidContract()
                    Contract contract = Contract.fromYamlFile(source);
                    keysMap().values().forEach(k -> contract.addSignerKey(k));
                    byte[] data = contract.seal();
                    checkBytesIsValidContract(data);
                } else {
                    HashMap<String, Contract> contracts = findContracts(source, options.has("r"));

                    report("");
                    if(contracts.size() > 0) {
                        report("Checking loaded contracts");
                        report("---");
                        for (String key : contracts.keySet()) {
                            report("Checking contract at " + key);
                            checkContract(contracts.get(key));
                            report("---");
                        }
                    } else {
                        report("No contracts found");
                    }
                }

                finish();
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

    private static void printFingerprints() throws IOException {
        Map<String, PrivateKey> kk = keysMap();
        if( kk.isEmpty() )
            report("please specify at least one key file with --fingerprints");
        else {
            kk.forEach((name, key)->{
                report("Fingerprints:");
                report(name+"\t"+ Base64.encodeCompactString(key.fingerprint()));
            });
        }
    }

    private static void anonymousKeyPrints() throws IOException {
        Map<String, PrivateKey> kk = keysMap();
        if( kk.isEmpty() )
            report("please specify at least one key file with --fingerprints");
        else {
            kk.forEach((name, key)->{
                report("Anonymous key prints:");
                report(name+"\t"+ Base64.encodeCompactString(key.fingerprint()));
            });
        }
    }

    private static void createContract() throws IOException {
        String source = (String) options.valueOf("c");
        Contract c = Contract.fromYamlFile(source);
        keysMap().values().forEach(k -> c.addSignerKey(k));
        byte[] data = c.seal();
        // try sign
        String contractFileName = source.replaceAll("\\.(yml|yaml)$", ".unicon");
        try (FileOutputStream fs = new FileOutputStream(contractFileName)) {
            fs.write(data);
        }
        report("created contract file: " + contractFileName);
        checkContract(c);
        finish();
    }

    /**
     * Check contract for errors. Print errors if found.
     *
     * @param contract - contract to check.
     *
     */
    private static void checkContract(Contract contract) {
        if( !contract.isOk() ) {
            reporter.message("The capsule is not sealed");
            contract.getErrors().forEach(e->reporter.error(e.getError().toString(), e.getObjectName(), e.getMessage()));
        }
        contract.seal();
        contract.check();
        contract.getErrors().forEach(error -> {
            addError(error.getError().toString(), error.getObjectName(), error.getMessage());
        });
        if(contract.getErrors().size() == 0) {
            report("Contract is valid");
        }
    }

    /**
     * Check bytes is contract. And if bytes is, check contract for errors. Print errors if found.
     *
     * @param data - data to check.
     *
     * @return true if bytes is Contract and Contract is valid.
     *
     */
    private static Boolean checkBytesIsValidContract(byte[] data) {
        try {
            Contract contract = new Contract(data);
            if( !contract.isOk() ) {
                reporter.message("The capsule is not sealed");
                contract.getErrors().forEach(e->reporter.error(e.getError().toString(), e.getObjectName(), e.getMessage()));
            }
            checkContract(contract);
        } catch (RuntimeException e) {
            addError(Errors.BAD_VALUE.name(), "", e.getMessage());
            return false;
        } catch (IOException e) {
            addError(Errors.BAD_VALUE.name(), "", e.getMessage());
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

        Contract contract;
        if("yaml".equals(extension) || "yml".equals(extension)) {
            contract = Contract.fromYamlFile(sourceName);
        } else {
            String stringData = "";
            BufferedReader in = new BufferedReader(new FileReader(sourceName));
            String str;
            while ((str = in.readLine()) != null)
                stringData += str;
            in.close();

            Binder binder;

            if ("json".equals(extension)) {
                binder = Binder.convertAllMapsToBinders(JsonTool.fromJson(stringData));
            } else {
                XStream xstream = new XStream(new DomDriver());
//            magicApi.registerConverter(new MapEntryConverter());
                xstream.alias("root", Binder.class);
                binder = (Binder) xstream.fromXML(stringData);

            }

            BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();
            contract = new Contract();
            contract.deserialize(binder, bm);
        }
        report(">>> imported contract: " + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()));

        if("yaml".equals(extension) || "yml".equals(extension)) {
            report("import from yaml ok");
        } else if("json".equals(extension)) {
            report("import from json ok");
        } else {
            report("import ok");

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
    private static Contract loadContract(String fileName) throws IOException {
        Contract contract;

        reporter.verbose("---");
        reporter.verbose("Loading contract from: " + fileName);
        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);

        contract = new Contract(data);
        reporter.verbose("Contract has loaded");

        return contract;
    }

    /**
     * Export contract to specified xml or json file.
     *
     * @param contract - contract to export.
     * @param fileName - name of file to export to.
     * @param format - format of file to export to. Can be xml or json.
     *
     */
    private static void exportContract(Contract contract, String fileName, String format) throws IOException {
        report("export format: " + format);

        if (fileName == null)
        {
            if(testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt());
            } else {
                fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt());
            }
        }

        Binder binder = contract.serialize(DefaultBiMapper.getInstance().newSerializer());

        byte[] data;
        if("json".equals(format)) {
            String jsonString = JsonTool.toJsonString(binder);
            data = jsonString.getBytes();

        } else {
            XStream xstream = new XStream(new DomDriver());
//            magicApi.registerConverter(new MapEntryConverter());
            xstream.alias("root", Binder.class);
            data = xstream.toXML(binder).getBytes();
        }
        try (FileOutputStream fs = new FileOutputStream(fileName + "." + format)) {
            fs.write(data);
            fs.close();
        }

        if("json".equals(format)) {
            report(fileName + " export as json ok");
        } else {
            report(fileName + " export ok");
        }
    }

    /**
     * Export public keys from specified contract.
     *
     * @param contract - contract to export.
     * @param roleName - from which role keys should be exported.
     * @param fileName - name of file to export to.
     *
     */
    private static void exportPublicKeys(Contract contract, String roleName, String fileName) throws IOException {

        if (fileName == null)
        {
            if(testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_" + roleName + "_public_key";
            } else {
                fileName = "Universa_" + roleName + "_public_key";
            }
        }

        Role role = contract.getRole(roleName);

        if(role != null) {
            Set<PublicKey> keys = role.getKeys();

            int index = 0;
            byte[] data;
            for (PublicKey key : keys) {
                index++;
                data = key.pack();
                try (FileOutputStream fs = new FileOutputStream(fileName + "_" + index + ".pub")) {
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
     * @param contract - contract to export.
     * @param fieldNames - list of field names to export.
     * @param fileName - name of file to export to.
     * @param format - format of file to export to. Can be xml or json.
     *
     */
    private static void exportFields(Contract contract, List<String> fieldNames, String fileName, String format) throws IOException {
        report("export format: " + format);

        if (fileName == null)
        {
            if(testMode && testRootPath != null) {
                fileName = testRootPath + "Universa_fields_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt());
            } else {
                fileName = "Universa_fields_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt());
            }
        }

        Binder hm = new Binder();

        try {
            for (String fieldName : fieldNames) {
                report("export field: " + fieldName + " -> " + contract.get(fieldName));
                hm.put(fieldName, contract.get(fieldName));
            }

            Binder binder =  DefaultBiMapper.getInstance().newSerializer().serialize(hm);

            byte[] data;
            if ("json".equals(format)) {
                String jsonString = JsonTool.toJsonString(binder);
                data = jsonString.getBytes();
            } else {
                XStream xstream = new XStream(new DomDriver());
                xstream.alias("root", Binder.class);
                data = xstream.toXML(binder).getBytes();
            }
            try (FileOutputStream fs = new FileOutputStream(fileName + "." + format)) {
                fs.write(data);
                fs.close();
            }

            if ("json".equals(format)) {
                report("export fields as json ok");
            } else {
                report("export fields ok");
            }
        } catch (IllegalArgumentException e) {
            report("export fields error: " + e.getMessage());
        }
    }

    /**
     * Update fields for specified contract.
     *
     * @param contract - contract for update.
     * @param fields - map of field names and values.
     *
     */
    private static void updateFields(Contract contract, HashMap<String, String> fields) throws IOException {

        for (String fieldName : fields.keySet()) {
            report("update field: " + fieldName + " -> " + fields.get(fieldName) );

            Binder binder = new Binder();
            Binder data = null;

            try {
                XStream xstream = new XStream(new DomDriver());
                xstream.alias("root", Binder.class);
                data = (Binder) xstream.fromXML(fields.get(fieldName));
            } catch (Exception xmlEx) {
                try {
                    data = Binder.convertAllMapsToBinders(JsonTool.fromJson(fields.get(fieldName)));
                } catch (Exception jsonEx) {

                }
            }

            if(data != null) {
                BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();

                binder.put("data", bm.deserialize(data));

                contract.set(fieldName, binder);

                report("update field " + fieldName + " ok");
            } else {
                report("update field " + fieldName + " error: no valid data");
            }
        }

        report("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getExpiresAt()));
    }

    /**
     * Save specified contract to file.
     *
     * @param contract - contract for update.
     * @param fileName - name of file to save to.
     *
     */
    private static void saveContract(Contract contract, String fileName) throws IOException {
        if (fileName == null)
        {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unicon";
        }

        byte[] data = contract.seal();
        report("save contract, seal size: " + data.length);
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }
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

        if(pathFile.exists()) {

            fillWithContractsFiles(foundContractFiles, path, recursively);

            Contract contract;
            for (File file : foundContractFiles) {
                try {
                    contract = loadContract(file.getAbsolutePath());
                    foundContracts.put(file.getAbsolutePath(), contract);
                } catch (RuntimeException e) {
                    addError(Errors.BAD_VALUE.name(), "", e.getMessage());
                } catch (IOException e) {
                    addError(Errors.BAD_VALUE.name(), "", e.getMessage());
                }
            }
        } else {
            addError("0", "", "Path does not exist");
        }
        return foundContracts;
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
     * Fill given List with contract files, found in given path recursively.
     *
     * Does not return new Lists but put found files into the given List for optimisation purposes.
     *
     * @param foundContractFiles
     * @param path
     */
    private static void fillWithContractsFiles(List<File> foundContractFiles, String path, Boolean recursively) {
        File pathFile = new File(path);

        if(pathFile.exists()) {
            ContractFilesFilter filter = new ContractFilesFilter();
            DirsFilter dirsFilter = new DirsFilter();

            if (pathFile.isDirectory()) {
                File[] foundFiles = pathFile.listFiles(filter);
                foundContractFiles.addAll(Arrays.asList(foundFiles));

                if(recursively) {
                    File[] foundDirs = pathFile.listFiles(dirsFilter);
                    for (File file : foundDirs) {
                        fillWithContractsFiles(foundContractFiles, file.getPath(), true);
                    }
                }
            } else {
                if (filter.accept(pathFile)) {
                    foundContractFiles.add(pathFile);
                }
            }
        }
    }

    /**
     * Just print wallets info to console.
     *
     * @param wallets
     *
     */
    private static void printWallets(List<Wallet> wallets) {
        reporter.message("---");
        reporter.message("");

        List<Contract> foundContracts = new ArrayList<>();
        for(Wallet wallet : wallets) {
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
                    if(balance.containsKey(currency)) {
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
            reporter.message("total in the wallet: " );
            for (String c : balance.keySet()) {
                reporter.message( balance.get(c) + " (" + c + ") ");
            }
        }
    }

    /**
     * Just print contracts info to console.
     *
     * @param contracts
     *
     */
    private static void printContracts(HashMap<String, Contract> contracts) {
        reporter.verbose("");
        reporter.verbose("---");
        reporter.verbose("");
        reporter.verbose("found contracts list: ");
        reporter.verbose("");
        for (String key : contracts.keySet()) {
            try {
                reporter.verbose(key + ": " +
                        "contract created at " +
                        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contracts.get(key).getCreatedAt()) +
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

    public static void setTestRootPath(String rootPath) {
        testRootPath = rootPath;
    }

    public static Reporter getReporter() {
        return reporter;
    }

    public static synchronized ClientNetwork getClientNetwork() {
        if( clientNetwork == null )
            clientNetwork = new ClientNetwork();
        return clientNetwork;
    }

    public static synchronized Map<String,PrivateKey> keysMap() throws IOException {
        if( keyFiles == null ) {
            keyFiles = new HashMap<>();
            for(String fileName: keyFileNames) {
                PrivateKey pk = new PrivateKey(Do.read(fileName));
                keyFiles.put(fileName, pk);
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
                if(unc.equals(extension)) {
                    return true;
                }
            }
            return false;
        }

        private String getExtension(File pathname) {
            String filename = pathname.getPath();
            int i = filename.lastIndexOf('.');
            if ( i>0 && i<filename.length()-1 ) {
                return filename.substring(i+1).toLowerCase();
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
}
