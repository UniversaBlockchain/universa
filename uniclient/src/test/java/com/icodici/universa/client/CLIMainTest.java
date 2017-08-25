/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

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

public class CLIMainTest {

    String rootPath;
    private List<Binder> errors;
    private String output;

    @Before
    public void prepareRoot() {
        rootPath = "./src/test_files/";
        new File(rootPath + "/simple_root_contract.unic").delete();
        assert (new File(rootPath + "/simple_root_contract.yml").exists());
        CLIMain.setTestMode();
    }

    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml", "-j");
        assert (new File(rootPath + "/simple_root_contract.unic").exists());
    }

    @Test
    public void checNetwork() throws Exception {
        Reporter r = callMain("-n");
        assertThat(r.getMessage(-2), matches(".*10/10"));
    }

    @Test
    public void createAndSign() throws Exception {
        callMain("-c", rootPath + "simple_root_contract.yml",
                 "-k", rootPath + "_xer0yfe2nn1xthc.private.unikey"
        );
        assert (new File(rootPath + "/simple_root_contract.unic").exists());
        if (errors.size() > 0) {
            System.out.println(errors);
        }
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