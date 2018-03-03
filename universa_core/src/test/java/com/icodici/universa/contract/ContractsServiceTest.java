/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Do;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.ZonedDateTime;
import java.util.HashSet;
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

        Contract payment = ContractsService.createFreshTU(100, publicKeys, true);

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
    public void createTU() throws Exception
    {
        PrivateKey privateKey = TestKeys.privateKey(3);
        Set<PublicKey> keys = new HashSet();
        keys.add(privateKey.getPublicKey());
        Contract tu = ContractsService.createFreshTU(100, keys);
        tu.check();
        tu.traceErrors();

        assertEquals(true, tu.getRole("owner").isAllowedForKeys(keys));
        assertEquals(100, tu.getStateData().getIntOrThrow("transaction_units"));


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read( "./src/test_contracts/keys/tu_key.private.unikey"));
        Set<PublicKey> badKeys = new HashSet();
        badKeys.add(manufacturePrivateKey.getPublicKey());
        assertEquals(false, tu.getRole("owner").isAllowedForKeys(badKeys));
    }

    @Test
    public void createTestTU() throws Exception
    {
        PrivateKey privateKey = TestKeys.privateKey(3);
        Set<PublicKey> keys = new HashSet();
        keys.add(privateKey.getPublicKey());
        Contract tu = ContractsService.createFreshTU(100, keys, true);
        tu.check();
        tu.traceErrors();

        assertEquals(true, tu.getRole("owner").isAllowedForKeys(keys));
        assertEquals(100, tu.getStateData().getIntOrThrow("transaction_units"));
        assertEquals(10000, tu.getStateData().getIntOrThrow("test_transaction_units"));


        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read( "./src/test_contracts/keys/tu_key.private.unikey"));
        Set<PublicKey> badKeys = new HashSet();
        badKeys.add(manufacturePrivateKey.getPublicKey());
        assertEquals(false, tu.getRole("owner").isAllowedForKeys(badKeys));
    }
}