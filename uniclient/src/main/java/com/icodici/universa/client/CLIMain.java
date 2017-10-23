/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import net.sergeych.tools.Reporter;
import net.sergeych.utils.Base64;
import org.yaml.snakeyaml.Yaml;

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
//                Contract contract = Contract.fromYamlFile(source);
                Contract contract = loadContract(source);
//                if (name == null) {
//                    File file = new File(source);
//                    name = file.getParent() + "/Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt());
//                }
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
                    name = file.getParent() + "/Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unic";
                }
                saveContract(contract, name);
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
                binder = (Binder) convertAllMapsToBinder(JsonTool.fromJson(stringData));
            } else {
                XStream xstream = new XStream(new DomDriver());
//            magicApi.registerConverter(new MapEntryConverter());
                xstream.alias("root", Binder.class);
                binder = (Binder) xstream.fromXML(stringData);

            }
//        traceAllMapsToBinder(binder2, 0);

            BiDeserializer bm = DefaultBiMapper.getInstance().newDeserializer();
            contract = new Contract();
            contract.deserialize(binder, bm);

//        keysMap().values().forEach(k -> contract.addSignerKey(k));
//        checkContract(contract);
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

    private static Contract loadContract(String fileName) throws IOException {
        // TODO: resolve Contract bug: Contract cannot be initiated from sealed data until
        // Permissions beaing created or initialized or something like that.
        loadContractHook();

        Contract contract;

        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);
        report("load contract, data size: " + data.length);

        contract = new Contract(data);

        return contract;
    }

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
//            data = "xml".getBytes();
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

//        Binder binder = contract.serialize(DefaultBiMapper.getInstance().newSerializer());
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
                    data = (Binder) convertAllMapsToBinder(JsonTool.fromJson(fields.get(fieldName)));
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

    private static void saveContract(Contract contract, String fileName) throws IOException {
        if (fileName == null)
        {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unic";
        }

        byte[] data = contract.seal();
        report("save contract, seal size: " + data.length);
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }
    }

    // This method is a hook, it resolve Contract bug: Contract cannot be initiated from sealed data until
    // Permissions beaing created or initialized or something like that.
    private static void loadContractHook() throws IOException {
        Contract.fromYamlFile("./src/test_files/simple_root_contract_v2.yml");
    }

    private static Object convertAllMapsToBinder(Object object) {

        if(object != null) {
            if (object instanceof List) {
                List list = (List) object;
                for (int i = 0; i < list.size(); i++) {
                    list.set(i, convertAllMapsToBinder(list.get(i)));
                }
            }

            if (object instanceof Map) {
                object = Binder.from(object);
                Map map = (Map) object;
                for (Object key : map.keySet()) {
                    map.replace(key, convertAllMapsToBinder(map.get(key)));
                }
            }
        }

        return object;
    }

    private static void traceAllMapsToBinder(Object object, int level) {
        String shift = level + "";
        for (int i = 0; i < level; i++) {
            shift += "-";
        }

        if(object != null) {
            report(shift + "object is: " + object.getClass());

            if (object instanceof List) {
                List list = (List) object;
                for (Object item : list) {
                    traceAllMapsToBinder(item, level + 1);
                }
            }

            if (object instanceof Map) {
                object = Binder.from(object);
                Map map = (Map) object;
                for (Object key : map.keySet()) {
                    traceAllMapsToBinder(map.get(key), level + 1);
                }
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
}
