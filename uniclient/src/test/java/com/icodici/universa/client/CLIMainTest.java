/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import net.sergeych.tools.Binder;
import net.sergeych.tools.ConsoleInterceptor;
import net.sergeych.tools.Reporter;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.icodici.universa.client.RegexMatcher.matches;
import static org.junit.Assert.*;

public class CLIMainTest  {

    protected String rootPath = "./src/test_files/";
    private List<Binder> errors;
    private String output;

    protected PrivateKey ownerKey1;
    protected PrivateKey ownerKey2;
    protected PrivateKey ownerKey3;

    public static final String FIELD_NAME = "amount";

    protected static final String PRIVATE_KEY = "_xer0yfe2nn1xthc.private.unikey";

    protected final String PRIVATE_KEY_PATH = rootPath + PRIVATE_KEY;

    @Before
    public void prepareRoot() throws Exception {
//        new File(rootPath + "/simple_root_contract.unicon").delete();
        assert (new File(rootPath + "/simple_root_contract.yml").exists());
        CLIMain.setTestMode();
        CLIMain.setTestRootPath(rootPath);

        ownerKey1 = TestKeys.privateKey(3);
        ownerKey2 = TestKeys.privateKey(1);
        ownerKey3 = TestKeys.privateKey(2);
    }

//    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml", "-j");
        assert (new File(rootPath + "/simple_root_contract.unicon").exists());
    }

    // we are moving the network so this test do not pass as for now
//    @Test
    public void checNetwork() throws Exception {
        Reporter r = callMain("-n");
        assertThat(r.getMessage(-2), matches(".*10/10"));
    }

    @Test
    public void createAndSign() throws Exception {
        callMain("-c", rootPath + "simple_root_contract_v2.yml",
                 "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey"
        );
        System.out.println(new File(rootPath + "/simple_root_contract_v2.unicon").getAbsolutePath());
        assert (new File(rootPath + "/simple_root_contract_v2.unicon").exists());
        if (errors.size() > 0) {
            System.out.println(errors);
        }
        System.out.println(output);
        assertEquals(0, errors.size());
    }

//    @Test
    public void fingerprints() throws Exception {
        callMain(
                "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey",
                "--fingerprints"
        );
        assert(output.indexOf("test_files/_xer0yfe2nn1xthc.private.unikey") >= 0);
        assert(output.indexOf("B24XkVNy3fSJUZBzLsnJo4f+ZqGwbNxHgBr198FIPgyy") >= 0);
//        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportTest() throws Exception {
        callMain(
                "-e", rootPath + "contract_to_export.unicon");
        System.out.println(output);
        assert(output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsJSONTest() throws Exception {
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-as", "json");
        System.out.println(output);
        assert(output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsXMLTest() throws Exception {
        callMain(
                "-e", rootPath + "simple_root_contract_v2.unicon", "-as", "xml");
        System.out.println(output);
        assert(output.indexOf("export as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWithNameTest() throws Exception {
        String name = "ExportedContract";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-name", rootPath + name);
        System.out.println(output);
        assert(output.indexOf(name + " export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportPublicKeys() throws Exception {
        String role = "owner";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-extract-key", role);
        System.out.println(output);
        assert(output.indexOf(role + " export public keys ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportPublicKeysWrongRole() throws Exception {
        String role = "wrongRole";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-extract-key", role);
        System.out.println(output);
        assert(output.indexOf(role + " export public keys ok") < 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFields() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-get", field1, "-get", field2);
        System.out.println(output);
        assert(output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsXML() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-as", "xml", "-get", field1, "-get", field2);
        System.out.println(output);
        assert(output.indexOf("export fields as xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFields() throws Exception {
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
                "       <unixtime>2022-08-05 10:25:37</unixtime>\n" +
                "</definition.expires__at>";
        callMain(
                "-e", rootPath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2);
        System.out.println(output);
//        assert(output.indexOf("update field " + field1 + " ok") >= 0);
        assert(output.indexOf("update field " + field2 + " ok") >= 0);
        assert(output.indexOf("contract expires at 2022-08-05") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void updateFieldsFromJSON() throws Exception {
        String field1 = "definition.issuer";
        String value1 = "{\"keys\":[{\"name\":\"Universa\",\"key\":{\"__type\":\"RSAPublicKey\",\"packed\":{\"__type\":\"binary\",\"base64\":\"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0W\\nsmcAt5a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOyta\\nICE01bkOkf6Mz5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJ\\nUZi66iu9e0SXupOr/+BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/na\\nIaf2yuxiQNz3uFMTn0IpULCMvLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ\\n/4wNBUveBDLFLlOcMpCzWlO/D7M2IyNa8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzF\\nbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZchxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GL\\ny+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8LyIqeM7dSyaHFTBII/sLuFru6ffoKx\\nBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwalMexOc3/kPEEdfjH/GcJU0Mw6\\nDgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"}},\"__type\":\"KeyRecord\"}],\"__type\":\"SimpleRole\",\"name\":\"issuer\"}";
        String field2 = "definition.expires_at";
        String value2 = "{\"seconds\":1519772317,\"__type\":\"unixtime\"}";
        callMain(
                "-e", rootPath + "contract_to_export.unicon",
                "-set", field1, "-value", value1,
                "-set", field2, "-value", value2);
        System.out.println(output);
        assert(output.indexOf("update field " + field1 + " ok") >= 0);
        assert(output.indexOf("update field " + field2 + " ok") >= 0);
        assert(output.indexOf("contract expires at 2018-02-27") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportFieldsAsJSON() throws Exception {
        String field1 = "definition.issuer";
        String field2 = "state.origin";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-get", field1, "-get", field2, "-as", "json");
        System.out.println(output);
        assert(output.indexOf("export fields as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWrongFields() throws Exception {
        String field = "definition.wrong";
        callMain(
                "-e", rootPath + "contract_to_export.unicon", "-get", field, "-as", "json");
        System.out.println(output);
        assert(output.indexOf("export fields as json ok") < 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importTest() throws Exception {
        callMain(
                "-i", rootPath + "contract_to_import.json");
        System.out.println(output);
        assert(output.indexOf("import from json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importFromJSONTest() throws Exception {
        callMain(
                "-i", rootPath + "contract_to_import.json");
        System.out.println(output);
        assert(output.indexOf("import from json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importFromXMLTest() throws Exception {
        callMain(
                "-i", rootPath + "contract_to_import.xml");
        System.out.println(output);
        assert(output.indexOf("import from xml ok") >= 0);
        assertEquals(0, errors.size());
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
    public void importFromYamlTest() throws Exception {
        callMain(
                "-i", rootPath + "simple_root_contract_v2.yml");
        System.out.println(output);
        assert(output.indexOf("import from yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importWithNameTest() throws Exception {
        String name = "ImportedContract.unicon";
        callMain(
                "-i", rootPath + "contract_to_import.xml", "-name", rootPath + name);
        System.out.println(output);
        assert(output.indexOf("import from xml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void findContractsInPath() throws Exception {

        // Create contract files (coins and some non-coins)
        File dirFile = new File(rootPath + "contract_subfolder/");
        if(!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if(!dirFile.exists()) dirFile.mkdir();

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
            saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getIntOrThrow(FIELD_NAME) + ".unicon");
        }

        Contract nonCoin = Contract.fromYamlFile("./src/test_files/simple_root_contract_v2.yml");
        saveContract(nonCoin, rootPath + "contract_subfolder/NonCoin.unicon");
        saveContract(nonCoin, rootPath + "contract_subfolder/contract_subfolder_level2/NonCoin.unicon");

        // Found wallets

        callMain("-f", rootPath + "contract_subfolder/", "-v", "-r");
        System.out.println(output);


        // Clean up files

        File[] filesToRemove = new File(rootPath + "contract_subfolder/").listFiles();
        for(File file : filesToRemove) {
            file.delete();
        }

        filesToRemove = new File(rootPath + "contract_subfolder/contract_subfolder_level2/").listFiles();
        for(File file : filesToRemove) {
            file.delete();
        }

        Integer total = 0;
        for (Integer i : coinValues) {
            total += i;
        }
        assert(output.indexOf(total + " (TUNC)") >= 0);
    }

//    @Test
    public void findContractsInWrongPath() throws Exception {

        try {
            callMain("-f", rootPath + "not_exist_subfolder/", "-v", "-r");
            System.out.println(output);
        } catch (Exception e) {

        }
        assert(output.indexOf("No contracts found") >= 0);
    }

    @Test
    public void downloadContract() throws Exception {
        callMain("-d", "www.universa.io");
        System.out.println(output);
        assert(output.indexOf("downloading from www.universa.io") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkDataIsValidContract() throws Exception {
        callMain("-ch", rootPath + "simple_root_contract_v2.yml", "--binary");
        System.out.println(output);
        assert(output.indexOf("Contract is valid") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkContract() throws Exception {
        callMain("-ch", rootPath + "contract_to_export.unicon");
        System.out.println(output);
        assertEquals(0, errors.size());
    }

    @Test
    public void checkContractInPath() throws Exception {
        // check contracts
        callMain("-ch", rootPath, "-v");
        System.out.println(output);
//        assertEquals(3, errors.size());
    }

//    @Test
    public void checkContractInNotExistPath() throws Exception {
        // check contracts
        callMain("-ch", rootPath + "notexist.unicon", "-v");
        System.out.println(output);

        assert(output.indexOf("No contracts found") >= 0);
    }

    @Test
    public void checkContractInPathRecursively() throws Exception {

        // Create contract files (coins and some non-coins)

        File dirFile = new File(rootPath + "contract_subfolder/");
        if(!dirFile.exists()) dirFile.mkdir();
        dirFile = new File(rootPath + "contract_subfolder/contract_subfolder_level2/");
        if(!dirFile.exists()) dirFile.mkdir();

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
            saveContract(coin, rootPath + dir + "Coin_" + coin.getStateData().getIntOrThrow(FIELD_NAME) + ".unicon");
        }

        Contract nonCoin = Contract.fromYamlFile("./src/test_files/simple_root_contract_v2.yml");
        saveContract(nonCoin, rootPath + "contract_subfolder/NonCoin.unicon");
        saveContract(nonCoin, rootPath + "contract_subfolder/contract_subfolder_level2/NonCoin.unicon");

        // check contracts

        callMain("-ch", rootPath, "-v", "-r");
        System.out.println(output);
//        assertEquals(5, errors.size());


        // Clean up files

        File[] filesToRemove = new File(rootPath + "contract_subfolder/").listFiles();
        for(File file : filesToRemove) {
            file.delete();
        }

        filesToRemove = new File(rootPath + "contract_subfolder/contract_subfolder_level2/").listFiles();
        for(File file : filesToRemove) {
            file.delete();
        }
    }

    @Test
    public void checkNotSignedContract() throws Exception {
        callMain("-ch", rootPath + "not_signed_contract.unicon");
        System.out.println(output);
        assertEquals(1, errors.size());
    }

    @Test
    public void checkOldContract() throws Exception {
        callMain("-ch", rootPath + "old_api_contract.unicon", "-v");
        System.out.println(output);
        assertEquals(1, errors.size());
    }

    private List<Contract> createListOfCoinsWithAmount(List<Integer> values) throws Exception {
        List<Contract> contracts = new ArrayList<>();


        for (Integer value : values) {
            Contract contract = createCoin();
            contract.getStateData().set(FIELD_NAME, new Decimal(value));
            contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

            sealCheckTrace(contract, true);

            contracts.add(contract);
        }

        return contracts;
    }

    private void saveContract(Contract contract, String fileName) throws IOException {

        if (fileName == null)
        {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(contract.getCreatedAt()) + ".unicon";
        }

        byte[] data = contract.seal();
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }
    }

    private Reporter callMain(String... args) throws Exception {
        output = ConsoleInterceptor.copyOut(() -> {
            CLIMain.main(args);
            errors = CLIMain.getReporter().getErrors();
        });
        return CLIMain.getReporter();
    }


    protected void sealCheckTrace(Contract c, boolean isOk) {
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
        Contract c = Contract.fromYamlFile(yamlFilePath);
        c.setOwnerKey(ownerKey2);
        return c;
    }
}