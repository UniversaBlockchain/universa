/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 * Written by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/02/17.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.permissions.RevokePermission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.TestKeys;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class PermissionsTest extends ContractTestBase {

    @Test
    public void newRevision() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        byte[] sealed = c.seal();
        assertTrue(c.check());

        Contract c2 = c.createRevision(TestKeys.privateKey(0), TestKeys.privateKey(3));
        assertEquals(1, c2.getRevokingItems().size());
        assertEquals(c, c2.getRevokingItems().iterator().next());
        assertEquals(2, c2.getKeysToSignWith().size());
        assertEquals(2, c2.getRevision());
        assertEquals(c.getId(), c2.getParent());
        assertEquals(c.getId(), c2.getRawOrigin());

        c2.seal();

        Contract c3 = c2.createRevision(TestKeys.privateKey(0), TestKeys.privateKey(3));
        assertEquals(1, c3.getRevokingItems().size());
        assertEquals(c2, c3.getRevokingItems().iterator().next());
        assertEquals(2, c3.getKeysToSignWith().size());
        assertEquals(3, c3.getRevision());
        assertEquals(c2.getId(), c3.getParent());
        assertEquals(c.getId(), c3.getRawOrigin());


//        c2.check();
//        c2.traceErrors();
    }

    @Test
    public void validPermissionIds() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        byte[] sealed = c.seal();
        assertTrue(c.check());
        Binder s = DefaultBiMapper.serialize(c);
        s.getBinderOrThrow("definition","permissions");
    }

    @Test
    public void changeOwner() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        c.setOwnerKey(ownerKey1);
        assertThat(c.getPermissions().getFirst("change_owner").getRole(), is(instanceOf(RoleLink.class)));
        assertTrue(c.getPermissions().getFirst("change_owner").getRole().isAllowedForKeys(new HashSet(Do.listOf(ownerKey1))));

//        System.out.println("Owner now :" + c.getOwner());
//        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        assertEquals(c, ((RoleLink) c.getPermissions().getFirst("change_owner").getRole()).getContract());
        Role cOwner = c.getOwner();
        assertTrue (cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey1))));
        assertTrue (!cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey2))));

        // Bad contract change: owner has no right to change owner ;)
        Contract c1 = c.createRevision(TestKeys.privateKey(0));
        c1.setOwnerKey(ownerKey2);
        assertNotEquals(c.getOwner(), c1.getOwner());
        c1.seal();
        c1.check();
        c1.traceErrors();
        assertEquals(1, c1.getErrors().size());
        ErrorRecord error = c1.getErrors().get(0);
        assertEquals(Errors.FORBIDDEN, error.getError());

        // good contract change: creator is an owner

        Contract c2 = c.createRevision(TestKeys.privateKey(0), ownerKey1);
        assertEquals(c, ((RoleLink) c.getPermissions().getFirst("change_owner").getRole()).getContract());

//        System.out.println("c owner   : "+c.getRole("owner"));
//        System.out.println("c2 creator: "+c2.getRole("creator"));

        assertEquals(c.getOwner(), ((RoleLink) c.getPermissions().getFirst("change_owner").getRole()).getRole());
        assertEquals(c2, ((RoleLink) c2.getPermissions().getFirst("change_owner").getRole()).getContract());
        assertEquals(c, ((RoleLink) c.getPermissions().getFirst("change_owner").getRole()).getContract());
        c2.setOwnerKey(ownerKey3);
        assertNotEquals(c.getOwner(), c2.getOwner());
        assertEquals(c.getOwner(), ((RoleLink) c.getPermissions().getFirst("change_owner").getRole()).getRole());

        sealCheckTrace(c2, true);
    }

    @Test
    public void changeNumber() throws Exception {
        Contract c = Contract.fromDslFile(SUBSCRIPTION_PATH);
        c.setOwnerKey(ownerKey2);
//        c.getPermission("change_value")


//        assertThat(c.getPermissions().get("change_owner").getRole(), is(instanceOf(RoleLink.class)));
//        assertTrue(c.getPermissions().get("change_owner").getRole().isAllowedForKeys(new HashSet(Do.listOf(ownerKey1))));

//        System.out.println("Owner now :" + c.getOwner());
//        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        assertEquals(1000, d.getIntOrThrow("U_left"));
        c.seal();
        c.check();
        c.traceErrors();

        assertTrue(c.check());


        // valid decrement: by owner
        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        assertNotSame(c, c1);
        assertNotSame(d, d1);

        assertEquals(1000, d.getIntOrThrow("U_left"));
        assertEquals(1000, d1.getIntOrThrow("U_left"));
        d1.addToInt("U_left", -10);
        assertEquals(990, d1.getIntOrThrow("U_left"));
        assertEquals(1000, d.getIntOrThrow("U_left"));

        sealCheckTrace(c1, true);

        // not valid: increment by owner
        d1.addToInt("U_left", +11);
        assertEquals(1001, d1.getIntOrThrow("U_left"));
        assertEquals(1000, d.getIntOrThrow("U_left"));

        sealCheckTrace(c1, false);

        // todo: check valid increment by issuer
    }

    @Test
    public void shouldModifyStateDataValues() throws  Exception {
        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "An example of smart contract.";
        final String newValue = "UniversaSmartContract";
        final String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, true);
    }

    @Test
    public void shouldModifyAnyValueForKey() throws  Exception {
        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "35";
        final String newValue = "303434935245";
        final String field = "units";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, true);
    }

    @Test
    public void shouldPopulateWithEmptyStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "An example of smart contract.";
        final String newValue = "";
        final String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, true);
    }

    @Test
    public void shouldNotPopulateWithEmptyStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "2";
        final String newValue = "";
        final String field = "direction";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, false);
    }

    @Test
    public void shouldNotPopulateWithNullStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "2";
        final String newValue = null;
        final String field = "direction";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, false);
    }

    @Test
    public void shouldNotModifyDescEmptyStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "An example of smart contract.";
        final String newValue = "wrong value.";
        final String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, false);
    }

    @Test
    public void shouldModifyDescNullStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "An example of smart contract.";
        final String newValue = null;
        final String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, true);
    }


    @Test
    public void shouldNotModifyStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        final String oldValue = "1";
        final String newValue = "2";
        final String field = "option";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        sealCheckTrace(c1, false);
    }

    @Test
    public void shouldModifySeveralStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        String oldValue = "An example of smart contract.";
        String newValue = "UniversaSmartContract";
        String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        oldValue = "blockchain-partnership.";
        newValue = "blockchain-universa.";
        field = "partner_name";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        d1.addToInt("direction", 3);

        sealCheckTrace(c1, true);
    }

    @Test
    public void shouldNotModifySeveralStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

        Contract c = basicContractCreation(SUBSCRIPTION_WITH_DATA, PRIVATE_KEY, ownerKey2);
        Binder d = c.getStateData();

        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        String oldValue = "An example of smart contract.";
        String newValue = "UniversaSmartContract";
        String field = "description";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        oldValue = "blockchain-partnership.";
        newValue = "blockchain-universa.";
        field = "partner_name";

        setAndCheckOldNewValues(d, d1, oldValue, newValue, field);

        d1.addToInt("U_left", -50);
        d1.addToInt("direction", 5);
        d1.addToInt("option", -1);

        sealCheckTrace(c1, false);
    }

    @Test
    public void testInvalidChild() throws Exception {
        Contract c = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        c.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");

        c.setOwnerKey(ownerKey1);
        assertThat(c.getPermissions().getFirst("change_owner").getRole(), is(instanceOf(RoleLink.class)));
        assertTrue(c.getPermissions().getFirst("change_owner").getRole().isAllowedForKeys(new HashSet(Do.listOf(ownerKey1))));

//        System.out.println("Owner now :" + c.getOwner());
//        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.seal();
        assertTrue(c.check());

        Contract c2 = c.createRevision(TestKeys.privateKey(0), ownerKey1);
        c2.setOwnerKey(ownerKey3);

        // Let's attach a just a bad contract. not properly signed in our case.
        Contract badc = Contract.fromDslFile(rootPath + "simple_root_contract.yml");
        badc.addSignerKey(ownerKey1);
        badc.setOwnerKey(ownerKey1);
        badc.seal();
        assertFalse(badc.check());

        c2.addNewItems(badc);

        // now c2 should be bad: it tries to create a bad contract!
        sealCheckTrace(c2, false);

        // and now let's add bad contract as a child. Actually it is a good contract, but, it is the same revision
        // as the parent. This should not be allowed too.
        Contract c3 = c.createRevision(ownerKey1);
        c3.setOwnerKey(ownerKey2);
        c3.seal();
        assertTrue(c3.isOk());

        c2.getNewItems().clear();
        c2.addNewItems(c3);

        assertEquals(1, c2.getNewItems().size());

        sealCheckTrace(c2, false);
    }

    @Test
    public void changeOwnerWithReference() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));
        llcPrivateKeys.add(new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<String> references = new HashSet<>();
        references.add("certification_contract");

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        Contract c = Contract.fromDslFile(rootPath + "references/NotaryWithReferenceDSLTemplate.yml");
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        c.addNewItems(jobCertificate);

        Role r = c.getPermissions().getFirst("change_owner").getRole();
        assertThat(r, is(instanceOf(ListRole.class)));
        assertFalse(r.isAllowedForKeys(stepaPublicKeys));
        c.getValidRoleReferences().addAll(references);
        assertTrue(r.isAllowedForKeys(stepaPublicKeys));

        System.out.println("Owner now :" + c.getOwner());
        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());
        assertEquals(c, (c.getPermissions().getFirst("change_owner").getRole()).getContract());

        // Bad contract change: owner has no right to change owner ;)
        Contract c1 = c.createRevision(TestKeys.privateKey(0));
        c1.setOwnerKey(ownerKey2);
        c1.addNewItems(jobCertificate);
        assertNotEquals(c.getOwner(), c1.getOwner());
        c1.seal();
        c1.check();
        c1.traceErrors();
        assertEquals(1, c1.getErrors().size());
        for(ErrorRecord error : c1.getErrors()) {
            assertThat(error.getError(), anyOf(equalTo(Errors.FORBIDDEN), equalTo(Errors.FAILED_CHECK)));
        }

        // bad contract change: good key but bad reference

        Contract c2 = c.createRevision(stepaPrivateKeys);
        c2.setOwnerKey(ownerKey3);
        assertEquals(c2, c2.getPermissions().getFirst("change_owner").getRole().getContract());
        assertNotEquals(c.getOwner(), c2.getOwner());
        c2.seal();
        c2.check();
        c2.traceErrors();
        assertEquals(1, c2.getErrors().size());
        for(ErrorRecord error : c1.getErrors()) {
            assertThat(error.getError(), anyOf(equalTo(Errors.FORBIDDEN), equalTo(Errors.FAILED_CHECK)));
        }

        // good contract change: creator is an owner

        Contract c3 = c.createRevision(stepaPrivateKeys);
        c3.setOwnerKey(ownerKey3);
        c3.addNewItems(jobCertificate);
        assertEquals(c3, c3.getPermissions().getFirst("change_owner").getRole().getContract());
        assertNotEquals(c.getOwner(), c3.getOwner());

        sealCheckTrace(c3, true);
    }

    @Test
    public void revokeWithReference() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<String> references = new HashSet<>();
        references.add("certification_contract");

        Contract referencedItem = new Contract();
        referencedItem.seal();
        Contract c = Contract.fromDslFile(rootPath + "references/NotaryWithReferenceDSLTemplate.yml");
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        c.findReferenceByName("certification_contract").addMatchingItem(referencedItem);

        Role r = c.getPermissions().getFirst("revoke").getRole();
        assertThat(r, is(instanceOf(ListRole.class)));
        assertFalse(r.isAllowedForKeys(stepaPublicKeys));
        c.getValidRoleReferences().addAll(references);
        assertTrue(r.isAllowedForKeys(stepaPublicKeys));

        System.out.println("revoke permission :" + c.getPermissions().get("revoke"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());
        assertEquals(c, (c.getPermissions().getFirst("revoke").getRole()).getContract());

        // Bad contract change: owner has no right to change owner ;)
        Contract c1 = c.createRevision(TestKeys.privateKey(0));
        c1.setOwnerKey(ownerKey2);
        c1.findReferenceByName("certification_contract").addMatchingItem(referencedItem);
        assertNotEquals(c.getOwner(), c1.getOwner());
        c1.seal();
        c1.check();
        c1.traceErrors();
        assertEquals(1, c1.getErrors().size());
        for(ErrorRecord error : c1.getErrors()) {
            assertThat(error.getError(), anyOf(equalTo(Errors.FORBIDDEN), equalTo(Errors.FAILED_CHECK)));
        }

        // bad contract change: good key but bad reference

        Contract c2 = ContractsService.createRevocation(c, stepaPrivateKeys.iterator().next());
        c2.getRevoking().get(0).removeReferencedItem(referencedItem);
        assertEquals(c2.getRevoking().get(0), c2.getRevoking().get(0).getPermissions().getFirst("revoke").getRole().getContract());
        c2.seal();
        c2.check();
        c2.traceErrors();
        assertEquals(1, c2.getErrors().size());
        for(ErrorRecord error : c1.getErrors()) {
            assertThat(error.getError(), anyOf(equalTo(Errors.FORBIDDEN), equalTo(Errors.FAILED_CHECK)));
        }

        // good contract change: creator is an owner

        Contract c3 = ContractsService.createRevocation(c, stepaPrivateKeys.iterator().next());
        Reference ref = new Reference();
        ref.name = "certification_contract";
        ref.type = Reference.TYPE_EXISTING_DEFINITION;
        ref.addMatchingItem(new Contract());
        c3.getRevoking().get(0).getReferences().put(ref.name, ref);
//        assertEquals(c3.getRevoking().get(0), c3.getPermissions().getFirst("revoke").getRole().getContract());

        System.out.println("-------------");
        sealCheckTrace(c3, true);
    }

    @Test
    public void splitJoinWithReference() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));
        llcPrivateKeys.add(new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<String> references = new HashSet<>();
        references.add("certification_contract");

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        Contract c = Contract.fromDslFile(rootPath + "references/TokenWithReferenceDSLTemplate.yml");
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        c.addNewItems(jobCertificate);

        Role r = c.getPermissions().getFirst("split_join").getRole();
        assertThat(r, is(instanceOf(ListRole.class)));
        assertFalse(r.isAllowedForKeys(stepaPublicKeys));
        c.getValidRoleReferences().addAll(references);
        assertTrue(r.isAllowedForKeys(stepaPublicKeys));

        System.out.println("split join permission :" + c.getPermissions().get("split_join"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());
        assertEquals(c, (c.getPermissions().getFirst("split_join").getRole()).getContract());

        // Bad contract change: owner has no right to change owner ;)
        Set<PrivateKey> badPrivateKeys = new HashSet<>();
        badPrivateKeys.add(TestKeys.privateKey(0));
        Contract c1 = ContractsService.createSplit(c, new BigDecimal("1"), "amount", badPrivateKeys);
        c1.seal();
        c1.check();
        c1.traceErrors();
//        assertEquals(1, c1.getErrors().size());
//        ErrorRecord error = c1.getErrors().get(0);
//        assertEquals(Errors.FORBIDDEN, error.getError());
        assertFalse(c1.isOk());

        // bad contract change: good key but no reference

        Contract c2 = ContractsService.createSplit(c, new BigDecimal("1"), "amount", stepaPrivateKeys);
        c2.createRole("creator", c2.getRole("owner"));
        c2.getNew().get(0).createRole("creator", c2.getNew().get(0).getRole("owner"));
        assertEquals(c2, c2.getPermissions().getFirst("split_join").getRole().getContract());

        System.out.println("-------------");
        c1.seal();
        c1.check();
        c1.traceErrors();
//        assertEquals(1, c1.getErrors().size());
//        ErrorRecord error = c1.getErrors().get(0);
//        assertEquals(Errors.FORBIDDEN, error.getError());
        assertFalse(c1.isOk());

        // good contract change: creator is an owner

        Contract c3 = ContractsService.createSplit(c, new BigDecimal("1"), "amount", stepaPrivateKeys);
        c3.createRole("creator", c3.getRole("owner"));
        c3.getNew().get(0).createRole("creator", c3.getNew().get(0).getRole("owner"));
        c3.addNewItems(jobCertificate);
        assertEquals(c3, c3.getPermissions().getFirst("split_join").getRole().getContract());

        System.out.println("-------------");
        sealCheckTrace(c3, true);
    }

    @Test
    public void decrementWithReference() throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PrivateKey>  llcPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));
        llcPrivateKeys.add(new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey")));

        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        for (PrivateKey pk : stepaPrivateKeys) {
            stepaPublicKeys.add(pk.getPublicKey());
        }
        Set<String> references = new HashSet<>();
        references.add("certification_contract");

        Contract jobCertificate = new Contract(llcPrivateKeys.iterator().next());
        jobCertificate.setOwnerKeys(stepaPublicKeys);
        jobCertificate.getDefinition().getData().set("issuer", "Roga & Kopita");
        jobCertificate.getDefinition().getData().set("type", "chief accountant assignment");
        jobCertificate.seal();

        Contract c = Contract.fromDslFile(rootPath + "references/AbonementWithReferenceDSLTemplate.yml");
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        c.addNewItems(jobCertificate);

        Role r = c.getPermissions().getFirst("decrement_permission").getRole();
        assertThat(r, is(instanceOf(ListRole.class)));
        assertFalse(r.isAllowedForKeys(stepaPublicKeys));
        c.getValidRoleReferences().addAll(references);
        assertTrue(r.isAllowedForKeys(stepaPublicKeys));

        System.out.println("decrement permission :" + c.getPermissions().get("decrement_permission"));

        c.seal();
        c.check();
        c.traceErrors();
        assertTrue(c.isOk());
        assertEquals(c, (c.getPermissions().getFirst("decrement_permission").getRole()).getContract());

        // Bad contract change: owner has no right to change owner ;)
        Set<PrivateKey> badPrivateKeys = new HashSet<>();
        badPrivateKeys.add(TestKeys.privateKey(0));
        Contract c1 = c.createRevision(TestKeys.privateKey(0));
        c1.getStateData().set("units", c.getStateData().getIntOrThrow("units") - 1);

        c1.seal();
        c1.check();
        c1.traceErrors();
//        assertEquals(1, c1.getErrors().size());
//        ErrorRecord error = c1.getErrors().get(0);
//        assertEquals(Errors.FORBIDDEN, error.getError());
        assertFalse(c1.isOk());

        // bad contract change: good key but no reference

        Contract c2 = c.createRevision(stepaPrivateKeys);
        c2.getStateData().set("units", c.getStateData().getIntOrThrow("units") - 1);
        assertEquals(c2, c2.getPermissions().getFirst("decrement_permission").getRole().getContract());

        System.out.println("-------------");
        c1.seal();
        c1.check();
        c1.traceErrors();
//        assertEquals(1, c1.getErrors().size());
//        ErrorRecord error = c1.getErrors().get(0);
//        assertEquals(Errors.FORBIDDEN, error.getError());
        assertFalse(c1.isOk());

        // good contract change: creator is an owner

        Contract c3 = c.createRevision(stepaPrivateKeys);
        c3.getStateData().set("units", c.getStateData().getIntOrThrow("units") - 1);
        c3.addNewItems(jobCertificate);
        assertEquals(c3, c3.getPermissions().getFirst("decrement_permission").getRole().getContract());

        System.out.println("-------------");
        sealCheckTrace(c3, true);
    }

//    @Test
//    public void shouldCheckSplitJoinAndSent80coins() throws Exception {
////        String transactionName = "./src/test_contracts/transaction/0a875b1f-d979-45d4-85f5-a388e70692a3.transaction";
//
//        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
//        c.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//        c.seal();
//        assertTrue(c.check());
//        c.traceErrors();
//
//        assertEquals(100, c.getStateData().get("amount"));
//
//
//        // 80
//        Contract cRev = c.createRevision();
//        Contract c2 = cRev.splitValue("amount", new Decimal(80));
//        c2.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//
////        Contract contract = readContract(transactionName, true);
//
//        sealCheckTrace(c2, true);
//    }
//
//    @Test
//    public void shouldCheckSplitJoinAndSent50coins() throws Exception {
////        String transactionName = "./src/test_contracts/transaction/2a8960bb-9a30-4173-8702-42084553e9b4.transaction";
//
//        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
//        c.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//        c.seal();
//        assertTrue(c.check());
//        c.traceErrors();
//
//        assertEquals(100, c.getStateData().get("amount"));
//
//
//        // 50
//        Contract cRev = c.createRevision();
//        Contract c2 = cRev.splitValue("amount", new Decimal(50));
//        c2.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//
////        Contract contract = readContract(transactionName, true);
//
//        sealCheckTrace(c2, true);
//    }
//
//    @Test
//    public void shouldCheckSplitJoinAndSentTest() throws Exception {
////        String transactionName = "./src/test_contracts/transaction/b8f8a512-8c45-4744-be4e-d6788729b2a7.transaction";
//
//        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
//        c.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//        c.seal();
//        assertTrue(c.check());
//        c.traceErrors();
//
//        assertEquals(100, c.getStateData().get("amount"));
//
//
//        // 20
//        Contract cRev = c.createRevision();
//        Contract c2 = cRev.splitValue("amount", new Decimal(20));
//        c2.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//
////        Contract contract = readContract(transactionName, true);
//
//        sealCheckTrace(c2, true);
//    }
//
//    @Test
//    public void sendTwice() throws Exception {
////        String transactionName = "./src/test_contracts/transaction/93441e20-242a-4e91-b283-8d0fd5f624dd.transaction";
//
//        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
//        c.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//        c.seal();
//        assertTrue(c.check());
//        c.traceErrors();
//
//        assertEquals(100, c.getStateData().get("amount"));
//
//
//        // 80
//        Contract cRev = c.createRevision();
//        cRev.splitValue("amount", new Decimal(80));
//        Contract c2 = cRev.splitValue("amount", new Decimal(80));
//        c2.addSignerKeyFromFile(rootPath +"_xer0yfe2nn1xthc.private.unikey");
//
////        Contract contract = readContract(transactionName, true);
//
//        sealCheckTrace(c2, true);
//    }

    private void setAndCheckOldNewValues(Binder d, Binder d1, String oldValue, String newValue, String field) {
        assertEquals(oldValue, d.getString(field));
        assertEquals(oldValue, d1.getString(field));

        d1.put(field, newValue);

        assertEquals(oldValue, d.getString(field));
        assertEquals(newValue, d1.getString(field, null));
    }

    private Contract basicContractCreation(final String fileName, final String keyFileName, final PrivateKey key) throws Exception {
        Contract c = Contract.fromDslFile(rootPath + fileName);
        c.setOwnerKey(key);
        c.addSignerKeyFromFile(rootPath + keyFileName);
        c.seal();
        c.check();
        c.traceErrors();

        assertTrue(c.check());
        return c;
    }

    @Test
    public void badRevokeAnother() throws Exception {
        Contract c1 = new Contract(ownerKey1);
        RoleLink rl = new RoleLink("@revoke", "owner");
        rl.setContract(c1);
        c1.addPermission(new RevokePermission(rl));
        c1.seal();
        Contract c2 = new Contract(ownerKey2);
        c2.seal();
        Assert.assertTrue(c2.check());
        Contract c3 = c2.createRevision(ownerKey2);
        //to prevent "state is identical"
        c3.setOwnerKeys(ownerKey3);
        c3.addRevokingItems(c1);
        c3.seal();
        assertFalse(c3.check());
    }

    @Test
    public void goodRevokeAnother() throws Exception {
        Contract c1 = new Contract(ownerKey1);
        RoleLink rl = new RoleLink("@revoke", "owner");
        rl.setContract(c1);
        c1.addPermission(new RevokePermission(rl));
        c1.seal();
        Contract c2 = new Contract(ownerKey2);
        c2.seal();
        Assert.assertTrue(c2.check());
        Contract c3 = c2.createRevision(ownerKey2);
        //to prevent "state is identical"
        c3.setOwnerKeys(ownerKey3);
        c3.addSignerKey(ownerKey1);
        c3.addRevokingItems(c1);
        c3.seal();
        assertTrue(c3.check());
    }

    @Test
    public void revokeAnotherAfterSplit() throws Exception {
        Contract c1 = new Contract(ownerKey1);
        RoleLink rl = new RoleLink("@revoke", "owner");
        rl.setContract(c1);
        c1.addPermission(new RevokePermission(rl));
        c1.seal();
        Assert.assertTrue(c1.check());

        Contract[] array = c1.split(2);
        Contract c2 = array[0];
        Contract c3 = array[1];
        c2.addSignerKey(ownerKey1);
        c3.addSignerKey(ownerKey1);
        //change owner of c3
        c3.setOwnerKeys(ownerKey2);
        c2.seal();
        c3.seal();

        Assert.assertTrue(c2.check());
        Assert.assertTrue(c3.check());

        Contract c4 = c2.createRevision(ownerKey1);
        //change owner of c4 to prevent "state is identical"
        c4.setOwnerKeys(ownerKey3);
        c4.addSignerKey(ownerKey1);
        //try revoke c3 (requires ownerKey2)
        c4.addRevokingItems(c3);
        c4.seal();
        assertFalse(c4.check());
    }
}