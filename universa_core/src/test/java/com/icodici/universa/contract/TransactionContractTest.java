/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransactionContractTest extends ContractTestBase {

    @Test
    public void badRevoke() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        c.seal();

        PrivateKey issuer = TestKeys.privateKey(2);
        Contract tc = c.createRevocation(issuer);

        // c can't be revoked with this key!
        boolean result = tc.check();
        assertFalse(result);
        assertEquals(1, tc.getErrors().size());
        assertEquals(Errors.FORBIDDEN, tc.getErrors().get(0).getError());
    }

    @Test
    public void goodRevoke() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath+"_xer0yfe2nn1xthc.private.unikey");
        PrivateKey goodKey = c.getKeysToSignWith().iterator().next();
        // let's make this key among owners
        ((SimpleRole)c.getRole("owner")).addKeyRecord(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

        Contract revokeContract = c.createRevocation(goodKey);


        assertTrue(revokeContract.check());
//        tc.traceErrors();
    }


    @Test
    public void checkTransactional() throws Exception {

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(rootPath + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        delorean.traceErrors();

        Contract.Transactional transactional = delorean.createTransactionalSection();
        Reference reference = new Reference();
//        reference.setName("transactional_example");
        transactional.addReference(reference);
        Contract deloreanTransactional = delorean.createRevision(transactional);
        deloreanTransactional.addSignerKey(manufacturePrivateKey);
        deloreanTransactional.seal();
        deloreanTransactional.traceErrors();

    }
}