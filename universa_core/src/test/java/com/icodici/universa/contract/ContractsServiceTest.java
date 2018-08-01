/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ContractsServiceTest extends ContractTestBase {

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
        c.setOwnerKeys(new KeyRecord(goodKey.getPublicKey()));
        c.seal();

        Contract revokeContract = c.createRevocation(goodKey);

        revokeContract.check();
        assertTrue(revokeContract.isOk());
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

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    private void checkCreateParcel(String contract_file_payload, String contract_file_payment) throws Exception
    {
        final String ROOT_PATH = "./src/test_contracts/contractService/";

        PrivateKey privateKey = TestKeys.privateKey(3);

        Contract payload = Contract.fromDslFile(ROOT_PATH + contract_file_payload);
        payload.addSignerKey(privateKey);
        payload.seal();

        Contract payment = Contract.fromDslFile(ROOT_PATH + contract_file_payment);
        payment.addSignerKey(privateKey);
        payment.seal();

        Set<PrivateKey> PrivateKeys = new HashSet<>();
        PrivateKeys.add(privateKey);

        Parcel parcel = ContractsService.createParcel(payload, payment, 20, PrivateKeys);

        assertEquals(parcel.getPayloadContract().getState().getBranchId(), payload.getState().getBranchId());
        assertEquals(parcel.getPaymentContract().getState().getBranchId(), payment.getState().getBranchId());

        assertEquals(parcel.getPayloadContract().getStateData(), payload.getStateData());
        assertEquals(parcel.getPaymentContract().getDefinition().getData(), payment.getDefinition().getData());

        System.out.println("OK");

    }

    private void checkCreateParcelFotTestNet(String contract_file_payload, String contract_file_payment) throws Exception
    {
        final String ROOT_PATH = "./src/test_contracts/contractService/";

        PrivateKey privateKey = TestKeys.privateKey(3);
        Set<PrivateKey> privateKeys = new HashSet<>();
        privateKeys.add(privateKey);
        Set<PublicKey> publicKeys = new HashSet<>();
        publicKeys.add(privateKey.getPublicKey());

        Contract payload = Contract.fromDslFile(ROOT_PATH + contract_file_payload);
        payload.addSignerKey(privateKey);
        payload.seal();

        Contract payment = InnerContractsService.createFreshU(100, publicKeys, true);

        Parcel parcel = ContractsService.createParcel(payload, payment, 20, privateKeys, true);

        assertEquals(parcel.getPayloadContract().getState().getBranchId(), payload.getState().getBranchId());
        assertEquals(parcel.getPaymentContract().getState().getBranchId(), payment.getState().getBranchId());

        assertEquals(parcel.getPayloadContract().getStateData(), payload.getStateData());
        assertEquals(parcel.getPaymentContract().getDefinition().getData(), payment.getDefinition().getData());

        assertEquals(100, parcel.getPaymentContract().getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000 - 20, parcel.getPaymentContract().getStateData().getIntOrThrow("test_transaction_units"));

        System.out.println("OK");

    }

    @Test
    public void checkCreateGoodParcel() throws Exception
    {
        checkCreateParcel("simple_root_contract.yml", "simple_root_contract.yml");
    }

    @Test
    public void checkCreateParcelBadPayload() throws Exception
    {
        exception.expect(IllegalArgumentException.class);
        checkCreateParcel("bad_contract_payload.yml", "simple_root_contract.yml");
    }

    @Test
    public void checkCreateParcelBadPayment() throws Exception
    {
        exception.expect(NumberFormatException.class);
        checkCreateParcel("simple_root_contract.yml","bad_contract_payment.yml");
    }

    @Test
    public void checkCreateGoodParcelForTestNet() throws Exception
    {
        checkCreateParcelFotTestNet("simple_root_contract.yml", "simple_root_contract.yml");
    }

    @Test
    public void createU() throws Exception
    {
        PrivateKey privateKey = TestKeys.privateKey(3);
        Set<PublicKey> keys = new HashSet();
        keys.add(privateKey.getPublicKey());
        Contract u = InnerContractsService.createFreshU(100, keys);
        u.check();
        u.traceErrors();

        assertEquals(true, u.getRole("owner").isAllowedForKeys(keys));
        assertEquals(100, u.getStateData().getIntOrThrow("transaction_units"));


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read( "./src/test_contracts/keys/u_key.private.unikey"));
        Set<PublicKey> badKeys = new HashSet();
        badKeys.add(manufacturePrivateKey.getPublicKey());
        assertEquals(false, u.getRole("owner").isAllowedForKeys(badKeys));
    }

    @Test
    public void createTestU() throws Exception
    {
        PrivateKey privateKey = TestKeys.privateKey(3);
        Set<PublicKey> keys = new HashSet();
        keys.add(privateKey.getPublicKey());
        Contract u = InnerContractsService.createFreshU(100, keys, true);
        u.check();
        u.traceErrors();

        assertEquals(true, u.getRole("owner").isAllowedForKeys(keys));
        assertEquals(100, u.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, u.getStateData().getIntOrThrow("test_transaction_units"));


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read( "./src/test_contracts/keys/u_key.private.unikey"));
        Set<PublicKey> badKeys = new HashSet();
        badKeys.add(manufacturePrivateKey.getPublicKey());
        assertEquals(false, u.getRole("owner").isAllowedForKeys(badKeys));
    }

    @Test
    public void goodNotary() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        Contract notaryContract = ContractsService.createNotaryContract(martyPrivateKeys, stepaPublicKeys);

        notaryContract.check();
        notaryContract.traceErrors();
        assertTrue(notaryContract.isOk());

        assertTrue(notaryContract.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertTrue(notaryContract.getIssuer().isAllowedForKeys(martyPrivateKeys));
        assertTrue(notaryContract.getCreator().isAllowedForKeys(martyPrivateKeys));

        assertFalse(notaryContract.getOwner().isAllowedForKeys(martyPrivateKeys));
        assertFalse(notaryContract.getIssuer().isAllowedForKeys(stepaPublicKeys));
        assertFalse(notaryContract.getCreator().isAllowedForKeys(stepaPublicKeys));

        assertTrue(notaryContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(notaryContract.getCreatedAt().isBefore(ZonedDateTime.now()));


        assertTrue(notaryContract.isPermitted("revoke", stepaPublicKeys));
        assertTrue(notaryContract.isPermitted("revoke", martyPublicKeys));

        assertTrue(notaryContract.isPermitted("change_owner", stepaPublicKeys));
        assertFalse(notaryContract.isPermitted("change_owner", martyPublicKeys));
    }

    @Test
    public void goodToken() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        Contract tokenContract = ContractsService.createTokenContract(martyPrivateKeys, stepaPublicKeys, "100");

        tokenContract.check();
        tokenContract.traceErrors();
        assertTrue(tokenContract.isOk());

        assertTrue(tokenContract.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertTrue(tokenContract.getIssuer().isAllowedForKeys(martyPrivateKeys));
        assertTrue(tokenContract.getCreator().isAllowedForKeys(martyPrivateKeys));

        assertFalse(tokenContract.getOwner().isAllowedForKeys(martyPrivateKeys));
        assertFalse(tokenContract.getIssuer().isAllowedForKeys(stepaPublicKeys));
        assertFalse(tokenContract.getCreator().isAllowedForKeys(stepaPublicKeys));

        assertTrue(tokenContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(tokenContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertEquals(InnerContractsService.getDecimalField(tokenContract, "amount"), new Decimal(100));

        assertEquals(tokenContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = tokenContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), 0.01);
        assertEquals(splitJoinParams.get("min_unit"), 0.01);
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List)splitJoinParams.get("join_match_fields")).get(0), "state.origin");


        assertTrue(tokenContract.isPermitted("revoke", stepaPublicKeys));
        assertTrue(tokenContract.isPermitted("revoke", martyPublicKeys));

        assertTrue(tokenContract.isPermitted("change_owner", stepaPublicKeys));
        assertFalse(tokenContract.isPermitted("change_owner", martyPublicKeys));

        assertTrue(tokenContract.isPermitted("split_join", stepaPublicKeys));
        assertFalse(tokenContract.isPermitted("split_join", martyPublicKeys));
    }

    @Test
    public void goodShare() throws Exception {

        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();

        martyPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/marty_mcfly.private.unikey")));
        stepaPrivateKeys.add(new PrivateKey(Do.read(rootPath + "keys/stepan_mamontov.private.unikey")));

        for (PrivateKey pk : stepaPrivateKeys)
            stepaPublicKeys.add(pk.getPublicKey());

        for (PrivateKey pk : martyPrivateKeys)
            martyPublicKeys.add(pk.getPublicKey());

        Contract shareContract = ContractsService.createShareContract(martyPrivateKeys, stepaPublicKeys, "100");

        shareContract.check();
        shareContract.traceErrors();
        assertTrue(shareContract.isOk());

        assertTrue(shareContract.getOwner().isAllowedForKeys(stepaPublicKeys));
        assertTrue(shareContract.getIssuer().isAllowedForKeys(martyPrivateKeys));
        assertTrue(shareContract.getCreator().isAllowedForKeys(martyPrivateKeys));

        assertFalse(shareContract.getOwner().isAllowedForKeys(martyPrivateKeys));
        assertFalse(shareContract.getIssuer().isAllowedForKeys(stepaPublicKeys));
        assertFalse(shareContract.getCreator().isAllowedForKeys(stepaPublicKeys));

        assertTrue(shareContract.getExpiresAt().isAfter(ZonedDateTime.now().plusMonths(3)));
        assertTrue(shareContract.getCreatedAt().isBefore(ZonedDateTime.now()));

        assertEquals(InnerContractsService.getDecimalField(shareContract, "amount"), new Decimal(100));

        assertEquals(shareContract.getPermissions().get("split_join").size(), 1);

        Binder splitJoinParams = shareContract.getPermissions().get("split_join").iterator().next().getParams();
        assertEquals(splitJoinParams.get("min_value"), 1);
        assertEquals(splitJoinParams.get("min_unit"), 1);
        assertEquals(splitJoinParams.get("field_name"), "amount");
        assertTrue(splitJoinParams.get("join_match_fields") instanceof List);
        assertEquals(((List)splitJoinParams.get("join_match_fields")).get(0), "state.origin");


        assertTrue(shareContract.isPermitted("revoke", stepaPublicKeys));
        assertTrue(shareContract.isPermitted("revoke", martyPublicKeys));

        assertTrue(shareContract.isPermitted("change_owner", stepaPublicKeys));
        assertFalse(shareContract.isPermitted("change_owner", martyPublicKeys));

        assertTrue(shareContract.isPermitted("split_join", stepaPublicKeys));
        assertFalse(shareContract.isPermitted("split_join", martyPublicKeys));
    }
}