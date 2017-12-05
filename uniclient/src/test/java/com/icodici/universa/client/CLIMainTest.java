/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.contract.TransactionContract;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.Main;
import net.sergeych.tools.Binder;
import net.sergeych.tools.ConsoleInterceptor;
import net.sergeych.tools.Reporter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.icodici.universa.client.RegexMatcher.matches;
import static org.junit.Assert.*;

public class CLIMainTest {

    protected static String rootPath = "./src/test_files/";
    protected static String basePath = rootPath + "temp_contracts/";
    private static List<Binder> errors;
    private static String output;

    protected static PrivateKey ownerKey1;
    protected static PrivateKey ownerKey2;
    protected static PrivateKey ownerKey3;

    public static final String FIELD_NAME = "amount";

    protected static final String PRIVATE_KEY = "_xer0yfe2nn1xthc.private.unikey";

    protected static final String PRIVATE_KEY_PATH = rootPath + PRIVATE_KEY;

    protected static List<Main> localNodes = new ArrayList<>();

    @BeforeClass
    public static void prepareRoot() throws Exception {

        createLocalNetwork();

//        new File(rootPath + "/simple_root_contract.unicon").delete();
        assert (new File(rootPath + "/simple_root_contract.yml").exists());
        assert (new File(rootPath + "/simple_root_contract_v2.yml").exists());

        CLIMain.setTestMode();
        CLIMain.setTestRootPath(rootPath);
        CLIMain.setNodeUrl("http://localhost:8080");

        File file = new File(basePath);
        if(!file.exists()) {
            file.mkdir();
        }

        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field = "definition.expires_at";
        String value = "definition.expires_at:\n" +
                "    seconds: " + zdt.toEpochSecond() + "\n" +
                "    __type: unixtime";

        callMain("-c", "-v", rootPath + "simple_root_contract_v2.yml", "-name", basePath + "contract1.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "-name", basePath + "contract2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "-name", basePath + "contract3.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "-name", basePath + "contract_to_export.unicon",
                "-set", field, "-value", value);


        Contract c1 = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c1.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey1 = c1.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c1.getRole("owner")).addKeyRecord(new KeyRecord(goodKey1.getPublicKey()));
        c1.seal();
        CLIMain.saveContract(c1, basePath + "contract_for_revoke1.unicon");

        Contract c2 = Contract.fromDslFile(rootPath + "another_root_contract_v2.yml");
        c2.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey2 = c2.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c2.getRole("owner")).addKeyRecord(new KeyRecord(goodKey2.getPublicKey()));
        c2.seal();
        CLIMain.saveContract(c2, basePath + "contract_for_revoke2.unicon");

        Contract c3 = Contract.fromDslFile(rootPath + "simple_root_contract_v2.yml");
        c3.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey3 = c3.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c3.getRole("owner")).addKeyRecord(new KeyRecord(goodKey3.getPublicKey()));
        c3.seal();
        CLIMain.saveContract(c3, basePath + "contract_for_revoke3.unicon");

        callMain("-e", basePath + "contract1.unicon", "-name", basePath + "contract_to_import.json");
        callMain("-e", basePath + "contract1.unicon", "-name", basePath + "contract_to_import.xml");
        callMain("-e", basePath + "contract1.unicon", "-name", basePath + "contract_to_import.XML");
        callMain("-e", basePath + "contract1.unicon", "-name", basePath + "contract_to_import.yaml");

        callMain("-i", basePath + "contract_to_import.json", "-name", basePath + "not_signed_contract.unicon");

        Path path = Paths.get(rootPath + "packedContract.unicon");
        byte[] data = Files.readAllBytes(path);
        try (FileOutputStream fs = new FileOutputStream(basePath + "packedContract.unicon")) {
            fs.write(data);
            fs.close();
        }

        path = Paths.get(rootPath + "packedContract.unicon");
        data = Files.readAllBytes(path);
        try (FileOutputStream fs = new FileOutputStream(basePath + "packedContract2.unicon")) {
            fs.write(data);
            fs.close();
        }

        path = Paths.get(rootPath + "packedContract_new_item.unicon");
        data = Files.readAllBytes(path);
        try (FileOutputStream fs = new FileOutputStream(basePath + "packedContract_new_item.unicon")) {
            fs.write(data);
            fs.close();
        }

        path = Paths.get(rootPath + "packedContract_revoke.unicon");
        data = Files.readAllBytes(path);
        try (FileOutputStream fs = new FileOutputStream(basePath + "packedContract_revoke.unicon")) {
            fs.write(data);
            fs.close();
        }

        ownerKey1 = TestKeys.privateKey(3);
        ownerKey2 = TestKeys.privateKey(1);
        ownerKey3 = TestKeys.privateKey(2);
    }


    @AfterClass
    public static void cleanAfter() throws Exception {
        File file = new File(basePath);
        if(file.exists()) {
            for (File f : file.listFiles())
                f.delete();
        }
        file.delete();

        destroyLocalNetwork();
    }

    public static void createLocalNetwork() throws Exception {
        for (int i = 0; i < 3; i++)
            localNodes.add(createMain("node" + (i + 1), false));

        Main main = localNodes.get(0);
        assertEquals("http://localhost:8080", main.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", main.myInfo.publicUrlString());

        assertEquals(main.cache, main.node.getCache());
    }


    public static void destroyLocalNetwork() {

        localNodes.forEach(x->x.shutdown());
    }

    @Test
    public void checkTransactionPack() throws Exception {
        Contract r = new Contract(ownerKey1);
        r.seal();

        Contract c = r.createRevision(ownerKey1);
        Contract n = c.split(1)[0];
        n.seal();
        c.seal();
        c.addNewItems(n);

        String path = rootPath + "/testtranspack.unicon";
//        path = "/Users/sergeych/dev/!/e7810197-d148-4936-866b-44daae182e83.transaction";
        c.seal();
        CLIMain.saveContract(c, path, true);
//        try (FileOutputStream fs = new FileOutputStream(path)) {
//            fs.write(c.getPackedTransaction());
//            fs.close();
//        }
        callMain("--check", path, "-v");
        System.out.println(output);
    }

    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml", "-j", "-name", basePath + "simple_root_contract.unicon");
        System.out.println(output);
        assert (new File(basePath + "simple_root_contract.unicon").exists());
    }

    @Test
    public void createContractWithUpdateField() throws Exception {

        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field = "definition.expires_at";
        String value = "definition.expires_at:\n" +
                "    seconds: " + zdt.toEpochSecond() + "\n" +
                "    __type: unixtime";
        callMain("-c", rootPath + "simple_root_contract.yml", "-v",
                "-name", basePath + "simple_root_contract3.unicon",
                "-set", field, "-value", value);
        System.out.println(output);
        assert (new File(basePath + "simple_root_contract3.unicon").exists());
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
    }

    @Test
    public void createTwoNotSignedContracts() throws Exception {
        callMain("-c", "-v",
                rootPath + "simple_root_contract.yml", "-name", basePath + "simple_root_contract1.unicon",
                rootPath + "simple_root_contract.yml", "-name", basePath + "simple_root_contract2.unicon");
        System.out.println(output);
        assert (new File(basePath + "simple_root_contract1.unicon").exists());
        assert (new File(basePath + "simple_root_contract2.unicon").exists());
    }

    @Test
    public void createTwoSignedContracts() throws Exception {
        callMain("-c", "-v",
                rootPath + "simple_root_contract.yml", "-name", basePath + "simple_root_contract1.unicon",
                rootPath + "simple_root_contract.yml", "-name", basePath + "simple_root_contract2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assert (new File(basePath + "simple_root_contract1.unicon").exists());
        assert (new File(basePath + "simple_root_contract2.unicon").exists());
    }

    @Test
    public void checkTheNetwork() throws Exception {
        Reporter r = callMain("--network", "--verbose");
        assertThat(r.getMessage(-1) +
                        r.getMessage(-2) +
                        r.getMessage(-3) +
                        r.getMessage(-4) +
                        r.getMessage(-5),
                matches("3 node"));
    }

    @Test
    public void createRegisterCheckRevoke() throws Exception {
        String keyFileName = rootPath + "_xer0yfe2nn1xthc.private.unikey";
        String contractFileName = basePath + "contract7.unicon";
        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                "-k", keyFileName, "-name", contractFileName
        );
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());
        Contract c = Contract.fromSealedFile(contractFileName);
        System.out.println(c.getId());
//        callMain2("--ch", contractFileName, "--verbose");
        callMain2("--register", contractFileName, "--verbose");
        for (int i = 0; i < 10; i++) {
            callMain2("--probe", c.getId().toBase64String());
            Thread.sleep(500);
        }
    }

    @Test
    public void createRegisterTwoContractsCheckRevoke1() throws Exception {
        String keyFileName = rootPath + "_xer0yfe2nn1xthc.private.unikey";

        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "another_root_contract_v2.unicon"
        );
        String contractFileName2 = basePath + "another_root_contract_v2.unicon";
        assertTrue(new File(contractFileName2).exists());
        assertEquals(0, errors.size());

        Contract c = Contract.fromSealedFile(contractFileName);
        System.out.println("first contract: " + c.getId());

        Contract c2 = Contract.fromSealedFile(contractFileName2);
        System.out.println("second contract: " + c.getId());

        callMain2("--register", contractFileName, contractFileName2, "--verbose");
    }

    @Test
    public void createRegisterTwoContractsCheckRevoke2() throws Exception {
        String keyFileName = rootPath + "_xer0yfe2nn1xthc.private.unikey";

        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "another_root_contract_v2.unicon"
        );
        String contractFileName2 = basePath + "another_root_contract_v2.unicon";
        assertTrue(new File(contractFileName2).exists());
        assertEquals(0, errors.size());

        Contract c = Contract.fromSealedFile(contractFileName);
        System.out.println("first contract: " + c.getId());

        Contract c2 = Contract.fromSealedFile(contractFileName2);
        System.out.println("second contract: " + c.getId());

        callMain2("--register", contractFileName + "," + contractFileName2, "--verbose");
    }

    @Test
    public void createRegisterTwoContractsCheckRevoke3() throws Exception {
        String keyFileName = rootPath + "_xer0yfe2nn1xthc.private.unikey";

        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "another_root_contract_v2.unicon"
        );
        String contractFileName2 = basePath + "another_root_contract_v2.unicon";
        assertTrue(new File(contractFileName2).exists());
        assertEquals(0, errors.size());

        Contract c = Contract.fromSealedFile(contractFileName);
        System.out.println("first contract: " + c.getId());

        Contract c2 = Contract.fromSealedFile(contractFileName2);
        System.out.println("second contract: " + c.getId());

        callMain2("--register", "--verbose", contractFileName, contractFileName2);
    }

    @Test
    public void createRegisterTwoContractsCheckRevoke4() throws Exception {
        String keyFileName = rootPath + "_xer0yfe2nn1xthc.private.unikey";

        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "-name", basePath + "another_root_contract_v2.unicon"
        );
        String contractFileName2 = basePath + "another_root_contract_v2.unicon";
        assertTrue(new File(contractFileName2).exists());
        assertEquals(0, errors.size());

        Contract c = Contract.fromSealedFile(contractFileName);
        System.out.println("first contract: " + c.getId());

        Contract c2 = Contract.fromSealedFile(contractFileName2);
        System.out.println("second contract: " + c.getId());

        callMain2("--register", "--verbose", contractFileName + "," + contractFileName2);
    }

    @Test
    public void checkState() throws Exception {
        callMain2("--probe", "py2GSOxgOGBPiaL9rnGm800lf1Igk3/BGU/wSawFNic7H/x0r8KPOb61iqYlDXWMtR44r5GaO/EDa5Di8c6lmQ", "--verbose");
    }

    @Test
    public void checkStateTwoHashes1() throws Exception {
        callMain2("--probe",
                "py2GSOxgOGBPiaL9rnGm800lf1Igk3/BGU/wSawFNic7H/x0r8KPOb61iqYlDXWMtR44r5GaO/EDa5Di8c6lmQ",
                "G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA",
                "--verbose");
    }

    @Test
    public void checkStateTwoHashes2() throws Exception {
        callMain2("--probe",
                "py2GSOxgOGBPiaL9rnGm800lf1Igk3/BGU/wSawFNic7H/x0r8KPOb61iqYlDXWMtR44r5GaO/EDa5Di8c6lmQ" + "," +
                "G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA",
                "--verbose");
    }

    @Test
    public void checkStateTwoHashes3() throws Exception {
        callMain2("--probe",
                "--verbose",
                "py2GSOxgOGBPiaL9rnGm800lf1Igk3/BGU/wSawFNic7H/x0r8KPOb61iqYlDXWMtR44r5GaO/EDa5Di8c6lmQ",
                "G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA");
    }

    @Test
    public void checkStateTwoHashes4() throws Exception {
        callMain2("--probe",
                "--verbose",
                "py2GSOxgOGBPiaL9rnGm800lf1Igk3/BGU/wSawFNic7H/x0r8KPOb61iqYlDXWMtR44r5GaO/EDa5Di8c6lmQ" + "," +
                "G0lCqE2TPn9wiioHDy5nllWbLkRwPA97HdnhtcCn3EDAuoDBwiZcRIGjrBftGLFWOVUY8D5yPVkEj+wqb6ytrA");
    }

    @Test
    public void createAndSign() throws Exception {
        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                 "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-name", basePath + "simple_root_contract_v2.unicon"
        );
        System.out.println(new File(basePath + "simple_root_contract_v2.unicon").getAbsolutePath());
        assert (new File(basePath + "simple_root_contract_v2.unicon").exists());
        if (errors.size() > 0) {
            System.out.println(errors);
        }
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void fingerprints() throws Exception {
        callMain(
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "--fingerprints"
        );
        assert (output.indexOf("test_files/_xer0yfe2nn1xthc.private.unikey") >= 0);
        assert (output.indexOf("B24XkVNy3fSJUZBzLsnJo4f+ZqGwbNxHgBr198FIPgyy") >= 0);
//        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsJSONTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "json");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsPrettyJSONTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "json", "-pretty");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsXMLTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "xml");
        System.out.println(output);
        assert (output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsYamlTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "yaml");
        System.out.println(output);
        assert (output.indexOf("export as yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWithNameTest() throws Exception {
        String name = "ExportedContract.json";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-name", basePath + name);
        System.out.println(output);
        assert (output.indexOf(name + " export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest1() throws Exception {
        callMain(
                "-e", "-as", "json", "-pretty",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest2() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon", "-as", "json", "-pretty",
                basePath + "contract2.unicon", "-as", "xml");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 0);
        assert (output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest3() throws Exception {
        callMain(
                "-e", "-pretty", "-v",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest4() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon," + basePath + "contract2.unicon",
                "-pretty", "-v");
        System.out.println(output);
        assert (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest5() throws Exception {
        callMain(
                "-e", "-as", "xml",
                basePath + "contract1.unicon," + basePath + "contract2.unicon");
        System.out.println(output);
        assert (output.indexOf("export as xml ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest6() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon", "-name", basePath + "test6.XML",
                basePath + "contract2.unicon", "-name", basePath + "test6.YML");
        System.out.println(output);
        assert (output.indexOf("export as xml ok") >= 0);
        assert (output.indexOf("export as yml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWrongPathTest() throws Exception {
        callMain(
                "-e", basePath + "not_exist_contract.unicon");
        System.out.println(output);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_FOUND.name(), errors.get(0).get("code"));
            assertEquals(basePath + "not_exist_contract.unicon", errors.get(0).get("object"));
        }
    }

    @Test
    public void exportPublicKeys() throws Exception {
        String role = "owner";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "--extract-key", role);
        System.out.println(output);
        assert (output.indexOf(role + " export public keys ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportPublicKeysWrongRole() throws Exception {
        String role = "wrongRole";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-extract-key", role);
        System.out.println(output);
        assert (output.indexOf(role + " export public keys ok") < 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFields1() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2);
        System.out.println(output);
        assert (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFields2() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1 + "," + field2);
        System.out.println(output);
        assert (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsXML() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "xml", "-get", field1, "-get", field2);
        System.out.println(output);
        assert (output.indexOf("export fields as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsYaml() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "yaml", "-get", field1, "-get", field2);
        System.out.println(output);
        assert (output.indexOf("export fields as yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsJSON() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2, "-as", "json");
        System.out.println(output);
        assert (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsPrettyJSON() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2, "-as", "json", "-pretty");
        System.out.println(output);
        assert (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFields() throws Exception {
        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field1 = "definition.issuer";
        String value1 = "<definition.issuer>\n" +
                "    <SimpleRole>\n" +
                "      <keys isArray=\"true\">\n" +
                "        <item>\n" +
                "          <KeyRecord>\n" +
                "            <name>Universa</name>\n" +
                "            <key>\n" +
                "              <RSAPublicKey>\n" +
                "                <packed>\n" +
                "                  <binary>\n" +
                "                    <base64>HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0W\n" +
                "smcAt5a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOyta\n" +
                "ICE01bkOkf6Mz5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJ\n" +
                "UZi66iu9e0SXupOr/+BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/na\n" +
                "Iaf2yuxiQNz3uFMTn0IpULCMvLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ\n" +
                "/4wNBUveBDLFLlOcMpCzWlO/D7M2IyNa8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzF\n" +
                "bNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZchxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GL\n" +
                "y+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8LyIqeM7dSyaHFTBII/sLuFru6ffoKx\n" +
                "BNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwalMexOc3/kPEEdfjH/GcJU0Mw6\n" +
                "DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=</base64>\n" +
                "                  </binary>\n" +
                "                </packed>\n" +
                "              </RSAPublicKey>\n" +
                "            </key>\n" +
                "          </KeyRecord>\n" +
                "        </item>\n" +
                "      </keys>\n" +
                "      <name>issuer</name>\n" +
                "    </SimpleRole>\n" +
                "  </definition.issuer>";
        String field2 = "definition.expires_at";
        String value2 = "<definition.expires__at>\n" +
                "       <unixtime>" + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss [XXX]").format(zdt) + "</unixtime>\n" +
                "</definition.expires__at>";
        callMain(
                "-e", basePath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2);
        System.out.println(output);
//        assert(output.indexOf("update field " + field1 + " ok") >= 0);
        assert (output.indexOf("update field " + field2 + " ok") >= 0);
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFieldsFromJSON() throws Exception {
        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field1 = "definition.issuer";
        String value1 = "{\"definition.issuer\":{\"keys\":[{\"name\":\"Universa\",\"key\":{\"__type\":\"RSAPublicKey\",\"packed\":{\"__type\":\"binary\",\"base64\":\"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0W\\nsmcAt5a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOyta\\nICE01bkOkf6Mz5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJ\\nUZi66iu9e0SXupOr/+BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/na\\nIaf2yuxiQNz3uFMTn0IpULCMvLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ\\n/4wNBUveBDLFLlOcMpCzWlO/D7M2IyNa8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzF\\nbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZchxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GL\\ny+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8LyIqeM7dSyaHFTBII/sLuFru6ffoKx\\nBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwalMexOc3/kPEEdfjH/GcJU0Mw6\\nDgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"}},\"__type\":\"KeyRecord\"}],\"__type\":\"SimpleRole\",\"name\":\"issuer\"}}";
        String field2 = "definition.expires_at";
        String value2 = "{\"definition.expires_at\": {\"seconds\":" + zdt.toEpochSecond() + ",\"__type\":\"unixtime\"}}";
        callMain(
                "-e", basePath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2);
        System.out.println(output);
        assert (output.indexOf("update field " + field1 + " ok") >= 0);
        assert (output.indexOf("update field " + field2 + " ok") >= 0);
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFieldsFromPrettyJSON() throws Exception {
        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field1 = "definition.issuer";
        String value1 = "{\"definition.issuer\": {\n" +
                "      \"keys\": [\n" +
                "        {\n" +
                "          \"name\": \"Universa\",\n" +
                "          \"key\": {\n" +
                "            \"__type\": \"RSAPublicKey\",\n" +
                "            \"packed\": {\n" +
                "              \"__type\": \"binary\",\n" +
                "              \"base64\": \"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0W\\nsmcAt5a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOyta\\nICE01bkOkf6Mz5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJ\\nUZi66iu9e0SXupOr/+BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/na\\nIaf2yuxiQNz3uFMTn0IpULCMvLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ\\n/4wNBUveBDLFLlOcMpCzWlO/D7M2IyNa8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzF\\nbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZchxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GL\\ny+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8LyIqeM7dSyaHFTBII/sLuFru6ffoKx\\nBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwalMexOc3/kPEEdfjH/GcJU0Mw6\\nDgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c\\u003d\"\n" +
                "            }\n" +
                "          },\n" +
                "          \"__type\": \"KeyRecord\"\n" +
                "        }\n" +
                "      ],\n" +
                "      \"__type\": \"SimpleRole\",\n" +
                "      \"name\": \"issuer\"\n" +
                "    }}";
        String field2 = "definition.expires_at";
        String value2 = "{\"definition.expires_at\": {\"seconds\":" + zdt.toEpochSecond() + ",\"__type\":\"unixtime\"}}";
        callMain(
                "-e", basePath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2,
                "-pretty");
        System.out.println(output);
        assert (output.indexOf("update field " + field1 + " ok") >= 0);
        assert (output.indexOf("update field " + field2 + " ok") >= 0);
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFieldsFromYaml() throws Exception {
        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field1 = "definition.issuer";
        String value1 = "definition.issuer:\n" +
                "  keys:\n" +
                "  - name: Universa\n" +
                "    key:\n" +
                "      __type: RSAPublicKey\n" +
                "      packed:\n" +
                "        __type: binary\n" +
                "        base64: |-\n" +
                "          HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0W\n" +
                "          smcAt5a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOyta\n" +
                "          ICE01bkOkf6Mz5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJ\n" +
                "          UZi66iu9e0SXupOr/+BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/na\n" +
                "          Iaf2yuxiQNz3uFMTn0IpULCMvLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ\n" +
                "          /4wNBUveBDLFLlOcMpCzWlO/D7M2IyNa8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzF\n" +
                "          bNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZchxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GL\n" +
                "          y+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8LyIqeM7dSyaHFTBII/sLuFru6ffoKx\n" +
                "          BNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwalMexOc3/kPEEdfjH/GcJU0Mw6\n" +
                "          DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\n" +
                "    __type: KeyRecord\n" +
                "  __type: SimpleRole\n" +
                "  name: issuer";
        String field2 = "definition.expires_at";
        String value2 = "definition.expires_at:\n" +
                "    seconds: " + zdt.toEpochSecond() + "\n" +
                "    __type: unixtime";
        callMain(
                "-e", basePath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2);
        System.out.println(output);
        assert (output.indexOf("update field " + field1 + " ok") >= 0);
        assert (output.indexOf("update field " + field2 + " ok") >= 0);
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWrongFields() throws Exception {
        String field = "definition.wrong";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field, "-as", "json");
        System.out.println(output);
        assert (output.indexOf("export fields as json ok") < 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromJSONTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromXMLTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.XML");
        System.out.println(output);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromYamlTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from yaml ok") >= 0);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importAndUpdateTest() throws Exception {
        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field2 = "definition.expires_at";
        String value2 = "definition.expires_at:\n" +
                "    seconds: " + zdt.toEpochSecond() + "\n" +
                "    __type: unixtime";

        callMain(
                "-i", basePath + "contract_to_import.yaml",
                "-set", field2, "-value", value2);
        System.out.println(output);
        assertEquals(1, errors.size());
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("update field " + field2 + " ok") >= 0);
        assert (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest1() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json",
                basePath + "contract_to_import.xml",
                basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(3, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(1).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest2() throws Exception {
        callMain(
                "-i", "-name", basePath + "contract_json.unicon", basePath + "contract_to_import.json",
                "-name", basePath + "contract_xml.unicon", basePath + "contract_to_import.xml",
                "-name", basePath + "contract_yaml.unicon", basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(3, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(1).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest3() throws Exception {
        callMain(
                "-i", "-v", basePath + "contract_to_import.json",
                basePath + "contract_to_import.xml",
                basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(3, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(1).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest4() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json," +
                        basePath + "contract_to_import.xml," +
                        basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(3, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(1).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest5() throws Exception {
        callMain(
                "-i", "-v", basePath + "contract_to_import.json," +
                        basePath + "contract_to_import.xml," +
                        basePath + "contract_to_import.yaml");
        System.out.println(output);
        assert (output.indexOf("import from json ok") >= 0);
        assert (output.indexOf("import from yaml ok") >= 0);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(3, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(1).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
        }
    }

    @Test
    public void importFromWrongPathTest() throws Exception {
        callMain(
                "-i", basePath + "not_exist_contract.yaml");
        System.out.println(output);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_FOUND.name(), errors.get(0).get("code"));
            assertEquals(basePath + "not_exist_contract.yaml", errors.get(0).get("object"));
        }
    }
//
//    @Test
//    public void importExportXMLTest() throws Exception {
//        callMain(
//                "-ie", rootPath + "contract_to_import.xml");
//        System.out.println(output);
//        assert(output.indexOf("files are equals") >= 0);
//        assertEquals(0, errors.size());
//    }


    @Test
    public void importWithNameTest() throws Exception {
        String name = "ImportedContract.unicon";
        callMain(
                "-i", basePath + "contract_to_import.xml", "-name", basePath + name);
        System.out.println(output);
        assert (output.indexOf("import from xml ok") >= 0);
        assertEquals(1, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void findContractsInPath() throws Exception {

        // Create contract files (coins and some non-coins)
        File dirFile = new File(rootPath + "contract_subfolder/");
        if (!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if (!dirFile.exists()) dirFile.mkdir();

        List<Integer> coinValues = Arrays.asList(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60);
        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(coinValues);
        for (Contract coin : listOfCoinsWithAmount) {
            int rnd = new Random().nextInt(2);
            String dir = "";
            switch (rnd) {
                case 0:
                    dir += "contract_subfolder/";
                    break;
                case 1:
                    dir += "contract_subfolder/contract_subfolder_level2/";
                    break;
            }
            CLIMain.saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getIntOrThrow(FIELD_NAME) + ".unicon");
        }

        Contract nonCoin = Contract.fromDslFile("./src/test_files/simple_root_contract_v2.yml");
        nonCoin.seal();
        CLIMain.saveContract(nonCoin, rootPath + "contract_subfolder/NonCoin.unicon");
        CLIMain.saveContract(nonCoin, rootPath + "contract_subfolder/contract_subfolder_level2/NonCoin.unicon");

        // Found wallets

        callMain("-f", rootPath + "contract_subfolder/", "-v", "-r");
        System.out.println(output);


        // Clean up files

        File[] filesToRemove = new File(rootPath + "contract_subfolder/").listFiles();
        for (File file : filesToRemove) {
            file.delete();
        }

        filesToRemove = new File(rootPath + "contract_subfolder/contract_subfolder_level2/").listFiles();
        for (File file : filesToRemove) {
            file.delete();
        }

        Integer total = 0;
        for (Integer i : coinValues) {
            total += i;
        }
        assert (output.indexOf(total + " (TUNC)") >= 0);
    }

    @Test
    public void findContractsInWrongPath() throws Exception {

        callMain("-f", rootPath + "not_exist_subfolder/", "-v", "-r");
        System.out.println(output);
        assert (output.indexOf("No contracts found") >= 0);
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_FOUND.name(), errors.get(0).get("code"));
            assertEquals(rootPath + "not_exist_subfolder/", errors.get(0).get("object"));
        }
    }

    @Test
    public void findTwoPaths1() throws Exception {
        callMain("-f",
                rootPath + "not_exist_subfolder",
                rootPath + "not_exist_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void findTwoPaths2() throws Exception {
        callMain("-f",
                rootPath + "not_exist_subfolder" + "," +
                        rootPath + "not_exist_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void findTwoPaths3() throws Exception {
        callMain("-f", "-v",
                rootPath + "not_exist_subfolder",
                rootPath + "not_exist_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void findTwoPaths4() throws Exception {
        callMain("-f", "-v",
                rootPath + "not_exist_subfolder" + "," +
                        rootPath + "not_exist_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void downloadContract() throws Exception {
        callMain("-d", "www.universa.io");
        System.out.println(output);
        assert (output.indexOf("downloading from www.universa.io") >= 0);
        assertEquals(0, errors.size());
    }

//    @Test
//    public void checkDataIsValidContract() throws Exception {
//        callMain("-ch", rootPath + "simple_root_contract_v2.yml", "--binary");
//        System.out.println(output);
//        assert(output.indexOf("Contract is valid") >= 0);
//        assertEquals(0, errors.size());
//    }

    @Test
    public void checkContract() throws Exception {
        callMain("-ch", basePath + "contract1.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoContracts1() throws Exception {
        callMain("-ch",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoContracts2() throws Exception {
        callMain("-ch",
                basePath + "contract1.unicon," +
                        basePath + "contract2.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoContracts3() throws Exception {
        callMain("-ch", "-v",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoContracts4() throws Exception {
        callMain("-ch", "-v",
                basePath + "contract1.unicon," +
                        basePath + "contract2.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkContractInPath() throws Exception {
        // check contracts
        callMain("-ch", basePath, "-v");
        System.out.println(output);
//        assertEquals(3, errors.size());
    }

    //    @Test
    public void checkContractInNotExistPath() throws Exception {
        // check contracts
        callMain("-ch", basePath + "notexist.unicon", "-v");
        System.out.println(output);

        assert (output.indexOf("No contracts found") >= 0);
    }

    @Test
    public void checkContractInPathRecursively() throws Exception {

        // Create contract files (coins and some non-coins)

        File dirFile = new File(rootPath + "contract_subfolder/");
        if (!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if (!dirFile.exists()) dirFile.mkdir();

        List<Integer> coinValues = Arrays.asList(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60);
        List<Contract> listOfCoinsWithAmount = createListOfCoinsWithAmount(coinValues);
        for (Contract coin : listOfCoinsWithAmount) {
            int rnd = new Random().nextInt(2);
            String dir = "";
            switch (rnd) {
                case 0:
                    dir += "contract_subfolder/";
                    break;
                case 1:
                    dir += "contract_subfolder/contract_subfolder_level2/";
                    break;
            }
            CLIMain.saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getIntOrThrow(FIELD_NAME) + ".unicon");
        }

        Contract nonCoin = Contract.fromDslFile("./src/test_files/simple_root_contract_v2.yml");
        nonCoin.seal();
        CLIMain.saveContract(nonCoin, rootPath + "contract_subfolder/NonCoin.unicon");
        CLIMain.saveContract(nonCoin, rootPath + "contract_subfolder/contract_subfolder_level2/NonCoin.unicon");

        // check contracts

        callMain("-ch", rootPath, "-v", "-r");
        System.out.println(output);
//        assertEquals(5, errors.size());


        // Clean up files

        File[] filesToRemove = new File(rootPath + "contract_subfolder/").listFiles();
        for (File file : filesToRemove) {
            file.delete();
        }

        filesToRemove = new File(rootPath + "contract_subfolder/contract_subfolder_level2/").listFiles();
        for (File file : filesToRemove) {
            file.delete();
        }
    }

    @Test
    public void checkTwoPaths1() throws Exception {
        callMain("-ch",
                rootPath,
                rootPath + "contract_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoPaths2() throws Exception {
        callMain("-ch",
                rootPath + "," +
                        rootPath + "contract_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoPaths3() throws Exception {
        callMain("-ch", "-v",
                rootPath,
                rootPath + "contract_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void checkTwoPaths4() throws Exception {
        callMain("-ch", "-v",
                rootPath + "," +
                        rootPath + "contract_subfolder2");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void checkNotSignedContract() throws Exception {
        callMain("-ch", basePath + "not_signed_contract.unicon");
        System.out.println(output);
        assertEquals(1, errors.size());
    }

    @Test
    public void checkOldContract() throws Exception {
        callMain("-ch", rootPath + "old_api_contract.unicon", "-v");
        System.out.println(output);
        assertEquals(true, errors.size() > 0);
    }

    @Test
    public void revokeContractVirtual() throws Exception {

        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

        System.out.println("---");
        System.out.println("register contract");
        System.out.println("---");

        CLIMain.registerContract(c);

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);
        assert (output.indexOf(ItemState.APPROVED.name()) >= 0);



        PrivateKey issuer1 = TestKeys.privateKey(1   );
        TransactionContract tc = new TransactionContract();

        // among issuers there is now owner
        tc.setIssuer(issuer1, goodKey);
        tc.addContractToRemove(c);

        tc.seal();

        assertTrue(tc.check());

        System.out.println("---");
        System.out.println("register revoking contract");
        System.out.println("---");

        CLIMain.registerContract(tc);

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check revoking contract");
        System.out.println("---");

        callMain("--probe", tc.getId().toBase64String());

        System.out.println(output);
        assert (output.indexOf(ItemState.APPROVED.name()) >= 1);



        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);

        assert (output.indexOf(ItemState.REVOKED.name()) >= 0);
    }
//
//    @Test
//    public void registerManyContracts() throws Exception {
//
//        int numContracts = 100;
//        List<Contract> contracts = new ArrayList<>();
//
//        for (int i = 0; i < numContracts; i++) {
//            Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
//            c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
//            PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
//            // let's make this key among owners
//            ((SimpleRole) c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
//            c.seal();
//
//            contracts.add(c);
//        }
//
//        Thread.sleep(500);
//
//        for (int i = 0; i < numContracts; i++) {
//
//            System.out.println("---");
//            System.out.println("register contract " + i);
//            System.out.println("---");
//            final Contract contract = contracts.get(i);
//            Thread thread = new Thread(() -> {
//                try {
//                    System.out.println("register contract -> run thread");
//                    CLIMain.registerContract(contract);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });
//
//            thread.start();
//        }
//
//        Thread.sleep(30000);
//
//        for (int i = 0; i < numContracts; i++) {
//            System.out.println("---");
//            System.out.println("check contract " + i);
//            System.out.println("---");
//
//            final Contract contract = contracts.get(i);
//            Thread thread = new Thread(() -> {
//                System.out.println("check contract -> run thread");
//                try {
//                    callMain2("--probe", contract.getId().toBase64String());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//
//            thread.start();
//        }
//
//        Thread.sleep(30000);
//
//        System.out.println("---");
//        System.out.println("check contracts in order");
//        System.out.println("---");
//        for (int i = 0; i < numContracts; i++) {
//
//            final Contract contract = contracts.get(i);
//            try {
//                callMain2("--probe", contract.getId().toBase64String());
//            } catch (IOException e) {
//                e.printStackTrace();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//
//        assertEquals(0, CLIMain.getReporter().getErrors().size());
//    }

    @Test
    public void registerManyContractsFromVariousNodes() throws Exception {

        ClientNetwork clientNetwork1 = new ClientNetwork("http://localhost:8080", null);
        ClientNetwork clientNetwork2 = new ClientNetwork("http://localhost:6002", null);
        ClientNetwork clientNetwork3 = new ClientNetwork("http://localhost:6004", null);


        int numContracts = 10;
        List<Contract> contracts = new ArrayList<>();

        for (int i = 0; i < numContracts; i++) {
            Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
            c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
            PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
            // let's make this key among owners
            ((SimpleRole) c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
            c.seal();

            contracts.add(c);
        }

        Thread.sleep(500);

        for (int i = 0; i < numContracts; i++) {

//            System.out.println("---");
//            System.out.println("register contract " + i);
//            System.out.println("---");
            final Contract contract = contracts.get(i);
            Thread thread1 = new Thread(() -> {
                try {
//                    System.out.println("register contract on the client 1 -> run thread");
                    CLIMain.registerContract(contract);
                    ItemResult r1 = clientNetwork1.register(contract.getPackedTransaction(), 50);
//                    System.out.println("register contract on the client 1 -> result: " + r1.toString());
                } catch (IOException e) {
                    if(e.getCause() instanceof SocketTimeoutException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else if(e.getCause() instanceof ConnectException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else if(e.getCause() instanceof IllegalStateException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else {
                        e.printStackTrace();
                    }
                }
            });
            thread1.start();

            Thread thread2 = new Thread(() -> {
                try {
//                    System.out.println("register contracz on the client 2 -> run thread");
                    CLIMain.registerContract(contract);
                    ItemResult r2 = clientNetwork2.register(contract.getPackedTransaction(), 50);
//                    System.out.println("register contract on the client 2 -> result: " + r2.toString());
                } catch (IOException e) {
                    if(e.getCause() instanceof SocketTimeoutException) {
                        System.err.println(">>>> ERROR 2: " + e.getMessage());
                    } else if(e.getCause() instanceof ConnectException) {
                        System.err.println(">>>> ERROR 2: " + e.getMessage());
                    } else if(e.getCause() instanceof IllegalStateException) {
                        System.err.println(">>>> ERROR 2: " + e.getMessage());
                    } else {
                        e.printStackTrace();
                    }
                }
            });
            thread2.start();

            Thread thread3 = new Thread(() -> {
                try {
//                    System.out.println("register contract on the client 3 -> run thread");
                    CLIMain.registerContract(contract);
                    ItemResult r3 = clientNetwork3.register(contract.getPackedTransaction(), 50);
//                    System.out.println("register contract on the client 3 -> result: " + r3.toString());
                } catch (IOException e) {
                    if(e.getCause() instanceof SocketTimeoutException) {
                        System.err.println(">>>> ERROR 3: " + e.getMessage());
                    } else if(e.getCause() instanceof ConnectException) {
                        System.err.println(">>>> ERROR 3: " + e.getMessage());
                    } else if(e.getCause() instanceof IllegalStateException) {
                        System.err.println(">>>> ERROR 3: " + e.getMessage());
                    } else {
                        e.printStackTrace();
                    }
                }
            });
            thread3.start();
        }


        Thread.sleep(1000);

        System.out.println("---");
        System.out.println("check contracts in order");
        System.out.println("---");
        for (int i = 0; i < numContracts; i++) {

            final Contract contract = contracts.get(i);
            callMain2("--probe", contract.getId().toBase64String());
        }

        assertEquals(0, CLIMain.getReporter().getErrors().size());
    }

    @Test
    public void registerContractFromVariousNetworks() throws Exception {

        final Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

//        CLIMain.registerContract(c);

        List<ClientNetwork> clientNetworks = new ArrayList<>();

        int numConnections = 10;
        for (int i = 0; i < numConnections; i++) {
            clientNetworks.add(new ClientNetwork("http://localhost:8080", new PrivateKey(2048), null));
        }

        for (int i = 0; i < numConnections; i++) {
            final int index = i;
            try {
                clientNetworks.get(index).ping();
//                System.out.println("result (" + index + "): " + r1.toString());
            } catch (IOException e) {
                if (e.getCause() instanceof SocketTimeoutException) {
                    System.err.println(">>>> ERROR 1: " + e.getMessage());
                } else if (e.getCause() instanceof ConnectException) {
                    System.err.println(">>>> ERROR 1: " + e.getMessage());
                } else if (e.getCause() instanceof IllegalStateException) {
                    System.err.println(">>>> ERROR 1: " + e.getMessage());
                } else {
                    e.printStackTrace();
                }
            }
        }

        for (int i = 0; i < numConnections; i++) {
            final int index = i;
            Thread thread1 = new Thread(() -> {
                try {
                    ItemResult r1 = clientNetworks.get(index).register(c.getPackedTransaction());
                    System.out.println("result from thread (" + index + "): " + r1.toString());
                } catch (IOException e) {
                    if (e.getCause() instanceof SocketTimeoutException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else if (e.getCause() instanceof ConnectException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else if (e.getCause() instanceof IllegalStateException) {
                        System.err.println(">>>> ERROR 1: " + e.getMessage());
                    } else {
                        e.printStackTrace();
                    }
                }
            });
            thread1.start();
        }

//        Thread.sleep(10000);
    }

    @Test
    public void checkSessionReusing() throws Exception {

        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole) c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

        CLIMain.setVerboseMode(true);

        Thread.sleep(1000);


        CLIMain.clearSession();

        System.out.println("---session cleared---");

        CLIMain.registerContract(c);


        Thread.sleep(1000);

        CLIMain.setNodeUrl("http://localhost:8080");

        System.out.println("---session should be reused from variable---");

        CLIMain.registerContract(c);


        CLIMain.saveSession();

        Thread.sleep(1000);

        CLIMain.clearSession(false);

        CLIMain.setNodeUrl("http://localhost:8080");

        System.out.println("---session should be reused from file---");

        CLIMain.registerContract(c);


        CLIMain.saveSession();

        Thread.sleep(1000);

        CLIMain.clearSession(false);

        CLIMain.setNodeUrl(null);

        System.out.println("---session should be created for remote network---");

        CLIMain.registerContract(c);

        CLIMain.saveSession();


        CLIMain.breakSession(-1);

        Thread.sleep(2000);

        CLIMain.clearSession(false);

        CLIMain.setNodeUrl("http://localhost:8080");

        System.out.println("---broken session should be recreated---");

        CLIMain.registerContract(c);
    }

    @Test
    public void revokeCreatedContractWithRole() throws Exception {

        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();
        String contractFileName = basePath + "with_role_for_revoke.unicon";
        CLIMain.saveContract(c, contractFileName);

        System.out.println("---");
        System.out.println("register contract");
        System.out.println("---");

//        CLIMain.registerContract(c);
        callMain2("--register", contractFileName, "--verbose");

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);
        assert (output.indexOf(ItemState.APPROVED.name()) >= 0);



        callMain2("-revoke", contractFileName, "-v",
                "-k", PRIVATE_KEY_PATH);



        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract after revoke");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);

        assert (output.indexOf(ItemState.REVOKED.name()) >= 0);
    }

    @Test
    public void revokeContract() throws Exception {
        String contractFileName = basePath + "contract_for_revoke3.unicon";

        callMain2("--register", contractFileName, "--verbose");

        Contract c = CLIMain.loadContract(contractFileName);
        System.out.println("contract: " + c.getId().toBase64String());

        Thread.sleep(1500);
        System.out.println("probe before revoke");
        callMain2("--probe", c.getId().toBase64String(), "--verbose");
        Thread.sleep(1500);
        callMain2("-revoke", contractFileName, "-v",
                "-k", PRIVATE_KEY_PATH);
        Thread.sleep(1500);
        System.out.println("probe after revoke");
        callMain("--probe", c.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assert (output.indexOf(ItemState.REVOKED.name()) >= 0);
    }

    @Test
    public void revokeContractWithoutKey() throws Exception {
        String contractFileName = basePath + "contract_for_revoke1.unicon";

        callMain2("--register", contractFileName, "--verbose");
        callMain2("-revoke", contractFileName, "-v");

        Thread.sleep(1500);
        System.out.println("probe after revoke");
        Contract c = CLIMain.loadContract(contractFileName);
        callMain("--probe", c.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assert (output.indexOf(ItemState.REVOKED.name()) < 0);
    }

    @Test
    public void revokeTwoContracts() throws Exception {
        String contractFileName1 = basePath + "contract_for_revoke1.unicon";
        String contractFileName2 = basePath + "contract_for_revoke2.unicon";

        System.out.println("---");
        System.out.println("register contracts");
        System.out.println("---");
        callMain2("--register", contractFileName1, contractFileName2, "--verbose");

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("revoke contracts");
        System.out.println("---");

        callMain2("-revoke", contractFileName1, contractFileName2, "-v",
                "-k", PRIVATE_KEY_PATH);


        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contracts after revoke");
        System.out.println("---");

        Contract c1 = CLIMain.loadContract(contractFileName1);
        callMain2("--probe", c1.getId().toBase64String(), "--verbose");

        Contract c2 = CLIMain.loadContract(contractFileName2);
        callMain("--probe", c2.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assert (output.indexOf(ItemState.REVOKED.name()) >= 1);
    }

    @Test
    public void packContractWithCounterParts() throws Exception {
        String contractFileName = basePath + "coin1000.unicon";
        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(1000));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();
        CLIMain.saveContract(contract, contractFileName);
        callMain2("--check", contractFileName, "-v");
        callMain2("-pack-with", contractFileName,
                "-add-sibling", basePath + "packedContract_new_item.unicon",
                "-add-revoke", basePath + "packedContract_revoke.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-v");

        callMain("--check", contractFileName, "-v");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void packContractWithCounterPartsWithName() throws Exception {
        String contractFileName = basePath + "coin100.unicon";
        String savingFileName = basePath + "packed.unicon";
        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();
        CLIMain.saveContract(contract, contractFileName);
        callMain2("--check", contractFileName, "-v");
        callMain2("-pack-with", contractFileName,
                "-add-sibling", basePath + "contract2.unicon",
                "-add-revoke", basePath + "contract_for_revoke1.unicon",
                "-name", savingFileName,
                "-v");

        callMain("--check", savingFileName, "-v");
        System.out.println(output);
//        assertEquals(0, errors.size());
    }

    @Test
    public void unpackContractWithCounterParts() throws Exception {
        String fileName = basePath + "packedContract.unicon";
        callMain2("--check", fileName, "-v");
        callMain2("-unpack", fileName, "-v");
        System.out.println(" ");
        callMain2("--check", basePath + "packedContract_new_item_1.unicon", "-v");
        System.out.println(" ");
        callMain("--check", basePath + "packedContract_revoke_1.unicon", "-v");

        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void extraChecks() throws Exception {
        callMain2("-v", "--check", "/Users/sergeych/dev/!/0199efcd-0313-4e2c-8f19-62d6bd1c9755.transaction");
    }

    @Test
    public void calculateContractProcessingCostFromBinary() throws Exception {

        // Should use a binary contract, call -cost command and print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

//        sealCheckTrace(contract, true);

        CLIMain.saveContract(contract, basePath + "contract_for_cost.unicon");

        System.out.println("--- cost checking ---");

        // Register a version (20) +
        // Check 2048 bits signature (1)
        int costShouldBe = 21;
        callMain("--cost", basePath + "contract_for_cost.unicon");
        System.out.println(output);

        assert (output.indexOf("Contract processing cost is " + costShouldBe + " (UTN)") >= 0);
    }

    @Test
    public void calculateContractProcessingCostFromManySources() throws Exception {

        // Should use contracts from all sources, call one -cost command for all of them and print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

//        sealCheckTrace(contract, true);

        CLIMain.saveContract(contract, basePath + "contract_for_cost1.unicon");
        CLIMain.saveContract(contract, basePath + "contract_for_cost2.unicon");

        System.out.println("--- cost checking ---");

        // Register a version (20) +
        // Check 2048 bits signature (1)
        int costShouldBe = 21;
        callMain("--cost",
                basePath + "contract_for_cost1.unicon",
                basePath + "contract_for_cost2.unicon");
        System.out.println(output);

        assert (output.indexOf("Contract processing cost is " + costShouldBe + " (UTN)") >= 2);
    }

    @Test
    public void registerContractAndPrintProcessingCost() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

//        sealCheckTrace(contract, true);

        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- registering contract (with processing cost print) ---");

        // Register a version (20) +
        // Check 2048 bits signature (1)
        int costShouldBe = 21;
        callMain("--register", basePath + "contract_for_register_and_cost.unicon", "--cost");
        System.out.println(output);

        assert (output.indexOf("Contract processing cost is " + costShouldBe + " (UTN)") >= 0);
    }

    @Test
    public void registerManyContractAndPrintProcessingCost() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        for (int i = 0; i < 2; i++) {
            Contract contract = createCoin();
            contract.getStateData().set(FIELD_NAME, new Decimal(100));
            contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
            contract.seal();

//            sealCheckTrace(contract, true);

            CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost" + i + ".unicon");
        }

        System.out.println("--- registering contract (with processing cost print) ---");

        // Register a version (20) +
        // Check 2048 bits signature (1)
        int costShouldBe = 21;
        callMain("--register",
                basePath + "contract_for_register_and_cost0.unicon",
                basePath + "contract_for_register_and_cost1.unicon",
                "--cost");
        System.out.println(output);

        assert (output.indexOf("Contract processing cost is " + costShouldBe + " (UTN)") >= 1);
    }

    private List<Contract> createListOfCoinsWithAmount(List<Integer> values) throws Exception {
        List<Contract> contracts = new ArrayList<>();


        for (Integer value : values) {
            Contract contract = createCoin();
            contract.getStateData().set(FIELD_NAME, new Decimal(value));
            contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
            contract.seal();

            sealCheckTrace(contract, true);

            contracts.add(contract);
        }

        return contracts;
    }

//    private void saveContract(Contract contract, String fileName) throws IOException {
//
//        if (fileName == null) {
//            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unicon";
//        }
//
//        byte[] data = contract.getPackedTransaction();
//        try (FileOutputStream fs = new FileOutputStream(fileName)) {
//            fs.write(data);
//            fs.close();
//        }
//    }

    private static Reporter callMain(String... args) throws Exception {
        output = ConsoleInterceptor.copyOut(() -> {
            CLIMain.main(args);
            errors = CLIMain.getReporter().getErrors();
        });
        return CLIMain.getReporter();
    }

    private static void callMain2(String... args) throws Exception {
        CLIMain.main(args);
    }


    protected static void sealCheckTrace(Contract c, boolean isOk) {
        c.seal();
        c.check();
        c.traceErrors();

        if (isOk)
            assertTrue(c.isOk());
        else
            assertFalse(c.isOk());
    }

    protected Contract createCoin() throws IOException {
        return createCoin(rootPath + "coin.yml");
    }

    protected Contract createCoin(String yamlFilePath) throws IOException {
        Contract c = Contract.fromDslFile(yamlFilePath);
        c.setOwnerKey(ownerKey2);
        return c;
    }

    static Main createMain(String name,boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2/"+name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};
        Main main = new Main(args);
        main.waitReady();
        return main;
    }
}