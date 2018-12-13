/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.Main;
import com.icodici.universa.node2.Quantiser;
import com.icodici.universa.node2.network.Client;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.*;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.LogPrinter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.icodici.universa.client.RegexMatcher.matches;
import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
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

    protected static Contract uContract = null;
    protected static Object uContractLock = new Object();

    @BeforeClass
    public static void prepareRoot() throws Exception {

        createLocalNetwork();

        ownerKey1 = TestKeys.privateKey(3);
        ownerKey2 = TestKeys.privateKey(1);
        ownerKey3 = TestKeys.privateKey(2);

//        new File(rootPath + "/simple_root_contract.unicon").delete();
        assertTrue (new File(rootPath + "/simple_root_contract.yml").exists());
        assertTrue (new File(rootPath + "/simple_root_contract_v2.yml").exists());

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

        callMain("-c", "-v", rootPath + "simple_root_contract_v2.yml", "--output", basePath + "contract1.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "--output", basePath + "contract2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "--output", basePath + "contract3.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "simple_root_contract_v2.yml", "--output", basePath + "contract_to_export.unicon",
                "-set", field, "-value", value);
        callMain("-c", rootPath + "ref_root_contract.yml", "--output", basePath + "ref_contract_to_export.unicon",
                "-set", field, "-value", value);

        Contract c1 = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c1.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey1 = c1.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        c1.setOwnerKeys(goodKey1);
        c1.seal();
        CLIMain.saveContract(c1, basePath + "contract_for_revoke1.unicon");

        Contract c2 = Contract.fromDslFile(rootPath + "another_root_contract_v2.yml");
        c2.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey2 = c2.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        c2.setOwnerKeys(goodKey2);
        c2.seal();
        CLIMain.saveContract(c2, basePath + "contract_for_revoke2.unicon");

        Contract c3 = Contract.fromDslFile(rootPath + "simple_root_contract_v2.yml");
        c3.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey3 = c3.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        c3.setOwnerKeys(goodKey3);
        c3.seal();
        CLIMain.saveContract(c3, basePath + "contract_for_revoke3.unicon");

        callMain("-e", basePath + "contract1.unicon", "--output", basePath + "contract_to_import.json");
        callMain("-e", basePath + "contract1.unicon", "--output", basePath + "contract_to_import.xml");
        callMain("-e", basePath + "contract1.unicon", "--output", basePath + "contract_to_import.XML");
        callMain("-e", basePath + "contract1.unicon", "--output", basePath + "contract_to_import.yaml");

        callMain("-i", basePath + "contract_to_import.json", "--output", basePath + "not_signed_contract.unicon");

        Path path = Paths.get(rootPath + "packedContract.unicon");
        byte[] data = Files.readAllBytes(path);

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(new PrivateKey(Do.read(PRIVATE_KEY_PATH)));
        Contract contract = createCoin100apiv3();
        contract.addSignerKey(keys.iterator().next());
        contract.seal();
        CLIMain.saveContract(contract, basePath + "packedContract.unicon");

        String uContract = getApprovedUContract();

        callMain("--register", basePath + "packedContract.unicon", "--u", uContract, "-k", rootPath + "keys/stepan_mamontov.private.unikey", "--wait", "5000");
        Contract packedContract = ContractsService.createSplit(contract, "1", FIELD_NAME, keys);
        packedContract.addSignerKey(keys.iterator().next());
        packedContract.seal();

        CLIMain.saveContract(packedContract, basePath + "packedContract.unicon", true, true);
//        try (FileOutputStream fs = new FileOutputStream(basePath + "packedContract.unicon")) {
//            fs.write(data);
//            fs.close();
//        }

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
        Files.deleteIfExists(Paths.get(path));
        CLIMain.saveContract(c, path, true, true);
//        try (FileOutputStream fs = new FileOutputStream(path)) {
//            fs.write(c.getPackedTransaction());
//            fs.close();
//        }
        callMain("--check", path, "-v");
        System.out.println(output);
    }

    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml", "-j", "--output", basePath + "simple_root_contract.unicon");
        System.out.println(output);
        assertTrue (new File(basePath + "simple_root_contract.unicon").exists());
    }

    @Test
    public void createContractWithUpdateField() throws Exception {

        ZonedDateTime zdt = ZonedDateTime.now().plusHours(1);
        String field = "definition.expires_at";
        String value = "definition.expires_at:\n" +
                "    seconds: " + zdt.toEpochSecond() + "\n" +
                "    __type: unixtime";
        callMain("-c", rootPath + "simple_root_contract.yml", "-v",
                "--output", basePath + "simple_root_contract3.unicon",
                "-set", field, "-value", value);
        System.out.println(output);
        assertTrue (new File(basePath + "simple_root_contract3.unicon").exists());
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
    }

    @Test
    public void createTwoNotSignedContracts() throws Exception {
        callMain("-c", "-v",
                rootPath + "simple_root_contract.yml", "--output", basePath + "simple_root_contract1.unicon",
                rootPath + "simple_root_contract.yml", "--output", basePath + "simple_root_contract2.unicon");
        System.out.println(output);
        assertTrue (new File(basePath + "simple_root_contract1.unicon").exists());
        assertTrue (new File(basePath + "simple_root_contract2.unicon").exists());
    }

    @Test
    public void createTwoSignedContracts() throws Exception {
        callMain("-c", "-v",
                rootPath + "simple_root_contract.yml", "--output", basePath + "simple_root_contract1.unicon",
                rootPath + "simple_root_contract.yml", "--output", basePath + "simple_root_contract2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (new File(basePath + "simple_root_contract1.unicon").exists());
        assertTrue (new File(basePath + "simple_root_contract2.unicon").exists());
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
                "-k", keyFileName, "--output", contractFileName
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
                "-k", keyFileName, "--output", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "--output", basePath + "another_root_contract_v2.unicon"
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
                "-k", keyFileName, "--output", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "--output", basePath + "another_root_contract_v2.unicon"
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
                "-k", keyFileName, "--output", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "--output", basePath + "another_root_contract_v2.unicon"
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
                "-k", keyFileName, "--output", basePath + "simple_root_contract_v2.unicon"
        );
        String contractFileName = basePath + "simple_root_contract_v2.unicon";
        assertTrue(new File(contractFileName).exists());
        assertEquals(0, errors.size());

        callMain("-c", rootPath + "another_root_contract_v2.yml",
                "-k", keyFileName, "--output", basePath + "another_root_contract_v2.unicon"
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
                "--output", basePath + "simple_root_contract_v2.unicon"
        );
        System.out.println(new File(basePath + "simple_root_contract_v2.unicon").getAbsolutePath());
        assertTrue (new File(basePath + "simple_root_contract_v2.unicon").exists());
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
        assertTrue (output.indexOf("test_files/_xer0yfe2nn1xthc.private.unikey") >= 0);
        assertTrue (output.indexOf("B24XkVNy3fSJUZBzLsnJo4f+ZqGwbNxHgBr198FIPgyy") >= 0);
//        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTest() throws Exception {
        callMain("-e", basePath + "contract_to_export.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

//    @Test
    public void exportUTest() throws Exception {
        callMain2("--network");
        Contract c1 = CLIMain.loadContract(rootPath + "test_access.unicon");
        System.out.println(c1.getId());
        callMain2("--probe", c1.getId().toBase64String());
//        System.out.println(Bytes.toHex(c1.getIssuer().getKeys().iterator().next().pack()));
//        Contract c2 = CLIMain.loadContract(rootPath + "test_access_rev1.unicon");
//        System.out.println(c2.getId());
//        callMain2("--probe", c2.getId().toBase64String());
//        Contract c3 = CLIMain.loadContract(rootPath + "test_access_2_rev1_rev2.unicon");
//        System.out.println(c3.getId());
//        callMain2("--probe", c3.getId().toBase64String());

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", rootPath + "realToken.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(rootPath + "realToken.unicon").exists());
        callMain2("-register", rootPath + "realToken.unicon",
                "-u", rootPath + "test_access.unicon",
                "-utest",
                "-k", rootPath + "at70.privateKey.unikey",
                "-wait", "1000");
        callMain("-e", rootPath + "test_access.unicon", "-pretty");
        System.out.println(output);

        Thread.sleep(10000);

        Contract c5 = CLIMain.loadContract(rootPath + "test_access.unicon");
        System.out.println(c5.getId());
        callMain2("--probe", c5.getId().toBase64String());
        Contract c6 = CLIMain.loadContract(rootPath + "realToken.unicon");
        System.out.println(c6.getId());
        callMain2("--probe", c6.getId().toBase64String());
    }

    @Test
    public void exportAsJSONTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "json");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsPrettyJSONTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "json", "-pretty");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsXMLTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "xml");
        System.out.println(output);
        assertTrue (output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsYamlTest() throws Exception {
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "yaml", "-set", "origin", "-value", HashId.createRandom().toBase64String());
        System.out.println(output);
        assertTrue (output.indexOf("export as yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWithNameTest() throws Exception {
        String name = "ExportedContract.json";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "--output", basePath + name);
        System.out.println(output);
        assertTrue (output.indexOf(name + " export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest1() throws Exception {
        callMain(
                "-e", "-as", "json", "-pretty",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest2() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon", "-as", "json", "-pretty",
                basePath + "contract2.unicon", "-as", "xml");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertTrue (output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest3() throws Exception {
        callMain(
                "-e", "-pretty", "-v",
                basePath + "contract1.unicon",
                basePath + "contract2.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest4() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon," + basePath + "contract2.unicon",
                "-pretty", "-v");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest5() throws Exception {
        callMain(
                "-e", "-as", "xml",
                basePath + "contract1.unicon," + basePath + "contract2.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("export as xml ok") >= 1);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTwoContractsTest6() throws Exception {
        callMain(
                "-e",
                basePath + "contract1.unicon", "--output", basePath + "test6.XML",
                basePath + "contract2.unicon", "--output", basePath + "test6.YML");
        System.out.println(output);
        assertTrue (output.indexOf("export as xml ok") >= 0);
        assertTrue (output.indexOf("export as yml ok") >= 0);
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
        assertTrue (output.indexOf(role + " export public keys ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportPublicKeysWrongRole() throws Exception {
        String role = "wrongRole";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-extract-key", role);
        System.out.println(output);
        assertTrue (output.indexOf(role + " export public keys ok") < 0);
        assertEquals(1, errors.size());
    }

    @Test
    public void exportFields1() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2);
        System.out.println(output);
        assertTrue (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFields2() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1 + "," + field2);
        System.out.println(output);
        assertTrue (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsXML() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "xml", "-get", field1, "-get", field2);
        System.out.println(output);
        assertTrue (output.indexOf("export fields as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsYaml() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-as", "yaml", "-get", field1, "-get", field2);
        System.out.println(output);
        assertTrue (output.indexOf("export fields as yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsJSON() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2, "-as", "json");
        System.out.println(output);
        assertTrue (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsPrettyJSON() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field1, "-get", field2, "-as", "json", "-pretty");
        System.out.println(output);
        assertTrue (output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportRefsTest() throws Exception {
        callMain("-e", basePath + "ref_contract_to_export.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportRefsAsJSONTest() throws Exception {
        callMain(
                "-e", basePath + "ref_contract_to_export.unicon", "-as", "json");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportRefsAsPrettyJSONTest() throws Exception {
        callMain(
                "-e", basePath + "ref_contract_to_export.unicon", "-as", "json", "-pretty");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportRefsAsXMLTest() throws Exception {
        callMain(
                "-e", basePath + "ref_contract_to_export.unicon", "-as", "xml");
        System.out.println(output);
        assertTrue (output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportRefsAsYamlTest() throws Exception {
        callMain(
                "-e", basePath + "ref_contract_to_export.unicon", "-as", "yaml");
        System.out.println(output);
        assertTrue (output.indexOf("export as yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void parsingAndExportRefsTest() throws Exception {

        // load contract with references
        Contract refContract = Contract.fromDslFile(rootPath + "ref_conditions_root_contract.yml");

        Binder conditions = refContract.getReferences().get("test_ref4").getConditions();
        List<Object> condList = conditions.getList(all_of.name(), null);

        condList.add("\"string\"!=this.state.data.string3");
        condList.add("\"==INFORMATION==\"==this.state.data.string2");
        condList.add("\"string\"==this.state.data.string4");

        refContract.getReferences().get("test_ref4").setConditions(conditions);

        // check parsing conditions
        conditions = refContract.getReferences().get("test_ref3").getConditions();
        condList = conditions.getList(all_of.name(), null);

        Binder parsed = ((Binder)condList.get(0));
        assertEquals(parsed.getString("leftOperand", ""), "this.definition.issuer");
        assertEquals(parsed.getString("rightOperand", ""), "this.state.issuer");
        assertEquals(parsed.getIntOrThrow("rightConversion"), 0);
        assertEquals(parsed.getIntOrThrow("typeOfLeftOperand"), 0);
        assertEquals(parsed.getIntOrThrow("leftConversion"), 0);
        assertEquals(parsed.getIntOrThrow("typeOfRightOperand"), 0);
        assertEquals(parsed.getIntOrThrow("operator"), 7);

        parsed = ((Binder)condList.get(9));
        assertEquals(parsed.getString("leftOperand", ""), "this.state.data.bigdecimal_val");
        assertEquals(parsed.getString("rightOperand", ""), "123980111893281903812098390128320192830219821321321321123910849732682917138291");
        assertEquals(parsed.getIntOrThrow("rightConversion"), 0);
        assertEquals(parsed.getIntOrThrow("typeOfLeftOperand"), 0);
        assertEquals(parsed.getIntOrThrow("leftConversion"), 1);
        assertEquals(parsed.getIntOrThrow("typeOfRightOperand"), 1);
        assertEquals(parsed.getIntOrThrow("operator"), 3);

        conditions = refContract.getReferences().get("test_ref4").getConditions();
        condList = conditions.getList(all_of.name(), null);

        parsed = ((Binder)condList.get(4));
        assertEquals(parsed.getString("leftOperand", ""), "==INFORMATION==");
        assertEquals(parsed.getString("rightOperand", ""), "this.state.data.string2");
        assertEquals(parsed.getIntOrThrow("rightConversion"), 0);
        assertEquals(parsed.getIntOrThrow("typeOfLeftOperand"), 1);
        assertEquals(parsed.getIntOrThrow("leftConversion"), 0);
        assertEquals(parsed.getIntOrThrow("typeOfRightOperand"), 0);
        assertEquals(parsed.getIntOrThrow("operator"), 7);

        refContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        refContract.seal();
        refContract.check();
        refContract.traceErrors();

        CLIMain.saveContract(refContract, basePath + "ref_conditions_root_contract.unicon");

        callMain(
                "-e", basePath + "ref_conditions_root_contract.unicon", "-as", "yaml");
        System.out.println(output);
        assertTrue (output.indexOf("export as yaml ok") >= 0);
        assertEquals(0, errors.size());

        // check assembly conditions
        File file = new File( basePath + "ref_conditions_root_contract.yaml");

        Scanner scanner = new Scanner(file);

        List<String> list=new ArrayList<>();
        while(scanner.hasNextLine()){
            list.add(scanner.nextLine());
        }

        assertTrue((list.contains("      - this.definition.issuer==this.state.issuer")));
        assertTrue((list.contains("      - this.owner defined")));
        assertTrue((list.contains("      - this.state.data.int_val>37")));
        assertTrue((list.contains("      - this.state.data.string_val==\"==DATA==\"")));
        assertTrue((list.contains("      - false!=this.state.data.boolean_val")));
        assertTrue((list.contains("      - this.state.data.long_val<=1540809613457836")));
        assertTrue((list.contains("      - -67029039209309103.09204932<=this.state.data.double_val")));
        assertTrue((list.contains("      - this.state.data.string_val!=\"jkdsjdksjakjl12901ndasds_ksdokaoss\"")));
        assertTrue((list.contains("      - 3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939==this.state.data.bigdecimal_val::number")));
        assertTrue((list.contains("      - this.state.data.bigdecimal_val::number>=\"123980111893281903812098390128320192830219821321321321123910849732682917138291\"")));

        assertTrue((list.contains("      - this.definition.issuer defined")));
        assertTrue((list.contains("      - inherits this.state.references.test_ref1")));
        assertTrue((list.contains("        - this.state.data.int_val>37")));
        assertTrue((list.contains("          - this.state.data.string_val==\"==DATA==\"")));
        assertTrue((list.contains("          - false!=this.state.data.boolean_val")));
        assertTrue((list.contains("            - this.state.data.long_val<=1540809613457836")));
        assertTrue((list.contains("            - -67029039209309103.09204932>this.state.data.double_val")));
        assertTrue((list.contains("              - jkdsjdksjakjl12901ndasds_ksdokaoss!=this.state.data.string_val")));
        assertTrue((list.contains("              - 3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939==this.state.data.bigdecimal_val::number")));
        assertTrue((list.contains("              - this.state.data.bigdecimal_val::number>=\"123980111893281903812098390128320192830219821321321321123910849732682917138291\"")));
        assertTrue((list.contains("        - this.state.data.long_val<=1540809613457836")));
        assertTrue((list.contains("        - -67029039209309103.09204932>this.state.data.double_val")));
        assertTrue((list.contains("          - jkdsjdksjakjl12901ndasds_ksdokaoss!=this.state.data.string_val")));
        assertTrue((list.contains("          - 3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939==this.state.data.bigdecimal_val::number")));
        assertTrue((list.contains("          - this.state.data.bigdecimal_val::number>=\"123980111893281903812098390128320192830219821321321321123910849732682917138291\"")));

        assertTrue((list.contains("      - this.definition.issuer==this.state.issuer")));
        assertTrue((list.contains("      - this.owner defined")));
        assertTrue((list.contains("      - this.state.data.int_val>37")));
        assertTrue((list.contains("      - this.state.data.string_val==\"==DATA==\"")));
        assertTrue((list.contains("      - false!=this.state.data.boolean_val")));
        assertTrue((list.contains("      - this.state.data.long_val<=1540809613457836")));
        assertTrue((list.contains("      - -67029039209309103.09204932<=this.state.data.double_val")));
        assertTrue((list.contains("      - this.state.data.string_val!=\"jkdsjdksjakjl12901ndasds_ksdokaoss\"")));
        assertTrue((list.contains("      - 3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939==this.state.data.bigdecimal_val::number")));
        assertTrue((list.contains("      - this.state.data.bigdecimal_val::number>=\"123980111893281903812098390128320192830219821321321321123910849732682917138291\"")));
        assertTrue((list.contains("      - this.state.references.test_ref2 is_a this.state.references.test_ref1")));

        assertTrue((list.contains("      - this.definition.created_at>this.state.data.time3")));
        assertTrue((list.contains("      - this.definition.expires_at<\"2908-04-18 23:58:00\"")));
        assertTrue((list.contains("      - this.state.data.time5<=this.definition.created_at")));
        assertTrue((list.contains("      - '\"string\"!=this.state.data.string3'")));
        assertTrue((list.contains("      - '\"==INFORMATION==\"==this.state.data.string2'")));
        assertTrue((list.contains("      - '\"string\"==this.state.data.string4'")));
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
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
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
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
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
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
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
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateDataFields() throws Exception {
        String field1 = "definition.data.field1";
        String field2 = "definition.data.field2";
        String field3 = "definition.data.field3";
        String field4 = "state.data.field4";
        String field5 = "state.data.field5";
        String field6 = "state.data.field6";
        String value1 = "string_field1";
        String value2 = "[\"qwerty\", \"asdfgh\", \"zxcvbn\"]";
        String value3 = "{obj: data}";
        String value4 = "-10393029.2329082";
        String value5 = "45303893488122293029328493482478274382898910210012391093019102817384578732318029381202198312029318";
        String value6 = "\"45303893488122293029328493482478274382898910210012391093019102817384578732318029381202198312029318\"";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2,
                "-set", field3, "-value", value3,
                "-set", field4, "-value", value4,
                "-set", field5, "-value", value5,
                "-set", field6, "-value", value6);
        System.out.println(output);
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field3 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field4 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field5 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field6 + " ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateSpecialFields() throws Exception {
        String field1 = "definition.extended_type";
        String field2 = "state.created_at";
        String field3 = "state.expires_at";
        String field4 = "state.creator";
        String field5 = "state.revision";
        String field6 = "state.branchRevision";
        String field7 = "transactional.id";
        String field8 = "transactional.validUntil";
        String field9 = "state.parent";
        String field10 = "origin";
        String value1 = "UNS1";
        String value2 = "seconds: " + ZonedDateTime.now().toEpochSecond() + "\n__type: unixtime";
        String value3 = "{\"seconds\":" + ZonedDateTime.now().plusMonths(3).toEpochSecond() + ",\"__type\":\"unixtime\"}";
        String value4 = "      {\"keys\": [\n" +
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
                "      \"name\": \"creator\"\n" +
                "    }";
        String value5 = "1";
        String value6 = "8";
        String value7 = HashId.createRandom().toBase64String();
        String value8 = "" + ZonedDateTime.now().plusMonths(3).toEpochSecond();
        String value9 = "    composite3:\n" +
                "      __type: binary\n" +
                "      base64: |-\n" +
                "        eP4oXmKwyUDmvgFzjHkjRO+/3g4KUHsn8gpXRcPSG1yRbMjAc4lOKf+h9a+2pFTdp2WKUiMA\n" +
                "        fxVyjDWXOwFLPmSJHGAiDcRVv7xiAAuo4pXL3cc92qUjymG17qtVxUFo\n" +
                "    __type: HashId";
        String value10 = HashId.createRandom().toBase64String();     // HashId as String
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2,
                "-set", field3, "-value", value3,
                "-set", field4, "-value", value4,
                "-set", field5, "-value", value5,
                "-set", field6, "-value", value6,
                "-set", field7, "-value", value7,
                "-set", field8, "-value", value8,
                "-set", field9, "-value", value9,
                "-set", field10, "-value", value10);
        System.out.println(output);
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field3 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field4 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field5 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field6 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field7 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field8 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field9 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field10 + " ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateReferencesFields() throws Exception {
        String field1 = "definition.references.ref1";
        String field2 = "state.references.ref2";
        String field3 = "transactional.references.ref3";
        String field4 = "definition.references.ref4";
        String field5 = "state.references.ref5";
        String field6 = "transactional.references.ref6";
        String value1 = "    signed_by: []\n" +
                "    roles: []\n" +
                "    __type: Reference\n" +
                "    name: ref1\n" +
                "    where:\n" +
                "      all_of:\n" +
                "      - this.definition.issuer==this.state.issuer\n" +
                "    type: 2\n" +
                "    fields: []\n" +
                "    transactional_id: ''\n" +
                "    required: true";
        String value2 = "    signed_by: []\n" +
                "    roles: []\n" +
                "    __type: Reference\n" +
                "    name: ref2\n" +
                "    where:\n" +
                "      any_of:\n" +
                "      - this.definition.issuer defined\n" +
                "    type: 3\n" +
                "    fields: []\n" +
                "    transactional_id: ''\n" +
                "    required: true";
        String value3 = "    signed_by: []\n" +
                "    roles: []\n" +
                "    __type: Reference\n" +
                "    name: ref3\n" +
                "    where:\n" +
                "      any_of:\n" +
                "      - this.definition.issuer defined\n" +
                "    type: 1\n" +
                "    fields: []\n" +
                "    transactional_id: ''\n" +
                "    required: true";
        String value4 = "      <definition.references.ref4>\n" +
                "        <Reference>\n" +
                "          <signed__by isArray=\"true\"/>\n" +
                "          <roles isArray=\"true\"/>\n" +
                "          <name>ref4</name>\n" +
                "          <where>\n" +
                "            <all__of isArray=\"true\">\n" +
                "              <item>this.definition.issuer==this.state.issuer</item>\n" +
                "            </all__of>\n" +
                "          </where>\n" +
                "          <type>2</type>\n" +
                "          <fields isArray=\"true\"/>\n" +
                "          <transactional__id></transactional__id>\n" +
                "          <required>true</required>\n" +
                "        </Reference>\n" +
                "      </definition.references.ref4>";
        String value5 = "{\"signed_by\":[],\"roles\":[],\"__type\":\"Reference\",\"name\":\"ref5\",\"where\":{\"all_of\":[\"this.definition.issuer\\u003d\\u003dthis.state.issuer\",\"this.owner defined\",\"this.state.data.int_val\\u003e37\",\"this.state.data.string_val\\u003d\\u003d\\\"\\u003d\\u003dDATA\\u003d\\u003d\\\"\",\"false!\\u003dthis.state.data.boolean_val\",\"this.state.data.long_val\\u003c\\u003d1540809613457836\",\"-67029039209309103.09204932\\u003ethis.state.data.double_val\",\"jkdsjdksjakjl12901ndasds_ksdokaoss!\\u003dthis.state.data.string_val\",\"3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939\\u003d\\u003dthis.state.data.bigdecimal_val::number\",\"this.state.data.bigdecimal_val::number\\u003e\\u003d\\\"123980111893281903812098390128320192830219821321321321123910849732682917138291\\\"\"]},\"type\":3,\"fields\":[],\"transactional_id\":\"\",\"required\":true}";
        String value6 = "      {\n" +
                "        \"signed_by\": [],\n" +
                "        \"roles\": [],\n" +
                "        \"__type\": \"Reference\",\n" +
                "        \"name\": \"ref6\",\n" +
                "        \"where\": {\n" +
                "          \"any_of\": [\n" +
                "            \"this.definition.issuer defined\",\n" +
                "            \"inherits this.state.references.test_ref1\",\n" +
                "            {\n" +
                "              \"all_of\": [\n" +
                "                \"this.state.data.int_val\\u003e37\",\n" +
                "                {\n" +
                "                  \"any_of\": [\n" +
                "                    \"this.state.data.string_val\\u003d\\u003d\\\"\\u003d\\u003dDATA\\u003d\\u003d\\\"\",\n" +
                "                    \"false!\\u003dthis.state.data.boolean_val\",\n" +
                "                    {\n" +
                "                      \"all_of\": [\n" +
                "                        \"this.state.data.long_val\\u003c\\u003d1540809613457836\",\n" +
                "                        \"-67029039209309103.09204932\\u003ethis.state.data.double_val\",\n" +
                "                        {\n" +
                "                          \"any_of\": [\n" +
                "                            \"jkdsjdksjakjl12901ndasds_ksdokaoss!\\u003dthis.state.data.string_val\",\n" +
                "                            \"3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939\\u003d\\u003dthis.state.data.bigdecimal_val::number\",\n" +
                "                            \"this.state.data.bigdecimal_val::number\\u003e\\u003d\\\"123980111893281903812098390128320192830219821321321321123910849732682917138291\\\"\"\n" +
                "                          ]\n" +
                "                        }\n" +
                "                      ]\n" +
                "                    }\n" +
                "                  ]\n" +
                "                }\n" +
                "              ]\n" +
                "            },\n" +
                "            {\n" +
                "              \"all_of\": [\n" +
                "                \"this.state.data.long_val\\u003c\\u003d1540809613457836\",\n" +
                "                \"-67029039209309103.09204932\\u003ethis.state.data.double_val\",\n" +
                "                {\n" +
                "                  \"any_of\": [\n" +
                "                    \"jkdsjdksjakjl12901ndasds_ksdokaoss!\\u003dthis.state.data.string_val\",\n" +
                "                    \"3242905403309310398882034989390309091424678928328433888839898041300111129094320492094029007845298372939\\u003d\\u003dthis.state.data.bigdecimal_val::number\",\n" +
                "                    \"this.state.data.bigdecimal_val::number\\u003e\\u003d\\\"123980111893281903812098390128320192830219821321321321123910849732682917138291\\\"\"\n" +
                "                  ]\n" +
                "                }\n" +
                "              ]\n" +
                "            }\n" +
                "          ]\n" +
                "        },\n" +
                "        \"type\": 1,\n" +
                "        \"fields\": [],\n" +
                "        \"transactional_id\": \"\",\n" +
                "        \"required\": true\n" +
                "      }";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2,
                "-set", field3, "-value", value3,
                "-set", field4, "-value", value4,
                "-set", field5, "-value", value5,
                "-set", field6, "-value", value6);
        System.out.println(output);
        assertTrue (output.indexOf("update field " + field1 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field3 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field4 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field5 + " ok") >= 0);
        assertTrue (output.indexOf("update field " + field6 + " ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateWrongNameRoleFields() throws Exception {
        String field = "owner";
        String value = "      {\"keys\": [\n" +
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
                "    }";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field, "-value", value);
        System.out.println(output);
        assertTrue (output.indexOf("Field: " + field + " not equals role name in field value") >= 0);
    }

    @Test
    public void updateWrongNameReferencesFields() throws Exception {
        String field = "definition.references.ref1";
        String value = "    signed_by: []\n" +
                "    roles: []\n" +
                "    __type: Reference\n" +
                "    name: notref1\n" +
                "    where:\n" +
                "      all_of:\n" +
                "      - this.definition.issuer==this.state.issuer\n" +
                "    type: 2\n" +
                "    fields: []\n" +
                "    transactional_id: ''\n" +
                "    required: true";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field, "-value", value);
        System.out.println(output);
        assertTrue (output.indexOf("Field: " + field + " not equals reference name in field value") >= 0);
    }

    @Test
    public void updateWrongTypeReferencesFields() throws Exception {
        String field = "state.references.ref2";
        String value = "    signed_by: []\n" +
                "    roles: []\n" +
                "    __type: Reference\n" +
                "    name: ref2\n" +
                "    where:\n" +
                "      any_of:\n" +
                "      - this.definition.issuer defined\n" +
                "    type: 1\n" +
                "    fields: []\n" +
                "    transactional_id: ''\n" +
                "    required: true";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-pretty",
                "-set", field, "-value", value);
        System.out.println(output);
        assertTrue (output.indexOf("Field: " + field + " contains not state-type reference in field value") >= 0);
    }

    @Test
    public void exportWrongFields() throws Exception {
        String field = "definition.wrong";
        callMain(
                "-e", basePath + "contract_to_export.unicon", "-get", field, "-as", "json");
        System.out.println(output);
        assertTrue (output.indexOf("export fields as json ok") < 0);
        assertEquals(1, errors.size());
    }

    @Test
    public void importTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertEquals(2, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromJSONTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertEquals(2, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromXMLTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.XML");
        System.out.println(output);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(2, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }
    }

    @Test
    public void importFromYamlTest() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.yaml");
        System.out.println(output);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertEquals(2, errors.size());
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
        assertEquals(2, errors.size());
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("update field " + field2 + " ok") >= 0);
        assertTrue (output.indexOf("contract expires at " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(zdt)) >= 0);
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
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(6, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(4).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest2() throws Exception {
        callMain(
                "-i", "--output", basePath + "contract_json.unicon", basePath + "contract_to_import.json",
                "--output", basePath + "contract_xml.unicon", basePath + "contract_to_import.xml",
                "--output", basePath + "contract_yaml.unicon", basePath + "contract_to_import.yaml");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(6, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(4).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest3() throws Exception {
        callMain(
                "-i", "-v", basePath + "contract_to_import.json",
                basePath + "contract_to_import.xml",
                basePath + "contract_to_import.yaml");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(6, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(4).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest4() throws Exception {
        callMain(
                "-i", basePath + "contract_to_import.json," +
                        basePath + "contract_to_import.xml," +
                        basePath + "contract_to_import.yaml");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(6, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(4).get("code"));
        }
    }

    @Test
    public void importTwoContractsTest5() throws Exception {
        callMain(
                "-i", "-v", basePath + "contract_to_import.json," +
                        basePath + "contract_to_import.xml," +
                        basePath + "contract_to_import.yaml");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertTrue (output.indexOf("import from yaml ok") >= 0);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(6, errors.size());
        if(errors.size() > 2) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(2).get("code"));
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(4).get("code"));
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
                "-i", basePath + "contract_to_import.xml", "--output", basePath + name);
        System.out.println(output);
        assertTrue (output.indexOf("import from xml ok") >= 0);
        assertEquals(2, errors.size());
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

        List<String> coinValues = Arrays.asList("5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60", "0.3");
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
            CLIMain.saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getStringOrThrow(FIELD_NAME) + ".unicon");
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

        Decimal total = new Decimal(0);
        for (String i : coinValues) {
            total = total.add(new Decimal(i));
        }
        System.out.println(output);
        assertTrue (output.indexOf(total + " (TUNC)") >= 0);
    }

    @Test
    public void findMintContractsInPath() throws Exception {

        // Create contract files (coins and some non-coins)
        File dirFile = new File(rootPath + "contract_subfolder/");
        if (!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if (!dirFile.exists()) dirFile.mkdir();

        List<String> mintContractsHexes = new ArrayList<>(); //pre-downloaded from web
        mintContractsHexes.add("27 33 5F 5F 74 79 70 65 7B 54 72 61 6E 73 61 63 74 69 6F 6E 50 61 63 6B 43 63 6F 6E 74 72 61 63 74 C4 70 05 27 3B 76 65 72 73 69 6F 6E 18 23 74 79 70 65 53 75 6E 69 63 61 70 73 75 6C 65 53 73 69 67 6E 61 74 75 72 65 73 0E C4 A8 02 17 23 65 78 74 73 C4 97 01 27 1B 6B 65 79 BC 21 07 66 EF 93 DB A7 78 84 0B 7C CF 64 DB 9E 07 24 4F F3 6E 48 C7 80 01 EE 46 E4 52 40 A7 3B 90 A8 61 33 73 68 61 35 31 32 BC 40 4D B2 60 99 0D C1 71 E2 1A 2C EF 0D E1 2E 2F 8D AB 2B 89 2B BC 86 13 6C 3F EE 62 CF F5 72 A8 5C 9B 06 A4 54 66 D9 E9 2F 43 66 B0 7E 99 A2 1B 7B 62 40 44 F0 B6 54 B8 39 8E C4 37 68 D9 F9 00 FB 53 63 72 65 61 74 65 64 5F 61 74 79 38 22 70 58 85 3B 70 75 62 5F 6B 65 79 C4 0A 01 1E 08 1C 01 00 01 C4 01 01 00 AB 3A 2E D8 2A 22 AF E2 30 89 85 73 14 37 BC 7B D7 6D 04 2F 44 35 EF DF 98 0B 79 0C EA 88 D1 6A F0 CD 9A 79 C6 C8 70 9A DC 71 21 E9 36 62 9B 6F DB 75 6B 02 25 E5 37 93 B7 65 0A DD 3D 13 AE 36 D4 0A 72 4D 2B 7E 58 37 BB C4 5C 92 BB FA D5 42 85 46 B9 73 B2 C3 DE CE 89 AA BA FC D7 DB CD 48 AC 98 D3 13 AC 38 16 62 D1 97 08 62 CF E1 1A F0 1B BC 2E 95 BE DF 83 07 AF BA E3 60 4D 7B 67 E2 9D 35 9A 5A D6 7C 66 8F 47 C1 3E 91 2F 57 39 72 CF 0F 1D CE 51 73 58 07 26 07 5F 91 8A 3A F7 45 35 F3 A8 15 89 6C 7F FC 44 33 81 2A 6B 51 AC 4E 2C 44 70 9E FE 39 90 A7 DA 5A 15 10 CC 67 6B 50 F1 B4 3E EB A2 BC 7C B5 24 D6 7E 9E A1 E2 AC 1C 99 C8 17 63 54 4A BE C6 63 D2 BA DF E0 FC 2A 52 16 30 92 9E 5C 7D B1 3D 76 A1 EF E7 6E 5A A0 73 B9 93 71 1D 0D E4 6E AA 87 5C 64 E8 76 40 4B BD 23 73 69 67 6E C4 00 01 02 3C 70 A1 49 5E 6B A4 BF BF DB 77 82 D3 3C 4C C9 9C 25 E5 F3 CC 7A AD 48 A7 7E 05 6B 32 C0 50 E7 61 43 C1 47 3B 9F 51 92 85 06 75 4E C8 9E 2E B9 33 13 5F A0 6F 6C 70 86 F7 10 CF 6E D5 A6 9E 0B 0D 12 6E F5 09 2D 62 FC 7C B0 54 6F A9 CA 24 8B AB 15 B7 CC 5E 44 95 9A CA 32 FE 57 97 77 AC 0B 15 17 66 0B 3C 4B A3 F3 E3 5E 8D C3 14 F1 E0 AC EB D1 E1 65 2F B4 0C 11 49 9A BD 2E AD 6F AA ED DB EB 54 B6 1F 54 67 02 95 E3 C6 41 2F 03 6B 0F B2 39 DB B9 FE B8 03 13 21 E9 6A F3 9E 35 04 2A 67 6B C5 DD 3A 08 52 BB 15 08 FC 34 E5 05 C2 AB E6 B0 29 A2 22 8D 83 2C 32 A2 6D 39 23 75 EC C3 D3 3F 93 8D 2B 5D 0C BC D2 0F 62 2A EF C2 C5 FB E6 C9 29 8E 72 0A BD DB AE 97 86 5E 08 0F 99 14 B9 7E 05 86 C7 0E 4D F8 11 9A BB 7B 8F 7E 29 0B B3 BF 09 11 FC B2 2A 27 C2 DA 89 FB DA A0 BD 23 64 61 74 61 C4 97 02 1F 43 72 65 76 6F 6B 69 6E 67 06 43 63 6F 6E 74 72 61 63 74 27 33 5F 5F 74 79 70 65 83 55 6E 69 76 65 72 73 61 43 6F 6E 74 72 61 63 74 4B 61 70 69 5F 6C 65 76 65 6C 18 53 64 65 66 69 6E 69 74 69 6F 6E 2F 53 63 72 65 61 74 65 64 5F 61 74 79 22 06 70 58 85 23 64 61 74 61 1F 23 6E 61 6D 65 23 6D 65 77 31 43 63 75 72 72 65 6E 63 79 7D 73 73 68 6F 72 74 5F 63 75 72 72 65 6E 63 79 7D 33 69 73 73 75 65 72 1F 35 53 53 69 6D 70 6C 65 52 6F 6C 65 75 95 4B 61 64 64 72 65 73 73 65 73 0E 17 35 53 4B 65 79 41 64 64 72 65 73 73 43 75 61 64 64 72 65 73 73 BC 25 10 AE 02 80 D9 08 99 1A E5 E4 78 6A 71 D7 48 8F 65 4F 97 8C 89 78 23 D2 D0 9F 9C FE C2 11 F4 2E A6 CB A0 42 33 5B 70 65 72 6D 69 73 73 69 6F 6E 73 1F 33 64 6C 75 79 78 69 1F 35 83 52 65 76 6F 6B 65 50 65 72 6D 69 73 73 69 6F 6E 23 72 6F 6C 65 1F 35 43 52 6F 6C 65 4C 69 6E 6B 75 3B 40 72 65 76 6F 6B 65 5B 74 61 72 67 65 74 5F 6E 61 6D 65 2B 6F 77 6E 65 72 75 33 72 65 76 6F 6B 65 33 76 36 57 57 43 33 3F 53 66 69 65 6C 64 5F 6E 61 6D 65 33 61 6D 6F 75 6E 74 4B 6D 69 6E 5F 76 61 6C 75 65 08 43 6D 69 6E 5F 75 6E 69 74 08 8B 6A 6F 69 6E 5F 6D 61 74 63 68 5F 66 69 65 6C 64 73 16 BB 18 64 65 66 69 6E 69 74 69 6F 6E 2E 64 61 74 61 2E 63 75 72 72 65 6E 63 79 8B 64 65 66 69 6E 69 74 69 6F 6E 2E 69 73 73 75 65 72 35 9B 53 70 6C 69 74 4A 6F 69 6E 50 65 72 6D 69 73 73 69 6F 6E BD 20 1F 35 BD 22 75 5B 40 73 70 6C 69 74 5F 6A 6F 69 6E BD 24 BD 25 75 53 73 70 6C 69 74 5F 6A 6F 69 6E 33 49 73 61 49 36 64 1F 35 AB 43 68 61 6E 67 65 4F 77 6E 65 72 50 65 72 6D 69 73 73 69 6F 6E BD 20 1F 35 BD 22 75 6B 40 63 68 61 6E 67 65 5F 6F 77 6E 65 72 BD 24 BD 25 75 63 63 68 61 6E 67 65 5F 6F 77 6E 65 72 2B 72 6F 6C 65 73 1D 2B 73 74 61 74 65 4F 5D 79 22 06 70 58 85 53 65 78 70 69 72 65 73 5F 61 74 79 12 7E 54 5C 85 53 63 72 65 61 74 65 64 5F 62 79 1F 35 A5 75 3B 63 72 65 61 74 6F 72 AD B5 65 0F BD 2A C0 E8 03 BD 25 1F 35 BD 22 75 BD 25 BD 24 95 43 72 65 76 69 73 69 6F 6E 08 BD 3B 1D 33 70 61 72 65 6E 74 05 33 6F 72 69 67 69 6E 05 1B 6E 65 77 1D 43 73 75 62 49 74 65 6D 73 06 23 6B 65 79 73 0E C4 0A 01 1E 08 1C 01 00 01 C4 01 01 00 AB 3A 2E D8 2A 22 AF E2 30 89 85 73 14 37 BC 7B D7 6D 04 2F 44 35 EF DF 98 0B 79 0C EA 88 D1 6A F0 CD 9A 79 C6 C8 70 9A DC 71 21 E9 36 62 9B 6F DB 75 6B 02 25 E5 37 93 B7 65 0A DD 3D 13 AE 36 D4 0A 72 4D 2B 7E 58 37 BB C4 5C 92 BB FA D5 42 85 46 B9 73 B2 C3 DE CE 89 AA BA FC D7 DB CD 48 AC 98 D3 13 AC 38 16 62 D1 97 08 62 CF E1 1A F0 1B BC 2E 95 BE DF 83 07 AF BA E3 60 4D 7B 67 E2 9D 35 9A 5A D6 7C 66 8F 47 C1 3E 91 2F 57 39 72 CF 0F 1D CE 51 73 58 07 26 07 5F 91 8A 3A F7 45 35 F3 A8 15 89 6C 7F FC 44 33 81 2A 6B 51 AC 4E 2C 44 70 9E FE 39 90 A7 DA 5A 15 10 CC 67 6B 50 F1 B4 3E EB A2 BC 7C B5 24 D6 7E 9E A1 E2 AC 1C 99 C8 17 63 54 4A BE C6 63 D2 BA DF E0 FC 2A 52 16 30 92 9E 5C 7D B1 3D 76 A1 EF E7 6E 5A A0 73 B9 93 71 1D 0D E4 6E AA 87 5C 64 E8 76 40 4B BD");
        mintContractsHexes.add("27 33 5F 5F 74 79 70 65 7B 54 72 61 6E 73 61 63 74 69 6F 6E 50 61 63 6B 43 63 6F 6E 74 72 61 63 74 C4 6E 06 27 3B 76 65 72 73 69 6F 6E 18 23 74 79 70 65 53 75 6E 69 63 61 70 73 75 6C 65 53 73 69 67 6E 61 74 75 72 65 73 0E C4 A8 02 17 23 65 78 74 73 C4 97 01 27 1B 6B 65 79 BC 21 07 66 EF 93 DB A7 78 84 0B 7C CF 64 DB 9E 07 24 4F F3 6E 48 C7 80 01 EE 46 E4 52 40 A7 3B 90 A8 61 33 73 68 61 35 31 32 BC 40 5D 24 63 80 51 96 37 55 B4 21 88 5A F3 D7 CA FA D0 4A 30 F0 23 0D 39 90 7B 2F 1D 30 42 DC F3 15 99 6F F1 4F 6B 7C EE D4 F2 B2 AD F8 12 52 69 91 C4 9E F3 B4 10 CA 0C 96 AA 16 EB 31 11 42 84 16 53 63 72 65 61 74 65 64 5F 61 74 79 0C 24 70 58 85 3B 70 75 62 5F 6B 65 79 C4 0A 01 1E 08 1C 01 00 01 C4 01 01 00 AB 3A 2E D8 2A 22 AF E2 30 89 85 73 14 37 BC 7B D7 6D 04 2F 44 35 EF DF 98 0B 79 0C EA 88 D1 6A F0 CD 9A 79 C6 C8 70 9A DC 71 21 E9 36 62 9B 6F DB 75 6B 02 25 E5 37 93 B7 65 0A DD 3D 13 AE 36 D4 0A 72 4D 2B 7E 58 37 BB C4 5C 92 BB FA D5 42 85 46 B9 73 B2 C3 DE CE 89 AA BA FC D7 DB CD 48 AC 98 D3 13 AC 38 16 62 D1 97 08 62 CF E1 1A F0 1B BC 2E 95 BE DF 83 07 AF BA E3 60 4D 7B 67 E2 9D 35 9A 5A D6 7C 66 8F 47 C1 3E 91 2F 57 39 72 CF 0F 1D CE 51 73 58 07 26 07 5F 91 8A 3A F7 45 35 F3 A8 15 89 6C 7F FC 44 33 81 2A 6B 51 AC 4E 2C 44 70 9E FE 39 90 A7 DA 5A 15 10 CC 67 6B 50 F1 B4 3E EB A2 BC 7C B5 24 D6 7E 9E A1 E2 AC 1C 99 C8 17 63 54 4A BE C6 63 D2 BA DF E0 FC 2A 52 16 30 92 9E 5C 7D B1 3D 76 A1 EF E7 6E 5A A0 73 B9 93 71 1D 0D E4 6E AA 87 5C 64 E8 76 40 4B BD 23 73 69 67 6E C4 00 01 8B 59 27 EB 2C 32 37 A3 F6 D4 B3 09 B2 B3 CE 32 14 FA 55 C6 95 02 0F 78 1D 18 5C AE BA DE FD BF B2 E1 A3 A3 F0 BE 0B F3 7F CC DD EA 8A 1E 47 61 FD 43 F5 39 13 EB 69 76 DC AE 73 9D B7 08 EE 2C 2A C6 16 EF 77 CE 59 28 EA 37 E9 9E FF C8 74 59 48 79 E1 21 07 1F D8 88 F0 AE D5 BE B3 61 25 04 BD 65 6A 31 A4 12 93 EB FE 42 FE 60 5F 36 3C 1B 5E 5C B3 43 08 75 4D 94 5F C3 F1 35 C8 E2 C8 54 BE 67 42 9C 21 E9 F9 19 D3 0C 2D 42 F7 C2 E1 48 B9 21 1F DF D2 1C 06 3C C1 C4 5B 15 E6 42 68 A0 37 7F 15 96 10 63 48 EC D1 DC 9E 79 EB BE 90 5C 9C BC C8 49 35 EF C7 11 53 0E A7 51 E2 3F 9B F1 48 47 D4 19 27 C0 FF E9 AD C9 68 07 73 52 DA 74 A2 DC 53 D7 02 81 26 F6 BE 1C 0C 9A A4 F3 C2 08 42 B0 3C 66 24 9E 81 1D F4 31 7E 9D 22 FB 69 5F 24 C9 DC 30 D0 B3 AE 29 E7 D4 41 89 46 CD 47 99 23 64 61 74 61 C4 95 03 1F 43 72 65 76 6F 6B 69 6E 67 0E 17 33 5F 5F 74 79 70 65 33 48 61 73 68 49 64 53 63 6F 6D 70 6F 73 69 74 65 33 BC 60 9E A0 99 F2 14 85 9B 79 53 88 17 55 78 B8 9B 27 AB 0B E7 85 25 E8 EE 79 8B 7A 6C 34 D0 6F 68 53 E5 3F 6B 19 F8 49 FA 2F 96 4E D2 B6 B9 3D CB 57 27 7C AF 95 27 8F 33 E5 85 8D 74 F6 41 00 65 AA 66 B5 F9 CD 5F 81 6F 42 44 8B CB 59 BA ED C9 6B 38 C1 6B F2 66 86 93 5B CA 9C 63 45 B8 63 7D 48 43 63 6F 6E 74 72 61 63 74 2F 2D 83 55 6E 69 76 65 72 73 61 43 6F 6E 74 72 61 63 74 4B 61 70 69 5F 6C 65 76 65 6C 18 53 64 65 66 69 6E 69 74 69 6F 6E 2F 53 63 72 65 61 74 65 64 5F 61 74 79 62 06 70 58 85 23 64 61 74 61 1F 23 6E 61 6D 65 23 6D 65 77 31 43 63 75 72 72 65 6E 63 79 9D 73 73 68 6F 72 74 5F 63 75 72 72 65 6E 63 79 9D 33 69 73 73 75 65 72 1F 2D 53 53 69 6D 70 6C 65 52 6F 6C 65 95 B5 4B 61 64 64 72 65 73 73 65 73 0E 17 2D 53 4B 65 79 41 64 64 72 65 73 73 43 75 61 64 64 72 65 73 73 BC 25 10 AE 02 80 D9 08 99 1A E5 E4 78 6A 71 D7 48 8F 65 4F 97 8C 89 78 23 D2 D0 9F 9C FE C2 11 F4 2E A6 CB A0 42 33 5B 70 65 72 6D 69 73 73 69 6F 6E 73 1F 33 6B 55 6F 45 4F 44 1F 2D 83 52 65 76 6F 6B 65 50 65 72 6D 69 73 73 69 6F 6E 23 72 6F 6C 65 1F 2D 43 52 6F 6C 65 4C 69 6E 6B 95 3B 40 72 65 76 6F 6B 65 5B 74 61 72 67 65 74 5F 6E 61 6D 65 2B 6F 77 6E 65 72 95 33 72 65 76 6F 6B 65 33 35 38 75 50 4B 77 3F 53 66 69 65 6C 64 5F 6E 61 6D 65 33 61 6D 6F 75 6E 74 4B 6D 69 6E 5F 76 61 6C 75 65 08 43 6D 69 6E 5F 75 6E 69 74 08 8B 6A 6F 69 6E 5F 6D 61 74 63 68 5F 66 69 65 6C 64 73 16 BB 18 64 65 66 69 6E 69 74 69 6F 6E 2E 64 61 74 61 2E 63 75 72 72 65 6E 63 79 8B 64 65 66 69 6E 69 74 69 6F 6E 2E 69 73 73 75 65 72 2D 9B 53 70 6C 69 74 4A 6F 69 6E 50 65 72 6D 69 73 73 69 6F 6E BD 24 1F 2D BD 26 95 5B 40 73 70 6C 69 74 5F 6A 6F 69 6E BD 28 BD 29 95 53 73 70 6C 69 74 5F 6A 6F 69 6E 33 4F 68 48 52 57 57 1F 2D AB 43 68 61 6E 67 65 4F 77 6E 65 72 50 65 72 6D 69 73 73 69 6F 6E BD 24 1F 2D BD 26 95 6B 40 63 68 61 6E 67 65 5F 6F 77 6E 65 72 BD 28 BD 29 95 63 63 68 61 6E 67 65 5F 6F 77 6E 65 72 2B 72 6F 6C 65 73 06 2B 73 74 61 74 65 57 7D 79 0C 24 70 58 85 53 65 78 70 69 72 65 73 5F 61 74 79 52 7E 54 5C 85 53 63 72 65 61 74 65 64 5F 62 79 1F 2D BD 18 95 3B 63 72 65 61 74 6F 72 BD 19 BD 1A 85 0F BD 2E 1B 39 36 37 BD 29 1F 2D BD 26 95 BD 29 BD 28 B5 43 72 65 76 69 73 69 6F 6E 10 BD 3F BD 40 33 70 61 72 65 6E 74 25 33 6F 72 69 67 69 6E 25 4B 62 72 61 6E 63 68 5F 69 64 1B 31 3A 30 6B 74 72 61 6E 73 61 63 74 69 6F 6E 61 6C 05 1B 6E 65 77 0E 17 2D 35 3D BC 60 09 1B EB A6 43 D9 46 06 57 6A 54 81 D2 67 B0 03 7B 8B 2F FF E2 2E BD BD A5 CE E3 0D 69 CF E2 DE 30 0D A8 5B 26 77 28 14 92 B1 1B ED 1D 31 D8 AA 39 AA 46 58 DA 2F E5 74 E6 62 0E B4 60 D7 4A 0F D5 C7 F5 E5 B8 37 CD 30 C8 47 F1 D1 6D 99 45 F4 86 59 35 3E 43 3A 3C 2B AA B6 66 93 26 93 F3 58 43 73 75 62 49 74 65 6D 73 06 23 6B 65 79 73 0E C4 0A 01 1E 08 1C 01 00 01 C4 01 01 00 AB 3A 2E D8 2A 22 AF E2 30 89 85 73 14 37 BC 7B D7 6D 04 2F 44 35 EF DF 98 0B 79 0C EA 88 D1 6A F0 CD 9A 79 C6 C8 70 9A DC 71 21 E9 36 62 9B 6F DB 75 6B 02 25 E5 37 93 B7 65 0A DD 3D 13 AE 36 D4 0A 72 4D 2B 7E 58 37 BB C4 5C 92 BB FA D5 42 85 46 B9 73 B2 C3 DE CE 89 AA BA FC D7 DB CD 48 AC 98 D3 13 AC 38 16 62 D1 97 08 62 CF E1 1A F0 1B BC 2E 95 BE DF 83 07 AF BA E3 60 4D 7B 67 E2 9D 35 9A 5A D6 7C 66 8F 47 C1 3E 91 2F 57 39 72 CF 0F 1D CE 51 73 58 07 26 07 5F 91 8A 3A F7 45 35 F3 A8 15 89 6C 7F FC 44 33 81 2A 6B 51 AC 4E 2C 44 70 9E FE 39 90 A7 DA 5A 15 10 CC 67 6B 50 F1 B4 3E EB A2 BC 7C B5 24 D6 7E 9E A1 E2 AC 1C 99 C8 17 63 54 4A BE C6 63 D2 BA DF E0 FC 2A 52 16 30 92 9E 5C 7D B1 3D 76 A1 EF E7 6E 5A A0 73 B9 93 71 1D 0D E4 6E AA 87 5C 64 E8 76 40 4B BD");
        for (String hex : mintContractsHexes) {
            Contract coin = Contract.fromPackedTransaction(Bytes.fromHex(hex).getData());
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
            CLIMain.saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getStringOrThrow(FIELD_NAME) + ".unicon");
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

        Decimal total = new Decimal(1967);
        System.out.println(output);
        assertTrue (output.indexOf(total + " (mew1)") >= 0);
    }

    @Test
    public void findContractsInWrongPath() throws Exception {

        callMain("-f", rootPath + "not_exist_subfolder/", "-v", "-r");
        System.out.println(output);
        assertTrue (output.indexOf("No contracts found") >= 0);
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
        assertTrue (output.indexOf("downloading from www.universa.io") >= 0);
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

        assertTrue (output.indexOf("No contracts found") >= 0);
    }

    @Test
    public void checkContractInPathRecursively() throws Exception {

        // Create contract files (coins and some non-coins)

        File dirFile = new File(rootPath + "contract_subfolder/");
        if (!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if (!dirFile.exists()) dirFile.mkdir();

        List<String> coinValues = Arrays.asList("5", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60");
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
            CLIMain.saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getStringOrThrow(FIELD_NAME) + ".unicon");
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
        assertEquals(2, errors.size());
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
        c.addSignerKeyFromFile(rootPath + "keys/u_key.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        c.setOwnerKeys(goodKey);
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
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);



        PrivateKey issuer1 = TestKeys.privateKey(1   );
        Contract tc = ContractsService.createRevocation(c, issuer1, goodKey);

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
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 1);



        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);

        assertTrue (output.indexOf(ItemState.REVOKED.name()) >= 0);
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

        ClientNetwork clientNetwork1 = new ClientNetwork("http://localhost:8080", CLIMain.getPrivateKey(),null);
        ClientNetwork clientNetwork2 = new ClientNetwork("http://localhost:6002", CLIMain.getPrivateKey(), null);
        ClientNetwork clientNetwork3 = new ClientNetwork("http://localhost:6004", CLIMain.getPrivateKey(), null);


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

//        CLIMain.clearSession(false);
//
//        CLIMain.setNodeUrl(null);
//
//        System.out.println("---session should be created for remote network---");
//
//        CLIMain.registerContract(c);
//
//        CLIMain.saveSession();


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
        c.setOwnerKeys(goodKey);
        c.seal();
        String contractFileName = basePath + "with_role_for_revoke.unicon";
        CLIMain.saveContract(c, contractFileName);

        System.out.println("---");
        System.out.println("register contract");
        System.out.println("---");

        String uContract = getApprovedUContract();
//        CLIMain.registerContract(c);
        callMain2("--register", contractFileName, "--verbose",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);

        uContract = getApprovedUContract();
        callMain2("-revoke", contractFileName,
                "-k", PRIVATE_KEY_PATH, "-v",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract after revoke");
        System.out.println("---");

        callMain("--probe", c.getId().toBase64String());

        System.out.println(output);

        assertTrue (output.indexOf(ItemState.REVOKED.name()) >= 0);
    }

    @Test
    public void revokeContract() throws Exception {
        String contractFileName = basePath + "contract_for_revoke3.unicon";

        String uContract = getApprovedUContract();
        callMain2("--register", contractFileName, "--verbose",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");

        Contract c = CLIMain.loadContract(contractFileName);
        System.out.println("contract: " + c.getId().toBase64String());

        Thread.sleep(1500);
        System.out.println("probe before revoke");
        callMain2("--probe", c.getId().toBase64String(), "--verbose");
        Thread.sleep(500);

        uContract = getApprovedUContract();
        callMain2("-revoke", contractFileName,
                "-k", PRIVATE_KEY_PATH, "-v",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey");
        Thread.sleep(2500);
        System.out.println("probe after revoke");
        callMain("--probe", c.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assertTrue (output.indexOf(ItemState.REVOKED.name()) >= 0);
    }

    @Test
    public void revokeContractWithoutKey() throws Exception {
        String contractFileName = basePath + "contract_for_revoke1.unicon";


        String uContract = getApprovedUContract();
        callMain2("--register", contractFileName, "--verbose",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");
        callMain2("-revoke", contractFileName, "-v");

        Thread.sleep(1500);
        System.out.println("probe after revoke");
        Contract c = CLIMain.loadContract(contractFileName);
        callMain("--probe", c.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assertTrue (output.indexOf(ItemState.REVOKED.name()) < 0);
    }

    @Test
    public void revokeTwoContracts() throws Exception {
        String contractFileName1 = basePath + "contract_for_revoke1.unicon";
        String contractFileName2 = basePath + "contract_for_revoke2.unicon";

        System.out.println("---");
        System.out.println("register contracts");
        System.out.println("---");
        String uContract = getApprovedUContract();
        callMain2("--register", contractFileName1, contractFileName2, "--verbose",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");

        Thread.sleep(1500);


        System.out.println("---");
        System.out.println("check u");
        System.out.println("---");

        uContract = getApprovedUContract();

        Contract u = CLIMain.loadContract(uContract);
        System.out.println("check u " + u.getId().toBase64String());
        callMain2("--probe", u.getId().toBase64String(), "--verbose");

        System.out.println("---");
        System.out.println("revoke contracts");
        System.out.println("---");
        callMain2("-revoke", contractFileName1, contractFileName2, "-v",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-k", PRIVATE_KEY_PATH);


        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contracts after revoke");
        System.out.println("---");

        Contract c1 = CLIMain.loadContract(contractFileName1);
        callMain2("--probe", c1.getId().toBase64String(), "--verbose");

        Contract c2 = CLIMain.loadContract(contractFileName2);
        callMain("--probe", c2.getId().toBase64String(), "--verbose");

        Contract u2 = CLIMain.loadContract(uContract);
        System.out.println("check u " + u2.getId().toBase64String());
        callMain2("--probe", u2.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assertTrue (output.indexOf(ItemState.REVOKED.name()) >= 1);
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
                "--output", savingFileName,
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
//        System.out.println(" ");
//        callMain2("--check", basePath + "packedContract_new_item_1.unicon", "-v");
//        System.out.println(" ");
//        callMain("--check", basePath + "packedContract_revoke_1.unicon", "-v");

        System.out.println(output);
        assertEquals(0, errors.size());
    }


    @Test
    public void unpackParcel() throws Exception {
        String fileName = basePath + "packedContract.uniparcel";
        callMain2("--check", fileName, "-v");
        callMain2("-unpack", fileName, "-v");
//        System.out.println(" ");
//        callMain2("--check", basePath + "packedContract_new_item_1.unicon", "-v");
//        System.out.println(" ");
//        callMain("--check", basePath + "packedContract_revoke_1.unicon", "-v");

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

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = (int) Math.ceil((double)28 / Quantiser.quantaPerU);
        callMain("--cost", basePath + "contract_for_cost.unicon");
        System.out.println(output);

        assertTrue (output.indexOf("Contract processing cost is " + costShouldBe + " U") >= 0);
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

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = (int) Math.ceil((double)28 / Quantiser.quantaPerU);
        callMain("--cost",
                basePath + "contract_for_cost1.unicon",
                basePath + "contract_for_cost2.unicon");
        System.out.println(output);

        assertTrue (output.indexOf("Contract processing cost is " + costShouldBe + " U") >= 2);
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

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = (int) Math.ceil((double) 28 / Quantiser.quantaPerU);
        callMain("--register", basePath + "contract_for_register_and_cost.unicon",
                "--cost");
        System.out.println(output);

        assertTrue (output.indexOf("Contract processing cost is " + costShouldBe + " U") >= 0);
    }

    @Test
    public void registerContractWithDefaultPayment() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

        Files.deleteIfExists(Paths.get(basePath + "contract_for_register_and_cost.unicon"));
        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- get u ---");

        String uContract = getApprovedUContract();

        Contract u = CLIMain.loadContract(uContract);
        System.out.println("check u " + u.getId().toBase64String());
        callMain2("--probe", u.getId().toBase64String(), "--verbose");
        LogPrinter.showDebug(true);

        System.out.println("--- registering contract (with processing cost print) ---");

        callMain("--register", basePath + "contract_for_register_and_cost.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "5000");


        System.out.println(output);

        assertTrue (output.indexOf("paid contract " + contract.getId() +  " submitted with result: ItemResult<APPROVED") >= 0);

    }

    @Test
    public void registerContractWithPayment() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

        Files.deleteIfExists(Paths.get(basePath + "contract_for_register_and_cost.unicon"));
        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- get u ---");

        String uContract = getApprovedUContract();

        System.out.println("--- registering contract (with processing cost print) ---");
        LogPrinter.showDebug(true);

        callMain("--register", basePath + "contract_for_register_and_cost.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-amount", "2",
                "-wait", "5000");

        System.out.println(output);

        assertTrue (output.indexOf("registering the paid contract " + contract.getId()
                + " from " + basePath + "contract_for_register_and_cost.unicon"
                + " for 2 U") >= 0);
        assertTrue (output.indexOf("paid contract " + contract.getId() +  " submitted with result: ItemResult<APPROVED") >= 0);
    }

    @Test
    public void registerContractWithPaymentForStorage() throws Exception {

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

        Files.deleteIfExists(Paths.get(basePath + "contract_for_register_and_cost.unicon"));
        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- get u ---");

        String uContract = getApprovedUContract();

        System.out.println("--- registering contract with paying parcel (with processing cost print) ---");
        LogPrinter.showDebug(true);

        callMain("--register", basePath + "contract_for_register_and_cost.unicon",
                "-k-contract", PRIVATE_KEY_PATH,
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-amount", "2",
                "-amount-storage", "100",
                "-wait", "10000");

        System.out.println(output);

        assertTrue (output.indexOf("registering the paid contract " + contract.getId()
                + " from " + basePath + "contract_for_register_and_cost.unicon"
                + " for 2 U (and 100 U for storage)") >= 0);
        assertTrue (output.indexOf("submitted with result: ItemResult<APPROVED") >= 0);
    }

    @Test
    public void saveAndLoad() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        Contract stepaU = Contract.fromDslFile(rootPath + "StepaU.yml");
        stepaU.addSignerKey(manufacturePrivateKey);
        stepaU.seal();
        stepaU.check();
        //stepaU.setIsU(true);
        stepaU.traceErrors();
        Files.deleteIfExists(Paths.get(basePath + "save_and_load.unicon"));
        CLIMain.saveContract(stepaU, basePath + "save_and_load.unicon");
        callMain2("--register", basePath + "save_and_load.unicon", "--cost");

        System.out.println("--- save --- " + stepaU.getId());

        Contract loaded = CLIMain.loadContract(basePath + "save_and_load.unicon", true);

        System.out.println("--- load --- " + loaded.getId());

        assertTrue (loaded.getId().equals(stepaU.getId()));


        Contract paymentDecreased = loaded.createRevision(stepaPrivateKey);
        paymentDecreased.getStateData().set("transaction_units", 99);

        paymentDecreased.seal();
        Files.deleteIfExists(Paths.get(basePath + "save_and_load.unicon"));
        CLIMain.saveContract(paymentDecreased, basePath + "save_and_load.unicon");

        System.out.println("--- save 2 --- " + paymentDecreased.getId());

        callMain("--register", basePath + "save_and_load.unicon", "--cost");

        Contract loaded2 = CLIMain.loadContract(basePath + "save_and_load.unicon", true);

        System.out.println("--- load 2 --- " + loaded2.getId());

        assertTrue (loaded2.getId().equals(paymentDecreased.getId()));

    }

    protected static String getApprovedUContract() throws Exception {
        synchronized (uContractLock) {
            if (uContract == null) {
                PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey"));
                Contract stepaU = Contract.fromDslFile(rootPath + "StepaU.yml");
                stepaU.addSignerKey(manufacturePrivateKey);
                stepaU.seal();
                stepaU.check();
                //stepaU.setIsU(true);
                stepaU.traceErrors();
                Files.deleteIfExists(Paths.get(basePath + "stepaU.unicon"));
                CLIMain.saveContract(stepaU, basePath + "stepaU.unicon");

                System.out.println("--- register new u --- " + stepaU.getId());
                Client client = CLIMain.getClientNetwork().client;
                client.register(stepaU.getPackedTransaction(), 5000);
                //callMain2("--register", basePath + "stepaU.unicon", "-v", "-wait", "5000");
                uContract = stepaU;

            }
            return basePath + "stepaU.unicon";
        }
    }

    @Test
    public void registerContractAndPrintProcessingCostBreak() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

//        sealCheckTrace(contract, true);

        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- registering contract (with processing cost print) ---");

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = 28;
        Contract.setTestQuantaLimit(15);
        callMain("--register", basePath + "contract_for_register_and_cost.unicon", "--cost");
        System.out.println(output);

        assertTrue (output.indexOf("ERROR: QUANTIZER_COST_LIMIT") >= 0);
        Contract.setTestQuantaLimit(-1);
    }

    @Test
    public void registerContractAndPrintProcessingCostBreakWhileUnpacking() throws Exception {

        // Should register contracts and use -cost as key to print cost of processing it.

        Contract contract = createCoin();
        contract.getStateData().set(FIELD_NAME, new Decimal(100));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        contract.seal();

//        sealCheckTrace(contract, true);

        CLIMain.saveContract(contract, basePath + "contract_for_register_and_cost.unicon");

        System.out.println("--- registering contract (with processing cost print) ---");

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = 28;
        Contract.setTestQuantaLimit(1);
        callMain("--register", basePath + "contract_for_register_and_cost.unicon", "--cost");
        System.out.println(output);

        assertTrue (output.indexOf("ERROR: QUANTIZER_COST_LIMIT") >= 0);
        Contract.setTestQuantaLimit(-1);
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

        // Check 4096 bits signature (8) +
        // Register a version (20)
        int costShouldBe = (int) Math.ceil((double)28 / Quantiser.quantaPerU);
        callMain("--register",
                basePath + "contract_for_register_and_cost0.unicon",
                basePath + "contract_for_register_and_cost1.unicon",
                "--cost");
        System.out.println(output);

        assertTrue (output.indexOf("Contract processing cost is " + costShouldBe + " U") >= 1);
    }

    @Test
    public void createShortAddressTest() throws Exception {

        callMain("-address", rootPath + "_xer0yfe2nn1xthc.private.unikey", "-short");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/_xer0yfe2nn1xthc.private.unikey") >= 0);
        assertTrue (output.indexOf("Address: 26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R") >= 0);

        callMain("-address-match", "26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R",
                "-keyfile", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);
    }

    @Test
    public void createLongAddressTest() throws Exception {

        callMain("-address", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/_xer0yfe2nn1xthc.private.unikey") >= 0);
        assertTrue (output.indexOf("Address: bZmurQxHtG8S8RgZabTrvfa5Rsan7DZZGS4fjWrScb3jVmPtNP1oRiJBiJCAqchjyuH2ov3z") >= 0);

        callMain("-address-match", "bZmurQxHtG8S8RgZabTrvfa5Rsan7DZZGS4fjWrScb3jVmPtNP1oRiJBiJCAqchjyuH2ov3z",
                "-keyfile", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);
    }

    @Test
    public void createShortAddressFromPrivateAndPublicKeysTest() throws Exception {

        callMain("-address", rootPath + "keys/marty_mcfly.public.unikey", "-short");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/keys/marty_mcfly.public.unikey") >= 0);
        assertTrue (output.indexOf("Address: ZkQv1Dw3LniMLvT3jKqYVQEdLAJbsKs3eeDLM2UyQTJagTKKGc") >= 0);

        callMain("-address-match", "ZkQv1Dw3LniMLvT3jKqYVQEdLAJbsKs3eeDLM2UyQTJagTKKGc",
                "-keyfile", rootPath + "keys/marty_mcfly.public.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);

        callMain("-address", rootPath + "keys/marty_mcfly.private.unikey", "-short");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/keys/marty_mcfly.private.unikey") >= 0);
        assertTrue (output.indexOf("Address: ZkQv1Dw3LniMLvT3jKqYVQEdLAJbsKs3eeDLM2UyQTJagTKKGc") >= 0);

        callMain("-address-match", "ZkQv1Dw3LniMLvT3jKqYVQEdLAJbsKs3eeDLM2UyQTJagTKKGc",
                "-keyfile", rootPath + "keys/marty_mcfly.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);
    }

    @Test
    public void createLongAddressFromPrivateAndPublicKeysTest() throws Exception {

        callMain("-address", rootPath + "keys/marty_mcfly.public.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/keys/marty_mcfly.public.unikey") >= 0);
        assertTrue (output.indexOf("Address: J2jM85Jh6qC8L2TJkdznnE3xmGuTBE2nEngbs8yD75fQqFtzGMGExhYEw8A1LWU7aHTK3HeA") >= 0);

        callMain("-address-match", "J2jM85Jh6qC8L2TJkdznnE3xmGuTBE2nEngbs8yD75fQqFtzGMGExhYEw8A1LWU7aHTK3HeA",
                "-keyfile", rootPath + "keys/marty_mcfly.public.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);

        callMain("-address", rootPath + "keys/marty_mcfly.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("test_files/keys/marty_mcfly.private.unikey") >= 0);
        assertTrue (output.indexOf("Address: J2jM85Jh6qC8L2TJkdznnE3xmGuTBE2nEngbs8yD75fQqFtzGMGExhYEw8A1LWU7aHTK3HeA") >= 0);

        callMain("-address-match", "J2jM85Jh6qC8L2TJkdznnE3xmGuTBE2nEngbs8yD75fQqFtzGMGExhYEw8A1LWU7aHTK3HeA",
                "-keyfile", rootPath + "keys/marty_mcfly.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);
    }

    @Ignore
    @Test
    public void printWallets() throws Exception {

        System.out.println("\n\n");
        callMain("-f", "/home/flint/w/uniclient-test", "--verbose");
        System.out.println(output);
        System.out.println("\n\n");
    }

    @Test
    public void matchingAddressTestPositive() throws Exception {

        callMain("-address-match", "26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R",
                "-keyfile", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: true") >= 0);

        callMain("-folder-match", rootPath,
                "-addr", "26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R");
        System.out.println(output);
        assertTrue (output.indexOf("Filekey: _xer0yfe2nn1xthc.private.unikey") >= 0);
    }

    @Test
    public void matchingAddressTestNegative() throws Exception {

        callMain("-address-match", "27RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R",
                "-keyfile", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        System.out.println(output);
        assertTrue (output.indexOf("Matching result: false") >= 0);

        callMain("-folder-match", rootPath,"-addr", "27RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R");
        System.out.println(output);
        assertTrue (output.indexOf("Invalid address.") >= 0);
    }

    @Test
    public void testExportImportWithAddresses() throws Exception {

        callMain2("-create", rootPath + "simple_root_contract_v2.yml", "--output", basePath + "contractWithAddresses.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        Contract contract = CLIMain.loadContract(basePath + "contractWithAddresses.unicon", true);

        Set<KeyAddress> keyAddresses = new HashSet<>();
        keyAddresses.add(new KeyAddress(TestKeys.publicKey(0), 0, true));
        SimpleRole sr1 = new SimpleRole("owner", keyAddresses);

        contract.registerRole(sr1);
        contract.addSignerKey(TestKeys.privateKey(0));
        contract.seal();

        Files.deleteIfExists(Paths.get(basePath + "contractWithAddresses.unicon"));
        CLIMain.saveContract(contract, basePath + "contractWithAddresses.unicon");

        callMain("-e", basePath + "contractWithAddresses.unicon", "--output", basePath + "contractWithAddresses.json");
        System.out.println(output);
        assertTrue (output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());

        callMain("-i", basePath + "contractWithAddresses.json", "--output", basePath + "contractWithAddressesImported.unicon");
        System.out.println(output);
        assertTrue (output.indexOf("import from json ok") >= 0);
        assertEquals(2, errors.size());
        if(errors.size() > 0) {
            assertEquals(Errors.NOT_SIGNED.name(), errors.get(0).get("code"));
        }

        Contract contractImported = CLIMain.loadContract(basePath + "contractWithAddressesImported.unicon", true);

        assertTrue(contractImported.getOwner().getKeyAddresses().iterator().next().isMatchingKey(TestKeys.privateKey(0).getPublicKey()));

        PrivateKey creatorPrivateKey = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        contractImported.addSignatureToSeal(creatorPrivateKey);
        contractImported.addSignatureToSeal(TestKeys.privateKey(0));

        assertTrue(contractImported.check());

        Set<PrivateKey> signKeys= new HashSet<>();
        signKeys.add(creatorPrivateKey);
        signKeys.add(TestKeys.privateKey(0));
        contractImported.setKeysToSignWith(signKeys);
        byte[] sealedContract = contractImported.sealAsV2();
        TransactionPack tp = new TransactionPack();
        tp.addKeys(creatorPrivateKey.getPublicKey());
        tp.addKeys(TestKeys.privateKey(0).getPublicKey());
        Contract restoredContract = new Contract(sealedContract, tp);

        assertTrue(restoredContract.check());
    }

    @Test
    public void anonymizeRole() throws Exception {

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing.unicon").exists());
        callMain("-anonymize", basePath + "forRoleAnonymizing.unicon",
                "-role", "issuer");
        assertTrue (new File(basePath + "forRoleAnonymizing_anonymized.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract contract = CLIMain.loadContract(basePath + "forRoleAnonymizing_anonymized.unicon", true);

        assertFalse(contract.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract = new Contract(contract.getLastSealedBinary());

        assertFalse(anonPublishedContract.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract.getSealedByKeys().contains(key.getPublicKey()));
    }

    @Test
    public void anonymizeAllRoles() throws Exception {

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing.unicon").exists());
        callMain("-anonymize", basePath + "forRoleAnonymizing.unicon");
        assertTrue (new File(basePath + "forRoleAnonymizing_anonymized.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        Contract contract = CLIMain.loadContract(basePath + "forRoleAnonymizing_anonymized.unicon", true);

        assertFalse(contract.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(contract.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(contract.getCreator().getKeys().contains(key.getPublicKey()));
        Contract anonPublishedContract = new Contract(contract.getLastSealedBinary());

        assertFalse(anonPublishedContract.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(anonPublishedContract.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract.getCreator().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract.getSealedByKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract.getSealedByKeys().contains(ownerKey.getPublicKey()));
    }

    @Test
    public void anonymizeRoleAndSaveWithName() throws Exception {

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing.unicon").exists());
        callMain("-anonymize", basePath + "forRoleAnonymizing.unicon",
                "-role", "issuer",
                "--output", basePath + "myAnon.unicon");
        assertTrue (new File(basePath + "myAnon.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract contract = CLIMain.loadContract(basePath + "myAnon.unicon", true);

        assertFalse(contract.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract = new Contract(contract.getLastSealedBinary());

        assertFalse(anonPublishedContract.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract.getSealedByKeys().contains(key.getPublicKey()));
    }

    @Test
    public void anonymizeRoleForTwoContracts() throws Exception {

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing1.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing1.unicon").exists());
        assertTrue (new File(basePath + "forRoleAnonymizing2.unicon").exists());

        callMain("-anonymize", basePath + "forRoleAnonymizing1.unicon", basePath + "forRoleAnonymizing2.unicon",
                "-role", "issuer");
        assertTrue (new File(basePath + "forRoleAnonymizing1_anonymized.unicon").exists());
        assertTrue (new File(basePath + "forRoleAnonymizing2_anonymized.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract contract1 = CLIMain.loadContract(basePath + "forRoleAnonymizing1_anonymized.unicon", true);

        assertFalse(contract1.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract1 = new Contract(contract1.getLastSealedBinary());

        assertFalse(anonPublishedContract1.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract1.getSealedByKeys().contains(key.getPublicKey()));

        Contract contract2 = CLIMain.loadContract(basePath + "forRoleAnonymizing2_anonymized.unicon", true);

        assertFalse(contract2.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract2 = new Contract(contract2.getLastSealedBinary());

        assertFalse(anonPublishedContract2.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract2.getSealedByKeys().contains(key.getPublicKey()));
    }

    @Test
    public void anonymizeRoleForTwoContractsWithNames() throws Exception {

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing1.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing1.unicon").exists());
        assertTrue (new File(basePath + "forRoleAnonymizing2.unicon").exists());

        callMain("-anonymize", basePath + "forRoleAnonymizing1.unicon", basePath + "forRoleAnonymizing2.unicon",
                "-role", "issuer",
                "--output", basePath + "myAnon1.unicon", "--output", basePath + "myAnon2.unicon");
        assertTrue (new File(basePath + "myAnon1.unicon").exists());
        assertTrue (new File(basePath + "myAnon2.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        Contract contract1 = CLIMain.loadContract(basePath + "myAnon1.unicon", true);

        assertFalse(contract1.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract1 = new Contract(contract1.getLastSealedBinary());

        assertFalse(anonPublishedContract1.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract1.getSealedByKeys().contains(key.getPublicKey()));

        Contract contract2 = CLIMain.loadContract(basePath + "myAnon2.unicon", true);

        assertFalse(contract2.getIssuer().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract2 = new Contract(contract2.getLastSealedBinary());

        assertFalse(anonPublishedContract2.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract2.getSealedByKeys().contains(key.getPublicKey()));
    }

    @Test
    public void anonymizeAllRolesForTwoContracts() throws Exception {

        Files.deleteIfExists(Paths.get(basePath + "forRoleAnonymizing1.unicon"));
        Files.deleteIfExists(Paths.get(basePath + "forRoleAnonymizing2.unicon"));

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing1.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "forRoleAnonymizing2.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "forRoleAnonymizing1.unicon").exists());
        assertTrue (new File(basePath + "forRoleAnonymizing2.unicon").exists());

        Files.deleteIfExists(Paths.get(basePath + "forRoleAnonymizing1_anonymized.unicon"));
        Files.deleteIfExists(Paths.get(basePath + "forRoleAnonymizing2_anonymized.unicon"));

        callMain("-anonymize", basePath + "forRoleAnonymizing1.unicon", basePath + "forRoleAnonymizing2.unicon");

        assertTrue (new File(basePath + "forRoleAnonymizing1_anonymized.unicon").exists());
        assertTrue (new File(basePath + "forRoleAnonymizing2_anonymized.unicon").exists());
        System.out.println(output);

        PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey ownerKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        Contract contract1 = CLIMain.loadContract(basePath + "forRoleAnonymizing1_anonymized.unicon", true);

        assertFalse(contract1.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(contract1.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(contract1.getCreator().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract1 = new Contract(contract1.getLastSealedBinary());

        assertFalse(anonPublishedContract1.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(anonPublishedContract1.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract1.getCreator().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract1.getSealedByKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract1.getSealedByKeys().contains(ownerKey.getPublicKey()));

        Contract contract2 = CLIMain.loadContract(basePath + "forRoleAnonymizing1_anonymized.unicon", true);

        assertFalse(contract2.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(contract2.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(contract2.getCreator().getKeys().contains(key.getPublicKey()));

        Contract anonPublishedContract2 = new Contract(contract2.getLastSealedBinary());

        assertFalse(anonPublishedContract2.getOwner().getKeys().contains(ownerKey.getPublicKey()));
        assertFalse(anonPublishedContract2.getIssuer().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract2.getCreator().getKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract2.getSealedByKeys().contains(key.getPublicKey()));
        assertFalse(anonPublishedContract2.getSealedByKeys().contains(ownerKey.getPublicKey()));
    }

    //////////////// common realistic use cases

    @Test
    public void createAndRegisterTokenFromDSL() throws Exception {

        // You have a token dsl and want to release own tokens

        String uContract = getApprovedUContract();

        callMain2("-create", rootPath + "TokenDSLTemplate.yml", "--output", basePath + "realToken.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "realToken.unicon").exists());
        callMain("-register", basePath + "realToken.unicon",
                "-u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "2000");
        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);
    }

    @Test
    public void createAndRegisterNotaryFromDSL() throws Exception {

        // You have a notary dsl and want to release notarized document for someone

        String uContract = getApprovedUContract();

        callMain2("-create", rootPath + "NotaryDSLTemplate.yml", "--output", basePath + "realNotary.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "realNotary.unicon").exists());
        callMain("--register", basePath + "realNotary.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "--wait", "1000");
        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);
    }

    @Test
    public void createAndRegisterNotaryContractWithFileFromDSL() throws Exception {

        String uContract = getApprovedUContract();

        callMain2("-create", rootPath + "notaryContractWithFile.yaml", "--output",
                basePath + "realNotaryContractWithFile.unicon",
                "-k", rootPath + "keys/marty_mcfly.private.unikey");

        assertTrue (new File(basePath + "realNotaryContractWithFile.unicon").exists());

        callMain("--register", basePath + "realNotaryContractWithFile.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "--wait", "1000");
        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);
    }


    @Test
    public void createAndRegisterShareFromDSL() throws Exception {

        String uContract = getApprovedUContract();

        callMain2("-create", rootPath + "ShareDSLTemplate.yml", "--output", basePath + "realShare.unicon",
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey");
        assertTrue (new File(basePath + "realShare.unicon").exists());
        callMain("--register", basePath + "realShare.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "--wait", "3000");
        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);
    }

    //////////////////////////////////////


//    @Test
    public void showSwapResult() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        PrivateKey martyPrivateKey = new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey"));
        martyPrivateKeys.add(martyPrivateKey);
        martyPublicKeys.add(martyPrivateKey.getPublicKey());
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey"));
        stepaPrivateKeys.add(stepaPrivateKey);
        stepaPublicKeys.add(stepaPrivateKey.getPublicKey());
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(rootPath + "LamborghiniOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        delorean.traceErrors();
        CLIMain.saveContract(delorean, rootPath + "delorean.unicon");

        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        lamborghini.traceErrors();
        CLIMain.saveContract(lamborghini, rootPath + "lamborghini.unicon");


        callMain("--register",
                rootPath + "delorean.unicon",
                rootPath + "lamborghini.unicon",
                "-wait", "5000");

        Contract swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        Contract newDelorean = null;
        Contract newLamborghini = null;
        for (Contract c : swapContract.getNew()) {
            if(c.getParent().equals(delorean.getId())) {
                newDelorean = c;
            }
            if(c.getParent().equals(lamborghini.getId())) {
                newLamborghini = c;
            }
        }

        CLIMain.saveContract(swapContract, rootPath + "swapContract.unicon", true, true);
        CLIMain.saveContract(newDelorean, rootPath + "newDelorean.unicon");
        CLIMain.saveContract(newLamborghini, rootPath + "newLamborghini.unicon");

        callMain("--register",
                rootPath + "swapContract.unicon",
                "-wait", "5000");

        System.out.println("delorean: " + delorean.check());
        System.out.println("lamborghini: " + lamborghini.check());
        System.out.println("newDelorean: " + newDelorean.check());
        System.out.println("newLamborghini: " + newLamborghini.check());
        System.out.println("swapContract: " + swapContract.check());

        callMain("-e",
                rootPath + "delorean.unicon",
                rootPath + "lamborghini.unicon",
                rootPath + "newDelorean.unicon",
                rootPath + "newLamborghini.unicon",
                rootPath + "swapContract.unicon",
                "-pretty");
    }



//    @Test
    public void swapManyContractsViaTransactionAllGood() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean1 = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        Contract delorean2 = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        Contract delorean3 = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        List<Contract> deloreans = new ArrayList<>();
        deloreans.add(delorean1);
        deloreans.add(delorean2);
        deloreans.add(delorean3);
        Contract lamborghini1 = Contract.fromDslFile(rootPath + "LamborghiniOwnership.yml");
        Contract lamborghini2 = Contract.fromDslFile(rootPath + "LamborghiniOwnership.yml");
        List<Contract> lamborghinis = new ArrayList<>();
        lamborghinis.add(lamborghini1);
        lamborghinis.add(lamborghini2);

        // ----- prepare contracts -----------

        martyPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : martyPrivateKeys) {
            martyPublicKeys.add(pk.getPublicKey());
        }

        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        int i = 0;
        for(Contract d : deloreans) {
            i++;
            d.addSignerKey(manufacturePrivateKey);
            d.seal();
            CLIMain.saveContract(d, rootPath + "delorean" + i + ".unicon");
            callMain("--register",
                    rootPath + "delorean" + i + ".unicon",
                    "-wait", "5000");
        }

        i = 0;
        for(Contract l : lamborghinis) {
            i++;
            l.addSignerKey(manufacturePrivateKey);
            l.seal();
            CLIMain.saveContract(l, rootPath + "lamborghini" + i + ".unicon");
            callMain("--register",
                    rootPath + "lamborghini" + i + ".unicon",
                    "-wait", "5000");
        }

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(deloreans, lamborghinis, martyPrivateKeys, stepaPublicKeys);
        ContractsService.signPresentedSwap(swapContract, stepaPrivateKeys);
        ContractsService.finishSwap(swapContract, martyPrivateKeys);

        swapContract.check();
        swapContract.traceErrors();
        System.out.println("Transaction contract for swapping is valid: " + swapContract.isOk() + " num new contracts: " + swapContract.getNewItems().size());

        CLIMain.saveContract(swapContract, rootPath + "swapContract.unicon", true, true);
        callMain("--register",
                rootPath + "swapContract.unicon",
                "-wait", "5000");


        i = 0;
        for (Contract c : swapContract.getNew()) {
            i++;
            CLIMain.saveContract(c, rootPath + "new" + i + ".unicon");

            callMain("-e",
                    rootPath + "new" + i + ".unicon",
                    "-pretty");
        }

//        checkSwapResultSuccess(swapContract, delorean, lamborghini, martyPublicKeys, stepaPublicKeys);
    }

//    @Test
    public void failedTransaction() throws Exception {
        Contract c = CLIMain.loadContract(rootPath + "failed3.transaction", true);

        c.check();
        c.traceErrors();

        System.out.println("c " + " isok=" + c.isOk() + " new: " + c.getNew().size() + " rev: " + c.getRevoking().size() + " ref: " + c.getReferences().size() + " signs:" + c.getSealedByKeys().size() + " data:" + c.getStateData() + " id:" + c.getId());
        Contract cNew1 = c.getNew().get(0);
        Contract cNew2 = c.getNew().get(1);
        System.out.println("cNew1 new: " + cNew1.getNew().size() + " rev: " + cNew1.getRevoking().size() + " ref: " + cNew1.getReferences().size() + " signs:" + cNew1.getSealedByKeys().size() + " data:" + cNew1.getStateData() + " id:" + cNew1.getId());
        System.out.println("cNew2 new: " + cNew2.getNew().size() + " rev: " + cNew2.getRevoking().size() + " ref: " + cNew2.getReferences().size() + " signs:" + cNew2.getSealedByKeys().size() + " data:" + cNew2.getStateData() + " id:" + cNew2.getId());
        Contract cRevoke1 = cNew1.getRevoking().get(0);
        Contract cRevoke2 = cNew2.getRevoking().get(0);
        System.out.println("cRevoke1 new: " + cRevoke1.getNew().size() + " rev: " + cRevoke1.getRevoking().size() + " ref: " + cRevoke1.getReferences().size() + " signs:" + cRevoke1.getSealedByKeys().size() + " data:" + cRevoke1.getStateData() + " id:" + cRevoke1.getId());
        System.out.println("cRevoke2 new: " + cRevoke2.getNew().size() + " rev: " + cRevoke2.getRevoking().size() + " ref: " + cRevoke2.getReferences().size() + " signs:" + cRevoke2.getSealedByKeys().size() + " data:" + cRevoke2.getStateData() + " id:" + cRevoke2.getId());

        Contract cNew1_1 = cNew1.getNew().get(0);
        System.out.println("cNew1_1 new: " + cNew1_1.getNew().size() + " rev: " + cNew1_1.getRevoking().size() + " ref: " + cNew1_1.getReferences().size() + " signs:" + cNew1_1.getSealedByKeys().size() + " data:" + cNew1_1.getStateData() + " id:" + cNew1_1.getId());


        CLIMain.exportContract(cNew1, rootPath + "cNew1.json", "json", true);
        CLIMain.exportContract(cNew2, rootPath + "cNew2.json", "json", true);
        CLIMain.exportContract(cNew1_1, rootPath + "cNew1_1.json", "json", true);
        CLIMain.exportContract(cRevoke1, rootPath + "cRevoke1.json", "json", true);
        CLIMain.exportContract(cRevoke2, rootPath + "cRevoke2.json", "json", true);

        System.out.println("cNew1 " + cNew1.getId() + " " + cNew1.isOk());
        cNew1.traceErrors();
        System.out.println("cNew2 " + cNew2.getId() + " " + cNew2.isOk());
        cNew2.traceErrors();
        System.out.println("cRevoke1 " + cRevoke1.getId() + " " + cRevoke1.isOk());
        cRevoke1.traceErrors();
        System.out.println("cRevoke2 " + cRevoke2.getId() + " " + cRevoke2.isOk());
        cRevoke2.traceErrors();
//        c.traceErrors();

        System.out.println("------------via network-----------");
//        LogPrinter.showDebug(true);
//        callMain("--register",
//                rootPath + "a61.transaction",
//                "-wait", "5000", "-v");

//        callMain2("--check", rootPath + "failed3.transaction", "-v");
//        callMain("--probe", "Ep6jLga8ALShUq/I2nO1dIshmw+7FkjHXs8JI2wQ6nZwXd66uC1c37w2asD9sR8O548qvU2sTfXlRMiNE24XkA", "-v");
        System.out.println(output);

    }

    @Ignore("use with real network and own U")
    @Test
    public void fireRegister() throws Exception {

//        // try to register with payment
        callMain2("--register",
                rootPath + "root5.unicon",
                "--u", rootPath + "UUP.unicon",
                "-k", rootPath + "UKey.private.unikey",
//                "-utest",
                "-wait", "15000", "-v");

        // try to register without payment
        callMain("--register",
                rootPath + "root5.unicon",
                "-wait", "15000", "-v");
        System.out.println(output);
        assertTrue (output.indexOf("payment contract or private keys for payment contract is missing") >= 0);
    }

    @Test
    public void checkNoUKeys() throws Exception {
        // try to register without payment
        callMain("--register",
                basePath + "packedContract.unicon",
                "-wait", "15000", "-v");
        System.out.println(output);
        assertTrue (output.indexOf("payment contract or private keys for payment contract is missing") >= 0);
    }

//    @Test
//    public void specialCheck() throws Exception {
//
//        Contract c = CLIMain.loadContract(rootPath + "root.unicon", true);
//
//        System.out.println(c.getId());
//        callMain2("--probe", c.getId().toBase64String());
//
//        for (Main m : localNodes) {
//            m.node.getLedger().getRecord(c.getId()).destroy();
//        }
//
//        String uContract = getApprovedUContract();
//
//        callMain("--register", rootPath + "root.unicon",
//                "--u", uContract,
//                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
//                "--wait", "3000", "-v");
//        System.out.println(output);
////        assertTrue (output.indexOf("payment contract or private keys for payment contract is missing") >= 0);
//    }

    private List<Contract> createListOfCoinsWithAmount(List<String> values) throws Exception {
        Contract money = createCoin();
        money.getStateData().set(FIELD_NAME, new Decimal(100500));
        money.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        money.seal();
        sealCheckTrace(money, true);

        List<Contract> res = new ArrayList<>();
        for (String value : values) {
            Contract contract = ContractsService.createSplit(money, value, FIELD_NAME, new HashSet<PrivateKey>());
            contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);
            res.add(contract.getNew().get(0));
        }
        return res;
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

            String[] argsNew = new String[args.length+1];
            for(int i =0; i< args.length;i++) {
                argsNew[i] = args[i];
            }
            argsNew[args.length] = "--no-exit";

            CLIMain.main(argsNew);
            errors = CLIMain.getReporter().getErrors();
        });
        return CLIMain.getReporter();
    }

    private static void callMain2(String... args) throws Exception {
        String[] argsNew = new String[args.length+1];
        for(int i =0; i< args.length;i++) {
            argsNew[i] = args[i];
        }
        argsNew[args.length] = "--no-exit";
        CLIMain.main(argsNew);
    }


    protected static void sealCheckTrace(Contract c, boolean isOk) {
        c.seal();
        try {
            c.check();
        } catch (Quantiser.QuantiserException e) {
            e.printStackTrace();
        }
        c.traceErrors();

        if (isOk)
            assertTrue(c.isOk());
        else
            assertFalse(c.isOk());
    }

    protected static Contract createCoin() throws IOException {
        return createCoin(rootPath + "coin.yml");
    }

    protected static Contract createCoin(String yamlFilePath) throws IOException {
        Contract c = Contract.fromDslFile(yamlFilePath);
        c.setOwnerKey(ownerKey2);
        return c;
    }

    protected static Contract createCoin100apiv3() throws IOException {
        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
        return c;
    }

    static Main createMain(String name,boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2/"+name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};
        Main main = new Main(args);
        try {
            main.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        }

        try {
            //main.config.getKeysWhiteList().add(CLIMain.getPrivateKey().getPublicKey());
            main.config.getAddressesWhiteList().add(new KeyAddress(CLIMain.getPrivateKey().getPublicKey(), 0, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //main.config.getKeysWhiteList().add(main.config.getUIssuerKey());
        main.waitReady();
        return main;
    }


    @Test
    public void splitJoin() throws Exception {
        PrivateKey splitJoinKey1 = new PrivateKey(2048);
        PrivateKey splitJoinKey2 = new PrivateKey(2048);
        PrivateKey splitJoinKey3 = new PrivateKey(2048);

        Files.write(Paths.get(basePath + "splitJoinKey1.privateKey.unikey"),splitJoinKey1.pack(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePath + "splitJoinKey2.privateKey.unikey"),splitJoinKey2.pack(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePath + "splitJoinKey3.privateKey.unikey"),splitJoinKey3.pack(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        HashSet<PrivateKey> issuers = new HashSet<>();
        issuers.add(splitJoinKey1);

        HashSet<PublicKey> owners = new HashSet<>();
        owners.add(splitJoinKey2.getPublicKey());


        Contract contract = ContractsService.createTokenContract(issuers, owners, "10000.50", "0.01");
        contract.seal();
        contract.check();
        assertTrue(contract.isOk());

        Files.write(Paths.get(basePath + "origin.unicon"),contract.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        callMain("--split-of", basePath + "origin.unicon",
                "--parts", "1000.50,300",
                "--owners", splitJoinKey1.getPublicKey().getShortAddress()+","+splitJoinKey3.getPublicKey().getShortAddress(), "--keys", basePath+"splitJoinKey2.privateKey.unikey");

        assertTrue (new File(basePath + "origin_main.unicon").exists());
        assertTrue (new File(basePath + "origin_0.unicon").exists());
        assertTrue (new File(basePath + "origin_1.unicon").exists());

        Contract main = TransactionPack.unpack(Files.readAllBytes(Paths.get(basePath + "origin_main.unicon"))).getContract();
        main.check();
        assertTrue(main.isOk());

        Contract part1 = TransactionPack.unpack(Files.readAllBytes(Paths.get(basePath + "origin_0.unicon"))).getContract();
        Contract part2 = TransactionPack.unpack(Files.readAllBytes(Paths.get(basePath + "origin_1.unicon"))).getContract();
        HashSet<PublicKey> part1Keys = new HashSet<>();
        part1Keys.add(splitJoinKey1.getPublicKey());

        HashSet<PublicKey> part2Keys = new HashSet<>();
        part2Keys.add(splitJoinKey3.getPublicKey());

        assertTrue(part1.getOwner().isAllowedForKeys(part1Keys));
        assertEquals(part1.getStateData().getStringOrThrow("amount"),"1000.5");
        assertTrue(part2.getOwner().isAllowedForKeys(part2Keys));
        assertEquals(part2.getStateData().getStringOrThrow("amount"),"300.0");


        callMain("--split-of", basePath + "origin_main.unicon", basePath + "origin_0.unicon", basePath + "origin_1.unicon", "--keys", basePath+"splitJoinKey1.privateKey.unikey"+","+basePath+"splitJoinKey3.privateKey.unikey","--output",basePath+"join_bad.unicon");
        callMain("--split-of", basePath + "origin_0.unicon", basePath + "origin_1.unicon", "--keys", basePath+"splitJoinKey1.privateKey.unikey"+","+basePath+"splitJoinKey3.privateKey.unikey","--output",basePath+"join_good.unicon");


        assertTrue (new File(basePath + "join_bad.unicon").exists());
        assertTrue (new File(basePath + "join_good.unicon").exists());

        Contract joinBad = TransactionPack.unpack(Files.readAllBytes(Paths.get(basePath + "join_bad.unicon"))).getContract();
        joinBad.check();
        assertFalse(joinBad.isOk());
        Contract joinGood = TransactionPack.unpack(Files.readAllBytes(Paths.get(basePath + "join_good.unicon"))).getContract();
        assertTrue(joinGood.getOwner().isAllowedForKeys(part1Keys));
        assertEquals(joinGood.getStateData().getString("amount"),"1300.5");
        joinGood.check();
        assertTrue(joinGood.isOk());


        callMain("--split-of", basePath + "join_good.unicon",
                "--parts", "1,1",
                "--owners", splitJoinKey2.getPublicKey().getShortAddress()+","+splitJoinKey3.getPublicKey().getShortAddress(), "--keys", basePath+"splitJoinKey1.privateKey.unikey","--output",basePath+"join_good_split.unicon","--output",basePath+"join_good_split_part.unicon");

        assertTrue (new File(basePath + "join_good_split.unicon").exists());
        assertTrue (new File(basePath + "join_good_split_part.unicon").exists());
        assertTrue (new File(basePath + "join_good_split_1.unicon").exists());

    }

    @Test
    public void sign() throws Exception {

        PrivateKey key1 = new PrivateKey(2048);
        PrivateKey key2 = new PrivateKey(2048);

        Files.write(Paths.get(basePath + "signKey1.privateKey.unikey"),key1.pack(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        Files.write(Paths.get(basePath + "signKey2.privateKey.unikey"),key2.pack(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        HashSet<PrivateKey> issuers = new HashSet<>();
        issuers.add(key1);

        HashSet<PublicKey> owners = new HashSet<>();
        owners.add(key1.getPublicKey());

        Contract contract = ContractsService.createTokenContract(issuers, owners, "10000.50", "0.01");
        SimpleRole issuer1 = new SimpleRole("issuer1",new KeyRecord(key1.getPublicKey()));
        SimpleRole issuer2 = new SimpleRole("issuer2",new KeyRecord(key2.getPublicKey()));
        ListRole issuer = new ListRole("issuer",ListRole.Mode.ALL,Do.listOf(issuer1,issuer2));
        contract.registerRole(issuer);
        contract.seal();
        contract.check();
        assertFalse(contract.isOk());

        Files.write(Paths.get(basePath + "sign.unicon"),contract.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        assertFalse(CLIMain.loadContract(basePath + "sign.unicon").check());

        callMain("--sign",basePath + "sign.unicon","--keys",basePath + "signKey2.privateKey.unikey");
        callMain("--sign",basePath + "sign.unicon","--keys",basePath + "signKey2.privateKey.unikey","--output", basePath + "sign_2.unicon");
        assertTrue(CLIMain.loadContract(basePath + "sign_2.unicon").check());
    }

    @Test
    public void probeFile() throws Exception {

        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        c.addSignerKeyFromFile(rootPath + "keys/u_key.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

        Files.write(Paths.get(basePath + "probeFile.unicon"),c.getPackedTransaction(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        System.out.println("---");
        System.out.println("register contract");
        System.out.println("---");

        CLIMain.registerContract(c);

        Thread.sleep(1500);
        System.out.println("---");
        System.out.println("check contract");
        System.out.println("---");

        callMain("--probe-file", basePath + "probeFile.unicon");

        System.out.println(output);
        assertTrue (output.indexOf(ItemState.APPROVED.name()) >= 0);

    }

    @Test
    public void createRegisterParcel() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();

        String contractFileName = basePath + "createRegisterParcel.unicon";
        new FileOutputStream(contractFileName).write(contract.getPackedTransaction());


        String uContract = getApprovedUContract();
        callMain("--create-parcel", contractFileName, "--verbose",
                "--u", uContract,
                "--k", rootPath + "keys/stepan_mamontov.private.unikey");


        callMain("--register-parcel", new FilenameTool(contractFileName).setExtension("uniparcel").toString());

        Thread.sleep(1500);
        System.out.println("probe after register");
        Contract c = CLIMain.loadContract(contractFileName);
        callMain("--probe", c.getId().toBase64String(), "--verbose");

        System.out.println(output);
        assertEquals(0, errors.size());

        assertTrue (output.indexOf(ItemState.APPROVED.name()) > 0);
    }

    @Test
    public void ecsrowComplete() throws Exception {

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>();
        Set<PrivateKey> customerPrivateKeys = new HashSet<>();
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>();
        Set<PrivateKey> executorPrivateKeys = new HashSet<>();

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        Set<PublicKey> executorPublicKeys = new HashSet<>();

        issuerPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }

        customerPrivateKeys.add(new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey")));
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }

        arbitratorPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey")));
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        executorPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/notarius.private.unikey")));
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }

        String uContract = getApprovedUContract();

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey"));
        Contract contract_for_payment = Contract.fromDslFile(rootPath + "StepaU.yml");
        contract_for_payment.addSignerKey(manufacturePrivateKey);
        contract_for_payment.seal();
        contract_for_payment.check();
        contract_for_payment.traceErrors();

        CLIMain.getClientNetwork().client.register(contract_for_payment.getPackedTransaction(), 5000);

        ItemResult itemResult = CLIMain.getClientNetwork().client.getState(contract_for_payment);
        System.out.println("contract_for_payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys,
                arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"),
                escrow.getNew().get(0).getOrigin().toBase64String());
        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        boolean result = ContractsService.addPaymentToEscrowContract(escrow, contract_for_payment, customerPrivateKeys,
                customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(contract_for_payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(contract_for_payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        CLIMain.saveContract(escrow, basePath + "escrow_root_contract.unicon", true, true);

        callMain2("--register", basePath + "escrow_root_contract.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        itemResult = CLIMain.getClientNetwork().client.getState(escrow);
        System.out.println("escrow: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = CLIMain.getClientNetwork().client.getState(contract_for_payment);
        System.out.println("contract_for_payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Contract completedEscrow = ContractsService.completeEscrowContract(escrow);

        assertEquals(completedEscrow.getStateData().getString("status", "null"), "completed");

        completedEscrow.addSignatureToSeal(issuerPrivateKeys);
        completedEscrow.addSignatureToSeal(customerPrivateKeys);
        completedEscrow.addSignatureToSeal(executorPrivateKeys);
        completedEscrow.seal();

        CLIMain.saveContract(completedEscrow, basePath + "completed_escrow.unicon", true, true);

        callMain2("--register", basePath + "completed_escrow.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        itemResult = CLIMain.getClientNetwork().client.getState(completedEscrow);
        System.out.println("completedEscrow: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // transfer payment to executor
        Contract newPayment = ContractsService.takeEscrowPayment(executorPrivateKeys, contract_for_payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(completedEscrow);

        newPayment.check();
        newPayment.traceErrors();

        CLIMain.saveContract(newPayment, basePath + "new_payment.unicon", true, true);

        callMain2("--register", basePath + "new_payment.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        itemResult = CLIMain.getClientNetwork().client.getState(newPayment);
        System.out.println("newPayment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }

    @Test(timeout = 90000)
    public void ecsrowCancel() throws Exception {

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>();
        Set<PrivateKey> customerPrivateKeys = new HashSet<>();
        Set<PrivateKey> arbitratorPrivateKeys = new HashSet<>();
        Set<PrivateKey> executorPrivateKeys = new HashSet<>();

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        Set<PublicKey> customerPublicKeys = new HashSet<>();
        Set<PublicKey> arbitratorPublicKeys = new HashSet<>();
        Set<PublicKey> executorPublicKeys = new HashSet<>();

        issuerPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }

        customerPrivateKeys.add(new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey")));
        for (PrivateKey pk : customerPrivateKeys) {
            customerPublicKeys.add(pk.getPublicKey());
        }

        arbitratorPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey")));
        for (PrivateKey pk : arbitratorPrivateKeys) {
            arbitratorPublicKeys.add(pk.getPublicKey());
        }

        executorPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/notarius.private.unikey")));
        for (PrivateKey pk : executorPrivateKeys) {
            executorPublicKeys.add(pk.getPublicKey());
        }

        String uContract = getApprovedUContract();

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "keys/u_key.private.unikey"));
        Contract contract_for_payment = Contract.fromDslFile(rootPath + "StepaU.yml");
        contract_for_payment.addSignerKey(manufacturePrivateKey);
        contract_for_payment.seal();
        contract_for_payment.check();
        contract_for_payment.traceErrors();

        CLIMain.getClientNetwork().client.register(contract_for_payment.getPackedTransaction(), 5000);

        // create external escrow contract
        Contract escrow = ContractsService.createEscrowContract(issuerPrivateKeys, customerPublicKeys, executorPublicKeys,
                arbitratorPublicKeys);

        // check link between external and internal escrow contracts
        assertEquals(escrow.getDefinition().getData().getString("EscrowOrigin", "null"), escrow.getNew().get(0).getOrigin().toBase64String());

        // check internal escrow contract status
        assertEquals(escrow.getNew().get(0).getStateData().getString("status", "null"), "opened");

        // add payment to escrow contract
        boolean result = ContractsService.addPaymentToEscrowContract(escrow, contract_for_payment, customerPrivateKeys,
                customerPublicKeys, executorPublicKeys);
        assertTrue(result);

        // check payment transactional references
        assertTrue(contract_for_payment.findReferenceByName("return_payment_to_customer", "transactional") != null);
        assertTrue(contract_for_payment.findReferenceByName("send_payment_to_executor", "transactional") != null);

        CLIMain.saveContract(escrow, basePath + "escrow2_root_contract.unicon", true, true);

        callMain2("--register", basePath + "escrow2_root_contract.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        ItemResult itemResult = CLIMain.getClientNetwork().client.getState(escrow);
        System.out.println("escrow: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        itemResult = CLIMain.getClientNetwork().client.getState(contract_for_payment);
        System.out.println("contract_for_payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // cancel escrow contract(by external escrow contract)
        Contract canceledEscrow = ContractsService.cancelEscrowContract(escrow);

        // check internal escrow contract status
        assertEquals(canceledEscrow.getStateData().getString("status", "null"), "canceled");

        canceledEscrow.addSignatureToSeal(issuerPrivateKeys);
        canceledEscrow.addSignatureToSeal(customerPrivateKeys);
        canceledEscrow.addSignatureToSeal(arbitratorPrivateKeys);
        canceledEscrow.seal();

        CLIMain.saveContract(canceledEscrow, basePath + "canceled_escrow.unicon", true, true);

        callMain2("--register", basePath + "canceled_escrow.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        itemResult = CLIMain.getClientNetwork().client.getState(canceledEscrow);
        System.out.println("canceledEscrow: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        // return payment to customer
        Contract newPayment = ContractsService.takeEscrowPayment(customerPrivateKeys, contract_for_payment);

        // internal escrow contract for checking references
        newPayment.getTransactionPack().addReferencedItem(canceledEscrow);

        newPayment.check();
        newPayment.traceErrors();

        CLIMain.saveContract(newPayment, basePath + "new_payment2.unicon", true, true);

        callMain2("--register", basePath + "new_payment2.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        itemResult = CLIMain.getClientNetwork().client.getState(newPayment);
        System.out.println("newPayment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }

    @Test(timeout = 90000)
    public void goodAttachDataToNotary() throws Exception {

        Set<PrivateKey> issuerPrivateKeys = new HashSet<>();
        Set<PrivateKey> notariusPrivateKeys = new HashSet<>();

        Set<PublicKey> issuerPublicKeys = new HashSet<>();
        Set<PublicKey> notariusPublicKeys = new HashSet<>();

        issuerPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        for (PrivateKey pk : issuerPrivateKeys) {
            issuerPublicKeys.add(pk.getPublicKey());
        }

        notariusPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/notarius.private.unikey")));
        for (PrivateKey pk : notariusPrivateKeys) {
            notariusPublicKeys.add(pk.getPublicKey());
        }

        List<String> fileName = new ArrayList<>();
        List<String> fileDesc = new ArrayList<>();
        fileName.add(rootPath + "ref_conditions_root_contract.yml");
        fileName.add(rootPath + "coin100.yml");
        fileDesc.add("ref_conditions_root_contract.yml - description");
        fileDesc.add("coin100.yml - description");

        String uContract = getApprovedUContract();

        Contract notaryContract = ContractsService.createNotaryContract(issuerPrivateKeys, notariusPublicKeys, fileName, fileDesc);

        assertTrue(notaryContract.getOwner().isAllowedForKeys(notariusPublicKeys));
        assertTrue(notaryContract.getIssuer().isAllowedForKeys(issuerPrivateKeys));
        assertTrue(notaryContract.getCreator().isAllowedForKeys(issuerPrivateKeys));

        assertFalse(notaryContract.getOwner().isAllowedForKeys(issuerPrivateKeys));
        assertFalse(notaryContract.getIssuer().isAllowedForKeys(notariusPublicKeys));
        assertFalse(notaryContract.getCreator().isAllowedForKeys(notariusPublicKeys));

        assertTrue(notaryContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(notaryContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertTrue(notaryContract.isPermitted("revoke", notariusPublicKeys));
        assertTrue(notaryContract.isPermitted("revoke", issuerPublicKeys));

        assertTrue(notaryContract.isPermitted("change_owner", notariusPublicKeys));
        assertFalse(notaryContract.isPermitted("change_owner", issuerPublicKeys));

        Binder files = notaryContract.getDefinition().getData().getBinder("files");
        assertEquals(files.getBinder("ref_conditions_root_contract_yml").getString("file_name"), "ref_conditions_root_contract.yml");
        assertEquals(files.getBinder("ref_conditions_root_contract_yml").getString("file_description"), "ref_conditions_root_contract.yml - description");
        assertEquals(files.getBinder("coin100_yml").getString("file_name"), "coin100.yml");
        assertEquals(files.getBinder("coin100_yml").getString("file_description"), "coin100.yml - description");

        Contract notaryDeserialized = DefaultBiMapper.deserialize(BossBiMapper.serialize(notaryContract));
        assertTrue(notaryContract.getDefinition().getData().equals(notaryDeserialized.getDefinition().getData()));

        CLIMain.saveContract(notaryContract, basePath + "notaryContractWithFile.unicon", true, true);

        callMain("-e", basePath + "notaryContractWithFile.unicon", "--output", basePath + "notaryContractWithFile.json");
        callMain("-e", basePath + "notaryContractWithFile.unicon", "--output", basePath + "notaryContractWithFile.xml");
        callMain("-e", basePath + "notaryContractWithFile.unicon", "--output", basePath + "notaryContractWithFile.XML");
        callMain("-e", basePath + "notaryContractWithFile.unicon", "--output", basePath + "notaryContractWithFile.yaml");

        callMain2("--register", basePath + "notaryContractWithFile.unicon",
                "--u", uContract,
                "-k", rootPath + "keys/stepan_mamontov.private.unikey",
                "-wait", "80000", "-v");

        ItemResult itemResult = CLIMain.getClientNetwork().client.getState(notaryContract);
        System.out.println("notaryContractWithFile: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

    }
}