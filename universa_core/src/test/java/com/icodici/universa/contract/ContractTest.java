/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ContractTest extends ContractTestBase {
    @Test
    public void fromYamlFile() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        assertProperSimpleRootContract(c);
    }


    @Test
    public void serializeToBinder() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        Binder b = c.serializeToBinder();
        Contract c1 = new Contract(b);
        assertProperSimpleRootContract(c1);
        Binder b1 = Boss.load(Boss.dump(b));
        Contract c2 = new Contract(b1);
        assertProperSimpleRootContract(c2);
    }

    @Test
    public void checkCreatingRootContract() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        boolean ok = c.check();
        List<ErrorRecord> errors = c.getErrors();
        // It is just ok but not signed
        assertEquals(1, errors.size());
        assertEquals(errors.get(0).getError(), Errors.NOT_SIGNED);

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        ok = c.check();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (ErrorRecord e : errors) {
                System.out.println(e);
                fail("errors in contract");
            }
        }
        assertTrue(c.check());
    }

    @Test
    public void checkSealingRootContract() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        byte[] sealed = c.seal();
//        Bytes.dump(sealed);
//        System.out.println(sealed.length);
        Contract c2 = new Contract(sealed);
        assertProperSimpleRootContract(c2);

        boolean ok = c2.check();
        List<ErrorRecord> errors = c2.getErrors();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (ErrorRecord e : errors) {
                System.out.println(e);
                fail("errors in contract");
            }
        }
        assertTrue(c.check());
    }
}