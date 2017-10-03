/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.HashSet;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class PermissionsTest extends ContractTestBase {

    private final String ROOT_CONTRACT = rootPath + "simple_root_contract.yml";

    public static final String SUBSCRIPTION = "subscription.yml";
    public static final String SUBSCRIPTION_WITH_DATA = "subscription_with_data.yml";
    public static final String PRIVATE_KEY = "_xer0yfe2nn1xthc.private.unikey";


    private final String SUBSCRIPTION_PATH = rootPath + SUBSCRIPTION;
    private final String PRIVATE_KEY_PATH = rootPath + PRIVATE_KEY;


    @Test
    public void newRevision() throws Exception {
        Contract c = Contract.fromYamlFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
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
    public void validPermissionIds() throws Exception {
        Contract c = Contract.fromYamlFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        byte[] sealed = c.seal();
        assertTrue(c.check());
        Binder s = DefaultBiMapper.serialize(c);
        s.getBinderOrThrow("definition","permissions");
    }

    @Test
    public void changeOwner() throws Exception {
        Contract c = Contract.fromYamlFile(ROOT_CONTRACT);
        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        PrivateKey ownerKey1 = TestKeys.privateKey(3);
        PrivateKey ownerKey2 = TestKeys.privateKey(1);
        PrivateKey ownerKey3 = TestKeys.privateKey(2);

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
        assert (cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey1))));
        assert (!cOwner.isAllowedForKeys(new HashSet<>(Do.listOf(ownerKey2))));

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
        PrivateKey ownerKey1 = TestKeys.privateKey(3);
        PrivateKey ownerKey2 = TestKeys.privateKey(1);
        PrivateKey ownerKey3 = TestKeys.privateKey(2);

        Contract c = Contract.fromYamlFile(SUBSCRIPTION_PATH);
        c.setOwnerKey(ownerKey2);
//        c.getPermission("change_value")


//        assertThat(c.getPermissions().get("change_owner").getRole(), is(instanceOf(RoleLink.class)));
//        assertTrue(c.getPermissions().get("change_owner").getRole().isAllowedForKeys(new HashSet(Do.listOf(ownerKey1))));

//        System.out.println("Owner now :" + c.getOwner());
//        System.out.println("change owner permission :" + c.getPermissions().get("change_owner"));

        c.addSignerKeyFromFile(PRIVATE_KEY_PATH);
        Binder d = c.getStateData();
        assertEquals(1000, d.getIntOrThrow("transactional_units_left"));
        c.seal();
        c.check();
        c.traceErrors();

        assertTrue(c.check());


        // valid decrement: by owner
        Contract c1 = c.createRevision(ownerKey2);
        Binder d1 = c1.getStateData();

        assertNotSame(c, c1);
        assertNotSame(d, d1);

        assertEquals(1000, d.getIntOrThrow("transactional_units_left"));
        assertEquals(1000, d1.getIntOrThrow("transactional_units_left"));
        d1.addToInt("transactional_units_left", -10);
        assertEquals(990, d1.getIntOrThrow("transactional_units_left"));
        assertEquals(1000, d.getIntOrThrow("transactional_units_left"));

        sealCheckTrace(c1, true);

        // not valid: increment by owner
        d1.addToInt("transactional_units_left", +11);
        assertEquals(1001, d1.getIntOrThrow("transactional_units_left"));
        assertEquals(1000, d.getIntOrThrow("transactional_units_left"));

        sealCheckTrace(c1, false);

        // todo: check valid increment by issuer
    }

    @Test
    public void shouldModifyStateDataValues() throws  Exception {
        PrivateKey ownerKey2 = TestKeys.privateKey(1);

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

        d1.addToInt("transactional_units_left", -50);
        d1.addToInt("direction", 5);
        d1.addToInt("option", -1);

        sealCheckTrace(c1, false);
    }

    private void setAndCheckOldNewValues(Binder d, Binder d1, String oldValue, String newValue, String field) {
        assertEquals(oldValue, d.getString(field));
        assertEquals(oldValue, d1.getString(field));

        d1.put(field, newValue);

        assertEquals(oldValue, d.getString(field));
        assertEquals(newValue, d1.getString(field, null));
    }

    private void sealCheckTrace(Contract c, boolean isOkShouldBeTrue) {
        c.seal();
        c.check();
        c.traceErrors();

        if (isOkShouldBeTrue)
            assertTrue(c.isOk());
        else
            assertFalse(c.isOk());
    }

    private Contract basicContractCreation(final String fileName, final String keyFileName, final PrivateKey key) throws Exception {
        Contract c = Contract.fromYamlFile(rootPath + fileName);
        c.setOwnerKey(key);
        c.addSignerKeyFromFile(rootPath + keyFileName);
        c.seal();
        c.check();
        c.traceErrors();

        assertTrue(c.check());
        return c;
    }
}