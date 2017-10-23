/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import com.icodici.universa.contract.Contract;
import net.sergeych.tools.Binder;
import net.sergeych.tools.ConsoleInterceptor;
import net.sergeych.tools.Reporter;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.icodici.universa.client.RegexMatcher.matches;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class CLIMainTest {

    String rootPath;
    private List<Binder> errors;
    private String output;

    @Before
    public void prepareRoot() {
        rootPath = "./src/test_files/";
//        new File(rootPath + "/simple_root_contract.unic").delete();
        assert (new File(rootPath + "/simple_root_contract.yml").exists());
        CLIMain.setTestMode();
    }

//    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml", "-j");
        assert (new File(rootPath + "/simple_root_contract.unic").exists());
    }

    // we are moving the network so this test do not pass as for now
//    @Test
    public void checNetwork() throws Exception {
        Reporter r = callMain("-n");
        assertThat(r.getMessage(-2), matches(".*10/10"));
    }

//    @Test
    public void createAndSign() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml",
                 "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey"
        );
        System.out.println(new File(rootPath + "/simple_root_contract.unic").getAbsolutePath());
        assert (new File(rootPath + "/simple_root_contract.unic").exists());
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
                "-e", rootPath + "contract_to_export.unic");
        System.out.println(output);
        assert(output.indexOf("export ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportAsJSONTest() throws Exception {
        callMain(
                "-e", rootPath + "contract_to_export.unic", "-as", "json");
        System.out.println(output);
        assert(output.indexOf("export as json ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void exportWithNameTest() throws Exception {
        String name = "ExportedContract";
        callMain(
                "-e", rootPath + "contract_to_export.unic", "-name", rootPath + name);
        System.out.println(output);
        assert(output.indexOf(name + " export ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importTest() throws Exception {
        callMain(
                "-i", rootPath + "contract_to_import.xml");
        System.out.println(output);
        assert(output.indexOf("import ok") >= 0);
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
    public void importFromYamlTest() throws Exception {
        callMain(
                "-i", rootPath + "simple_root_contract_v2.yml");
        System.out.println(output);
        assert(output.indexOf("import from yaml ok") >= 0);
        assertEquals(0, errors.size());
    }

    @Test
    public void importWithNameTest() throws Exception {
        String name = "ImportedContract.unic";
        callMain(
                "-i", rootPath + "contract_to_import.xml", "-name", rootPath + name);
        System.out.println(output);
        assert(output.indexOf("import ok") >= 0);
        assertEquals(0, errors.size());
    }

    private Reporter callMain(String... args) throws Exception {
        output = ConsoleInterceptor.copyOut(() -> {
            CLIMain.main(args);
            errors = CLIMain.getReporter().getErrors();
        });
        return CLIMain.getReporter();
    }
}