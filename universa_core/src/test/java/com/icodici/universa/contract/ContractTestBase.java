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
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.Quantiser;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class ContractTestBase extends TestCase {


    protected String rootPath = "./src/test_contracts/";

    protected final String ROOT_CONTRACT = rootPath + "simple_root_contract.yml";

    protected final String CONTRACT_WITH_REFERENCE = rootPath + "simple_root_contract.yml";

    protected static final String SUBSCRIPTION = "subscription.yml";
    protected static final String SUBSCRIPTION_WITH_DATA = "subscription_with_data.yml";
    protected static final String PRIVATE_KEY = "_xer0yfe2nn1xthc.private.unikey";
    protected static final String PRIVATE_KEY_2048 = "keys/marty_mcfly.private.unikey";

    protected final String SUBSCRIPTION_PATH = rootPath + SUBSCRIPTION;
    protected final String PRIVATE_KEY_PATH = rootPath + PRIVATE_KEY;
    protected final String PRIVATE_KEY2048_PATH = rootPath + PRIVATE_KEY_2048;

    protected static final String FIELD_NAME = "amount";

    protected PrivateKey ownerKey1;
    protected PrivateKey ownerKey2;
    protected PrivateKey ownerKey3;


    @Before
    public void setUp() throws Exception {
        ownerKey1 = TestKeys.privateKey(3);
        ownerKey2 = TestKeys.privateKey(1);
        ownerKey3 = TestKeys.privateKey(2);
    }

    protected void assertProperSimpleRootContract(Contract c) {
//        assertEquals(3, c.getApiLevel());

        // -- issuer
        KeyRecord issuer = c.getIssuer().getKeyRecords().iterator().next();
        assertNotNull(issuer);
        assertThat(issuer.getPublicKey(), is(instanceOf(PublicKey.class)));
        assertEquals(issuer.getStringOrThrow("name"), "Universa");

        // --- times
        ZonedDateTime t = c.getCreatedAt();
        assertAlmostSame(ZonedDateTime.now(), c.getCreatedAt());
        assertEquals("2022-08-05T17:25:37Z", c.getExpiresAt().withZoneSameInstant(ZoneOffset.UTC).toString());

        // -- data
        assertThat(c.getDefinition().getData().get("active_since"), is(instanceOf(ZonedDateTime.class)));
        assertEquals("2017-08-05T17:24:49Z", c.getDefinition()
                .getData()
                .getZonedDateTimeOrThrow("active_since")
                .withZoneSameInstant(ZoneOffset.UTC)
                .toString());
        assertEquals("access certificate", c.getDefinition().getData().get("type").toString());

        // -- state
        Contract.State state = c.getState();
        assertEquals(1, state.getRevision());
        assertEquals(c.getCreatedAt().truncatedTo(ChronoUnit.SECONDS),
                     state.getCreatedAt().truncatedTo(ChronoUnit.SECONDS));
        KeyRecord owner = c.testGetOwner();
        assertNotNull(owner);
        assertEquals("Pupkin", owner.getBinderOrThrow("name").getStringOrThrow("last"));

        // -- roles
        Map<String, Role> roles = c.getRoles();
        assertEquals(owner, roles.get("owner").getKeyRecord());
        assertEquals(issuer, roles.get("issuer").getKeyRecord());
        assertEquals(issuer, roles.get("creator").getKeyRecord());

        // -- permissions
        try {
            assertTrue(c.isPermitted("change_owner", owner));
            assertFalse(c.isPermitted("change_owner", issuer));
            assertTrue(c.isPermitted("revoke", owner));
            assertFalse(c.isPermitted("revoke", issuer));
        } catch (Quantiser.QuantiserException e) {
            e.printStackTrace();
        }
    }

    protected void assertSameContracts(Contract originContract, Contract checkingContract) {

        // check issuer
        KeyRecord originIssuer = originContract.getIssuer().getKeyRecords().iterator().next();
        KeyRecord checkingIssuer = checkingContract.getIssuer().getKeyRecords().iterator().next();
        assertNotNull(checkingIssuer);
        assertThat(checkingIssuer.getPublicKey(), is(instanceOf(PublicKey.class)));
        assertEquals(checkingIssuer, originIssuer);

        // check creator
        KeyRecord originCreator = originContract.getCreator().getKeyRecords().iterator().next();
        KeyRecord checkingCreator = checkingContract.getCreator().getKeyRecords().iterator().next();
        assertNotNull(checkingCreator);
        assertThat(checkingCreator.getPublicKey(), is(instanceOf(PublicKey.class)));
        assertEquals(checkingCreator, originCreator);

        // check owner
        KeyRecord originOwner = originContract.getOwner().getKeyRecords().iterator().next();
        KeyRecord checkingOwner = checkingContract.getOwner().getKeyRecords().iterator().next();
        assertNotNull(checkingOwner);
        assertThat(checkingOwner.getPublicKey(), is(instanceOf(PublicKey.class)));
        assertEquals(checkingOwner, originOwner);

        // --- times
        assertAlmostSame(ZonedDateTime.now(), checkingContract.getCreatedAt());
        assertEquals(originContract.getCreatedAt(), checkingContract.getCreatedAt());
        assertEquals(originContract.getExpiresAt(), checkingContract.getExpiresAt());

        // -- data
        for(Object k : originContract.getStateData().keySet()) {
            if(originContract.getStateData().get(k) instanceof byte[]) {
                assertArrayEquals((byte[]) originContract.getStateData().get(k), (byte[]) checkingContract.getStateData().get(k));
            } else {
                assertEquals(originContract.getStateData().get(k), checkingContract.getStateData().get(k));
            }
        }
        for(Object k : originContract.getDefinition().getData().keySet()) {
            if(originContract.getDefinition().getData().get(k) instanceof byte[]) {
                assertArrayEquals((byte[]) originContract.getDefinition().getData().get(k), (byte[]) checkingContract.getDefinition().getData().get(k));
            } else {
                assertEquals(originContract.getDefinition().getData().get(k), checkingContract.getDefinition().getData().get(k));
            }
        }

        // -- definition
        Contract.Definition originDefinition = originContract.getDefinition();
        Contract.Definition checkingDefinition = checkingContract.getDefinition();
        assertEquals(originDefinition.getExtendedType(), checkingDefinition.getExtendedType());

        // -- state
        Contract.State originState = originContract.getState();
        Contract.State checkingState = checkingContract.getState();
        assertEquals(originState.getRevision(), checkingState.getRevision());
        assertEquals(originState.getBranchId(), checkingState.getBranchId());
        assertEquals(originState.getBranchRevision(), checkingState.getBranchRevision());

        // -- references
        for (Reference ref: originContract.getReferences().values()) {
            Reference desRef = checkingContract.findReferenceByName(ref.getName());
            assertTrue(desRef != null);
            assertEquals(ref.getConditions(), desRef.getConditions());
        }

    }

    protected void sealCheckTrace(Contract c, boolean isOk) {
        c.seal();
        try {
            c.check();
        } catch (Quantiser.QuantiserException e) {
            e.printStackTrace();
        }
        c.traceErrors();

        if (isOk)
            assertTrue(c.isOk());
        else
            assertFalse(c.isOk());
    }

    protected Contract createCoin() throws IOException {
        return createCoin(rootPath + "coin.yml");
    }

    protected Contract createCoin100apiv3() throws IOException {
        Contract c = Contract.fromDslFile(rootPath + "coin100.yml");
        return c;
    }

    protected Contract createCoin100k2048apiv3() throws IOException {
        Contract c = Contract.fromDslFile(rootPath + "coin100k2048.yml");
        return c;
    }

    protected Contract createCoin(String yamlFilePath) throws IOException {
        Contract c = Contract.fromDslFile(yamlFilePath);
        c.setOwnerKey(ownerKey2);
        return c;
    }

    protected Contract createCoinWithAmount(String amount, String fieldName) throws Exception {
        Contract contract = createCoin();
        contract.getStateData().set(fieldName, new Decimal(amount));
        contract.addSignerKeyFromFile(PRIVATE_KEY_PATH);

        sealCheckTrace(contract, true);

        return contract;
    }
}
