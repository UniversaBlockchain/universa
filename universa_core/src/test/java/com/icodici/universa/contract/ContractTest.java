/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.is;

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
        List<Contract.Error> errors = c.getErrors();
        // It is just ok but not signed
        assertEquals(1, errors.size());
        assertEquals(errors.get(0).getCode(), Errors.NOT_SIGNED);

        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        ok = c.check();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (Contract.Error e : errors) {
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
        List<Contract.Error> errors = c2.getErrors();

        if (errors.isEmpty()) {
            assertTrue(ok);
            assertTrue(c.isOk());
        } else {
            for (Contract.Error e : errors) {
                System.out.println(e);
                fail("errors in contract");
            }
        }
        assertTrue(c.check());
    }

    @Test
    public void newRevision() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        byte[] sealed = c.seal();
        assertTrue(c.check());

        Contract c2 = c.createRevision(TestKeys.privateKey(0), TestKeys.privateKey(3));
        assertEquals(1, c2.getRevokingItems().size());
        assertEquals(c, c2.getRevokingItems().iterator().next());
        assertEquals(2, c2.getKeysToSignWith().size());
        assertEquals(2, c2.getRevision());
        assertEquals(c.getId(), c2.getParent());
        assertEquals(c.getId(), c2.getOrigin());

        c2.seal();

        // todo: ensure state-change check works
        // todo: ensure there are no other changes.

        Contract c3 = c2.createRevision(TestKeys.privateKey(0), TestKeys.privateKey(3));
        assertEquals(1, c3.getRevokingItems().size());
        assertEquals(c2, c3.getRevokingItems().iterator().next());
        assertEquals(2, c3.getKeysToSignWith().size());
        assertEquals(3, c3.getRevision());
        assertEquals(c2.getId(), c3.getParent());
        assertEquals(c.getId(), c3.getOrigin());


//        c2.check();
//        c2.traceErrors();
    }

    @Test
    public void changeOwner() throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");
        PrivateKey ownerKey1 = TestKeys.privateKey(3);
        PrivateKey ownerKey2 = TestKeys.privateKey(1);
        PrivateKey ownerKey3 = TestKeys.privateKey(2);

        c.setOwner(ownerKey1);
        assertThat(c.getPermissions().get("change_owner").getRole(), is(instanceOf(RoleLink.class)));
        assertTrue(c.getPermissions().get("change_owner").getRole().isAllowedForKeys(new HashSet(Do.listOf(ownerKey1))));

//        System.out.println("Owner now :" + c.getOwner());
//        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        assertEquals(c, ((RoleLink)c.getPermissions().get("change_owner").getRole()).getContract());
        Role cOwner = c.getOwner();
        assert (cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey1))));
        assert (!cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey2))));

        // Bad contract change: owner has no right to change owner ;)
        Contract c1 = c.createRevision(TestKeys.privateKey(0));
        c1.setOwner(ownerKey2);
        assertNotEquals(c.getOwner(), c1.getOwner());
        c1.seal();
        c1.check();
        assertEquals(1, c1.getErrors().size());
        Contract.Error error = c1.getErrors().get(0);
        assertEquals(Errors.FORBIDDEN, error.getCode());

        // good contract change: creator is an owner

        Contract c2 = c.createRevision(TestKeys.privateKey(0), ownerKey1);
        assertEquals(c, ((RoleLink)c.getPermissions().get("change_owner").getRole()).getContract());

//        System.out.println("c owner   : "+c.getRole("owner"));
//        System.out.println("c2 creator: "+c2.getRole("creator"));

        assertEquals(c.getOwner(), ((RoleLink)c.getPermissions().get("change_owner").getRole()).getRole());
        assertEquals(c2, ((RoleLink)c2.getPermissions().get("change_owner").getRole()).getContract());
        assertEquals(c, ((RoleLink)c.getPermissions().get("change_owner").getRole()).getContract());
        c2.setOwner(ownerKey3);
        assertNotEquals(c.getOwner(), c2.getOwner());
        assertEquals(c.getOwner(), ((RoleLink)c.getPermissions().get("change_owner").getRole()).getRole());


        c2.seal();
        c2.check();
        c2.traceErrors();
        assertTrue(c2.isOk());
    }
}