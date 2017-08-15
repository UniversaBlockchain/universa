/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.client;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class CLIMainTest {

    String rootPath;

    @Before
    public void prepareRoot() {
        rootPath = "./src/test_files/";
        new File(rootPath+"/simple_root_contract.unic").delete();
        assert(new File(rootPath+"/simple_root_contract.yml").exists());
        CLIMain.setTestMode();
    }

    @Test
    public void createContract() throws Exception {
        callMain("-c", rootPath+"simple_root_contract.yml", "-j");
        assert(new File(rootPath+"/simple_root_contract.unic").exists());
    }

    private void callMain(String... args) throws IOException {
        CLIMain.main(args);
    }
}